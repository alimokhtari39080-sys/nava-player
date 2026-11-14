package com.avanature.soundscape.v2;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private LinearLayout root, content, themeRow, cardsBox, miniPanel;
    private ScenicBackdrop scenicBackdrop;
    private TextView heroTitle, heroSubtitle, playerStatus, timerStatus, favoritesTab, homeTab;
    private EditText searchInput;
    private SeekBar volumeBar;
    private final List<ThemeData> themes = new ArrayList<>();
    private final Set<String> activeIds = new LinkedHashSet<>();
    private final Set<String> favoriteIds = new LinkedHashSet<>();
    private SharedPreferences prefs;
    private ThemeData selectedTheme;
    private CountDownTimer sleepTimer, testSoundTimer;
    private ToneGenerator testToneGenerator;
    private boolean showFavorites = false;
    private float masterVolume = 0.90f;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().setStatusBarColor(Color.rgb(5, 19, 26));
            getWindow().setNavigationBarColor(Color.rgb(5, 19, 26));
            prefs = getSharedPreferences("ava_nature_plus", MODE_PRIVATE);
            Set<String> saved = prefs.getStringSet("favorites", new LinkedHashSet<>());
            if (saved != null) favoriteIds.addAll(saved);
            buildCatalog();
            if (themes.isEmpty()) throw new IllegalStateException("Sound theme catalog is empty");
            selectedTheme = themes.get(0);
            createScreen();
            renderTheme();
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 41);
            }
        } catch (Throwable error) {
            android.util.Log.e("AvaNatureStartup", "Startup failed; showing recovery screen.", error);
            showRecoveryScreen(error);
        }
    }

    private void showRecoveryScreen(Throwable error) {
        LinearLayout recovery = new LinearLayout(this);
        recovery.setOrientation(LinearLayout.VERTICAL);
        recovery.setPadding(dp(24), dp(24), dp(24), dp(24));
        recovery.setBackgroundColor(Color.rgb(7, 25, 35));
        TextView heading = text("آواطبیعت پلاس", 22, Color.WHITE, true);
        recovery.addView(heading);
        TextView detail = text("برنامه با یک خطای داخلی روبه‌رو شد؛ اما برای جلوگیری از بسته‌شدن کامل، صفحه بازیابی نمایش داده شده است.", 14, 0xffd3e4df, false);
        detail.setPadding(0, dp(16), 0, dp(16));
        recovery.addView(detail);
        TextView retry = pill("تلاش دوباره", 14, Color.WHITE);
        retry.setOnClickListener(v -> recreate());
        recovery.addView(retry, new LinearLayout.LayoutParams(-1, dp(48)));
        setContentView(recovery);
    }

    private void createScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(2), 0, dp(12));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("AvaNature+", 25, Color.WHITE, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView about = pill("ⓘ  درباره", 12, 0xffd9ebdf);
        about.setOnClickListener(v -> showAbout());
        header.addView(about);
        content.addView(header);

        TextView strap = text("آواهای جهان؛ هر لحظه، هر حال‌وهوا", 12, 0xffa9c2bd, false);
        strap.setPadding(0, dp(1), 0, dp(12));
        content.addView(strap);

        heroTitle = text("", 22, Color.WHITE, true);
        heroSubtitle = text("", 12, 0xffe2eee7, false);
        FrameLayout hero = new FrameLayout(this);
        scenicBackdrop = new ScenicBackdrop(this);
        hero.addView(scenicBackdrop, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setGravity(Gravity.BOTTOM);
        heroText.setPadding(dp(18), dp(14), dp(18), dp(16));
        heroText.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x22000000, 0x99000000}));
        heroText.addView(heroTitle);
        heroText.addView(heroSubtitle);
        hero.addView(heroText, new FrameLayout.LayoutParams(-1, -1));
        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(174)));
        hero.setTag("hero");

        TextView themeHeading = text("تم‌های اختصاصی", 16, Color.WHITE, true);
        themeHeading.setPadding(0, dp(18), 0, dp(8));
        content.addView(themeHeading);
        HorizontalScrollView themeScroll = new HorizontalScrollView(this);
        themeScroll.setHorizontalScrollBarEnabled(false);
        themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        themeScroll.addView(themeRow);
        content.addView(themeScroll, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView presetHeading = text("میکس‌های آماده", 16, Color.WHITE, true);
        presetHeading.setPadding(0, dp(18), 0, dp(8));
        content.addView(presetHeading);
        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetScroll.addView(presetRow);
        content.addView(presetScroll, new LinearLayout.LayoutParams(-1, dp(82)));
        addPreset(presetRow, "🌧️  شب بارانی", "rain_soft", "rain_window", "brown_noise");
        addPreset(presetRow, "🌲  جنگل عمیق", "forest_wind", "birdsong", "stream");
        addPreset(presetRow, "🌩️  طوفان", "thunder_far", "rain_heavy", "storm_wind");
        addPreset(presetRow, "🖤  بم و چک‌چک", "bass_rain", "deep_thunder", "brown_noise");
        addPreset(presetRow, "🦉  حیات وحش", "owl", "wolf_howl", "cricket");
        addPreset(presetRow, "🌊  خواب اقیانوس", "waves", "ocean_underwater", "night_air");

        TextView soundsHeading = text("کتابخانه صداها", 16, Color.WHITE, true);
        soundsHeading.setPadding(0, dp(18), 0, dp(8));
        content.addView(soundsHeading);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(13);
        searchInput.setHint("جست‌وجوی صدا در این تم…");
        searchInput.setHintTextColor(0xff92a9a6);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setPadding(dp(14), dp(4), dp(14), dp(4));
        searchInput.setBackground(round(0xff122d35, 0xff294b4d, 1, 18));
        content.addView(searchInput, new LinearLayout.LayoutParams(-1, dp(46)));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderCards(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        cardsBox = new LinearLayout(this);
        cardsBox.setOrientation(LinearLayout.VERTICAL);
        cardsBox.setPadding(0, dp(10), 0, dp(6));
        content.addView(cardsBox);

        miniPanel = new LinearLayout(this);
        miniPanel.setOrientation(LinearLayout.VERTICAL);
        miniPanel.setPadding(dp(12), dp(10), dp(12), dp(8));
        miniPanel.setBackground(round(0xff102a33, 0xff2c5150, 1, 20));
        playerStatus = text("هنوز صدایی انتخاب نشده", 13, Color.WHITE, true);
        miniPanel.addView(playerStatus);
        LinearLayout volumeLine = new LinearLayout(this);
        volumeLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView volLabel = text("شدت صدا", 11, 0xffb9ceca, false);
        volumeLine.addView(volLabel);
        volumeBar = new SeekBar(this);
        volumeBar.setMax(100);
        volumeBar.setProgress((int)(masterVolume*100));
        volumeLine.addView(volumeBar, new LinearLayout.LayoutParams(0, dp(34), 1));
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                masterVolume = p/100f;
                sendVolume();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        miniPanel.addView(volumeLine);
        LinearLayout controlLine = new LinearLayout(this);
        controlLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView stop = pill("■  توقف همه", 12, 0xffffd8cc);
        stop.setOnClickListener(v -> clearSounds());
        controlLine.addView(stop);
        TextView test = pill("▶  تست صدا", 11, 0xffbdf4cb);
        test.setOnClickListener(v -> playTestSound());
        controlLine.addView(test);
        timerStatus = text("  ⏱ بدون تایمر", 11, 0xffc6d7d1, false);
        LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(0, -2, 1);
        timerLp.gravity = Gravity.CENTER_VERTICAL;
        controlLine.addView(timerStatus, timerLp);
        TextView timer = pill("تایمر خواب", 12, 0xffe7f3d9);
        timer.setOnClickListener(v -> showTimerDialog());
        controlLine.addView(timer);
        miniPanel.addView(controlLine);
        root.addView(miniPanel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(0, dp(8), 0, dp(1));
        homeTab = navButton("⌂  خانه", true);
        homeTab.setOnClickListener(v -> { showFavorites = false; updateNav(); renderCards(); });
        favoritesTab = navButton("♡  محبوب‌ها", false);
        favoritesTab.setOnClickListener(v -> { showFavorites = true; updateNav(); renderCards(); });
        TextView navTimer = navButton("◷  تایمر", false);
        navTimer.setOnClickListener(v -> showTimerDialog());
        nav.addView(homeTab, new LinearLayout.LayoutParams(0, dp(40), 1));
        nav.addView(favoritesTab, new LinearLayout.LayoutParams(0, dp(40), 1));
        nav.addView(navTimer, new LinearLayout.LayoutParams(0, dp(40), 1));
        root.addView(nav);
        setContentView(root);
    }

    private void buildCatalog() {
        themes.add(theme("rain","باران","🌧️","از نم‌نم پنجره تا باران سیل‌آسا",0xff123f58,0xff071d31,
                new String[][]{{"rain_soft","باران ملایم","🌦️","بارش آرام و پیوسته"},{"rain_heavy","باران شدید","🌧️","قطرات پرقدرت و متراکم"},{"rain_window","باران روی پنجره","💧","چکیدن قطره روی شیشه"},{"rain_roof","باران روی سقف","🏠","باران روی سقف خانه"},{"rain_car","باران داخل خودرو","🚗","باران در فضای بسته خودرو"},{"rain_tent","باران روی چادر","⛺","باران روی پارچه چادر"},{"rain_drips","چک‌چک قطره‌ها","💦","قطرات پراکنده آب"},{"rain_puddle","قطرات روی آب","🫧","ریزش قطره روی سطح آب"}}));
        themes.add(theme("forest","جنگل","🌲","هوای جنگل، پرندگان و جریان آب",0xff245b42,0xff09291f,
                new String[][]{{"forest_wind","باد میان درختان","🍃","وزش آرام در شاخ‌وبرگ"},{"birdsong","آواز پرندگان","🐦","جیک‌جیک پرندگان جنگلی"},{"stream","جویبار جنگلی","🏞️","جریان پیوسته آب"},{"waterfall","آبشار","💦","صدای آبشار پرخروش"},{"leaves","خش‌خش برگ‌ها","🍂","حرکت برگ‌ها در باد"},{"cricket","جیرجیرک شب","🦗","آوای حشرات شبانه"},{"owl","جغد شب","🦉","آوای بم و کوتاه جغد"},{"night_air","هوای شب جنگل","🌙","صدای آرام شب در جنگل"}}));
        themes.add(theme("storm","رعدوبرق","🌩️","غرش دوردست تا طوفان سهمگین",0xff41446c,0xff171a37,
                new String[][]{{"thunder_far","رعد دوردست","🌩️","غرش عمیق و دور"},{"thunder_close","رعد نزدیک","⚡","غرش ناگهانی و کوبنده"},{"storm_wind","باد طوفانی","🌬️","وزش باد شدید"},{"rain_heavy","باران سیل‌آسا","🌧️","بارش سنگین همراه طوفان"},{"hail","تگرگ","🧊","برخورد دانه‌های یخ"},{"deep_thunder","غرش بم طوفان","🔊","لایه فرکانس پایین و عمیق"}}));
        themes.add(theme("animals","حیوانات","🦊","آواهای حیات وحش و دنیای جانوران",0xff76522f,0xff2b1b17,
                new String[][]{{"birdsong","پرندگان آوازخوان","🦜","آواز و چهچهه پرندگان"},{"wolf_howl","زوزه گرگ","🐺","آوای کشیده و بم گرگ"},{"cat_purr","خرخر گربه","🐈","لرزش نرم و آرام"},{"dog_bark","پارس سگ","🐕","پالس‌های کوتاه صوتی"},{"frog","قورباغه","🐸","آوای کوتاه کنار آب"},{"cricket","جیرجیرک","🦗","صدای ریتمیک حشره"},{"horse","اسب","🐎","آوای شیهه شبیه‌سازی‌شده"},{"whale","نهنگ","🐋","آوای عمیق زیر آب"},{"lion","شیر","🦁","غرش بم شبیه‌سازی‌شده"},{"owl","جغد","🦉","آوای شبانه"}}));
        themes.add(theme("ocean","آب و دریا","🌊","موج، ساحل، رودخانه و اعماق",0xff14647a,0xff082a42,
                new String[][]{{"waves","موج دریا","🌊","بالا و پایین رفتن موج‌ها"},{"beach","ساحل آرام","🏖️","موج‌های نرم کنار ساحل"},{"ocean_underwater","اعماق دریا","🐠","فضای آرام زیر آب"},{"stream","رودخانه","🏞️","جریان آب روان"},{"waterfall","آبشار","💦","ریزش پیوسته آب"},{"whale","آوای نهنگ","🐋","طنین عمیق زیر آب"}}));
        themes.add(theme("bass","صدای بم","🖤","لایه‌های عمیق، بیس نرم و چک‌چک بم",0xff352c52,0xff100f27,
                new String[][]{{"bass_rain","چک‌چک بم باران","💧","قطره‌های بم با لرزش فرکانس پایین"},{"deep_thunder","رعد فوق‌بم","🌩️","غرش عمیق و نرم"},{"heartbeat","ضربان قلب","❤️","ریتم دوضرب آرام"},{"brown_noise","نویز قهوه‌ای","〰️","نویز عمیق با فرکانس پایین"},{"low_wind","باد بم","🌬️","وزش کم‌فرکانس"},{"sub_bass","ساب‌بیس","🔊","طنین بسیار پایین"},{"bass_drips","چک‌چک عمیق","🫧","ضربه‌های قطره‌ای روی بیس"}}));
        themes.add(theme("human","انسان و شهر","🏙️","کافه، رفت‌وآمد و صداهای محیط",0xff605044,0xff251c1b,
                new String[][]{{"cafe","کافه دنج","☕","همهمه نرم فضای کافه"},{"crowd","همهمه جمعیت","👥","لایه صوتی جمعیت"},{"footsteps","قدم‌زدن","👣","ریتم قدم‌های آرام"},{"train","قطار","🚆","طنین یکنواخت رفت‌وآمد"},{"city","شهر دوردست","🌆","صدای محو شهر"},{"rain_car","باران در خودرو","🚗","باران پشت شیشه خودرو"},{"fire","شومینه","🔥","صدای آرام آتش"}}));
        themes.add(theme("calm","آرامش و خواب","🪷","نویزها، تنفس و آواهای آرام",0xff315d57,0xff102b31,
                new String[][]{{"pink_noise","نویز صورتی","🌸","نویز متعادل و ملایم"},{"white_noise","نویز سفید","〰️","صدای یکنواخت و پوشاننده"},{"brown_noise","نویز قهوه‌ای","🟤","صدای نرم و بم"},{"breathing","تنفس آرام","🌬️","دم‌وبازدم آهسته"},{"singing_bowl","کاسه صوتی","🔔","طنین هارمونیک"},{"night_air","هوای شب","🌙","پس‌زمینه آرام شبانه"},{"heartbeat","ضربان قلب","❤️","ریتم ملایم قلب"}}));
        themes.add(theme("fire","آتش و کمپ","🔥","آتش، هیزم و شب‌نشینی",0xff74452b,0xff2b1a16,
                new String[][]{{"campfire","آتش کمپ","🏕️","شعله و ترق‌وتروق"},{"fire","ترق‌وتروق هیزم","🪵","صدای ریز سوختن چوب"},{"embers","اخگرهای آتش","✨","شعله‌های آرام"},{"forest_wind","باد شبانه","🌲","باد اطراف محل کمپ"},{"owl","جغد شب","🦉","آوای شب"}}));
        themes.add(theme("space","فضا و رویا","🌌","فضای فراگیر، کیهان و فرکانس‌های نرم",0xff303b78,0xff10172d,
                new String[][]{{"space_drone","درون کیهانی","🪐","طنین فضایی چندلایه"},{"cosmic_noise","نویز کیهانی","✨","بافت صوتی آرام"},{"moon_wind","باد ماه","🌙","زمینه نرم و عمیق"},{"brown_noise","نویز قهوه‌ای","〰️","نویز بم"},{"singing_bowl","طنین مراقبه","🔔","صدای کشیده و هارمونیک"}}));
    }

    private ThemeData theme(String id, String name, String emoji, String subtitle, int c1, int c2, String[][] data) {
        List<SoundItem> items = new ArrayList<>();
        for (String[] s : data) items.add(new SoundItem(s[0],s[1],s[2],s[3]));
        return new ThemeData(id,name,emoji,subtitle,c1,c2,items);
    }

    private void renderTheme() {
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{selectedTheme.color1, selectedTheme.color2, 0xff06131a}));
        View hero = findHero();
        if (hero != null) hero.setBackground(round(selectedTheme.color1, selectedTheme.color2, 0, 22));
        if (scenicBackdrop != null) scenicBackdrop.setTheme(selectedTheme.id, selectedTheme.color1, selectedTheme.color2);
        heroTitle.setText(selectedTheme.emoji + "  " + selectedTheme.name);
        heroSubtitle.setText(selectedTheme.subtitle + "  •  " + selectedTheme.items.size() + " صدا");
        themeRow.removeAllViews();
        for (ThemeData t : themes) {
            TextView chip = pill(t.emoji + "  " + t.name, 12, Color.WHITE);
            chip.setBackground(round(t.id.equals(selectedTheme.id) ? selectedTheme.color1 : 0xff122730,
                    t.id.equals(selectedTheme.id) ? selectedTheme.color2 : 0xff0c2028,
                    t.id.equals(selectedTheme.id) ? 2 : 1, 24));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
            lp.setMargins(0, 0, dp(7), 0);
            themeRow.addView(chip, lp);
            chip.setOnClickListener(v -> {
                selectedTheme = t;
                showFavorites = false;
                searchInput.setText("");
                updateNav();
                renderTheme();
            });
        }
        renderCards();
    }

    private View findHero() {
        if (content == null) return null;
        for (int i=0;i<content.getChildCount();i++) {
            View v=content.getChildAt(i);
            if ("hero".equals(v.getTag())) return v;
        }
        return null;
    }

    private void renderCards() {
        if (cardsBox == null || selectedTheme == null) return;
        cardsBox.removeAllViews();
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(new Locale("fa"));
        List<SoundItem> visible = new ArrayList<>();
        for (SoundItem item : selectedTheme.items) {
            boolean favoriteOnly = !showFavorites || favoriteIds.contains(item.id);
            boolean match = query.isEmpty() || item.name.toLowerCase(new Locale("fa")).contains(query)
                    || item.description.toLowerCase(new Locale("fa")).contains(query);
            if (favoriteOnly && match) visible.add(item);
        }
        if (visible.isEmpty()) {
            TextView empty = text(showFavorites ? "هنوز صدایی را به محبوب‌ها اضافه نکرده‌ای." : "صدایی با این عبارت پیدا نشد.", 13, 0xffc0d4ce, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(28), dp(12), dp(28));
            cardsBox.addView(empty);
        }
        for (int i=0; i<visible.size(); i+=2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            SoundItem a = visible.get(i);
            row.addView(soundCard(a), new LinearLayout.LayoutParams(0, dp(148), 1));
            if (i+1 < visible.size()) {
                SoundItem b = visible.get(i+1);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(148), 1);
                lp.setMargins(dp(8),0,0,0);
                row.addView(soundCard(b), lp);
            } else {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(148), 1));
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(8);
            cardsBox.addView(row,rowLp);
        }
        updatePlayerStatus();
    }

    private View soundCard(SoundItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(9));
        boolean active = activeIds.contains(item.id);
        card.setBackground(round(active ? selectedTheme.color1 : blend(selectedTheme.color1,0xff10202a,0.22f),
                0xff0c2029, active ? 3 : 1, 18));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView emoji = text(item.emoji, 28, Color.WHITE, true);
        top.addView(emoji, new LinearLayout.LayoutParams(0, dp(36), 1));
        TextView fav = text(favoriteIds.contains(item.id) ? "♥" : "♡", 23, favoriteIds.contains(item.id) ? 0xffffacb0 : 0xffc3d7d2, true);
        top.addView(fav);
        fav.setOnClickListener(v -> toggleFavorite(item.id));
        card.addView(top);
        TextView name = text(item.name, 14, Color.WHITE, true);
        name.setMaxLines(1);
        card.addView(name);
        TextView desc = text(item.description, 10, 0xffb7cbc7, false);
        desc.setMaxLines(2);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, 0, 1);
        descLp.topMargin = dp(3);
        card.addView(desc, descLp);
        TextView action = text(active ? "●  در حال پخش" : "＋  افزودن به ترکیب", 11, active ? 0xffaaf29d : 0xffdcebe4, true);
        action.setPadding(0, dp(4), 0, 0);
        card.addView(action);
        card.setOnClickListener(v -> toggleSound(item));
        return card;
    }

    private void addPreset(LinearLayout row, String name, String... ids) {
        TextView card = pill(name, 12, Color.WHITE);
        card.setGravity(Gravity.CENTER);
        card.setMinWidth(dp(118));
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackground(round(0xff142f38, 0xff0c202a, 1, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -1);
        lp.setMargins(0,0,dp(8),0);
        row.addView(card, lp);
        card.setOnClickListener(v -> playPreset(ids));
    }

    private void playPreset(String... ids) {
        // Replace the current mix in one service command; do not race CLEAR against ADD.
        if (ids != null && ids.length > 0 && ids[0] != null) {
            activeIds.clear();
            activeIds.add(ids[0]);
            sendReplace(ids[0]);
        }
        renderCards();
        updatePlayerStatus();
    }

    private void sendReplace(String id) {
        Intent i = new Intent(this, SoundscapeService.class).setAction(SoundscapeService.ACTION_REPLACE);
        i.putExtra(SoundscapeService.EXTRA_ID, id);
        i.putExtra(SoundscapeService.EXTRA_GAIN, 0.90f);
        startPlaybackService(i);
    }

    private void playTestSound() {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) {
            android.widget.Toast.makeText(this, "مدیریت صدای گوشی در دسترس نیست.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (current == 0) {
            android.widget.Toast.makeText(this, "صدای رسانه گوشی روی صفر است؛ با دکمه صدا آن را زیاد کن.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        try {
            if (testToneGenerator != null) testToneGenerator.release();
            testToneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            testToneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 850);
            sendReplace("test_tone");
            android.widget.Toast.makeText(this, "تست خروجی: صدای رسانه " + current + " از " + max, android.widget.Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            android.util.Log.e("AvaNatureTest", "ToneGenerator test failed", error);
            android.widget.Toast.makeText(this, "تست صدای گوشی ناموفق بود؛ بلوتوث و خروجی رسانه را بررسی کن.", android.widget.Toast.LENGTH_LONG).show();
        }
        if (testSoundTimer != null) testSoundTimer.cancel();
        testSoundTimer = new CountDownTimer(1200, 1200) {
            @Override public void onTick(long millisUntilFinished) { }
            @Override public void onFinish() {
                sendRemove("test_tone");
                if (testToneGenerator != null) {
                    testToneGenerator.release();
                    testToneGenerator = null;
                }
            }
        }.start();
    }

    private void toggleSound(SoundItem item) {
        if (activeIds.contains(item.id)) {
            activeIds.remove(item.id);
            sendRemove(item.id);
        } else {
            activeIds.add(item.id);
            sendAdd(item.id);
        }
        renderCards();
        updatePlayerStatus();
    }

    private void sendAdd(String id) {
        Intent i = new Intent(this, SoundscapeService.class).setAction(SoundscapeService.ACTION_ADD);
        i.putExtra(SoundscapeService.EXTRA_ID, id);
        i.putExtra(SoundscapeService.EXTRA_GAIN, 0.90f);
        startPlaybackService(i);
    }
    private void sendRemove(String id) {
        Intent i = new Intent(this, SoundscapeService.class).setAction(SoundscapeService.ACTION_REMOVE);
        i.putExtra(SoundscapeService.EXTRA_ID, id);
        startPlaybackService(i);
    }
    private void sendVolume() {
        Intent i = new Intent(this, SoundscapeService.class).setAction(SoundscapeService.ACTION_VOLUME);
        i.putExtra(SoundscapeService.EXTRA_VOLUME, masterVolume);
        startPlaybackService(i);
    }
    private void startPlaybackService(Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (RuntimeException error) {
            android.util.Log.e("AvaNaturePlayback", "Playback service unavailable; main screen will stay open.", error);
            android.widget.Toast.makeText(this, "پخش صدا در این لحظه در دسترس نیست؛ خود برنامه باز می‌ماند.", android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private void clearSounds() {
        activeIds.clear();
        Intent i = new Intent(this, SoundscapeService.class).setAction(SoundscapeService.ACTION_CLEAR);
        try {
            startService(i);
        } catch (RuntimeException error) {
            android.util.Log.w("AvaNaturePlayback", "Could not send stop command.", error);
        }
        renderCards();
        updatePlayerStatus();
    }

    private void updatePlayerStatus() {
        if (playerStatus == null) return;
        if (activeIds.isEmpty()) playerStatus.setText("هنوز صدایی انتخاب نشده  •  از کارت‌ها صدا اضافه کن");
        else playerStatus.setText("در حال ترکیب " + activeIds.size() + " لایه صوتی  •  پخش در پس‌زمینه فعال است");
    }

    private void toggleFavorite(String id) {
        if (favoriteIds.contains(id)) favoriteIds.remove(id); else favoriteIds.add(id);
        prefs.edit().putStringSet("favorites", new LinkedHashSet<>(favoriteIds)).apply();
        renderCards();
    }

    private void showTimerDialog() {
        String[] choices = {"بدون تایمر", "۱۵ دقیقه", "۳۰ دقیقه", "۴۵ دقیقه", "۶۰ دقیقه", "۹۰ دقیقه", "۱۲۰ دقیقه"};
        int[] values = {0,15,30,45,60,90,120};
        new AlertDialog.Builder(this).setTitle("تایمر خواب")
                .setItems(choices, (d, which) -> {
                    if (sleepTimer != null) sleepTimer.cancel();
                    int minutes = values[which];
                    if (minutes == 0) {
                        timerStatus.setText("  ⏱ بدون تایمر");
                        return;
                    }
                    timerStatus.setText("  ⏱ " + minutes + " دقیقه");
                    sleepTimer = new CountDownTimer(minutes*60_000L,1000) {
                        @Override public void onTick(long millis) {
                            long mm = millis/60_000;
                            long ss = (millis%60_000)/1000;
                            timerStatus.setText(String.format(Locale.getDefault(),"  ⏱ %02d:%02d",mm,ss));
                        }
                        @Override public void onFinish() {
                            clearSounds();
                            timerStatus.setText("  ⏱ پایان تایمر");
                        }
                    }.start();
                }).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("AvaNature+  |  آواطبیعت پلاس")
                .setMessage("نسخه دوم مستقل با تم‌های پویا، میکس چندلایه، علاقه‌مندی‌ها، کنترل شدت صدا و تایمر خواب.\n\nصداهای این نسخه به‌صورت دیجیتالی و آفلاین تولید می‌شوند و ضبط میدانی واقعی حیوانات یا طبیعت نیستند.")
                .setPositiveButton("متوجه شدم", null).show();
    }

    private void updateNav() {
        if (homeTab == null) return;
        homeTab.setTextColor(showFavorites ? 0xffa6bbb6 : 0xffa6f29d);
        favoritesTab.setTextColor(showFavorites ? 0xffffaeb3 : 0xffa6bbb6);
    }

    private TextView navButton(String value, boolean selected) {
        TextView t = pill(value, 12, selected ? 0xffa6f29d : 0xffa6bbb6);
        t.setGravity(Gravity.CENTER);
        return t;
    }
    private TextView pill(String value, int size, int color) {
        TextView t = text(value,size,color,true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10),dp(7),dp(10),dp(7));
        t.setBackground(round(0xff142c35,0xff0c2029,1,24));
        return t;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private GradientDrawable round(int start, int end, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});
        d.setCornerRadius(dp(radius));
        if (stroke>0) d.setStroke(dp(stroke),0xff3a605a);
        return d;
    }
    private int blend(int a, int b, float f) {
        float g=1f-f;
        return Color.rgb((int)(Color.red(a)*f+Color.red(b)*g),(int)(Color.green(a)*f+Color.green(b)*g),(int)(Color.blue(a)*f+Color.blue(b)*g));
    }
    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }

    private final class ScenicBackdrop extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String scene = "rain";
        private int top = 0xff123f58, bottom = 0xff071d31;
        private long start = System.currentTimeMillis();
        ScenicBackdrop(android.content.Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setTheme(String id, int a, int b) { scene=id; top=a; bottom=b; start=System.currentTimeMillis(); invalidate(); }
        private void color(int value) { p.setColor(value); p.setShader(null); p.setStyle(Paint.Style.FILL); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth(), h=getHeight(), t=(System.currentTimeMillis()-start)/1000f;
            p.setShader(new LinearGradient(0,0,w,h,top,bottom,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p); p.setShader(null);

            if (scene.equals("rain") || scene.equals("storm") || scene.equals("bass")) {
                // Moving cloud bands, slanted rain, and tiny splash rings.
                color(0x443b5267);
                for(int k=0;k<5;k++) {
                    float x=((k*119f+t*(scene.equals("storm")?13:5))%(w+150))-75;
                    c.drawOval(x,h*.08f+(k%2)*h*.13f,x+145,h*.28f+(k%2)*h*.13f,p);
                }
                color(scene.equals("bass")?0x775a9cb9:0x885bc9e8);
                for(int i=0;i<125;i++) {
                    float speed=scene.equals("storm")?330:scene.equals("bass")?115:215;
                    float x=(i*71f+(float)Math.sin(t*.7+i)*7)%w;
                    float y=((i*47f+t*speed)%(h+50))-25;
                    p.setStrokeWidth(i%5==0?2.3f:1.15f);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    c.drawLine(x,y,x-8,y+19,p);
                }
                color(0x665fe5f4);
                for(int i=0;i<18;i++) {
                    float x=(i*97f+23)%w, y=(i*59f+(float)Math.sin(t*2+i)*5)%h;
                    float pulse=2+(float)Math.abs(Math.sin(t*3+i))*2;
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f);
                    c.drawCircle(x,y,pulse,p); p.setStyle(Paint.Style.FILL);
                }
                if(scene.equals("storm") && Math.sin(t*1.7)>0.985) {
                    color(0x66ffffff); c.drawRect(0,0,w,h,p);
                    color(0xffd7e8f2);
                    Path bolt=new Path(); bolt.moveTo(w*.72f,0); bolt.lineTo(w*.66f,h*.32f);
                    bolt.lineTo(w*.71f,h*.30f); bolt.lineTo(w*.62f,h*.58f);
                    bolt.lineTo(w*.67f,h*.34f); bolt.lineTo(w*.63f,h*.35f); bolt.close(); c.drawPath(bolt,p);
                }
            } else if (scene.equals("forest") || scene.equals("animals") || scene.equals("fire")) {
                // Distant forest layers and swaying pine silhouettes.
                color(0x3346a77a); c.drawOval(-w*.2f,h*.43f,w*.65f,h*.9f,p);
                c.drawOval(w*.35f,h*.38f,w*1.2f,h*.92f,p);
                for(int layer=0;layer<2;layer++) {
                    color(layer==0?0x55305243:0x99401f25);
                    for(int i=0;i<9;i++) {
                        float x=i*w/8f+(float)Math.sin(t*.55+i*1.7)*4;
                        float base=h*(layer==0?.82f:.99f), height=h*(layer==0?.45f:.58f);
                        float sway=(float)Math.sin(t*.8+i)*4;
                        Path tree=new Path(); tree.moveTo(x-3,base); tree.lineTo(x-27+sway,base-height*.45f);
                        tree.lineTo(x-12+sway,base-height*.42f); tree.lineTo(x,base-height);
                        tree.lineTo(x+12+sway,base-height*.42f); tree.lineTo(x+28+sway,base-height*.45f);
                        tree.close(); c.drawPath(tree,p);
                        color(0x66402720); c.drawRect(x-2,base-height*.28f,x+2,base,p);
                    }
                }
                color(0x779ce5a7);
                for(int i=0;i<28;i++) {
                    float x=(i*61f+t*(4+(i%4))*((i%2==0)?1:-1))%w;
                    float y=(i*37f+(float)Math.sin(t+i)*7)%h;
                    c.drawCircle(x,y,1.2f+(i%3)*.45f,p);
                }
                if(scene.equals("fire")) {
                    // Foreground campfire with independently flickering tongues.
                    color(0x774a2418); c.drawOval(w*.35f,h*.72f,w*.67f,h*.98f,p);
                    for(int i=0;i<7;i++) {
                        float x=w*(.42f+i*.025f), flick=(float)Math.sin(t*7+i*2)*7;
                        Path flame=new Path(); flame.moveTo(x-9,h*.88f);
                        flame.cubicTo(x-20,h*.77f+flick,x+8,h*.69f+flick,x,h*.58f+flick);
                        flame.cubicTo(x+21,h*.73f+flick,x+15,h*.84f,x+9,h*.9f); flame.close();
                        color(i%2==0?0xffff8a32:0xffffc15a); c.drawPath(flame,p);
                        color(0xffffe6a0); c.drawOval(x-3,h*.77f+flick,x+4,h*.88f,p);
                    }
                }
            } else if (scene.equals("ocean") || scene.equals("calm")) {
                // Rolling ocean swells with moving foam highlights.
                for(int i=0;i<7;i++) {
                    float base=h*(.48f+i*.085f);
                    Path wave=new Path(); wave.moveTo(0,base);
                    for(int x=0;x<=w;x+=8) {
                        float y=base+(float)Math.sin(x*.018-t*(1.2+i*.08)+i)*7
                                +(float)Math.sin(x*.035-t*.7+i)*3;
                        wave.lineTo(x,y);
                    }
                    wave.lineTo(w,h); wave.lineTo(0,h); wave.close();
                    color(i%2==0?0x5549d1d9:0x4450a6ce); c.drawPath(wave,p);
                    color(0x887ee9e2); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f);
                    for(int j=0;j<4;j++) {
                        float x=(j*w/4f+(t*24+i*19))%(w+20);
                        float y=base+(float)Math.sin(x*.018-t*1.2+i)*7;
                        c.drawLine(x,y,x+13,y-1.5f,p);
                    }
                    p.setStyle(Paint.Style.FILL);
                }
            } else {
                // Ambient night sky and slow drifting light particles.
                color(0x33d7e9d7);
                for(int i=0;i<38;i++) {
                    float x=(i*53f+t*(i%3+1)*2)%w, y=(i*31f+(float)Math.sin(t*.4+i)*6)%h;
                    c.drawCircle(x,y,1.1f+(i%3)*.4f,p);
                }
            }
            postInvalidateDelayed(40);
        }
    }

    private static final class SoundItem {
        final String id,name,emoji,description;
        SoundItem(String id,String name,String emoji,String description) {
            this.id=id; this.name=name; this.emoji=emoji; this.description=description;
        }
    }
    private static final class ThemeData {
        final String id,name,emoji,subtitle;
        final int color1,color2;
        final List<SoundItem> items;
        ThemeData(String id,String name,String emoji,String subtitle,int c1,int c2,List<SoundItem> items) {
            this.id=id;this.name=name;this.emoji=emoji;this.subtitle=subtitle;this.color1=c1;this.color2=c2;this.items=items;
        }
    }

    @Override protected void onDestroy() {
        if (sleepTimer != null) sleepTimer.cancel();
        if (testSoundTimer != null) testSoundTimer.cancel();
        if (testToneGenerator != null) { testToneGenerator.release(); testToneGenerator = null; }
        super.onDestroy();
    }
}
