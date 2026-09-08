package app.yoru.vk;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchRules {
    private SearchRules() {}
    public static String query(String title, int season, int episode, boolean movie, String voice) {
        String name = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Введите название для поиска.");
        if (name.length() > 180) name = name.substring(0, 180);
        if (!movie && (season < 1 || season > 99 || episode < 1 || episode > 9999)) throw new IllegalArgumentException("Сезон: 1–99. Серия: 1–9999.");
        String q = name + (movie ? " фильм" : " " + season + " сезон " + episode + " серия");
        if (voice != null && !voice.trim().isEmpty()) q += " " + voice.trim().substring(0, Math.min(60, voice.trim().length()));
        return q;
    }
    public static int season(String title) {
        if (title == null) return 1;
        Matcher m = Pattern.compile("(?:сезон|season|тв-)\\s*(\\d{1,2})|(?<!\\d)(\\d{1,2})\\s*сезон", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(title);
        if (m.find()) return Math.max(1, Integer.parseInt(m.group(1) == null ? m.group(2) : m.group(1)));
        String s = title.trim().toUpperCase(Locale.ROOT);
        if (s.endsWith(" III")) return 3;
        if (s.endsWith(" II")) return 2;
        if (s.endsWith(" IV")) return 4;
        return 1;
    }
    public static boolean episodeMention(String text, int episode) {
        if (text == null) return false;
        String n = "0*" + episode;
        return Pattern.compile("(?iu)(?<!\\d)" + n + "\\s*(?:серия|сер\\b|эпизод|episode)|(?:серия|эпизод|episode|ep\\.?|s\\d{1,2}e)\\s*" + n + "(?!\\d)").matcher(text).find();
    }
}
