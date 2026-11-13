// Link recognition shared by the resolvers. Mirrors app/.../data/LinkParser.kt.

const DIGITS = /^\d{5,}$/;
const IG_CODE = /^[A-Za-z0-9_-]{5,}$/;
const SHORT = /^[A-Za-z0-9_-]{3,}$/;
const IG_KINDS = new Set(['p', 'reel', 'reels', 'tv']);

function stripSub(host) {
  for (const p of ['www.', 'm.', 'web.', 'mobile.', 'mbasic.', 'touch.']) {
    if (host.startsWith(p)) host = host.slice(p.length);
  }
  return host;
}

export function extractUrl(text) {
  if (typeof text !== 'string') return null;
  const m = text.match(/https?:\/\/[^\s<>"']+/i);
  if (!m) return null;
  return m[0].replace(/[.,)\]!?;:]+$/, '');
}

/**
 * @returns {{platform:'tiktok'|'instagram'|'facebook', url:string, host:string,
 *            igShortcode?:string, fbVideoId?:string, tiktokVideoId?:string, isShortLink:boolean} | null}
 */
export function parseLink(text) {
  const raw = extractUrl(text);
  if (!raw) return null;
  let u;
  try { u = new URL(raw); } catch { return null; }
  if (u.protocol !== 'https:' && u.protocol !== 'http:') return null;
  u.protocol = 'https:';
  const full = u.hostname.toLowerCase();
  const host = stripSub(full);
  const s = u.pathname.split('/').filter(Boolean);
  const url = u.toString();

  if (host === 'tiktok.com' || full.endsWith('.tiktok.com')) {
    if (full.startsWith('vm.') || full.startsWith('vt.')) {
      return s.length === 1 && SHORT.test(s[0]) ? { platform: 'tiktok', url, host: full, isShortLink: true } : null;
    }
    const vi = s.indexOf('video');
    if (vi >= 0 && DIGITS.test(s[vi + 1] || '')) return { platform: 'tiktok', url, host: full, tiktokVideoId: s[vi + 1], isShortLink: false };
    if (s[0] === 't' && SHORT.test(s[1] || '')) return { platform: 'tiktok', url, host: full, isShortLink: true };
    if (s[0] === 'v' && DIGITS.test((s[1] || '').replace(/\.html$/, ''))) return { platform: 'tiktok', url, host: full, tiktokVideoId: s[1].replace(/\.html$/, ''), isShortLink: false };
    if (s[0] === 'embed') { const id = s.slice(1).find((x) => DIGITS.test(x)); if (id) return { platform: 'tiktok', url, host: full, tiktokVideoId: id, isShortLink: false }; }
    return null;
  }

  if (host === 'instagram.com' || host === 'instagr.am') {
    let code = null;
    if (IG_KINDS.has(s[0]) && IG_CODE.test(s[1] || '')) code = s[1];
    else if (s[0] === 'share') {
      if (IG_KINDS.has(s[1]) && IG_CODE.test(s[2] || '')) return { platform: 'instagram', url, host: full, isShortLink: true };
      if (IG_CODE.test(s[1] || '')) return { platform: 'instagram', url, host: full, isShortLink: true };
    } else if (s.length >= 3 && IG_KINDS.has(s[1]) && IG_CODE.test(s[2])) code = s[2];
    return code ? { platform: 'instagram', url, host: full, igShortcode: code, isShortLink: false } : null;
  }

  if (host === 'facebook.com' || host === 'fb.com' || host === 'fb.watch') {
    if (host === 'fb.watch') return s.length === 1 && SHORT.test(s[0]) ? { platform: 'facebook', url, host: full, isShortLink: true } : null;
    const v = u.searchParams.get('v');
    if ((s[0] === 'watch' || s[0] === 'video.php') && DIGITS.test(v || '')) return { platform: 'facebook', url, host: full, fbVideoId: v, isShortLink: false };
    if (s[0] === 'reel' && DIGITS.test(s[1] || '')) return { platform: 'facebook', url, host: full, fbVideoId: s[1], isShortLink: false };
    if (s[0] === 'share' && (s[1] === 'v' || s[1] === 'r') && SHORT.test(s[2] || '')) return { platform: 'facebook', url, host: full, isShortLink: true };
    const vi = s.indexOf('videos');
    if (vi >= 0) { const id = s.slice(vi + 1).find((x) => DIGITS.test(x)); if (id) return { platform: 'facebook', url, host: full, fbVideoId: id, isShortLink: false }; }
    return null;
  }
  return null;
}

/** Extracts the Instagram shortcode from a permalink such as https://www.instagram.com/reel/ABC123/ */
export function igShortcodeFromPermalink(permalink) {
  const p = parseLink(permalink || '');
  return p && p.platform === 'instagram' ? p.igShortcode || null : null;
}
