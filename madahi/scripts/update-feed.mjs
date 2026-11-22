import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const sourcesPath = resolve(root, 'data/sources.json');
const feedPath = resolve(root, 'src/main/assets/madahi/data/feed.json');

function decodeXml(value = '') {
  return value
    .replace(/^<!\[CDATA\[([\s\S]*?)\]\]>$/, '$1')
    .replaceAll('&amp;', '&')
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .trim();
}

function readTag(xml, tag) {
  const escaped = tag.replace(':', '\\:');
  const match = xml.match(new RegExp(`<${escaped}[^>]*>([\\s\\S]*?)<\\/${escaped}>`, 'i'));
  return match ? decodeXml(match[1]) : '';
}

function parseEntries(xml, channel) {
  return [...xml.matchAll(/<entry(?:\s[^>]*)?>([\s\S]*?)<\/entry>/gi)].map((match) => {
    const entry = match[1];
    const videoId = readTag(entry, 'yt:videoId');
    if (!videoId) return null;
    const title = readTag(entry, 'title');
    const publishedAt = readTag(entry, 'published');
    const description = readTag(entry, 'media:description');
    return {
      id: `yt-${videoId}`,
      videoId,
      maddah: channel.name,
      title,
      description: description.slice(0, 420),
      url: `https://www.youtube.com/watch?v=${videoId}`,
      source: 'YouTube',
      category: 'جدیدترین',
      publishedAt,
      type: 'video',
      curated: false
    };
  }).filter(Boolean);
}

const sources = JSON.parse(await readFile(sourcesPath, 'utf8'));
const oldFeed = JSON.parse(await readFile(feedPath, 'utf8'));
const oldItems = Array.isArray(oldFeed.items) ? oldFeed.items : [];
const discovered = [];

for (const channel of (sources.channels || []).filter((item) => item.enabled && item.channelId)) {
  const rssUrl = `https://www.youtube.com/feeds/videos.xml?channel_id=${encodeURIComponent(channel.channelId)}`;
  try {
    const response = await fetch(rssUrl, { headers: { 'user-agent': 'NavaMadahiFeedBot/1.0' } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const xml = await response.text();
    discovered.push(...parseEntries(xml, channel).slice(0, 15));
    console.log(`Updated ${channel.name}: ${parseEntries(xml, channel).length} items`);
  } catch (error) {
    console.warn(`Could not update ${channel.name}: ${error.message}`);
  }
}

const merged = new Map();
for (const item of [...discovered, ...oldItems]) merged.set(item.id, item);
const items = [...merged.values()]
  .sort((a, b) => new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0))
  .slice(0, 120);
const channels = (sources.channels || []).filter((item) => item.enabled).map(({ name, channelId, url }) => ({ name, channelId, url }));

const before = JSON.stringify({ items: oldItems, channels: oldFeed.channels || [] });
const after = JSON.stringify({ items, channels });
const nextFeed = {
  updatedAt: before === after ? oldFeed.updatedAt : new Date().toISOString(),
  items,
  channels
};
await writeFile(feedPath, `${JSON.stringify(nextFeed, null, 2)}\n`);
const seedPath = resolve(root, 'src/main/assets/madahi/data/seed.js');
await writeFile(seedPath, `window.NAVA_FEED = ${JSON.stringify(nextFeed)};\n`);
console.log(`Feed saved with ${items.length} items.`);
