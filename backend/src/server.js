// Social Video Downloader backend: keeps provider secrets off the phone.
// Zero dependencies; Node.js 18+ (global fetch).
//
// Endpoints
//   GET  /health
//   POST /api/resolve                 {"url": "...", "platform": "instagram"}  (Authorization: Bearer <session>)
//   GET  /auth/instagram/start        -> Instagram Login
//   GET  /auth/instagram/callback
//   GET  /auth/facebook/start         -> Facebook Login
//   GET  /auth/facebook/callback
// After login the browser is sent to socialvideodl://auth?session=...&platform=... which reopens the app.

import http from 'node:http';
import crypto from 'node:crypto';
import { parseLink } from './links.js';
import { resolveTikTok, resolveInstagram, resolveFacebook, GRAPH_VERSION } from './resolvers.js';
import { SessionStore } from './store.js';

export function loadConfig(env = process.env) {
  return {
    port: Number(env.PORT || 8080),
    publicBaseUrl: (env.PUBLIC_BASE_URL || '').replace(/\/+$/, ''),
    appScheme: env.APP_CALLBACK || 'socialvideodl://auth',
    igAppId: env.INSTAGRAM_APP_ID || '',
    igAppSecret: env.INSTAGRAM_APP_SECRET || '',
    fbAppId: env.FACEBOOK_APP_ID || '',
    fbAppSecret: env.FACEBOOK_APP_SECRET || '',
    dataFile: env.SESSION_FILE || './data/sessions.json',
  };
}

function sendJson(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(body);
}

function sendHtml(res, code, html) {
  res.writeHead(code, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(html);
}

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

function returnToAppPage(appUrl, title, text) {
  return `<!doctype html><meta name=viewport content="width=device-width,initial-scale=1">
<title>${esc(title)}</title>
<body style="font-family:system-ui;padding:24px;max-width:480px;margin:auto">
<h2>${esc(title)}</h2><p>${esc(text)}</p>
<p><a href="${esc(appUrl)}" style="display:inline-block;padding:14px 20px;background:#3A4BD8;color:#fff;border-radius:24px;text-decoration:none">Return to Social Video Downloader</a></p>
<script>setTimeout(function(){location.href=${JSON.stringify(appUrl)}},300)</script></body>`;
}

async function readJson(req, limit = 16 * 1024) {
  let size = 0;
  const chunks = [];
  for await (const c of req) {
    size += c.length;
    if (size > limit) throw new Error('body too large');
    chunks.push(c);
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
}

export function createServer({ config = loadConfig(), fetchImpl = globalThis.fetch, store } = {}) {
  store = store || new SessionStore(config.dataFile);
  const pendingStates = new Map(); // state -> {platform, session, created}

  const base = (req) => config.publicBaseUrl || `https://${req.headers.host}`;
  const sessionFrom = (req) => {
    const m = /^Bearer\s+(.+)$/i.exec(req.headers.authorization || '');
    return m ? m[1].trim() : null;
  };
  const appRedirect = (params) => `${config.appScheme}?${new URLSearchParams(params).toString()}`;

  function newState(platform, session) {
    const state = crypto.randomBytes(16).toString('hex');
    pendingStates.set(state, { platform, session, created: Date.now() });
    for (const [k, v] of pendingStates) if (Date.now() - v.created > 15 * 60_000) pendingStates.delete(k);
    return state;
  }

  async function handleResolve(req, res) {
    let body;
    try {
      body = await readJson(req);
    } catch {
      return sendJson(res, 400, { status: 'unavailable', reason: 'invalid', message: 'Bad request.' });
    }
    const link = parseLink(body.url || '');
    if (!link) {
      return sendJson(res, 200, {
        status: 'unavailable', reason: 'invalid',
        message: 'This is not a supported TikTok, Instagram or Facebook video link.', originalUrl: body.url || '',
      });
    }
    const sessionId = sessionFrom(req);
    const session = sessionId ? store.get(sessionId) : null;
    const b = base(req);
    let out;
    if (link.platform === 'tiktok') {
      out = await resolveTikTok(link, { fetchImpl });
    } else if (link.platform === 'instagram') {
      if (!config.igAppId || !config.igAppSecret) {
        out = { status: 'unavailable', reason: 'not_configured', message: "Instagram downloads aren't set up on this server yet (missing INSTAGRAM_APP_ID / INSTAGRAM_APP_SECRET).", originalUrl: link.url };
      } else {
        out = await resolveInstagram(link, { fetchImpl, session, authUrl: `${b}/auth/instagram/start` });
      }
    } else {
      if (!config.fbAppId || !config.fbAppSecret) {
        out = { status: 'unavailable', reason: 'not_configured', message: "Facebook downloads aren't set up on this server yet (missing FACEBOOK_APP_ID / FACEBOOK_APP_SECRET).", originalUrl: link.url };
      } else {
        out = await resolveFacebook(link, { fetchImpl, session, authUrl: `${b}/auth/facebook/start` });
      }
    }
    sendJson(res, 200, out);
  }

  // ----- Instagram Login -----
  function igStart(req, res, url) {
    if (!config.igAppId) return sendHtml(res, 503, returnToAppPage(appRedirect({ error: 'instagram_not_configured' }), 'Not configured', 'Instagram is not configured on this server.'));
    const state = newState('instagram', url.searchParams.get('session'));
    const q = new URLSearchParams({
      client_id: config.igAppId,
      redirect_uri: `${base(req)}/auth/instagram/callback`,
      response_type: 'code',
      scope: 'instagram_business_basic',
      state,
    });
    res.writeHead(302, { Location: `https://www.instagram.com/oauth/authorize?${q}` });
    res.end();
  }

  async function igCallback(req, res, url) {
    const st = pendingStates.get(url.searchParams.get('state') || '');
    const code = url.searchParams.get('code');
    if (!st || !code) {
      return sendHtml(res, 400, returnToAppPage(appRedirect({ error: url.searchParams.get('error_reason') || 'cancelled' }), 'Not connected', 'Instagram login was cancelled or expired.'));
    }
    pendingStates.delete(url.searchParams.get('state'));
    try {
      const form = new URLSearchParams({
        client_id: config.igAppId, client_secret: config.igAppSecret, grant_type: 'authorization_code',
        redirect_uri: `${base(req)}/auth/instagram/callback`, code,
      });
      const tr = await fetchImpl('https://api.instagram.com/oauth/access_token', { method: 'POST', body: form });
      const tj = await tr.json();
      if (!tr.ok || !tj.access_token) throw new Error(tj.error_message || 'token exchange failed');
      let token = tj.access_token;
      let expiresAt = Date.now() + 3600_000;
      const lr = await fetchImpl(`https://graph.instagram.com/access_token?grant_type=ig_exchange_token&client_secret=${encodeURIComponent(config.igAppSecret)}&access_token=${encodeURIComponent(token)}`);
      if (lr.ok) {
        const lj = await lr.json();
        if (lj.access_token) { token = lj.access_token; expiresAt = Date.now() + (lj.expires_in || 5184000) * 1000; }
      }
      let username = '';
      const me = await fetchImpl(`https://graph.instagram.com/${GRAPH_VERSION}/me?fields=user_id,username&access_token=${encodeURIComponent(token)}`);
      if (me.ok) username = (await me.json()).username || '';
      const sid = store.upsert(st.session, { instagram: { accessToken: token, userId: String(tj.user_id || ''), username, expiresAt } });
      sendHtml(res, 200, returnToAppPage(appRedirect({ session: sid, platform: 'instagram' }), 'Instagram connected', `Connected @${username}. Returning to the app…`));
    } catch (e) {
      sendHtml(res, 502, returnToAppPage(appRedirect({ error: 'instagram_login_failed' }), 'Not connected', `Instagram login failed: ${e.message}`));
    }
  }

  // ----- Facebook Login -----
  function fbStart(req, res, url) {
    if (!config.fbAppId) return sendHtml(res, 503, returnToAppPage(appRedirect({ error: 'facebook_not_configured' }), 'Not configured', 'Facebook is not configured on this server.'));
    const state = newState('facebook', url.searchParams.get('session'));
    const q = new URLSearchParams({
      client_id: config.fbAppId,
      redirect_uri: `${base(req)}/auth/facebook/callback`,
      state,
      response_type: 'code',
      scope: 'pages_show_list,pages_read_engagement,pages_read_user_content',
    });
    res.writeHead(302, { Location: `https://www.facebook.com/${GRAPH_VERSION}/dialog/oauth?${q}` });
    res.end();
  }

  async function fbCallback(req, res, url) {
    const st = pendingStates.get(url.searchParams.get('state') || '');
    const code = url.searchParams.get('code');
    if (!st || !code) {
      return sendHtml(res, 400, returnToAppPage(appRedirect({ error: url.searchParams.get('error_reason') || 'cancelled' }), 'Not connected', 'Facebook login was cancelled or expired.'));
    }
    pendingStates.delete(url.searchParams.get('state'));
    const g = `https://graph.facebook.com/${GRAPH_VERSION}`;
    try {
      const q = new URLSearchParams({ client_id: config.fbAppId, client_secret: config.fbAppSecret, redirect_uri: `${base(req)}/auth/facebook/callback`, code });
      const tr = await fetchImpl(`${g}/oauth/access_token?${q}`);
      const tj = await tr.json();
      if (!tr.ok || !tj.access_token) throw new Error(tj.error?.message || 'token exchange failed');
      let token = tj.access_token;
      const lq = new URLSearchParams({ grant_type: 'fb_exchange_token', client_id: config.fbAppId, client_secret: config.fbAppSecret, fb_exchange_token: token });
      const lr = await fetchImpl(`${g}/oauth/access_token?${lq}`);
      if (lr.ok) { const lj = await lr.json(); if (lj.access_token) token = lj.access_token; }
      const pr = await fetchImpl(`${g}/me/accounts?fields=id,name,access_token&limit=100&access_token=${encodeURIComponent(token)}`);
      const pj = pr.ok ? await pr.json() : { data: [] };
      const pages = (pj.data || []).map((p) => ({ id: p.id, name: p.name, accessToken: p.access_token }));
      const sid = store.upsert(st.session, { facebook: { accessToken: token, pages } });
      sendHtml(res, 200, returnToAppPage(appRedirect({ session: sid, platform: 'facebook' }), 'Facebook connected', `Connected with ${pages.length} Page(s). Returning to the app…`));
    } catch (e) {
      sendHtml(res, 502, returnToAppPage(appRedirect({ error: 'facebook_login_failed' }), 'Not connected', `Facebook login failed: ${e.message}`));
    }
  }

  return http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://local');
    try {
      if (req.method === 'GET' && url.pathname === '/health') {
        return sendJson(res, 200, { ok: true, instagram: !!config.igAppId, facebook: !!config.fbAppId, tiktok: 'check-only' });
      }
      if (req.method === 'POST' && url.pathname === '/api/resolve') return await handleResolve(req, res);
      if (req.method === 'GET' && url.pathname === '/auth/instagram/start') return igStart(req, res, url);
      if (req.method === 'GET' && url.pathname === '/auth/instagram/callback') return await igCallback(req, res, url);
      if (req.method === 'GET' && url.pathname === '/auth/facebook/start') return fbStart(req, res, url);
      if (req.method === 'GET' && url.pathname === '/auth/facebook/callback') return await fbCallback(req, res, url);
      if (req.method === 'GET' && url.pathname === '/auth/tiktok/start') {
        return sendHtml(res, 200, returnToAppPage(appRedirect({ error: 'tiktok_has_no_download_api' }), 'TikTok', "TikTok's official APIs don't provide video files, so there is no account to connect. Use TikTok's own Save video option."));
      }
      sendJson(res, 404, { error: 'not found' });
    } catch (e) {
      console.error(e);
      if (!res.headersSent) sendJson(res, 500, { status: 'retry', message: 'Server error. Try again.' });
    }
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const config = loadConfig();
  createServer({ config }).listen(config.port, () => {
    console.log(`Social Video Downloader backend listening on :${config.port}`);
    console.log(`  Instagram: ${config.igAppId ? 'configured' : 'NOT configured'}  Facebook: ${config.fbAppId ? 'configured' : 'NOT configured'}  TikTok: check-only`);
  });
}
