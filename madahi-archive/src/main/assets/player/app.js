const state = {
  catalog: window.NAVA_CATALOG || {items:[]},
  tab: "all",
  artist: "همه",
  artistPage: null,
  query: "",
  currentId: null,
  favorites: new Set(readSaved("nava-madahi-archive-favorites"))
};
const $ = (s) => document.querySelector(s);
const audio = $("#audioElement");

function readSaved(key) {
  try { return JSON.parse(localStorage.getItem(key) || "[]"); } catch { return []; }
}
function escapeText(value) {
  return String(value || "")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
function faNumber(n) {
  try { return new Intl.NumberFormat("fa-IR").format(n); } catch { return String(n); }
}
function formatTime(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return "۰۰:۰۰";
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return faNumber(mins).padStart(2, "۰") + ":" + faNumber(secs).padStart(2, "۰");
}
function persistFavorites() {
  localStorage.setItem("nava-madahi-archive-favorites", JSON.stringify([...state.favorites]));
}
function persistCatalog() {
  try { localStorage.setItem("nava-madahi-archive-catalog", JSON.stringify(state.catalog)); } catch {}
}
function loadCatalog() {
  try {
    const saved = JSON.parse(localStorage.getItem("nava-madahi-archive-catalog") || "null");
    if (saved && Array.isArray(saved.items) && saved.items.length) {
      const byId = new Map((state.catalog.items || []).map(x => [x.id,x]));
      saved.items.forEach(x => byId.set(x.id,x));
      state.catalog = {...saved,items:[...byId.values()]};
    }
  } catch {}
}
function artistNames() {
  return ["همه", ...new Set((state.catalog.items || []).map(x => x.maddah).filter(Boolean))];
}
function renderArtists() {
  $("#artistChips").innerHTML = artistNames().map(name =>
    '<button class="artist-chip '+(state.artist===name?'active':'')+'" data-artist="'+escapeText(name)+'">'+escapeText(name)+'</button>'
  ).join("");
}
function filteredItems() {
  const q = state.query.trim().toLocaleLowerCase("fa");
  return (state.catalog.items || []).filter(item => {
    const hay = [item.title,item.maddah,item.description].join(" ").toLocaleLowerCase("fa");
    const searchOk = !q || hay.includes(q);
    const artistOk = state.artist === "همه" || item.maddah === state.artist;
    const tabOk = state.tab === "all"
      || (state.tab === "latest" && item.group === "latest")
      || (state.tab === "old" && item.group !== "latest")
      || (state.tab === "favorites" && state.favorites.has(item.id));
    return searchOk && artistOk && tabOk;
  }).sort((a,b) => (a.order || 999) - (b.order || 999));
}
function trackMarkup(item) {
  const saved = state.favorites.has(item.id);
  const active = state.currentId === item.id && !audio.paused;
  return '<article class="track '+(active?'playing':'')+'">' +
    '<div class="track-main"><h3>'+escapeText(item.title)+'</h3><p>'+escapeText(item.maddah)+' · '+escapeText(item.quality || "MP3")+'</p></div>' +
    '<button class="play" data-play="'+escapeText(item.id)+'">'+(active?"در حال پخش":"▶ پخش")+'</button>' +
    '<button class="favorite '+(saved?'saved':'')+'" data-favorite="'+escapeText(item.id)+'" aria-label="نشان‌کردن">'+(saved?"♥":"♡")+'</button></article>';
}
function renderTracks() {
  const items = filteredItems();
  $("#resultCount").textContent = faNumber(items.length) + " مداحی";
  $("#tracks").innerHTML = items.map(trackMarkup).join("");
  $("#empty").hidden = items.length !== 0;
}
function renderArtistDirectory() {
  const directory = $("#artistDirectory");
  const library = $("#librarySection");
  const names = [...new Set([...(state.catalog.artists || []), ...(state.catalog.items || []).map(x => x.maddah)].filter(Boolean))]
    .sort((a,b) => a.localeCompare(b,"fa"));
  const q = state.query.trim().toLocaleLowerCase("fa");
  const matching = names.filter(name => !q || name.toLocaleLowerCase("fa").includes(q) ||
    (state.catalog.items || []).some(x => x.maddah === name && (x.title || "").toLocaleLowerCase("fa").includes(q)));
  const counts = name => (state.catalog.items || []).filter(x => x.maddah === name).length;
  directory.hidden = state.tab !== "artists";
  library.hidden = state.tab === "artists";
  if (state.tab !== "artists") return;
  $("#directoryCount").textContent = faNumber(names.length) + " مداح";
  const alimiCount = counts("حمید علیمی");
  $("#artistFeature").innerHTML = alimiCount
    ? '<button class="featured-alimi" data-open-artist="حمید علیمی"><span class="feature-kicker">صفحه اختصاصی</span><strong>حمید علیمی</strong><span>آرشیو اختصاصی · '+faNumber(alimiCount)+' مداحی صوتی</span><b>ورود به صفحه علیمی ←</b></button>'
    : "";
  if (state.artistPage) {
    const name = state.artistPage;
    const songs = (state.catalog.items || []).filter(x => x.maddah === name)
      .sort((a,b) => (a.order || 999) - (b.order || 999));
    $("#artistGrid").hidden = true;
    $("#artistFeature").hidden = true;
    $("#artistProfile").hidden = false;
    $("#profileHero").innerHTML = '<div class="profile-kicker">آرشیو اختصاصی مداح</div><h2>'+escapeText(name)+'</h2><p>'+faNumber(songs.length)+' مداحی صوتی در این آرشیو</p>' +
      (name === "حمید علیمی" ? '<span class="alimi-tag">صفحه ویژه حمید علیمی</span>' : '') +
      '<a href="https://sonalos.com/maddahi" target="_blank" rel="noopener">منبع آرشیو صوتی ↗</a>';
    $("#artistTracks").innerHTML = '<div class="section-title"><div><small>آثار ثبت‌شده</small><h3>مداحی‌های '+escapeText(name)+'</h3></div></div>' +
      (songs.length ? '<div class="tracks">'+songs.map(trackMarkup).join("")+'</div>' : '<div class="empty">هنوز مداحی صوتی برای این مداح ثبت نشده است.</div>');
  } else {
    $("#artistGrid").hidden = false;
    $("#artistFeature").hidden = false;
    $("#artistProfile").hidden = true;
    $("#artistGrid").innerHTML = matching.map(name =>
      '<button class="artist-card '+(name==="حمید علیمی"?"alimi-card":"")+'" data-open-artist="'+escapeText(name)+'"><span class="artist-avatar">'+escapeText(name.slice(0,1))+'</span><span class="artist-card-name">'+escapeText(name)+'</span><small>'+(counts(name)?faNumber(counts(name))+' مداحی':'در انتظار افزودن صوت')+'</small><b>صفحه مداح ←</b></button>'
    ).join("");
  }
}
function render() {
  renderArtists();
  renderTracks();
  renderArtistDirectory();
  document.querySelectorAll("[data-tab]").forEach(b => b.classList.toggle("active", b.dataset.tab === state.tab));
}
function setStatus(message) { $("#statusText").textContent = message; }
function setPlayerStatus(message) { $("#playerStatus").textContent = message; }
function safeAudioUrl(raw) {
  try {
    const u = new URL(raw);
    return u.protocol === "https:" &&
      (u.hostname === "sonalos.com" || u.hostname.endsWith(".sonalos.com"));
  } catch { return false; }
}
function playbackList() {
  if (state.tab === "artists" && state.artistPage) return (state.catalog.items || []).filter(x => x.maddah === state.artistPage).sort((a,b)=>(a.order||999)-(b.order||999));
  return filteredItems();
}
function currentIndex() {
  return playbackList().findIndex(x => x.id === state.currentId);
}
function setMediaSession(item) {
  if (!("mediaSession" in navigator)) return;
  try {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: item.title || "مداحی",
      artist: item.maddah || "مداح",
      album: "نوای هیئت آرشیو"
    });
    navigator.mediaSession.setActionHandler("play", () => audio.play());
    navigator.mediaSession.setActionHandler("pause", () => audio.pause());
    navigator.mediaSession.setActionHandler("previoustrack", previousTrack);
    navigator.mediaSession.setActionHandler("nexttrack", nextTrack);
  } catch {}
}
async function playItem(id) {
  const item = (state.catalog.items || []).find(x => x.id === id);
  if (!item || !safeAudioUrl(item.audioUrl)) {
    setStatus("برای این مدخل هنوز لینک صوتی معتبر ثبت نشده است.");
    return;
  }
  if (state.currentId === id && audio.src) {
    if (audio.paused) {
      try { await audio.play(); } catch { setPlayerStatus("پخش شروع نشد؛ اتصال منبع را بررسی کن."); }
    } else audio.pause();
    return;
  }

  state.currentId = id;
  $("#playerDock").hidden = false;
  $("#playerTitle").textContent = item.title;
  $("#playerArtist").textContent = item.maddah;
  $("#qualityLabel").textContent = "MP3 · " + (item.quality || "کیفیت منبع");
  $("#seekBar").value = "0";
  $("#currentTime").textContent = "۰۰:۰۰";
  $("#duration").textContent = "۰۰:۰۰";
  $("#playPauseButton").textContent = "Ⅱ";
  setPlayerStatus("در حال اتصال به منبع صوت...");
  renderTracks();

  audio.pause();
  audio.src = item.audioUrl;
  audio.load();
  setMediaSession(item);
  try {
    await audio.play();
    setPlayerStatus("در حال پخش صوت");
    $("#playerDock").scrollIntoView({behavior:"smooth",block:"end"});
  } catch (err) {
    setPlayerStatus("پخش شروع نشد؛ دسترسی به منبع صوت یا اینترنت را بررسی کن.");
    $("#playPauseButton").textContent = "▶";
  }
}
function previousTrack() {
  const list = playbackList();
  const index = currentIndex();
  if (index > 0) playItem(list[index - 1].id);
  else if (state.currentId) { audio.currentTime = 0; audio.play().catch(()=>{}); }
}
function nextTrack() {
  const list = playbackList();
  const index = currentIndex();
  if (index >= 0 && index < list.length - 1) playItem(list[index + 1].id);
}
function refreshCatalog() {
  setStatus("در حال بررسی آرشیو صوتی...");
  if (window.NavaPlayer && typeof window.NavaPlayer.refreshCatalog === "function") {
    window.NavaPlayer.refreshCatalog();
  } else {
    setStatus("نسخه آفلاین آماده است؛ برای تازه‌سازی به اینترنت وصل شو.");
  }
}
window.onRemoteCatalog = function(raw) {
  try {
    const remote = typeof raw === "string" ? JSON.parse(raw) : raw;
    if (!remote || !Array.isArray(remote.items)) throw new Error("invalid catalog");
    const audioItems = remote.items.filter(x => x.type === "audio" && safeAudioUrl(x.audioUrl));
    if (!audioItems.length) throw new Error("no audio items");
    const byId = new Map((state.catalog.items || []).map(x => [x.id,x]));
    audioItems.forEach(x => byId.set(x.id,x));
    state.catalog = {...remote,items:[...byId.values()]};
    persistCatalog();
    const d = remote.updatedAt ? new Date(remote.updatedAt) : new Date();
    $("#updatedAt").textContent = "به‌روزرسانی: " + new Intl.DateTimeFormat("fa-IR",{month:"short",day:"numeric"}).format(d);
    setStatus("آرشیو صوتی به‌روز شد.");
    render();
  } catch {
    setStatus("فهرست قبلی حفظ شد؛ تازه‌سازی ناموفق بود.");
  }
};

audio.addEventListener("play", () => {
  $("#playPauseButton").textContent = "Ⅱ";
  setPlayerStatus("در حال پخش صوت");
  render();
});
audio.addEventListener("pause", () => {
  $("#playPauseButton").textContent = "▶";
  if (!audio.ended) setPlayerStatus("پخش متوقف شده");
  render();
});
audio.addEventListener("waiting", () => setPlayerStatus("در حال دریافت صدا..."));
audio.addEventListener("canplay", () => { if (!audio.paused) setPlayerStatus("در حال پخش صوت"); });
audio.addEventListener("loadedmetadata", () => { $("#duration").textContent = formatTime(audio.duration); });
audio.addEventListener("timeupdate", () => {
  $("#currentTime").textContent = formatTime(audio.currentTime);
  $("#duration").textContent = formatTime(audio.duration);
  if (Number.isFinite(audio.duration) && audio.duration > 0) {
    $("#seekBar").value = String(Math.round(audio.currentTime / audio.duration * 1000));
  }
});
audio.addEventListener("ended", nextTrack);
audio.addEventListener("error", () => {
  setPlayerStatus("این صوت از منبع در دسترس نیست؛ شبکه یا لینک منبع را بررسی کن.");
  $("#playPauseButton").textContent = "▶";
});

document.addEventListener("click", e => {
  const play = e.target.closest("[data-play]");
  if (play) { playItem(play.dataset.play); return; }
  const fav = e.target.closest("[data-favorite]");
  if (fav) {
    const id = fav.dataset.favorite;
    state.favorites.has(id) ? state.favorites.delete(id) : state.favorites.add(id);
    persistFavorites(); render(); return;
  }
  const openArtist = e.target.closest("[data-open-artist]");
  if (openArtist) {
    state.artistPage = openArtist.dataset.openArtist;
    state.tab = "artists";
    render();
    return;
  }
  const backDirectory = e.target.closest("#backDirectory");
  if (backDirectory) {
    state.artistPage = null;
    render();
    return;
  }
  const artist = e.target.closest("[data-artist]");
  if (artist) { state.artist = artist.dataset.artist; state.tab = "all"; state.artistPage = null; render(); return; }
  const tab = e.target.closest("[data-tab]");
  if (tab) {
    state.tab = tab.dataset.tab;
    if (state.tab !== "artists") state.artistPage = null;
    render();
    return;
  }
});
$("#search").addEventListener("input", e => { state.query = e.target.value; $("#clearSearch").style.visibility = state.query ? "visible" : "hidden"; render(); });
$("#clearSearch").addEventListener("click", () => { $("#search").value=""; state.query=""; $("#clearSearch").style.visibility="hidden"; render(); });
$("#refreshButton").addEventListener("click", refreshCatalog);
$("#playPauseButton").addEventListener("click", () => {
  if (!audio.src) {
    if (state.currentId) playItem(state.currentId);
    return;
  }
  if (audio.paused) audio.play().catch(()=>setPlayerStatus("پخش شروع نشد؛ منبع صوت را بررسی کن."));
  else audio.pause();
});
$("#previousButton").addEventListener("click", previousTrack);
$("#nextButton").addEventListener("click", nextTrack);
$("#seekBar").addEventListener("input", e => {
  if (Number.isFinite(audio.duration) && audio.duration > 0) {
    audio.currentTime = (Number(e.target.value) / 1000) * audio.duration;
  }
});
$("#closePlayer").addEventListener("click", () => {
  audio.pause();
  audio.removeAttribute("src");
  audio.load();
  state.currentId = null;
  $("#playerDock").hidden = true;
  render();
});
$("#notifyButton").addEventListener("click", () => window.NavaPlayer && window.NavaPlayer.requestNotificationPermission());
$("#enableNotifications").addEventListener("click", () => window.NavaPlayer && window.NavaPlayer.requestNotificationPermission());

loadCatalog();
render();
$("#clearSearch").style.visibility = "hidden";
refreshCatalog();
