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
import java.util.HashSet;
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

    private boolean favoritesOnly = false;
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
        Button allTab = button("همه آهنگ‌ها", false);
        Button favoritesTab = button("علاقه‌مندی‌ها", false);
        tabs.addView(allTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(favoritesTab, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(-1, dp(48));
        tabsParams.topMargin = dp(8);
        root.addView(tabs, tabsParams);
        allTab.setOnClickListener(v -> {
            favoritesOnly = false;
            refreshSongList();
        });
        favoritesTab.setOnClickListener(v -> {
            favoritesOnly = true;
            refreshSongList();
        });

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
                toggleFavorite(shownSongs.get(position));
                return true;
            }
            return false;
        });

        LinearLayout playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setPadding(dp(16), dp(14), dp(16), dp(12));
        playerCard.setBackground(round(PANEL, 22));
        root.addView(playerCard, new LinearLayout.LayoutParams(-1, -2));

        nowTitle = text("هنوز آهنگی پخش نمی‌شود", 15, Color.WHITE, true);
        nowArtist = text("یک آهنگ از فهرست انتخاب کن", 12, MUTED, false);
        playerCard.addView(nowTitle, new LinearLayout.LayoutParams(-1, dp(26)));
        playerCard.addView(nowArtist, new LinearLayout.LayoutParams(-1, dp(22)));

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

        shuffleButton.setOnClickListener(v -> toggleShuffle());
        repeatButton.setOnClickListener(v -> cycleRepeat());
        speedButton.setOnClickListener(v -> chooseSpeed());
        sleep.setOnClickListener(v -> chooseSleepTimer());

        setContentView(root);
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
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String order = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";
        try (Cursor cursor = getContentResolver().query(collection, projection, selection, null, order)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    long duration = cursor.getLong(durationCol);
                    Uri contentUri = ContentUris.withAppendedId(collection, id);
                    allSongs.add(new Song(String.valueOf(id), title == null ? "بدون نام" : title,
                            artist == null ? "هنرمند ناشناس" : artist, contentUri, duration));
                }
            }
            refreshSongList();
        } catch (SecurityException e) {
            statusText.setText("دسترسی به فایل‌های موسیقی داده نشد.");
        }
    }

    private void refreshSongList() {
        if (adapter == null) return;
        String query = searchBox == null ? "" : searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);
        shownSongs.clear();
        for (Song song : allSongs) {
            if (favoritesOnly && !favorites.contains(song.id)) continue;
            String haystack = (song.title + " " + song.artist).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            shownSongs.add(song);
        }
        adapter.notifyDataSetChanged();
        if (statusText != null) {
            if (allSongs.isEmpty()) statusText.setText("هنوز آهنگی پیدا نشده؛ اجازه دسترسی یا وجود فایل موسیقی را بررسی کن.");
            else statusText.setText(shownSongs.size() + " آهنگ");
        }
    }

    private void playSong(int position) {
        if (controller == null || position < 0 || position >= shownSongs.size()) {
            Toast.makeText(this, "پخش‌کننده در حال آماده‌سازی است", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<MediaItem> queue = new ArrayList<>();
        for (Song song : shownSongs) queue.add(song.toMediaItem());
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
        final Uri uri;
        final long duration;

        Song(String id, String title, String artist, Uri uri, long duration) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.uri = uri;
            this.duration = duration;
        }

        MediaItem toMediaItem() {
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build();
            return new MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(metadata).build();
        }
    }

    private final class SongAdapter extends BaseAdapter {
        @Override public int getCount() { return shownSongs.size(); }
        @Override public Object getItem(int position) { return shownSongs.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            TextView artist;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                title = (TextView) row.getChildAt(0);
                artist = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                row.setBackground(round(PANEL, 13));
                title = text("", 14, Color.WHITE, true);
                artist = text("", 11, MUTED, false);
                row.addView(title, new LinearLayout.LayoutParams(-1, dp(25)));
                row.addView(artist, new LinearLayout.LayoutParams(-1, dp(20)));
            }
            Song song = shownSongs.get(position);
            title.setText((favorites.contains(song.id) ? "♥  " : "") + song.title);
            artist.setText(song.artist + "  •  " + formatTime(song.duration));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(7);
            row.setLayoutParams(params);
            return row;
        }
    }
}
