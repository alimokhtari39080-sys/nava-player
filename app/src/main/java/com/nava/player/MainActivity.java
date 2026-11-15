package com.nava.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public final class MainActivity extends Activity {
    private static final int AUDIO_PERMISSION_REQUEST = 410;
    private static final int BG = Color.rgb(11, 11, 18);
    private static final int PANEL = Color.rgb(24, 23, 36);
    private static final int PURPLE = Color.rgb(184, 166, 255);
    private static final int MUTED = Color.rgb(165, 163, 184);
    private static final int MINT = Color.rgb(99, 230, 215);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<Song> allSongs = new ArrayList<>();
    private final ArrayList<Song> shownSongs = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();
    private final Map<String, Integer> playCounts = new HashMap<>();
    private final ArrayList<String> listeningHistory = new ArrayList<>();

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private LinearLayout root;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView statusText;
    private TextView timeText;
    private TextView playButton;
    private TextView shuffleButton;
    private TextView repeatButton;
    private TextView speedButton;
    private SeekBar seekBar;
    private ListView songList;
    private EditText searchBox;
    private SongAdapter adapter;
    private LinearLayout genreTabs;
    private LinearLayout playerCard;
    private ImageView coverArt;
    private LinearLayout aiStudioCard;
    private LinearLayout moodChips;
    private MusicMood selectedMood = MusicMood.PERSONAL;
    private int activeBackground = BG;
    private int activePanel = PANEL;
    private int activeAccent = PURPLE;
    private int activeSecondaryAccent = MINT;
    private MusicGenre selectedGenre = MusicGenre.ALL;
    private final Map<String, MusicGenre> genreOverrides = new HashMap<>();

    private boolean favoritesOnly = false;
    private boolean recentOnly = false;
    private boolean popularOnly = false;
    private int sortMode = 0;
    private boolean userSeeking = false;
    private boolean shuffleEnabled = false;
    private int repeatMode = Player.REPEAT_MODE_OFF;
    private float playbackSpeed = 1.0f;
    private Runnable sleepTask;

    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            mainHandler.postDelayed(this, 500);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updatePlayerUi();
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            updatePlayerUi();
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            updatePlayerUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        favorites.addAll(getPreferences(MODE_PRIVATE).getStringSet("favorites", new HashSet<>()));
        loadGenreOverrides();
        loadListeningProfile();
        sortMode = getPreferences(MODE_PRIVATE).getInt("library_sort_mode", 0);
        try {
            selectedMood = MusicMood.valueOf(getPreferences(MODE_PRIVATE).getString("studio_mood", "PERSONAL"));
        } catch (IllegalArgumentException ignored) { selectedMood = MusicMood.PERSONAL; }
        buildUi();
        connectToPlaybackService();
        requestAudioPermissionIfNeeded();
        mainHandler.post(progressTask);
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(10));
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView brand = text("NAVA", 29, PURPLE, true);
        brand.setLetterSpacing(0.12f);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView subtitle = text("موسیقی، با حال‌وهوای تو", 13, MUTED, false);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(28)));

        buildMoodSelector();
        buildAiStudioCard();

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextSize(14);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setHintTextColor(MUTED);
        searchBox.setHint("جست‌وجوی آهنگ یا خواننده");
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        searchBox.setBackground(round(PANEL, 16));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.topMargin = dp(12);
        root.addView(searchBox, searchParams);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshSongList(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        Button allTab = button("همه", false);
        Button favoritesTab = button("♥", false);
        Button recentTab = button("اخیر", false);
        Button popularTab = button("پرمصرف", false);
        tabs.addView(allTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(favoritesTab, new LinearLayout.LayoutParams(0, dp(42), 0.72f));
        tabs.addView(recentTab, new LinearLayout.LayoutParams(0, dp(42), 0.9f));
        tabs.addView(popularTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(-1, dp(48));
        tabsParams.topMargin = dp(8);
        root.addView(tabs, tabsParams);
        buildGenreSelector();
        allTab.setOnClickListener(v -> {
            favoritesOnly = false; recentOnly = false; popularOnly = false;
            refreshSongList();
        });
        favoritesTab.setOnClickListener(v -> {
            favoritesOnly = true; recentOnly = false; popularOnly = false;
            refreshSongList();
        });
        recentTab.setOnClickListener(v -> {
            favoritesOnly = false; recentOnly = true; popularOnly = false;
            refreshSongList();
        });
        popularTab.setOnClickListener(v -> {
            favoritesOnly = false; recentOnly = false; popularOnly = true;
            refreshSongList();
        });

        LinearLayout libraryTools = new LinearLayout(this);
        libraryTools.setGravity(Gravity.CENTER_VERTICAL);
        libraryTools.setOrientation(LinearLayout.HORIZONTAL);
        TextView sortAction = smallAction("مرتب‌سازی  ⇅");
        TextView scanAction = smallAction("بازخوانی کتابخانه  ↻");
        libraryTools.addView(sortAction, new LinearLayout.LayoutParams(0, dp(38), 1));
        libraryTools.addView(scanAction, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, dp(42));
        toolsParams.topMargin = dp(4);
        root.addView(libraryTools, toolsParams);
        sortAction.setOnClickListener(v -> openSortDialog());
        scanAction.setOnClickListener(v -> loadSongs());

        statusText = text("در حال آماده‌سازی کتابخانه موسیقی…", 12, MUTED, false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, dp(28));
        statusParams.topMargin = dp(6);
        root.addView(statusText, statusParams);

        songList = new ListView(this);
        songList.setDivider(null);
        songList.setCacheColorHint(Color.TRANSPARENT);
        songList.setSelector(android.R.color.transparent);
        songList.setBackgroundColor(BG);
        adapter = new SongAdapter();
        songList.setAdapter(adapter);
        root.addView(songList, new LinearLayout.LayoutParams(-1, 0, 1));

        songList.setOnItemClickListener((parent, view, position, id) -> playSong(position));
        songList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < shownSongs.size()) {
                showSongOptions(shownSongs.get(position));
                return true;
            }
            return false;
        });

        playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setPadding(dp(16), dp(14), dp(16), dp(12));
        playerCard.setBackground(round(PANEL, 22));
        root.addView(playerCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout trackHeader = new LinearLayout(this);
        trackHeader.setOrientation(LinearLayout.HORIZONTAL);
        trackHeader.setGravity(Gravity.CENTER_VERTICAL);
        trackHeader.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        coverArt = new ImageView(this);
        coverArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverArt.setBackground(gradientRound(Color.rgb(31, 25, 55), Color.rgb(15, 35, 57), 18));
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(dp(68), dp(68));
        coverParams.leftMargin = dp(12);
        trackHeader.addView(coverArt, coverParams);
        LinearLayout trackLabels = new LinearLayout(this);
        trackLabels.setOrientation(LinearLayout.VERTICAL);
        trackLabels.setGravity(Gravity.CENTER_VERTICAL);
        nowTitle = text("هنوز آهنگی پخش نمی‌شود", 15, Color.WHITE, true);
        nowTitle.setMaxLines(1);
        nowTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nowArtist = text("یک آهنگ از فهرست انتخاب کن", 12, MUTED, false);
        nowArtist.setMaxLines(1);
        nowArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        trackLabels.addView(nowTitle, new LinearLayout.LayoutParams(-1, dp(28)));
        trackLabels.addView(nowArtist, new LinearLayout.LayoutParams(-1, dp(24)));
        trackHeader.addView(trackLabels, new LinearLayout.LayoutParams(0, -2, 1));
        playerCard.addView(trackHeader, new LinearLayout.LayoutParams(-1, dp(76)));

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(MINT));
        playerCard.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(34)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { }
            @Override
            public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                if (controller != null && controller.getDuration() > 0) {
                    controller.seekTo((controller.getDuration() * bar.getProgress()) / 1000L);
                }
            }
        });

        timeText = text("00:00  /  00:00", 11, MUTED, false);
        timeText.setGravity(Gravity.CENTER);
        playerCard.addView(timeText, new LinearLayout.LayoutParams(-1, dp(20)));

        LinearLayout primaryControls = new LinearLayout(this);
        primaryControls.setGravity(Gravity.CENTER);
        primaryControls.setOrientation(LinearLayout.HORIZONTAL);
        TextView previous = control("⏮", 25);
        playButton = control("▶", 29);
        TextView next = control("⏭", 25);
        primaryControls.addView(previous, controlParams(1));
        primaryControls.addView(playButton, controlParams(1.25f));
        primaryControls.addView(next, controlParams(1));
        playerCard.addView(primaryControls, new LinearLayout.LayoutParams(-1, dp(56)));
        previous.setOnClickListener(v -> { if (controller != null) controller.seekToPreviousMediaItem(); });
        playButton.setOnClickListener(v -> {
            if (controller == null) return;
            if (controller.isPlaying()) controller.pause(); else controller.play();
            updatePlayerUi();
        });
        next.setOnClickListener(v -> { if (controller != null) controller.seekToNextMediaItem(); });

        LinearLayout secondary = new LinearLayout(this);
        secondary.setGravity(Gravity.CENTER);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        shuffleButton = smallAction("تصادفی");
        repeatButton = smallAction("تکرار: خاموش");
        speedButton = smallAction("سرعت ۱×");
        TextView sleep = smallAction("تایمر خواب");
        secondary.addView(shuffleButton, new LinearLayout.LayoutParams(0, dp(38), 1));
        secondary.addView(repeatButton, new LinearLayout.LayoutParams(0, dp(38), 1));
        secondary.addView(speedButton, new LinearLayout.LayoutParams(0, dp(38), 1));
        secondary.addView(sleep, new LinearLayout.LayoutParams(0, dp(38), 1));
        playerCard.addView(secondary, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout proTools = new LinearLayout(this);
        proTools.setOrientation(LinearLayout.HORIZONTAL);
        proTools.setGravity(Gravity.CENTER);
        TextView queueAction = smallAction("صف پخش  ↕");
        TextView equalizerAction = smallAction("اکولایزر  ♪");
        proTools.addView(queueAction, new LinearLayout.LayoutParams(0, dp(38), 1));
        proTools.addView(equalizerAction, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams proParams = new LinearLayout.LayoutParams(-1, dp(40));
        proParams.topMargin = dp(4);
        playerCard.addView(proTools, proParams);
        queueAction.setOnClickListener(v -> showQueueDialog());
        equalizerAction.setOnClickListener(v -> openEqualizer());

        shuffleButton.setOnClickListener(v -> toggleShuffle());
        repeatButton.setOnClickListener(v -> cycleRepeat());
        speedButton.setOnClickListener(v -> chooseSpeed());
        sleep.setOnClickListener(v -> chooseSleepTimer());

        setContentView(root);
        applyMoodTheme(selectedMood);
    }

    private void buildAiStudioCard() {
        LinearLayout card = new LinearLayout(this);
        aiStudioCard = card;
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(55, 35, 92), Color.rgb(25, 67, 78)});
        bg.setCornerRadius(dp(20));
        card.setBackground(bg);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, dp(104));
        cardParams.topMargin = dp(8);
        cardParams.bottomMargin = dp(4);
        root.addView(card, cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text("✦  NAVA AI STUDIO", 16, Color.WHITE, true);
        title.setLetterSpacing(0.04f);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(30), 1));
        TextView tag = text("SMART DJ", 9, Color.rgb(154, 255, 232), true);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(dp(9), 0, dp(9), 0);
        tag.setBackground(round(Color.rgb(31, 91, 91), 14));
        header.addView(tag, new LinearLayout.LayoutParams(-2, dp(25)));
        card.addView(header);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        TextView description = text("میکس اختصاصی بر اساس حال‌وهوا و سلیقه‌ات", 11,
                Color.rgb(224, 220, 245), false);
        bottom.addView(description, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView launch = text("ساخت میکس  ✨", 12, Color.rgb(24, 22, 35), true);
        launch.setGravity(Gravity.CENTER);
        launch.setPadding(dp(12), 0, dp(12), 0);
        launch.setBackground(round(Color.rgb(154, 255, 232), 14));
        bottom.addView(launch, new LinearLayout.LayoutParams(-2, dp(34)));
        card.addView(bottom);
        card.setOnClickListener(v -> openAiStudio());
        launch.setOnClickListener(v -> openAiStudio());
    }

    private void openAiStudio() {
        if (allSongs.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("NAVA AI")
                    .setMessage("اول باید به کتابخانه موسیقی گوشی دسترسی داشته باشیم. اجازه دسترسی به موسیقی را فعال کن.")
                    .setPositiveButton("متوجه شدم", null).show();
            return;
        }
        String[] moods = {"🌙 آرامش و ریلکس", "🌈 شاد و سرحال", "🌧 غمگین و احساسی",
                "⚡ انرژی و هیجان", "🎯 تمرکز و مطالعه", "📻 نوستالژی و خاطره",
                "🕯 معنوی و مداحی", "💜 مطابق سلیقه من"};
        MusicMood[] values = MusicMood.values();
        new AlertDialog.Builder(this)
                .setTitle("NAVA AI  ·  دستیار موسیقی")
                .setMessage("الان چه حال‌وهوایی داری؟ از آهنگ‌های خود گوشی، یک میکس هوشمند می‌سازم.")
                .setItems(moods, (dialog, which) -> createAiMix(values[which]))
                .setNegativeButton("بعداً", null)
                .show();
    }

    private void createAiMix(MusicMood mood) {
        ArrayList<Song> ranked = new ArrayList<>(allSongs);
        Collections.sort(ranked, (left, right) ->
                Integer.compare(aiScore(right, mood), aiScore(left, mood)));
        if (ranked.isEmpty()) return;

        ArrayList<MediaItem> queue = new ArrayList<>();
        int limit = Math.min(30, ranked.size());
        for (int i = 0; i < limit; i++) queue.add(ranked.get(i).toMediaItem());

        selectedGenre = MusicGenre.ALL;
        favoritesOnly = false;
        if (searchBox != null) searchBox.setText("");
        refreshSongList();
        for (int i = 0; i < limit; i++) {
            if (ranked.get(i).id.equals(queue.get(0).mediaId)) {
                recordListening(ranked.get(i));
                break;
            }
        }
        if (controller == null) {
            Toast.makeText(this, "پخش‌کننده هنوز آماده نیست؛ دوباره تلاش کن.", Toast.LENGTH_LONG).show();
            return;
        }
        controller.setMediaItems(queue, 0, 0);
        controller.prepare();
        controller.play();
        applyMoodTheme(mood);
        String label = mood.label;
        Toast.makeText(this, "میکس هوشمند «" + label + "» ساخته شد · " + limit + " آهنگ",
                Toast.LENGTH_LONG).show();
    }

    private int aiScore(Song song, MusicMood mood) {
        MusicGenre genre = genreFor(song);
        String words = normalizeSearch(song.title + " " + song.artist + " " + song.album);
        int score = 0;
        if (favorites.contains(song.id)) score += 3;
        score += Math.min(2, playCounts.containsKey(song.id) ? playCounts.get(song.id) : 0);
        if (listeningHistory.contains(song.id)) score += 1;
        switch (mood) {
            case CALM:
                if (genre == MusicGenre.INSTRUMENTAL || genre == MusicGenre.TRADITIONAL) score += 8;
                if (hasAny(words, "آرام", "ملایم", "ریلکس", "مدیتیشن", "piano", "ambient", "calm")) score += 5;
                break;
            case HAPPY:
                if (genre == MusicGenre.POP) score += 9;
                if (hasAny(words, "شاد", "خنده", "جشن", "happy", "dance", "party", "upbeat")) score += 7;
                break;
            case SAD:
                if (hasAny(words, "غمگین", "دلتنگ", "جدایی", "گریه", "sad", "lonely", "heartbreak", "slow")) score += 9;
                if (genre == MusicGenre.TRADITIONAL || genre == MusicGenre.OLDIES) score += 3;
                break;
            case ENERGY:
                if (genre == MusicGenre.POP) score += 8;
                if (hasAny(words, "شاد", "انرژی", "رقص", "dance", "remix", "upbeat", "rock")) score += 5;
                break;
            case FOCUS:
                if (genre == MusicGenre.INSTRUMENTAL) score += 10;
                if (hasAny(words, "بی کلام", "instrumental", "focus", "study", "piano", "ambient")) score += 5;
                break;
            case NOSTALGIA:
                if (genre == MusicGenre.OLDIES) score += 10;
                if (hasAny(words, "قدیمی", "نوستالژی", "خاطره", "classic", "oldies")) score += 5;
                break;
            case SPIRITUAL:
                if (genre == MusicGenre.MADAHI) score += 12;
                if (hasAny(words, "مداحی", "نوحه", "روضه", "ذکر", "یا حسین", "یا زهرا")) score += 5;
                break;
            case PERSONAL:
                score += genre == MusicGenre.OTHER ? 0 : 3;
                break;
        }
        return score;
    }

    private void loadListeningProfile() {
        Set<String> savedCounts = getPreferences(MODE_PRIVATE)
                .getStringSet("ai_play_counts", new HashSet<>());
        for (String item : savedCounts) {
            int split = item.lastIndexOf('=');
            if (split <= 0) continue;
            try { playCounts.put(item.substring(0, split), Integer.parseInt(item.substring(split + 1))); }
            catch (NumberFormatException ignored) { }
        }
        String orderedHistory = getPreferences(MODE_PRIVATE)
                .getString("ai_recent_history_ordered", "");
        if (!orderedHistory.isEmpty()) {
            for (String id : orderedHistory.split("\\n")) {
                if (!id.isEmpty() && !listeningHistory.contains(id)) listeningHistory.add(id);
            }
        } else {
            listeningHistory.addAll(getPreferences(MODE_PRIVATE)
                    .getStringSet("ai_recent_history", new HashSet<>()));
        }
    }

    private void recordListening(Song song) {
        if (song == null) return;
        int count = playCounts.containsKey(song.id) ? playCounts.get(song.id) + 1 : 1;
        playCounts.put(song.id, count);
        listeningHistory.remove(song.id);
        listeningHistory.add(0, song.id);
        while (listeningHistory.size() > 30) listeningHistory.remove(listeningHistory.size() - 1);
        Set<String> savedCounts = new HashSet<>();
        for (Map.Entry<String, Integer> entry : playCounts.entrySet())
            savedCounts.add(entry.getKey() + "=" + entry.getValue());
        getPreferences(MODE_PRIVATE).edit()
                .putStringSet("ai_play_counts", savedCounts)
                .putString("ai_recent_history_ordered",
                        android.text.TextUtils.join("\\n", listeningHistory))
                .apply();
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace('ي', 'ی').replace('ك', 'ک')
                .replaceAll("[ًٌٍَُِّْـ]", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private boolean matchesSmartSearch(String haystack, String query) {
        if (haystack.contains(query)) return true;
        String expanded = query;
        if (hasAny(query, "غمگین", "دپ", "دلگیر")) expanded += " غمگین غم عشق جدایی";
        if (hasAny(query, "شاد", "سرحال", "انرژی")) expanded += " شاد انرژی رقص پاپ";
        if (hasAny(query, "آرام", "ریلکس", "استراحت")) expanded += " آرام ملایم بی کلام پیانو";
        if (hasAny(query, "مذهبی", "دینی", "روضه")) expanded += " مداحی نوحه روضه";
        if (hasAny(query, "قدیمی", "نوستالژی")) expanded += " قدیمی خاطره نوستالژی";
        if (hasAny(query, "بی کلام", "بدون خواننده")) expanded += " بی کلام instrumental piano";
        String[] terms = expanded.split("\\s+");
        int matches = 0;
        for (String term : terms) if (term.length() > 1 && haystack.contains(term)) matches++;
        return matches >= Math.max(1, Math.min(2, terms.length / 3));
    }

    private boolean hasAny(String text, String... terms) {
        for (String term : terms) if (text.contains(normalizeSearch(term))) return true;
        return false;
    }

    private enum MusicMood {
        CALM("آرامش"), HAPPY("شاد"), SAD("غمگین"), ENERGY("انرژی"), FOCUS("تمرکز"),
        NOSTALGIA("نوستالژی"), SPIRITUAL("معنوی"), PERSONAL("سلیقه شخصی");
        final String label;
        MusicMood(String label) { this.label = label; }
    }

    private void buildGenreSelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        genreTabs = new LinearLayout(this);
        genreTabs.setOrientation(LinearLayout.HORIZONTAL);
        genreTabs.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        for (MusicGenre genre : MusicGenre.values()) {
            TextView chip = text(genre.icon + "  " + genre.label, 12, Color.WHITE, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setBackground(round(PANEL, 18));
            chip.setTag(genre);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(40));
            params.leftMargin = dp(5);
            params.rightMargin = dp(2);
            genreTabs.addView(chip, params);
            chip.setOnClickListener(v -> {
                selectedGenre = genre;
                refreshSongList();
            });
        }
        scroll.addView(genreTabs, new HorizontalScrollView.LayoutParams(-2, dp(44)));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(48));
        scrollParams.topMargin = dp(7);
        root.addView(scroll, scrollParams);
        updateGenreTabStyles();
    }

    private void updateGenreTabStyles() {
        if (genreTabs == null) return;
        for (int i = 0; i < genreTabs.getChildCount(); i++) {
            View child = genreTabs.getChildAt(i);
            if (!(child instanceof TextView) || !(child.getTag() instanceof MusicGenre)) continue;
            MusicGenre genre = (MusicGenre) child.getTag();
            boolean selected = genre == selectedGenre;
            child.setBackground(round(selected ? activeAccent : activePanel, 18));
            ((TextView) child).setTextColor(selected ? Color.rgb(20, 18, 28) : Color.WHITE);
        }
    }

    private MusicGenre genreFor(Song song) {
        MusicGenre override = genreOverrides.get(song.id);
        return override != null ? override : MusicGenre.detect(song.title, song.artist, song.album);
    }

    private Song findSongById(String id) {
        if (id == null) return null;
        for (Song song : allSongs) if (id.equals(song.id)) return song;
        return null;
    }

    private void loadGenreOverrides() {
        Set<String> saved = getPreferences(MODE_PRIVATE).getStringSet("genre_overrides", new HashSet<>());
        for (String entry : saved) {
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator >= entry.length() - 1) continue;
            try {
                genreOverrides.put(entry.substring(0, separator),
                        MusicGenre.valueOf(entry.substring(separator + 1)));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveGenreOverrides() {
        Set<String> saved = new HashSet<>();
        for (Map.Entry<String, MusicGenre> entry : genreOverrides.entrySet()) {
            saved.add(entry.getKey() + "=" + entry.getValue().name());
        }
        getPreferences(MODE_PRIVATE).edit().putStringSet("genre_overrides", saved).apply();
    }

    private void showSongOptions(Song song) {
        String favoriteAction = favorites.contains(song.id)
                ? "حذف از علاقه‌مندی‌ها" : "افزودن به علاقه‌مندی‌ها";
        new AlertDialog.Builder(this)
                .setTitle(song.title)
                .setItems(new String[]{"تغییر سبک آهنگ", favoriteAction}, (dialog, which) -> {
                    if (which == 0) chooseSongGenre(song);
                    else toggleFavorite(song);
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void chooseSongGenre(Song song) {
        MusicGenre[] choices = {
                MusicGenre.MADAHI, MusicGenre.OLDIES, MusicGenre.TRADITIONAL,
                MusicGenre.POP, MusicGenre.INSTRUMENTAL, MusicGenre.OTHER
        };
        String[] labels = new String[choices.length];
        for (int i = 0; i < choices.length; i++) {
            labels[i] = choices[i].icon + "  " + choices[i].label;
        }
        new AlertDialog.Builder(this)
                .setTitle("سبک درست این آهنگ را انتخاب کن")
                .setItems(labels, (dialog, which) -> {
                    genreOverrides.put(song.id, choices[which]);
                    saveGenreOverrides();
                    refreshSongList();
                    Toast.makeText(this, "سبک آهنگ ذخیره شد", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void applyThemeForGenre(MusicGenre genre) {
        applyMoodTheme(selectedMood);
    }

    private void buildMoodSelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        LinearLayout chips = new LinearLayout(this);
        moodChips = chips;
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        MusicMood[] moods = {MusicMood.HAPPY, MusicMood.SAD, MusicMood.ENERGY,
                MusicMood.CALM, MusicMood.FOCUS, MusicMood.NOSTALGIA, MusicMood.SPIRITUAL};
        String[] labels = {"🌈 شاد", "🌧 غمگین", "⚡ پرانرژی", "🌙 آرام", "🎯 تمرکز", "📻 خاطره", "🕯 معنوی"};
        for (int i = 0; i < moods.length; i++) {
            final MusicMood mood = moods[i];
            TextView chip = text(labels[i], 12, Color.WHITE, true);
            chip.setGravity(Gravity.CENTER);
            chip.setTag(mood);
            chip.setPadding(dp(15), 0, dp(15), 0);
            chip.setBackground(round(activePanel, 18));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-2, dp(40));
            itemParams.leftMargin = dp(5);
            chips.addView(chip, itemParams);
            chip.setOnClickListener(v -> {
                selectedMood = mood;
                getPreferences(MODE_PRIVATE).edit().putString("studio_mood", mood.name()).apply();
                applyMoodTheme(mood);
                Toast.makeText(this, "تم «" + mood.label + "» فعال شد", Toast.LENGTH_SHORT).show();
            });
        }
        scroll.addView(chips, new HorizontalScrollView.LayoutParams(-2, dp(42)));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, dp(48));
        scrollParams.topMargin = dp(5);
        scrollParams.bottomMargin = dp(3);
        root.addView(scroll, scrollParams);
    }

    private void applyMoodTheme(MusicMood mood) {
        selectedMood = mood == null ? MusicMood.PERSONAL : mood;
        int start, end, panel, accent, secondary;
        switch (selectedMood) {
            case HAPPY:
                start = Color.rgb(47, 18, 48); end = Color.rgb(93, 35, 68);
                panel = Color.rgb(57, 27, 58); accent = Color.rgb(255, 145, 190); secondary = Color.rgb(255, 211, 127); break;
            case SAD:
                start = Color.rgb(10, 22, 43); end = Color.rgb(28, 48, 78);
                panel = Color.rgb(24, 39, 64); accent = Color.rgb(133, 187, 236); secondary = Color.rgb(190, 177, 233); break;
            case ENERGY:
                start = Color.rgb(40, 14, 36); end = Color.rgb(91, 29, 57);
                panel = Color.rgb(58, 25, 49); accent = Color.rgb(255, 105, 125); secondary = Color.rgb(255, 194, 102); break;
            case CALM:
                start = Color.rgb(9, 34, 36); end = Color.rgb(24, 65, 67);
                panel = Color.rgb(20, 49, 51); accent = Color.rgb(128, 229, 207); secondary = Color.rgb(181, 230, 216); break;
            case FOCUS:
                start = Color.rgb(12, 24, 44); end = Color.rgb(33, 54, 81);
                panel = Color.rgb(24, 41, 64); accent = Color.rgb(126, 187, 255); secondary = Color.rgb(183, 211, 255); break;
            case NOSTALGIA:
                start = Color.rgb(39, 27, 19); end = Color.rgb(82, 54, 32);
                panel = Color.rgb(52, 37, 26); accent = Color.rgb(244, 195, 125); secondary = Color.rgb(232, 173, 113); break;
            case SPIRITUAL:
                start = Color.rgb(29, 21, 43); end = Color.rgb(73, 48, 77);
                panel = Color.rgb(47, 34, 55); accent = Color.rgb(223, 193, 244); secondary = Color.rgb(240, 205, 145); break;
            default:
                start = BG; end = Color.rgb(27, 23, 51);
                panel = PANEL; accent = PURPLE; secondary = MINT; break;
        }
        activeBackground = start; activePanel = panel; activeAccent = accent; activeSecondaryAccent = secondary;
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end, BG});
        root.setBackground(background);
        if (playerCard != null) playerCard.setBackground(gradientRound(panel, end, 22));
        if (aiStudioCard != null) aiStudioCard.setBackground(gradientRound(end, panel, 20));
        if (searchBox != null) searchBox.setBackground(gradientRound(panel, end, 16));
        if (songList != null) songList.setBackgroundColor(start);
        if (nowTitle != null) nowTitle.setTextColor(accent);
        if (nowArtist != null) nowArtist.setTextColor(secondary);
        if (statusText != null) statusText.setTextColor(secondary);
        if (coverArt != null) coverArt.setBackground(gradientRound(end, panel, 24));
        if (seekBar != null) {
            seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(secondary));
        }
        if (playButton != null) playButton.setTextColor(accent);
        if (genreTabs != null) updateGenreTabStyles();
        if (moodChips != null) {
            for (int i = 0; i < moodChips.getChildCount(); i++) {
                View child = moodChips.getChildAt(i);
                if (!(child instanceof TextView) || !(child.getTag() instanceof MusicMood)) continue;
                boolean active = child.getTag() == selectedMood;
                child.setBackground(round(active ? accent : panel, 18));
                ((TextView) child).setTextColor(active ? Color.rgb(20, 18, 28) : Color.WHITE);
            }
        }
    }

    private GradientDrawable gradientRound(int first, int second, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{first, second});
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), Color.argb(45, 255, 255, 255));
        return drawable;
    }

    private void connectToPlaybackService() {
        SessionToken token = new SessionToken(this, new ComponentName(this, NavaPlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(playerListener);
                updatePlayerUi();
            } catch (ExecutionException e) {
                Toast.makeText(this, "اتصال پخش‌کننده برقرار نشد", Toast.LENGTH_LONG).show();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, command -> mainHandler.post(command));
    }

    private void requestAudioPermissionIfNeeded() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        } else {
            requestPermissions(new String[]{permission}, AUDIO_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSongs();
            } else {
                statusText.setText("برای نمایش آهنگ‌های گوشی، اجازه دسترسی به موسیقی لازم است.");
            }
        }
    }

    private void loadSongs() {
        allSongs.clear();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID
        };
        // Read every item indexed in Android's audio media collection.
        // Do not filter by IS_MUSIC or duration: some valid local tracks have neither metadata flag.
        String order = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";
        try (Cursor cursor = getContentResolver().query(collection, projection, null, null, order)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    long duration = cursor.getLong(durationCol);
                    String album = cursor.getString(albumCol);
                    long albumId = cursor.getLong(albumIdCol);
                    Uri contentUri = ContentUris.withAppendedId(collection, id);
                    Uri artworkUri = albumId > 0 ? ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"), albumId) : null;
                    allSongs.add(new Song(String.valueOf(id), title == null ? "بدون نام" : title,
                            artist == null ? "هنرمند ناشناس" : artist,
                            album == null ? "" : album, contentUri, duration, artworkUri));
                }
            }
            refreshSongList();
            if (allSongs.isEmpty() && statusText != null) {
                statusText.setText("فایلی پیدا نشد؛ آهنگ‌ها باید روی خود گوشی ذخیره شده باشند، نه فقط داخل برنامه‌های پخش آنلاین.");
            }
        } catch (SecurityException e) {
            statusText.setText("اجازهٔ دسترسی به موسیقی گوشی داده نشده است. از تنظیمات برنامه، دسترسی موسیقی را فعال کن.");
        }
    }

    private void refreshSongList() {
        if (adapter == null) return;
        String query = searchBox == null ? "" : normalizeSearch(searchBox.getText().toString());
        shownSongs.clear();
        for (Song song : allSongs) {
            if (favoritesOnly && !favorites.contains(song.id)) continue;
            if (recentOnly && !listeningHistory.contains(song.id)) continue;
            if (popularOnly && (!playCounts.containsKey(song.id) || playCounts.get(song.id) < 2)) continue;
            if (selectedGenre != MusicGenre.ALL && genreFor(song) != selectedGenre) continue;
            String haystack = normalizeSearch(song.title + " " + song.artist + " " + song.album);
            if (!query.isEmpty() && !matchesSmartSearch(haystack, query)) continue;
            shownSongs.add(song);
        }
        applyLibrarySort();
        adapter.notifyDataSetChanged();
        updateGenreTabStyles();
        applyMoodTheme(selectedMood);
        if (statusText != null) {
            if (allSongs.isEmpty()) statusText.setText("هنوز آهنگی پیدا نشده؛ اجازه دسترسی یا وجود فایل موسیقی را بررسی کن.");
            else statusText.setText(shownSongs.size() + " آهنگ  •  " +
                    (favoritesOnly ? "علاقه‌مندی‌ها" : recentOnly ? "اخیراً پخش‌شده" :
                    popularOnly ? "پرمخاطب‌ترین‌ها" : selectedGenre == MusicGenre.ALL ? "همه سبک‌ها" : selectedGenre.label));
        }
    }

    private void openSortDialog() {
        String[] options = {"نام آهنگ (الفبایی)", "نام خواننده", "مدت زمان", "بیشترین پخش", "آخرین پخش‌شده‌ها"};
        new AlertDialog.Builder(this).setTitle("مرتب‌سازی کتابخانه")
                .setSingleChoiceItems(options, sortMode, (dialog, which) -> {
                    sortMode = which;
                    getPreferences(MODE_PRIVATE).edit().putInt("library_sort_mode", sortMode).apply();
                    applyLibrarySort();
                    if (adapter != null) adapter.notifyDataSetChanged();
                    dialog.dismiss();
                    Toast.makeText(this, "ترتیب آهنگ‌ها تغییر کرد", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("بستن", null).show();
    }

    private void applyLibrarySort() {
        Collections.sort(shownSongs, (a, b) -> {
            switch (sortMode) {
                case 1: return a.artist.compareToIgnoreCase(b.artist);
                case 2: return Long.compare(b.duration, a.duration);
                case 3: return Integer.compare(
                        playCounts.containsKey(b.id) ? playCounts.get(b.id) : 0,
                        playCounts.containsKey(a.id) ? playCounts.get(a.id) : 0);
                case 4: return Integer.compare(listeningHistory.indexOf(a.id) < 0 ? Integer.MAX_VALUE : listeningHistory.indexOf(a.id),
                        listeningHistory.indexOf(b.id) < 0 ? Integer.MAX_VALUE : listeningHistory.indexOf(b.id));
                default: return a.title.compareToIgnoreCase(b.title);
            }
        });
    }

    private void playSong(int position) {
        if (controller == null || position < 0 || position >= shownSongs.size()) {
            Toast.makeText(this, "پخش‌کننده در حال آماده‌سازی است", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<MediaItem> queue = new ArrayList<>();
        for (Song song : shownSongs) queue.add(song.toMediaItem());
        applyMoodTheme(selectedMood);
        recordListening(shownSongs.get(position));
        controller.setMediaItems(queue, position, 0);
        controller.prepare();
        controller.play();
    }

    private void toggleFavorite(Song song) {
        if (favorites.contains(song.id)) favorites.remove(song.id);
        else favorites.add(song.id);
        getPreferences(MODE_PRIVATE).edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
        refreshSongList();
        Toast.makeText(this, favorites.contains(song.id) ? "به علاقه‌مندی‌ها اضافه شد" : "از علاقه‌مندی‌ها حذف شد",
                Toast.LENGTH_SHORT).show();
    }

    private void openEqualizer() {
        final Equalizer equalizer = NavaPlaybackService.getEqualizer();
        if (equalizer == null) {
            Toast.makeText(this, "اکولایزر این دستگاه در دسترس نیست؛ ابتدا یک آهنگ پخش کن.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            short[] range = equalizer.getBandLevelRange();
            final int min = range[0];
            int max = range[1];
            short count = equalizer.getNumberOfBands();
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(18), dp(10), dp(18), dp(10));
            for (short i = 0; i < count; i++) {
                final short band = i;
                int hz = equalizer.getCenterFreq(i) / 1000;
                String label = hz >= 1000 ? String.format(Locale.US, "%.1f kHz", hz / 1000f) : hz + " Hz";
                TextView name = text(label, 12, activeSecondaryAccent, true);
                content.addView(name, new LinearLayout.LayoutParams(-1, dp(22)));
                SeekBar slider = new SeekBar(this);
                slider.setMax(Math.max(1, max - min));
                slider.setProgress(equalizer.getBandLevel(i) - min);
                slider.setProgressTintList(android.content.res.ColorStateList.valueOf(activeAccent));
                slider.setThumbTintList(android.content.res.ColorStateList.valueOf(activeSecondaryAccent));
                content.addView(slider, new LinearLayout.LayoutParams(-1, dp(34)));
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        if (fromUser) {
                            try { equalizer.setBandLevel(band, (short) (progress + min)); }
                            catch (RuntimeException ignored) { }
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar bar) { }
                    @Override public void onStopTrackingTouch(SeekBar bar) { }
                });
            }
            ScrollView scroll = new ScrollView(this);
            scroll.addView(content);
            new AlertDialog.Builder(this).setTitle("NAVA · اکولایزر صوتی")
                    .setMessage("تنظیم واقعی فرکانس‌های صدای خروجی دستگاه")
                    .setView(scroll).setPositiveButton("انجام شد", null)
                    .setNeutralButton("خاموش / روشن", (dialog, which) -> {
                        try { equalizer.setEnabled(!equalizer.getEnabled()); }
                        catch (RuntimeException ignored) { }
                    }).show();
        } catch (RuntimeException ex) {
            Toast.makeText(this, "درایور صوتی گوشی اجازه استفاده از اکولایزر را نداد.", Toast.LENGTH_LONG).show();
        }
    }

    private void showQueueDialog() {
        if (controller == null || controller.getMediaItemCount() == 0) {
            Toast.makeText(this, "صف پخش خالی است.", Toast.LENGTH_SHORT).show();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(12), dp(8), dp(12), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("صف پخش · " + controller.getMediaItemCount() + " آهنگ")
                .setView(scroll).setNegativeButton("بستن", null).create();
        int current = controller.getCurrentMediaItemIndex();
        for (int i = 0; i < controller.getMediaItemCount(); i++) {
            final int index = i;
            MediaItem item = controller.getMediaItemAt(i);
            CharSequence title = item.mediaMetadata.title;
            CharSequence artist = item.mediaMetadata.artist;
            String label = (i == current ? "▶  " : (i + 1) + "  ")
                    + (title == null ? "آهنگ بدون نام" : title)
                    + (artist == null ? "" : "  ·  " + artist);
            TextView row = text(label, 13, Color.WHITE, i == current);
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setBackground(round(i == current ? activePanel : Color.rgb(34, 33, 48), 12));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(48));
            rowParams.bottomMargin = dp(5);
            rows.addView(row, rowParams);
            row.setOnClickListener(v -> {
                if (controller != null) {
                    controller.seekTo(index, 0);
                    controller.play();
                }
                dialog.dismiss();
            });
            row.setOnLongClickListener(v -> {
                String[] actions = {"انتقال به ابتدای صف", "حذف از صف"};
                new AlertDialog.Builder(this).setTitle("مدیریت آهنگ").setItems(actions, (d, which) -> {
                    if (controller == null) return;
                    if (which == 0 && index < controller.getMediaItemCount()) controller.moveMediaItem(index, 0);
                    else if (which == 1 && index < controller.getMediaItemCount()) controller.removeMediaItem(index);
                    dialog.dismiss();
                    showQueueDialog();
                }).show();
                return true;
            });
        }
        scroll.addView(rows);
        dialog.show();
    }

    private void toggleShuffle() {
        if (controller == null) return;
        shuffleEnabled = !shuffleEnabled;
        controller.setShuffleModeEnabled(shuffleEnabled);
        shuffleButton.setText(shuffleEnabled ? "تصادفی: روشن" : "تصادفی");
        shuffleButton.setTextColor(shuffleEnabled ? MINT : Color.WHITE);
    }

    private void cycleRepeat() {
        if (controller == null) return;
        repeatMode = repeatMode == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL
                : repeatMode == Player.REPEAT_MODE_ALL ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF;
        controller.setRepeatMode(repeatMode);
        String label = repeatMode == Player.REPEAT_MODE_OFF ? "تکرار: خاموش"
                : repeatMode == Player.REPEAT_MODE_ALL ? "تکرار: همه" : "تکرار: آهنگ";
        repeatButton.setText(label);
        repeatButton.setTextColor(repeatMode == Player.REPEAT_MODE_OFF ? Color.WHITE : MINT);
    }

    private void chooseSpeed() {
        String[] labels = {"۰٫۷۵×", "۱×", "۱٫۲۵×", "۱٫۵×", "۱٫۷۵×", "۲×"};
        float[] speeds = {0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        new AlertDialog.Builder(this).setTitle("سرعت پخش")
                .setItems(labels, (dialog, which) -> {
                    playbackSpeed = speeds[which];
                    if (controller != null) controller.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
                    speedButton.setText("سرعت " + labels[which]);
                }).setNegativeButton("بستن", null).show();
    }

    private void chooseSleepTimer() {
        String[] labels = {"۱۵ دقیقه", "۳۰ دقیقه", "۴۵ دقیقه", "۶۰ دقیقه", "غیرفعال کردن تایمر"};
        long[] delays = {15, 30, 45, 60, 0};
        new AlertDialog.Builder(this).setTitle("توقف خودکار پخش")
                .setItems(labels, (dialog, which) -> {
                    if (sleepTask != null) mainHandler.removeCallbacks(sleepTask);
                    sleepTask = null;
                    if (delays[which] == 0) {
                        Toast.makeText(this, "تایمر غیرفعال شد", Toast.LENGTH_SHORT).show();
                    } else {
                        sleepTask = () -> {
                            if (controller != null) controller.pause();
                            Toast.makeText(this, "تایمر خواب پخش را متوقف کرد", Toast.LENGTH_LONG).show();
                        };
                        mainHandler.postDelayed(sleepTask, delays[which] * 60_000L);
                        Toast.makeText(this, "تایمر برای " + labels[which] + " تنظیم شد", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("انصراف", null).show();
    }

    private void updatePlayerUi() {
        if (controller == null) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item != null) {
            CharSequence title = item.mediaMetadata.title;
            CharSequence artist = item.mediaMetadata.artist;
            nowTitle.setText(title == null ? "در حال پخش" : title);
            nowArtist.setText(artist == null ? " " : artist);
            Song playing = findSongById(item.mediaId);
            if (coverArt != null) {
                if (playing != null && playing.artworkUri != null) coverArt.setImageURI(playing.artworkUri);
                else coverArt.setImageDrawable(null);
            }
        }
        playButton.setText(controller.isPlaying() ? "Ⅱ" : "▶");
        updateProgress();
    }

    private void updateProgress() {
        if (controller == null || seekBar == null) return;
        long duration = controller.getDuration();
        long position = controller.getCurrentPosition();
        if (duration > 0) {
            if (!userSeeking) seekBar.setProgress((int) Math.min(1000, position * 1000 / duration));
            timeText.setText(formatTime(position) + "  /  " + formatTime(duration));
        } else {
            if (!userSeeking) seekBar.setProgress(0);
            timeText.setText("00:00  /  00:00");
        }
    }

    private String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button button(String value, boolean primary) {
        Button view = new Button(this);
        view.setText(value);
        view.setTextSize(12);
        view.setAllCaps(false);
        view.setTextColor(primary ? BG : Color.WHITE);
        view.setBackground(round(primary ? PURPLE : PANEL, 14));
        return view;
    }

    private TextView control(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(PURPLE);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView smallAction(String value) {
        TextView view = text(value, 10, Color.WHITE, false);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(Color.rgb(39, 37, 55), 12));
        return view;
    }

    private LinearLayout.LayoutParams controlParams(float weight) {
        return new LinearLayout.LayoutParams(0, dp(52), weight);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(progressTask);
        if (sleepTask != null) mainHandler.removeCallbacks(sleepTask);
        if (controller != null) {
            controller.removeListener(playerListener);
            controller.release();
            controller = null;
        }
        super.onDestroy();
    }

    private static final class Song {
        final String id;
        final String title;
        final String artist;
        final String album;
        final Uri uri;
        final long duration;
        final Uri artworkUri;

        Song(String id, String title, String artist, String album, Uri uri, long duration, Uri artworkUri) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.uri = uri;
            this.duration = duration;
            this.artworkUri = artworkUri;
        }

        MediaItem toMediaItem() {
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri)
                    .build();
            return new MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(metadata).build();
        }
    }

    private static final class SongRowHolder {
        TextView genreIcon;
        TextView title;
        TextView details;
    }

    private final class SongAdapter extends BaseAdapter {
        @Override public int getCount() { return shownSongs.size(); }
        @Override public Object getItem(int position) { return shownSongs.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            SongRowHolder holder;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof SongRowHolder) {
                row = (LinearLayout) convertView;
                holder = (SongRowHolder) row.getTag();
            } else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(8), dp(10), dp(8));
                holder = new SongRowHolder();

                holder.genreIcon = new TextView(MainActivity.this);
                holder.genreIcon.setGravity(Gravity.CENTER);
                holder.genreIcon.setTextSize(20);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(46), dp(46));
                iconParams.rightMargin = dp(10);
                row.addView(holder.genreIcon, iconParams);

                LinearLayout labels = new LinearLayout(MainActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setGravity(Gravity.CENTER_VERTICAL);
                holder.title = text("", 14, Color.WHITE, true);
                holder.title.setMaxLines(1);
                holder.title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                holder.details = text("", 11, MUTED, false);
                holder.details.setMaxLines(1);
                holder.details.setEllipsize(android.text.TextUtils.TruncateAt.END);
                labels.addView(holder.title, new LinearLayout.LayoutParams(-1, dp(25)));
                labels.addView(holder.details, new LinearLayout.LayoutParams(-1, dp(20)));
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                row.setTag(holder);
            }

            Song song = shownSongs.get(position);
            MusicGenre genre = genreFor(song);
            row.setBackground(round(genre.panelColor, 14));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(7);
            row.setLayoutParams(rowParams);

            holder.genreIcon.setText(genre.icon);
            holder.genreIcon.setBackground(round(genre.backgroundColor, 12));
            holder.genreIcon.setTextColor(genre.accent);
            holder.title.setText((favorites.contains(song.id) ? "♥  " : "") + song.title);
            holder.title.setTextColor(Color.WHITE);
            holder.details.setText(song.artist + "  •  " + genre.label + "  •  " + formatTime(song.duration));
            holder.details.setTextColor(genre.accent);
            return row;
        }
    }
}
