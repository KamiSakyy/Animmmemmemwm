package app.yoru.vk;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;

public final class TokenParser {
    private TokenParser() {}
    public static String parse(String input) {
        if (input == null || input.length() > 16384) throw bad();
        String s = input.trim();
        if (s.startsWith("https%3A") || s.startsWith("https%3a") || s.startsWith("http%3A")) s = decode(s);
        String token = null;
        if (s.contains("://")) {
            try {
                URI uri = new URI(s);
                if (!Arrays.asList("https", "http").contains(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1) throw bad();
                if (!Arrays.asList("vk.com", "vk.ru", "oauth.vk.com", "oauth.vk.ru", "api.vk.com", "api.vk.ru", "id.vk.com", "id.vk.ru").contains(uri.getHost())) throw bad();
                token = extract(uri.getRawQuery(), token);
                token = extract(uri.getRawFragment(), token);
            } catch (Exception e) { throw bad(); }
        } else if (s.startsWith("access_token=") || s.startsWith("#access_token=") || s.startsWith("?access_token=")) {
            token = extract(s.replaceFirst("^[#?]", ""), null);
        } else token = s;
        if (token == null || token.length() < 20 || token.length() > 4096 || !token.matches("[A-Za-z0-9_.~+/=\\-]+")) throw bad();
        return token;
    }
    private static String extract(String part, String found) {
        if (part == null) return found;
        for (String pair : part.split("&")) {
            int at = pair.indexOf('=');
            if (at > 0 && decode(pair.substring(0, at)).equals("access_token")) {
                if (found != null) throw bad();
                found = decode(pair.substring(at + 1));
            }
        }
        return found;
    }
    private static String decode(String s) {
        try { return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8"); }
        catch (Exception e) { throw bad(); }
    }
    private static IllegalArgumentException bad() {
        return new IllegalArgumentException("Вставьте пользовательский access_token или полную ссылку ВК с access_token. Дублирующиеся, пустые и повреждённые значения не принимаются.");
    }
}
