package com.avanature.soundscape.v2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
    private TextView heroTitle, heroSubtitle, playerStatus, timerStatus, favoritesTab, homeTab;
    private EditText searchInput;
    private SeekBar volumeBar;
    private final List<ThemeData> themes = new ArrayList<>();
    private final Set<String> activeIds = new LinkedHashSet<>();
    private final Set<String> favoriteIds = new LinkedHashSet<>();
    private SharedPreferences prefs;
    private ThemeData selectedTheme;
    private CountDownTimer sleepTimer;
    private boolean showFavorites = false;
    private float masterVolume = 0.78f;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(5, 19, 26));
        getWindow().setNavigationBarColor(Color.rgb(5, 19, 26));
        prefs = getSharedPreferences("ava_nature_plus", MODE_PRIVATE);
        favoriteIds.addAll(prefs.getStringSet("favorites", new LinkedHashSet<>()));
        buildCatalog();
        selectedTheme = themes.get(0);
        createScreen();
        renderTheme();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 41);
        }
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
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.BOTTOM);
        hero.setPadding(dp(18), dp(14), dp(18), dp(16));
        hero.addView(heroTitle);
        hero.addView(heroSubtitle);
        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(132)));
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
        clearSounds();
        for (String id : ids) {
            activeIds.add(id);
            sendAdd(id);
        }
        renderCards();
        updatePlayerStatus();
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
        i.putExtra(SoundscapeService.EXTRA_GAIN, 0.68f);
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
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }
    private void clearSounds() {
        activeIds.clear();
        Intent i = new Intent(this, SoundscapeService.class).setAction(SoundscapeService.ACTION_CLEAR);
        startService(i);
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
        super.onDestroy();
    }
}
