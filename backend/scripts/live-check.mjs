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
    const r = await resolveTikTok(parseLink(url), { fetchImpl: fetch });
    console.log(`${label}: status=${r.status} reason=${r.reason || '-'}`);
  } catch (e) {
    console.log(`${label}: ERROR ${e.message}`);
  }
}
