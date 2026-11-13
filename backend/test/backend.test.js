import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseLink } from '../src/links.js';
import { resolveTikTok, resolveInstagram, resolveFacebook } from '../src/resolvers.js';
import { createServer } from '../src/server.js';
import { SessionStore } from '../src/store.js';

const json = (status, body, headers = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json', ...headers } });

/** Minimal fake fetch: routes by URL prefix. */
function fakeFetch(routes) {
  const calls = [];
  const fn = async (url, opts = {}) => {
    calls.push(String(url));
    for (const [prefix, handler] of routes) {
      if (String(url).startsWith(prefix)) return handler(String(url), opts);
    }
    return json(404, { error: { message: 'no route ' + url } });
  };
  fn.calls = calls;
  return fn;
}

test('parseLink recognises supported links and rejects others', () => {
  assert.equal(parseLink('https://www.tiktok.com/@a/video/6718335390845095173').platform, 'tiktok');
  assert.equal(parseLink('see https://vm.tiktok.com/ZMabc12/ !').isShortLink, true);
  assert.equal(parseLink('https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=x').igShortcode, 'C1a2B3c4D5e');
  assert.equal(parseLink('https://www.facebook.com/watch/?v=1234567890123').fbVideoId, '1234567890123');
  assert.equal(parseLink('https://www.facebook.com/Page/videos/title/1234567890123/').fbVideoId, '1234567890123');
  assert.equal(parseLink('https://fb.watch/abcDEF1/').isShortLink, true);
  assert.equal(parseLink('https://www.youtube.com/watch?v=x'), null);
  assert.equal(parseLink('https://evil-tiktok.com/@a/video/6718335390845095173'), null);
  assert.equal(parseLink('https://www.instagram.com/someone/'), null);
});

test('TikTok: public post -> not_downloadable, missing post -> removed', async () => {
  const ok = fakeFetch([['https://www.tiktok.com/oembed', () => json(200, { title: 't', author_name: 'a' })]]);
  const r1 = await resolveTikTok(parseLink('https://www.tiktok.com/@a/video/6718335390845095173'), { fetchImpl: ok });
  assert.equal(r1.status, 'unavailable');
  assert.equal(r1.reason, 'not_downloadable');
  assert.match(ok.calls[0], /oembed\?url=https%3A%2F%2Fwww\.tiktok\.com/);

  const gone = fakeFetch([['https://www.tiktok.com/oembed', () => json(400, { code: 400 })]]);
  const r2 = await resolveTikTok(parseLink('https://www.tiktok.com/@a/video/6718335390845095173'), { fetchImpl: gone });
  assert.equal(r2.reason, 'removed');
});

const igSession = { instagram: { accessToken: 'IGTOKEN', username: 'me' } };

test('Instagram: no session -> auth_required', async () => {
  const r = await resolveInstagram(parseLink('https://www.instagram.com/reel/C1a2B3c4D5e/'), { fetchImpl: fakeFetch([]), session: null, authUrl: 'https://b/auth/instagram/start' });
  assert.equal(r.status, 'auth_required');
  assert.equal(r.authUrl, 'https://b/auth/instagram/start');
});

test('Instagram: own reel found on page 2 -> ready with media_url', async () => {
  const f = fakeFetch([
    ['https://graph.instagram.com/v21.0/me/media?', () =>
      json(200, { data: [{ id: '1', media_type: 'IMAGE', permalink: 'https://www.instagram.com/p/OTHERcode1/' }], paging: { next: 'https://graph.instagram.com/v21.0/page2' } })],
    ['https://graph.instagram.com/v21.0/page2', () =>
      json(200, { data: [{ id: '2', media_type: 'VIDEO', media_url: 'https://cdn.ig/v.mp4', permalink: 'https://www.instagram.com/reel/C1a2B3c4D5e/', caption: 'My reel' }] })],
  ]);
  const r = await resolveInstagram(parseLink('https://www.instagram.com/reel/C1a2B3c4D5e/'), { fetchImpl: f, session: igSession });
  assert.equal(r.status, 'ready');
  assert.equal(r.downloadUrl, 'https://cdn.ig/v.mp4');
  assert.equal(r.fileName, 'My reel.mp4');
});

test('Instagram: someone else\'s post -> not_owned; expired token -> auth_required', async () => {
  const f = fakeFetch([['https://graph.instagram.com/v21.0/me/media?', () => json(200, { data: [] })]]);
  const r = await resolveInstagram(parseLink('https://www.instagram.com/reel/NOTMINE123/'), { fetchImpl: f, session: igSession });
  assert.equal(r.reason, 'not_owned');

  const exp = fakeFetch([['https://graph.instagram.com/', () => json(400, { error: { code: 190, message: 'expired' } })]]);
  const r2 = await resolveInstagram(parseLink('https://www.instagram.com/reel/C1a2B3c4D5e/'), { fetchImpl: exp, session: igSession });
  assert.equal(r2.status, 'auth_required');
});

const fbSession = { facebook: { accessToken: 'USER', pages: [{ id: 'p1', accessToken: 'PAGE1' }] } };

test('Facebook: page video -> ready via page token', async () => {
  const f = fakeFetch([
    ['https://graph.facebook.com/v21.0/1234567890123?', (url) =>
      url.includes('access_token=PAGE1')
        ? json(200, { source: 'https://video.xx.fbcdn.net/v.mp4', title: 'Launch' })
        : json(400, { error: { code: 100, message: 'nope' } })],
  ]);
  const r = await resolveFacebook(parseLink('https://www.facebook.com/watch/?v=1234567890123'), { fetchImpl: f, session: fbSession });
  assert.equal(r.status, 'ready');
  assert.equal(r.downloadUrl, 'https://video.xx.fbcdn.net/v.mp4');
});

test('Facebook: inaccessible video -> not_owned; fb.watch short link is expanded', async () => {
  const denied = fakeFetch([['https://graph.facebook.com/', () => json(400, { error: { code: 100, message: 'Unsupported get request' } })]]);
  const r = await resolveFacebook(parseLink('https://www.facebook.com/reel/1234567890123'), { fetchImpl: denied, session: fbSession });
  assert.equal(r.reason, 'not_owned');

  const f = fakeFetch([
    ['https://fb.watch/', () => new Response(null, { status: 302, headers: { location: 'https://www.facebook.com/Page/videos/9876543210987/' } })],
    ['https://graph.facebook.com/v21.0/9876543210987?', () => json(200, { source: 'https://cdn/v.mp4' })],
  ]);
  const r2 = await resolveFacebook(parseLink('https://fb.watch/abcDEF1/'), { fetchImpl: f, session: fbSession });
  assert.equal(r2.status, 'ready');
});

test('HTTP server: /api/resolve end to end', async () => {
  const store = new SessionStore(null);
  const sid = store.upsert(null, igSession);
  const fetchImpl = fakeFetch([
    ['https://graph.instagram.com/v21.0/me/media?', () =>
      json(200, { data: [{ id: '2', media_type: 'VIDEO', media_url: 'https://cdn.ig/v.mp4', permalink: 'https://www.instagram.com/reel/C1a2B3c4D5e/' }] })],
  ]);
  const server = createServer({
    config: { publicBaseUrl: '', appScheme: 'socialvideodl://auth', igAppId: 'id', igAppSecret: 's', fbAppId: '', fbAppSecret: '' },
    fetchImpl, store,
  });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const post = (body, token) => fetch(`${base}/api/resolve`, {
      method: 'POST', headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(body),
    }).then((r) => r.json());

    assert.equal((await post({ url: 'https://www.instagram.com/reel/C1a2B3c4D5e/' }, sid)).status, 'ready');
    assert.equal((await post({ url: 'https://www.instagram.com/reel/C1a2B3c4D5e/' })).status, 'auth_required');
    assert.equal((await post({ url: 'https://example.com/x' })).reason, 'invalid');
    assert.equal((await post({ url: 'https://www.facebook.com/watch?v=1234567890123' })).reason, 'not_configured');
    const health = await fetch(`${base}/health`).then((r) => r.json());
    assert.equal(health.ok, true);
    const start = await fetch(`${base}/auth/instagram/start?session=${sid}`, { redirect: 'manual' });
    assert.equal(start.status, 302);
    assert.match(start.headers.get('location'), /^https:\/\/www\.instagram\.com\/oauth\/authorize\?client_id=id/);
  } finally {
    server.close();
  }
});
