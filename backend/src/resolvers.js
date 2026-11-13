// Platform resolvers. Each returns the JSON body sent to the app:
//   {status:'ready', downloadUrl, fileName, mimeType, title}
//   {status:'unavailable', reason, message, originalUrl}
//   {status:'auth_required', platform, message, authUrl, originalUrl}
//
// Only official endpoints are used:
//   Instagram - Instagram API with Instagram Login (graph.instagram.com), own Professional account media only
//   Facebook  - Graph API (graph.facebook.com), videos on Pages the user manages
//   TikTok    - public oEmbed (www.tiktok.com/oembed) to check the post; TikTok's APIs do not expose video files

import { parseLink, igShortcodeFromPermalink } from './links.js';

export const GRAPH_VERSION = 'v21.0';
const UA = 'SocialVideoDownloader-backend/0.1 (+https://github.com/robertlangat/Vediodownloader)';

const unavailable = (reason, message, originalUrl) => ({ status: 'unavailable', reason, message, originalUrl });

function safeName(s, fallback) {
  const base = String(s || '').split('\n')[0].replace(/[^\p{L}\p{N}._ -]/gu, '_').trim().slice(0, 60);
  return (base || fallback) + '.mp4';
}

/** Follow redirects of a short link manually (max 5 hops) and return the final URL. */
export async function expandShortLink(url, fetchImpl) {
  let current = url;
  for (let i = 0; i < 5; i++) {
    const res = await fetchImpl(current, { method: 'GET', redirect: 'manual', headers: { 'User-Agent': UA } });
    const loc = res.headers.get('location');
    if (res.status >= 300 && res.status < 400 && loc) {
      current = new URL(loc, current).toString();
      const p = parseLink(current);
      if (p && !p.isShortLink) return current;
      continue;
    }
    return current;
  }
  return current;
}

// ---------------- TikTok ----------------

export async function resolveTikTok(link, { fetchImpl }) {
  let res;
  try {
    res = await fetchImpl(`https://www.tiktok.com/oembed?url=${encodeURIComponent(link.url)}`, { headers: { 'User-Agent': UA } });
  } catch (e) {
    return { status: 'retry', message: "Couldn't reach TikTok. Try again." };
  }
  if (res.status === 400 || res.status === 404) {
    return unavailable('removed', 'This TikTok post is private, removed or the link is broken.', link.url);
  }
  if (!res.ok) return { status: 'retry', message: `TikTok answered HTTP ${res.status}. Try again.` };
  // oEmbed can answer 200 with an error body for missing/private posts, so check the payload too.
  const body = await res.json().catch(() => null);
  const failed = !body || (body.code !== undefined && body.code !== 0) || (body.status_code !== undefined && body.status_code !== 0);
  // A generic player iframe is returned even for unknown ids; real posts carry author/title metadata.
  if (failed || !(body.author_name || body.author_unique_id || body.title)) {
    return unavailable('removed', 'This TikTok post is private, removed or the link is broken.', link.url);
  }
  return unavailable(
    'not_downloadable',
    "This TikTok post exists, but TikTok's official APIs don't provide video files to other apps. " +
      "Open the post in TikTok and use Share → Save video (available when the creator allows downloads).",
    link.url,
  );
}

// ---------------- Instagram ----------------

async function igGet(url, fetchImpl) {
  const res = await fetchImpl(url, { headers: { 'User-Agent': UA } });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(json?.error?.message || `Instagram HTTP ${res.status}`);
    err.code = json?.error?.code;
    err.http = res.status;
    throw err;
  }
  return json;
}

export async function resolveInstagram(link, { fetchImpl, session, authUrl, maxPages = 20 }) {
  const ig = session?.instagram;
  if (!ig?.accessToken) {
    return {
      status: 'auth_required',
      platform: 'instagram',
      message: 'Connect your Instagram Professional account to download your own reels and videos.',
      authUrl,
      originalUrl: link.url,
    };
  }
  let target = link;
  if (link.isShortLink) {
    try {
      const expanded = parseLink(await expandShortLink(link.url, fetchImpl));
      if (expanded?.igShortcode) target = expanded;
    } catch { /* fall through */ }
    if (!target.igShortcode) {
      return unavailable('unresolved', "This Instagram share link couldn't be expanded. Open the post, tap ⋯ → Copy link, and paste the full /reel/ or /p/ link.", link.url);
    }
  }
  const wanted = target.igShortcode;
  const fields = 'id,media_type,media_url,permalink,shortcode,caption,timestamp';
  let next = `https://graph.instagram.com/${GRAPH_VERSION}/me/media?fields=${fields}&limit=100&access_token=${encodeURIComponent(ig.accessToken)}`;
  try {
    for (let page = 0; next && page < maxPages; page++) {
      const json = await igGet(next, fetchImpl);
      for (const m of json.data || []) {
        const code = m.shortcode || igShortcodeFromPermalink(m.permalink);
        if (code !== wanted) continue;
        let media = m;
        if (m.media_type === 'CAROUSEL_ALBUM') {
          const kids = await igGet(
            `https://graph.instagram.com/${GRAPH_VERSION}/${m.id}/children?fields=id,media_type,media_url&access_token=${encodeURIComponent(ig.accessToken)}`,
            fetchImpl,
          );
          media = (kids.data || []).find((k) => k.media_type === 'VIDEO') || null;
        }
        if (!media || media.media_type !== 'VIDEO') return unavailable('not_video', 'This Instagram post has no video.', link.url);
        if (!media.media_url) {
          return unavailable('not_downloadable', "Instagram didn't provide a file for this video (copyrighted audio can block it).", link.url);
        }
        return {
          status: 'ready',
          downloadUrl: media.media_url,
          fileName: safeName(m.caption, `instagram_${wanted}`),
          mimeType: 'video/mp4',
          title: (m.caption || '').slice(0, 100),
        };
      }
      next = json.paging?.next || null;
    }
  } catch (e) {
    if (e.code === 190) {
      return { status: 'auth_required', platform: 'instagram', message: 'Your Instagram connection expired. Connect again.', authUrl, originalUrl: link.url };
    }
    if (e.http >= 500 || e.http === 429 || e.code === 4 || e.code === 17) return { status: 'retry', message: 'Instagram is busy. Try again in a moment.' };
    return unavailable('error', `Instagram error: ${e.message}`, link.url);
  }
  return unavailable(
    'not_owned',
    `This post isn't in the connected Instagram account (@${ig.username || 'you'}). It may be private, removed, or posted by someone else — only your own videos can be downloaded.`,
    link.url,
  );
}

// ---------------- Facebook ----------------

async function fbGet(path, token, fetchImpl) {
  const sep = path.includes('?') ? '&' : '?';
  const res = await fetchImpl(`https://graph.facebook.com/${GRAPH_VERSION}/${path}${sep}access_token=${encodeURIComponent(token)}`, {
    headers: { 'User-Agent': UA },
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(json?.error?.message || `Facebook HTTP ${res.status}`);
    err.code = json?.error?.code;
    err.http = res.status;
    throw err;
  }
  return json;
}

export async function resolveFacebook(link, { fetchImpl, session, authUrl }) {
  const fb = session?.facebook;
  if (!fb?.accessToken) {
    return {
      status: 'auth_required',
      platform: 'facebook',
      message: 'Connect Facebook to download videos from Pages you manage.',
      authUrl,
      originalUrl: link.url,
    };
  }
  let target = link;
  if (!link.fbVideoId) {
    try {
      const expanded = parseLink(await expandShortLink(link.url, fetchImpl));
      if (expanded?.fbVideoId) target = expanded;
    } catch { /* ignore */ }
    if (!target.fbVideoId) {
      return unavailable('unresolved', "This Facebook share link couldn't be expanded. Open the video, copy the full link (…/videos/1234… or watch?v=…), and paste it.", link.url);
    }
  }
  const id = target.fbVideoId;
  const tokens = [...(fb.pages || []).map((p) => p.accessToken), fb.accessToken].filter(Boolean);
  let lastErr = null;
  for (const t of tokens) {
    try {
      const v = await fbGet(`${id}?fields=source,title,description,permalink_url`, t, fetchImpl);
      if (v.source) {
        return {
          status: 'ready',
          downloadUrl: v.source,
          fileName: safeName(v.title || v.description, `facebook_${id}`),
          mimeType: 'video/mp4',
          title: v.title || '',
        };
      }
      lastErr = { code: 'nosource' };
    } catch (e) {
      lastErr = e;
      if (e.code === 190 && t === fb.accessToken) {
        return { status: 'auth_required', platform: 'facebook', message: 'Your Facebook connection expired. Connect again.', authUrl, originalUrl: link.url };
      }
      if (e.http >= 500 || e.code === 4 || e.code === 17 || e.code === 32) return { status: 'retry', message: 'Facebook is busy. Try again in a moment.' };
    }
  }
  if (lastErr?.code === 'nosource') {
    return unavailable('not_downloadable', "Facebook didn't provide a downloadable file for this video.", link.url);
  }
  return unavailable(
    'not_owned',
    "Facebook didn't return this video for your account or the Pages you manage. It may be private, removed, or posted by someone else — only videos you own can be downloaded.",
    link.url,
  );
}
