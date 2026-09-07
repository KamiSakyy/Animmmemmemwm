package app.yoru.mobile;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class RetiredSettingsTest {

  @Test
  public void retiredHistoryOptOutIsRemovedWithoutChangingOtherChoices()
    throws Exception {
    JSONArray voices = new JSONArray().put("Voice A").put("Voice B");
    JSONObject settings = new JSONObject()
      .put("privateMode", true)
      .put("quality", 1080)
      .put("onlyPreferredVoice", true)
      .put("favoriteVoices", voices)
      .put("spoilerSafe", true);
    assertTrue(SecureStore.removeRetiredPreferences(settings));
    assertFalse(settings.has("privateMode"));
    assertEquals(1080, settings.getInt("quality"));
    assertTrue(settings.getBoolean("onlyPreferredVoice"));
    assertTrue(settings.getBoolean("spoilerSafe"));
    assertSame(voices, settings.getJSONArray("favoriteVoices"));
  }

  @Test
  public void migrationIsIdempotent() throws Exception {
    JSONObject settings = new JSONObject()
      .put("privateMode", false)
      .put("fontScale", 105);
    assertTrue(SecureStore.removeRetiredPreferences(settings));
    assertFalse(SecureStore.removeRetiredPreferences(settings));
    assertEquals(105, settings.getInt("fontScale"));
  }

  @Test
  public void settingsWithoutTheRemovedOptionAreUntouched() throws Exception {
    JSONObject settings = new JSONObject()
      .put("autoNext", false)
      .put("wifiDownloads", true);
    String before = settings.toString();
    assertFalse(SecureStore.removeRetiredPreferences(settings));
    assertEquals(before, settings.toString());
  }
}
