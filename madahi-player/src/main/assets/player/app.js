const state = {
  catalog: window.NAVA_CATALOG || {items:[],channels:[]},
  tab: "all",
  artist: "همه",
  query: "",
  favorites: new Set(readSaved("nava-madahi-player-favorites"))
};
const $ = (s) => document.querySelector(s);
const tracks = $("#tracks");
const playerPanel = $("#playerPanel");

function readSaved(key) {
  try { return JSON.parse(localStorage.getItem(key) || "[]"); } catch { return []; }
}
function escapeText(value) {
  return String(value || "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;","\\"":"&quot;","'":"&#39;"}[c]));
}
function faNumber(n) {
  try { return new Intl.NumberFormat("fa-IR").format(n); } catch { return String(n); }
}
function toDate(item) {
  const n = Date.parse(item.publishedAt || "");
  return Number.isFinite(n) ? n : 0;
}
function isRecent(item) {
  return item.type === "video" && toDate(item) > 0 &&
    (Date.now() - toDate(item)) < 180 * 24 * 60 * 60 * 1000;
}
function isOld(item) { return item.type === "archive" || !isRecent(item); }
function dateLabel(item) {
  const stamp = toDate(item);
  if (!stamp) return "آرشیو قدیمی";
  try { return new Intl.DateTimeFormat("fa-IR",{year:"numeric",month:"short",day:"numeric"}).format(new Date(stamp)); }
  catch { return ""; }
}
function saveFavorites() {
  localStorage.setItem("nava-madahi-player-favorites", JSON.stringify([...state.favorites]));
}
function persistCatalog() {
  try { localStorage.setItem("nava-madahi-player-catalog", JSON.stringify(state.catalog)); } catch {}
}
function loadCatalog() {
  const saved = localStorage.getItem("nava-madahi-player-catalog");
  if (!saved) return;
  try {
    const parsed = JSON.parse(saved);
    if (Array.isArray(parsed.items) && parsed.items.length) {
      const byId = new Map((state.catalog.items || []).map(x => [x.id, x]));
      parsed.items.forEach(x => byId.set(x.id, x));
      state.catalog = {...parsed, items:[...byId.values()]};
    }
  } catch {}
}
function artistNames() {
  const fromChannels = (state.catalog.channels || []).map(c => c.name).filter(Boolean);
  const fromItems = (state.catalog.items || []).map(x => x.maddah).filter(Boolean);
  return ["همه", ...new Set([...fromChannels, ...fromItems])];
}
function renderArtists() {
  $("#artistChips").innerHTML = artistNames().map(name =>
    '<button class="artist-chip '+(state.artist===name?'active':'')+'" data-artist="'+escapeText(name)+'">'+escapeText(name)+'</button>'
  ).join("");
}
function getFiltered() {
  const q = state.query.trim().toLocaleLowerCase("fa");
  return (state.catalog.items || []).filter(item => {
    const hay = [item.title,item.maddah,item.description,item.category,item.source].join(" ").toLocaleLowerCase("fa");
    const searchOk = !q || hay.includes(q);
    const artistOk = state.artist === "همه" || item.maddah === state.artist;
    let tabOk = true;
    if (state.tab === "latest") tabOk = isRecent(item);
    if (state.tab === "old") tabOk = isOld(item);
    if (state.tab === "favorites") tabOk = state.favorites.has(item.id);
    return searchOk && artistOk && tabOk;
  }).sort((a,b) => toDate(b)-toDate(a));
}
function renderTracks() {
  const items = getFiltered();
  $("#resultCount").textContent = faNumber(items.length)+" مداحی";
  $("#tracks").innerHTML = items.map(item => {
    const archived = item.type === "archive" || !item.videoId;
    const isSaved = state.favorites.has(item.id);
    const action = archived ? "مشاهده آرشیو" : "پخش داخل برنامه";
    return '<article class="track">'+
      '<div class="cover">'+(archived ? "◷" : "♫")+'</div>'+
      '<div class="track-main"><h3>'+escapeText(item.title)+'</h3>'+
      '<p>'+escapeText(item.maddah)+' · '+escapeText(item.source || "منبع")+'</p>'+
      '<div class="tags"><span class="tag">'+escapeText(item.category || (archived?"قدیمی و آرشیوی":"جدیدترین"))+'</span><span class="tag">'+dateLabel(item)+'</span></div></div>'+
      '<button class="play '+(archived?"archive":"")+'" data-play="'+escapeText(item.id)+'">'+action+'</button>'+
      '<button class="favorite '+(isSaved?"saved":"")+'" data-favorite="'+escapeText(item.id)+'" aria-label="نشان‌کردن">'+(isSaved?"♥":"♡")+'</button>'+
      '</article>';
  }).join("");
  $("#empty").hidden = items.length !== 0;
}
function render() {
  renderArtists();
  renderTracks();
  document.querySelectorAll("[data-tab]").forEach(b => b.classList.toggle("active", b.dataset.tab === state.tab));
}
function setStatus(text) { $("#statusText").textContent = text; }
function playItem(id) {
  const item = (state.catalog.items || []).find(x => x.id === id);
  if (!item) return;
  if (item.videoId) {
    $("#playerTitle").textContent = item.title || "مداحی";
    $("#playerHelp").textContent = "پخش‌کننده رسمی یوتیوب در همین برنامه باز شده است؛ برای شروع، Play را بزن.";
    const videoId = encodeURIComponent(item.videoId);
    const origin = encodeURIComponent("https://appassets.androidplatform.net");
    $("#videoFrame").innerHTML = '<iframe title="'+escapeText(item.title)+'" src="https://www.youtube.com/embed/'+videoId+'?playsinline=1&rel=0&origin='+origin+'" allow="autoplay; encrypted-media; picture-in-picture; web-share" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe>';
    playerPanel.hidden = false;
    playerPanel.scrollIntoView({behavior:"smooth",block:"start"});
    const history = readSaved("nava-madahi-player-history");
    localStorage.setItem("nava-madahi-player-history", JSON.stringify([id,...history.filter(x=>x!==id)].slice(0,100)));
  } else if (item.url && window.NavaPlayer && typeof window.NavaPlayer.openArchive === "function") {
    window.NavaPlayer.openArchive(item.url);
  } else if (item.url) {
    window.open(item.url, "_blank");
  }
}
function refreshCatalog() {
  setStatus("در حال بررسی تازه‌ها…");
  if (window.NavaPlayer && typeof window.NavaPlayer.refreshCatalog === "function") {
    window.NavaPlayer.refreshCatalog();
  } else {
    setStatus("برای دریافت تازه‌ها به اینترنت وصل شو.");
  }
}
window.onRemoteCatalog = function(raw) {
  try {
    const remote = typeof raw === "string" ? JSON.parse(raw) : raw;
    if (!remote || !Array.isArray(remote.items)) {
      setStatus("فهرست قبلی حفظ شد؛ اتصال ناموفق بود.");
      return;
    }
    const byId = new Map((state.catalog.items || []).map(x => [x.id,x]));
    remote.items.forEach(x => byId.set(x.id,x));
    state.catalog = {...remote,items:[...byId.values()]};
    persistCatalog();
    const stamp = remote.updatedAt ? new Date(remote.updatedAt) : new Date();
    $("#updatedAt").textContent = "آخرین بررسی: "+dateTime(stamp);
    setStatus("فهرست با موفقیت تازه شد.");
    render();
  } catch {
    setStatus("فهرست قبلی حفظ شد؛ خطا در خواندن اطلاعات.");
  }
};
function dateTime(d) {
  try { return new Intl.DateTimeFormat("fa-IR",{month:"short",day:"numeric",hour:"2-digit",minute:"2-digit"}).format(d); }
  catch { return d.toLocaleString(); }
}
document.addEventListener("click", e => {
  const play = e.target.closest("[data-play]");
  if (play) { playItem(play.dataset.play); return; }
  const fav = e.target.closest("[data-favorite]");
  if (fav) {
    const id = fav.dataset.favorite;
    state.favorites.has(id) ? state.favorites.delete(id) : state.favorites.add(id);
    saveFavorites(); renderTracks(); return;
  }
  const artist = e.target.closest("[data-artist]");
  if (artist) { state.artist = artist.dataset.artist; render(); return; }
  const tab = e.target.closest("[data-tab]");
  if (tab) { state.tab = tab.dataset.tab; renderTracks(); document.querySelectorAll("[data-tab]").forEach(b=>b.classList.toggle("active",b.dataset.tab===state.tab)); return; }
});
$("#search").addEventListener("input", e => { state.query = e.target.value; $("#clearSearch").style.visibility = state.query ? "visible" : "hidden"; renderTracks(); });
$("#clearSearch").addEventListener("click", () => { $("#search").value=""; state.query=""; $("#clearSearch").style.visibility="hidden"; renderTracks(); });
$("#refreshButton").addEventListener("click", refreshCatalog);
$("#closePlayer").addEventListener("click", () => { $("#videoFrame").innerHTML=""; playerPanel.hidden=true; });
$("#notifyButton").addEventListener("click", () => window.NavaPlayer && window.NavaPlayer.requestNotificationPermission());
$("#enableNotifications").addEventListener("click", () => window.NavaPlayer && window.NavaPlayer.requestNotificationPermission());

loadCatalog();
render();
$("#clearSearch").style.visibility = "hidden";
refreshCatalog();
