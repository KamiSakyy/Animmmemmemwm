package app.yoru.mobile;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.Test;

/** Source contracts for removed copy; these are not device/screenshot tests. */
public class UiCopyContractTest {

  private static String source(String name) throws IOException {
    for (
      Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
      dir != null;
      dir = dir.getParent()
    ) {
      for (String prefix : Arrays.asList(
        "src/main/java/app/yoru/mobile/",
        "yoru-android/app/src/main/java/app/yoru/mobile/"
      )) {
        Path file = dir.resolve(prefix + name + ".java");
        if (Files.isRegularFile(file)) return new String(
          Files.readAllBytes(file),
          java.nio.charset.StandardCharsets.UTF_8
        );
      }
    }
    throw new IOException("Cannot locate production source: " + name);
  }

  private static String literals(String source) {
    Matcher matcher = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"").matcher(
      source
    );
    StringBuilder text = new StringBuilder();
    while (matcher.find()) text.append(matcher.group()).append(' ');
    return text.toString().toLowerCase(Locale.ROOT);
  }

  @Test
  public void startupDoesNotCreateACustomLoadingPage() throws Exception {
    String ui = source("Ui");
    String gate = ui.substring(
      ui.indexOf("if (!app.initialized)"),
      ui.indexOf("if (app.original)")
    );
    assertFalse(gate.contains("TextView"));
    assertFalse(gate.contains("ProgressBar"));
    assertFalse(gate.contains("setContentView"));
    assertFalse(gate.contains("store.preload"));
    assertTrue(gate.contains("app.resumeWhenReady(a)"));
    assertTrue(gate.contains("removeOnPreDrawListener(this)"));
    assertFalse(ui.contains("Открываем сохранённую библиотеку"));
  }

  @Test
  public void redundantPanelsAndHiddenHistoryControlStayRemoved()
    throws Exception {
    String details = source("DetailsActivity"),
      profile = source("ProfileScreen");
    assertFalse(details.contains("Карточка+"));
    assertFalse(details.contains("richCard("));
    assertFalse(profile.contains("Проверить избранное сейчас"));
    assertFalse(profile.contains("Новые серии"));
    assertFalse(profile.contains("updatesStatus"));
    assertFalse(profile.contains("Не сохранять новую историю"));
    assertFalse(profile.contains("privateMode"));
  }

  @Test
  public void userFacingCopyDoesNotDescribeInternalDiscoveryOrScheduling()
    throws Exception {
    assertTrue(
      literals("String title = \"Во всей видеобазе\";").contains("видеобаз")
    );
    for (String file : Arrays.asList(
      "Ui",
      "MainActivity",
      "DetailsActivity",
      "PlayerActivity",
      "ProfileScreen",
      "DownloadActions"
    )) {
      String copy = literals(source(file));
      for (String forbidden : Arrays.asList(
        "видеобаз",
        "во всей базе",
        "jobscheduler",
        "alarmmanager",
        "больше не создаёт все строки",
        "без лишних потоков",
        "другой маршрут"
      )) {
        assertFalse(
          file + " contains internal implementation copy: " + forbidden,
          copy.contains(forbidden)
        );
      }
    }
  }

  @Test
  public void removingSettingsDoesNotDisableBackgroundUpdatesOrHistory()
    throws Exception {
    String app = source("YoruApp");
    assertTrue(app.contains("local.execute("));
    assertTrue(app.contains("store.preload()"));
    assertTrue(app.contains("EpisodeUpdateReceiver.schedule(this)"));
    assertFalse(source("SecureStore").contains("privateMode()"));
    assertTrue(source("Ui").contains("Manifest.permission.POST_NOTIFICATIONS"));
  }
}
