package app.yoru.mobile;

import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HlsManifests {

  private static final Pattern RESOLUTION = Pattern.compile(
    "RESOLUTION=\\d+x(\\d+)"
  );

  private HlsManifests() {}

  static TreeMap<Integer, String> qualities(String manifest, String masterUrl) {
    TreeMap<Integer, String> result = new TreeMap<>();
    if (
      manifest == null || !manifest.trim().startsWith("#EXTM3U")
    ) return result;
    int height = 0;
    for (String raw : manifest.split("\\r?\\n")) {
      String line = raw.trim();
      if (line.startsWith("#EXT-X-STREAM-INF")) {
        Matcher matcher = RESOLUTION.matcher(line);
        try {
          height = matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
        } catch (NumberFormatException invalid) {
          height = 0;
        }
      } else if (!line.isEmpty() && !line.startsWith("#") && height > 0) {
        if (height <= 8640) result.put(height, masterUrl);
        height = 0;
      }
    }
    // Keep the master URI: it owns AUDIO/SUBTITLES groups and adaptive renditions.
    return result;
  }
}
