// Live checks against the real platforms that need no credentials (run in CI).
// TikTok: official oEmbed for TikTok's own documentation sample video, plus a non-existent id.
import { parseLink } from '../src/links.js';
import { resolveTikTok } from '../src/resolvers.js';

const cases = [
  ['TikTok public sample (TikTok developer docs)', 'https://www.tiktok.com/@scout2015/video/6718335390845095173'],
  ['TikTok non-existent video id', 'https://www.tiktok.com/@scout2015/video/1000000000000000001'],
];
for (const [label, url] of cases) {
  try {
    const raw = await fetch(`https://www.tiktok.com/oembed?url=${encodeURIComponent(url)}`);
    const text = await raw.text();
    let keys = '';
    try { const j = JSON.parse(text); keys = Object.keys(j).join(',') + ` author_name=${j.author_name || '-'}`; } catch { keys = text.slice(0, 80); }
    console.log(`${label}: oEmbed HTTP ${raw.status} keys=${keys}`);
    const r = await resolveTikTok(parseLink(url), { fetchImpl: fetch });
    console.log(`${label}: status=${r.status} reason=${r.reason || '-'}`);
  } catch (e) {
    console.log(`${label}: ERROR ${e.message}`);
  }
}
