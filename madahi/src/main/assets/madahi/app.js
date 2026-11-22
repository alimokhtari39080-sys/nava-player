const ARTISTS = [
  { name: 'حمید علیمی', query: 'حمید علیمی', featured: true, url: 'https://www.youtube.com/@alimi_media' },
  { name: 'محمود کریمی', query: 'محمود کریمی' },
  { name: 'محمدرضا طاهری', query: 'محمدرضا طاهری' },
  { name: 'میثم مطیعی', query: 'میثم مطیعی', url: 'https://meysammotiee.ir/' },
  { name: 'سیدمجید بنی‌فاطمه', query: 'سید مجید بنی فاطمه', url: 'https://www.youtube.com/@seyedmajidbanifatemeh4385' },
  { name: 'محمدحسین پویانفر', query: 'محمد حسین پویانفر', url: 'https://www.youtube.com/@MohammadHosseinPouyanfar' },
  { name: 'حاج مهدی رسولی', query: 'حاج مهدی رسولی' },
  { name: 'عبدالرضا هلالی', query: 'عبدالرضا هلالی' },
  { name: 'حاج منصور ارضی', query: 'حاج منصور ارضی', url: 'https://t.me/hajmansourarzi' },
  { name: 'سعید حدادیان', query: 'سعید حدادیان' },
  { name: 'حسین طاهری', query: 'حسین طاهری' },
  { name: 'علی فانی', query: 'علی فانی' }
];

const state = { feed: { items: [], channels: [], updatedAt: null }, filter: 'all', query: '', favorites: new Set(readFavorites()) };
const $ = (selector) => document.querySelector(selector);
const tracksList = $('#tracksList');
const emptyState = $('#emptyState');

function readFavorites() {
  try { return JSON.parse(localStorage.getItem('nava-madahi-favorites') || '[]'); }
  catch { return []; }
}
function saveFavorites() {
  localStorage.setItem('nava-madahi-favorites', JSON.stringify([...state.favorites]));
}
function escapeHtml(value = '') {
  return String(value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}
function dateLabel(value) {
  if (!value) return 'آرشیو';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'آرشیو';
  return new Intl.DateTimeFormat('fa-IR', { year: 'numeric', month: 'short', day: 'numeric' }).format(date);
}
function filteredItems() {
  const query = state.query.trim().toLocaleLowerCase('fa');
  return (state.feed.items || []).filter((item) => {
    const haystack = `${item.title || ''} ${item.maddah || ''} ${item.description || ''} ${item.category || ''}`.toLocaleLowerCase('fa');
    const matchesQuery = !query || haystack.includes(query);
    const matchesFilter = state.filter === 'all'
      || (state.filter === 'favorites' && state.favorites.has(item.id))
      || (state.filter !== 'favorites' && `${item.maddah || ''} ${item.title || ''} ${item.category || ''}`.includes(state.filter));
    return matchesQuery && matchesFilter;
  }).sort((a, b) => new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0));
}
function renderTracks() {
  const items = filteredItems();
  tracksList.innerHTML = items.map((item) => {
    const saved = state.favorites.has(item.id);
    const category = item.category || (item.type === 'video' ? 'ویدئو' : 'آرشیو');
    return `<article class="track-card">
      <div class="track-art">${item.type === 'video' ? '▶' : '♫'}</div>
      <div class="track-copy"><h3>${escapeHtml(item.title)}</h3><p>${escapeHtml(item.maddah || 'مداحی')} · ${escapeHtml(item.source || 'منبع')}
      </p><div class="track-meta"><span>${escapeHtml(category)}</span><span>${dateLabel(item.publishedAt)}</span></div></div>
      <a class="track-open" href="${escapeHtml(item.url)}" target="_blank" rel="noopener">پخش ↗</a>
      <button class="fav-btn ${saved ? 'saved' : ''}" data-fav="${escapeHtml(item.id)}" aria-label="علاقه‌مندی">${saved ? '♥' : '♡'}</button>
    </article>`;
  }).join('');
  emptyState.hidden = items.length > 0;
}
function renderArtists() {
  $('#artistsGrid').innerHTML = ARTISTS.map((artist) => {
    const href = artist.url || `https://www.youtube.com/results?search_query=${encodeURIComponent(`${artist.query} مداحی`)}`;
    return `<button class="artist-card ${artist.featured ? 'featured' : ''}" data-artist="${escapeHtml(artist.query)}" data-url="${escapeHtml(href)}"><span class="artist-avatar">${artist.featured ? '✦' : '♫'}</span><b>${escapeHtml(artist.name)}</b><small>${artist.featured ? 'کانال معرفی‌شده' : 'جست‌وجوی آثار'}</small></button>`;
  }).join('');
}
function updateNote(text) {
  $('#updateNote').textContent = text;
}
function setFilter(filter) {
  state.filter = filter;
  document.querySelectorAll('.filter-chip').forEach((button) => button.classList.toggle('active', button.dataset.filter === filter));
  renderTracks();
}
function refreshRemote() {
  if (window.NavaBridge && typeof window.NavaBridge.refreshFeed === 'function') {
    $('#connectionLabel').textContent = 'در حال بررسی';
    window.NavaBridge.refreshFeed();
  } else {
    $('#connectionLabel').textContent = navigator.onLine ? 'آنلاین' : 'آفلاین';
  }
}
window.onRemoteFeed = (raw) => {
  try {
    const remote = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (remote && Array.isArray(remote.items) && remote.items.length) {
      const current = new Map((state.feed.items || []).map((item) => [item.id, item]));
      for (const item of remote.items) current.set(item.id, item);
      state.feed = { ...remote, items: [...current.values()] };
      state.feed.items.sort((a, b) => new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0));
      renderTracks();
      updateNote(`آخرین همگام‌سازی فهرست: ${dateLabel(remote.updatedAt)} · ${remote.items.length} مورد`);
      $('#connectionLabel').textContent = 'به‌روز';
    } else {
      $('#connectionLabel').textContent = navigator.onLine ? 'آماده' : 'آفلاین';
      updateNote('فهرست آفلاین نمایش داده می‌شود؛ اتصال بعداً دوباره بررسی می‌شود.');
    }
  } catch {
    $('#connectionLabel').textContent = 'آفلاین';
    updateNote('فهرست ذخیره‌شده نمایش داده می‌شود.');
  }
};

function init() {
  state.feed = window.NAVA_FEED || state.feed;
  renderArtists();
  renderTracks();
  if (state.feed.updatedAt) updateNote(`فهرست پایه · ${dateLabel(state.feed.updatedAt)}`);
  refreshRemote();
}

$('#searchInput').addEventListener('input', (event) => {
  state.query = event.target.value;
  $('#clearSearch').style.display = state.query ? 'block' : 'none';
  renderTracks();
});
$('#clearSearch').addEventListener('click', () => {
  $('#searchInput').value = '';
  state.query = '';
  $('#clearSearch').style.display = 'none';
  renderTracks();
});
$('#quickFilters').addEventListener('click', (event) => {
  const button = event.target.closest('[data-filter]');
  if (button) setFilter(button.dataset.filter);
});
document.querySelector('.occasion-grid').addEventListener('click', (event) => {
  const button = event.target.closest('[data-filter]');
  if (button) {
    setFilter(button.dataset.filter);
    $('#latestSection').scrollIntoView({ behavior: 'smooth' });
  }
});
$('#artistsGrid').addEventListener('click', (event) => {
  const card = event.target.closest('[data-url]');
  if (card) window.location.href = card.dataset.url;
});
tracksList.addEventListener('click', (event) => {
  const button = event.target.closest('[data-fav]');
  if (!button) return;
  const id = button.dataset.fav;
  if (state.favorites.has(id)) state.favorites.delete(id); else state.favorites.add(id);
  saveFavorites();
  renderTracks();
});
$('#refreshButton').addEventListener('click', refreshRemote);
$('#jumpLatest').addEventListener('click', () => $('#latestSection').scrollIntoView({ behavior: 'smooth' }));
document.querySelector('.bottom-nav').addEventListener('click', (event) => {
  const button = event.target.closest('[data-nav],[data-filter]');
  if (!button) return;
  document.querySelectorAll('.nav-item').forEach((item) => item.classList.toggle('selected', item === button));
  if (button.dataset.filter) setFilter(button.dataset.filter);
  if (button.dataset.nav === 'latest') $('#latestSection').scrollIntoView({ behavior: 'smooth' });
  if (button.dataset.nav === 'artists') $('#artistsGrid').scrollIntoView({ behavior: 'smooth' });
  if (button.dataset.nav === 'home') window.scrollTo({ top: 0, behavior: 'smooth' });
});
window.addEventListener('online', () => { $('#connectionLabel').textContent = 'آنلاین'; refreshRemote(); });
window.addEventListener('offline', () => { $('#connectionLabel').textContent = 'آفلاین'; });
init();
