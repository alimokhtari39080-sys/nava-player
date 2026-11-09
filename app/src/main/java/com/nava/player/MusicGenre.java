package com.nava.player;

import android.graphics.Color;
import java.util.Locale;

public enum MusicGenre {
    ALL("همه", "♫", Color.rgb(184, 166, 255), Color.rgb(31, 27, 49), Color.rgb(15, 12, 25)),
    MADAHI("مداحی", "🕯", Color.rgb(239, 190, 104), Color.rgb(48, 31, 39), Color.rgb(25, 13, 21)),
    OLDIES("قدیمی", "📻", Color.rgb(224, 184, 112), Color.rgb(49, 39, 27), Color.rgb(24, 18, 12)),
    TRADITIONAL("سنتی", "🎻", Color.rgb(113, 219, 181), Color.rgb(25, 49, 43), Color.rgb(9, 22, 20)),
    POP("پاپ", "🎧", Color.rgb(239, 126, 203), Color.rgb(50, 29, 53), Color.rgb(25, 13, 29)),
    INSTRUMENTAL("بی‌کلام", "🎹", Color.rgb(119, 190, 239), Color.rgb(27, 42, 58), Color.rgb(11, 18, 29)),
    OTHER("سایر", "♪", Color.rgb(184, 166, 255), Color.rgb(31, 27, 49), Color.rgb(15, 12, 25));

    public final String label;
    public final String icon;
    public final int accent;
    public final int panelColor;
    public final int backgroundColor;

    MusicGenre(String label, String icon, int accent, int panelColor, int backgroundColor) {
        this.label = label;
        this.icon = icon;
        this.accent = accent;
        this.panelColor = panelColor;
        this.backgroundColor = backgroundColor;
    }

    public static MusicGenre detect(String title, String artist, String album) {
        String text = normalize((title == null ? "" : title) + " "
                + (artist == null ? "" : artist) + " "
                + (album == null ? "" : album));

        if (containsAny(text, "مداحی", "نوحه", "روضه", "مولودی", "مرثیه", "سینه زنی",
                "هیئت", "ذکر", "latmiyya", "latmiyah", "madahi", "noha", "marsiya",
                "یا حسین", "یاحسین", "یا زهرا", "یا اباعبدالله")
                || containsAny(text, "حمید علیمی", "حاج محمود کریمی", "محمود کریمی",
                "میثم مطیعی", "عبدالرضا هلالی", "محمدرضا طاهری", "مجید بنی فاطمه",
                "جواد مقدم", "حسین سیب سرخی", "منصور ارضی", "صابر خراسانی",
                "نریمان پناهی", "سید مجید بنی فاطمه")) {
            return MADAHI;
        }

        if (containsAny(text, "قدیمی", "نوستالژی", "خاطره انگیز", "خاطره‌انگیز",
                "oldies", "nostalgia", "vintage", "persian classic", "classic hits")
                || containsAny(text, "داریوش", "گوگوش", "هایده", "مهستی", "ابی",
                "ستار", "معین", "ویگن", "پوران", "دلکش", "الهه", "مرضیه",
                "فریدون فروغی", "فرهاد", "عارف", "رامش", "حمیرا", "گلپا", "ایرج")) {
            return OLDIES;
        }

        if (containsAny(text, "سنتی", "تصنیف", "دستگاه", "آواز ایرانی", "موسیقی ایرانی",
                "traditional", "classical persian", "radif")
                || containsAny(text, "محمدرضا شجریان", "همایون شجریان", "شهرام ناظری",
                "علیرضا قربانی", "سالار عقیلی", "استاد بنان", "قمرالملوک وزیری",
                "فرامرز پایور", "کیهان کلهر", "پرویز مشکاتیان")) {
            return TRADITIONAL;
        }

        if (containsAny(text, "بی کلام", "بی‌کلام", "بدون کلام", "instrumental",
                "soundtrack", "پیانو", "piano solo", "violin solo",
                "ambient", "meditation", "relaxing music", "موسیقی آرامش")) {
            return INSTRUMENTAL;
        }

        if (containsAny(text, "پاپ", "pop", "dance", "دنس", "راک", "rock",
                "رپ", "rap", "hip hop", "هیپ هاپ", "electronic", "edm")) {
            return POP;
        }

        return OTHER;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('ي', 'ی')
                .replace('ك', 'ک')
                .replace('ة', 'ه')
                .replaceAll("[ًٌٍَُِّْـ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(normalize(word))) return true;
        }
        return false;
    }
}
