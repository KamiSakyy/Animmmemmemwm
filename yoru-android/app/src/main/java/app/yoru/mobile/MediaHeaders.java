package app.yoru.mobile;

import java.net.URI;
import java.util.*;

/** Small session registry for public media request context, never cookies/auth. */
final class MediaHeaders {

  private static final TimedCache<String, String> REFERERS = new TimedCache<>(
    128,
    10 * 60_000
  );

  private MediaHeaders() {}

  static void remember(String media, String referer) {
    if (valid(referer) && valid(media)) REFERERS.put(media, referer);
  }

  static void rememberIfMissing(String media, String referer) {
    if (REFERERS.get(media) == null) remember(media, referer);
  }

  static Map<String, String> forUrl(String media) {
    String referer = REFERERS.get(media);
    if (referer == null) referer = "https://yani.tv/";
    HashMap<String, String> headers = new HashMap<>();
    headers.put("Referer", referer);
    headers.put("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5");
    // A native request does not require a fabricated Origin or cross-site cookies.
    return headers;
  }

  private static boolean valid(String url) {
    try {
      if (url == null || url.length() > 4096) return false;
      URI uri = new URI(url);
      return (
        ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) &&
        uri.getHost() != null &&
        uri.getUserInfo() == null
      );
    } catch (Exception error) {
      return false;
    }
  }

  static void clear() {
    REFERERS.clear();
  }
}
