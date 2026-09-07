#!/usr/bin/env python3
"""Version-locked, read-only evidence checks for the YORU 4.14.0 handoff.

Usage:
    python3 docs/audit/verify_yoru_4_14_0.py /path/to/YORU-4.14.0-source-handoff.zip

This does NOT execute Java/Android, benchmark an APK, or contact any provider.
Static checks verify the cited source shapes. Deterministic Python models
illustrate selected algorithms with explicitly synthetic inputs. A successful
exit means the audit evidence was reproduced, NOT that the application is OK.
Only Python's standard library is required. The archive is never extracted.
"""

import argparse
import hashlib
import json
import re
import sys
import zipfile
from collections import deque
from pathlib import Path
from urllib.parse import urljoin

EXPECTED_SHA256 = "8616af04c9f9abfa868174b96f25e3259c7c6b8fb77831017af03a4900ecbe42"
JAVA_ROOT = "yoru-android/app/src/main/java/app/yoru/mobile/"


def require(condition, message):
    if not condition:
        raise ValueError(message)


class DispatchModel:
    """Only the admission order of ThreadPoolExecutor; no tasks finish here.

    core -> offer queue -> grow to max -> rejection policy.
    This is a model of the Java API semantics, not a JVM concurrency test.
    """

    def __init__(self, core, maximum, capacity, discard_oldest=False):
        self.core = core
        self.maximum = maximum
        self.capacity = capacity
        self.discard_oldest = discard_oldest
        self.running = []
        self.queue = deque()
        self.dropped = []

    def submit(self, task):
        if len(self.running) < self.core:
            self.running.append(task)
        elif len(self.queue) < self.capacity:
            self.queue.append(task)
        elif len(self.running) < self.maximum:
            self.running.append(task)
        elif self.discard_oldest:
            self.dropped.append(self.queue.popleft())
            self.queue.append(task)
        else:
            self.dropped.append(task)


def model_results():
    ui = DispatchModel(1, 2, 256, True)
    for task in range(1, 4):
        ui.submit(task)
    small_ui = {"running": list(ui.running), "queued": list(ui.queue)}
    require(small_ui == {"running": [1], "queued": [2, 3]}, "UI model")
    for task in range(4, 260):
        ui.submit(task)
    require(ui.running == [1, 258] and ui.dropped == [2], "Discard model")

    images = DispatchModel(4, 12, 180)
    for task in range(1, 201):
        images.submit(task)
    require(len(images.dropped) == 8, "Image admission model")

    densities = []
    for density in (1, 2, 3):
        dp = lambda value: int(value * density + 0.5)
        expected = dp(8 * 78)
        actual = dp(8 * dp(78))
        densities.append({"density": density, "expected_px": expected,
                          "current_expression_px": actual})
    require(densities[-1]["current_expression_px"] == 5616, "Double dp model")

    # All variants have the same source/voice and a stable ranking. A and B
    # fail, C would work. The original retry excludes ONLY the current URL.
    variants = ["A", "B", "C"]
    attempted_variants = [variants[0]]
    for _ in range(4):
        current = attempted_variants[-1]
        attempted_variants.append(next(v for v in variants if v != current))
    require(attempted_variants == ["A", "B", "A", "B", "A"], "Retry model")

    qualities = [720, 1080]
    attempted_qualities = [1080]
    for _ in range(4):
        current = attempted_qualities[-1]
        lower = [q for q in qualities if 0 < q < current]
        chosen = max(lower) if lower else next(q for q in qualities if q != current)
        attempted_qualities.append(chosen)
    require(attempted_qualities == [1080, 720, 1080, 720, 1080], "Quality retry model")

    # One source completes at 100 ms. Remaining jobs do not finish within
    # this synthetic 15,000 ms budget. The collector does not emit early.
    completion_times = [100] + [30000] * 9
    budget = 15000
    collector_returns_at = min(max(completion_times), budget)
    require(collector_returns_at == 15000, "Collector model")

    manifest = """#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="ru",NAME="Russian",URI="audio/ru.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720,AUDIO="ru"
video/720.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080,AUDIO="ru"
video/1080.m3u8
"""
    master = "https://fixture.invalid/master.m3u8"
    parsed = {}
    height = 0
    for line in manifest.splitlines():
        line = line.strip()
        if line.startswith("#EXT-X-STREAM-INF"):
            match = re.search(r"RESOLUTION=\d+x(\d+)", line)
            height = int(match.group(1)) if match else 0
        elif line and not line.startswith("#") and height > 0:
            parsed[height] = urljoin(master, line)
            height = 0
    require(set(parsed) == {720, 1080}, "HLS model")
    require(not any("audio/" in u for u in parsed.values()), "HLS audio model")

    return {
        "notice": "Synthetic algorithm illustrations only; no elapsed-time/FPS measurements.",
        "ui_pool_three_blocked_tasks": small_ui,
        "ui_pool_259_blocked_tasks": {
            "running_tasks": ui.running, "queued_count": len(ui.queue),
            "silently_discarded_tasks": ui.dropped,
            "note": "DiscardOldestPolicy does not itself cancel the dropped FutureTask."
        },
        "image_pool_200_unique_blocked_tasks": {
            "running_count": len(images.running), "queued_count": len(images.queue),
            "rejected_count": len(images.dropped)
        },
        "episode_list_height_8_rows": densities,
        "same_source_variant_retry_sequence": attempted_variants,
        "quality_retry_sequence": attempted_qualities,
        "wait_all_collector": {
            "synthetic_first_success_ms": 100,
            "synthetic_return_ms": collector_returns_at,
            "configured_budget_ms": budget
        },
        "hls_fixture_child_urls": parsed,
        "hls_fixture_external_audio_reference_retained": False
    }


def audit(archive):
    payload = archive.read_bytes()
    digest = hashlib.sha256(payload).hexdigest()
    require(digest == EXPECTED_SHA256,
            "Different archive/version: these checks are tied to the audited SHA-256.")
    with zipfile.ZipFile(archive) as z:
        files = [i for i in z.infolist() if not i.is_dir()]
        require(sum(i.file_size for i in files) < 10_000_000, "Unexpected archive size")
        names = {i.filename for i in files}
        java = {i.filename[len(JAVA_ROOT):]: z.read(i).decode("utf-8")
                for i in files if i.filename.startswith(JAVA_ROOT) and i.filename.endswith(".java")}
        settings = z.read("yoru-android/settings.gradle").decode("utf-8")
        proguard = z.read("yoru-android/app/proguard-rules.pro").decode("utf-8")
        profile = json.loads(z.read("yoru-android/app/src/main/assets/yoru_profile.json"))

    def line(name, start, end=None):
        return "\n".join(java[name + ".java"].splitlines()[start - 1:end or start])

    evidence = []

    def check(identifier, title, condition, references):
        require(condition, "Evidence mismatch: " + identifier)
        evidence.append({"id": identifier, "title": title, "verified_source_shape": True,
                         "references": references})

    app_start = line("YoruApp", 30)
    check("S01", "Eager SecureStore load through YoruShield before preload submission",
          app_start.index("api=new ApiRepository(this)") < app_start.index("store.preload()")
          and "new YoruShield(context)" in line("ApiRepository", 55)
          and "store.sourceProfile()" in line("YoruShield", 14)
          and "ensure();" in line("SecureStore", 125),
          ["YoruApp.java:30", "ApiRepository.java:55", "YoruShield.java:14", "SecureStore.java:19-23,125"])
    check("S02", "Single-core UI workload pool and silent discard policy",
          'pool("yoru-ui",1,2,' in line("YoruApp", 25)
          and "LinkedBlockingQueue<>(256)" in line("YoruApp", 23)
          and "DiscardOldestPolicy" in line("YoruApp", 23), ["YoruApp.java:23-26"])
    check("S03", "Discovery collector accumulates all completions without first-success return",
          "concrete?12500:15000" in line("ApiRepository", 209)
          and "i<jobs" in line("ApiRepository", 210)
          and "out.add(a)" in line("ApiRepository", 210)
          and "return" not in line("ApiRepository", 210), ["ApiRepository.java:201-211"])
    check("S04", "YORU catalog uses serial Shikimori then Yummy fallback",
          'catalog("shikimori",q,page,f)' in line("ApiRepository", 196)
          and 'catalog("yummy",q,page,f)' in line("ApiRepository", 196)
          and "for(String url:SHIKI_GRAPH)" in line("ApiRepository", 100),
          ["ApiRepository.java:22,100,196"])
    check("S05", "Shared details tail fetches optional metadata synchronously",
          all(x in line("ApiRepository", 146) for x in
              ("enrichSchedule(a)", "enrichVisuals(a)", "enrichRelated(a)")),
          ["ApiRepository.java:128-146,289-297"])
    check("S06", "Playback transfer serializes metadata without episode graph",
          'a.json().toString()' in line("Ui", 44)
          and "episodeList" not in line("Anime", 24)
          and 'api.playback(anime,"yoru")' in line("PlayerActivity", 60),
          ["Anime.java:24", "Ui.java:44-45", "PlayerActivity.java:60"])
    check("S07", "Progress performs three whole-store writes and SQLite mirror",
          all('write("' + k + '",' in line("SecureStore", 56)
              for k in ("favorites", "history", "settings"))
          and "cache.progressMirror" in line("SecureStore", 56)
          and "now-lastSaved<3500" in line("PlayerActivity", 110),
          ["SecureStore.java:23,56", "PlayerActivity.java:31,110", "YoruCache.java:34"])
    check("S08", "Calendar JSON is also retained inside encrypted settings",
          'settings.put("calendarCache",rows==null?"":rows.toString())' in line("SecureStore", 123),
          ["SecureStore.java:123", "YoruApp.java:32", "CalendarScreen.java:24"])
    render = line("MainActivity", 27)
    cached_branch = render[render.index("if(screenCache[tab]"):render.index("content.removeAllViews();if(tab==0)")]
    check("S09", "Navigation cancels work, then can reattach cached loading screen without restart",
          render.index("cancelUiTasks()") < render.index("if(screenCache[tab]")
          and "return;" in cached_branch and "loadCatalog" not in cached_branch,
          ["MainActivity.java:27,32-35,68"])
    check("S10", "Episode list height converts dp twice",
          "visible*Ui.dp(this,78)" in line("DetailsActivity", 27)
          and "height<0?height:dp(c,height)" in line("Ui", 32),
          ["DetailsActivity.java:27", "Ui.java:22,32"])
    check("S11", "Retry excludes only current variant and can reuse failed qualities",
          "key.equals(current)" in line("PlayerActivity", 66)
          and "q!=quality" in line("PlayerActivity", 66)
          and "rescueTries>=4" in line("PlayerActivity", 66), ["PlayerActivity.java:66"])
    check("S12", "Image rejection drops waiter entry without rescheduling",
          "AbortPolicy" in line("ImageLoader", 17)
          and "catch(RejectedExecutionException rejected){waiters.remove(key);}" in line("ImageLoader", 25),
          ["ImageLoader.java:17,25"])
    check("S13", "Network refresh holds same monitor as route lookup",
          "synchronized ArrayList<String> routes" in line("YoruShield", 15)
          and "synchronized void refresh" in line("YoruShield", 18)
          and "download(u)" in line("YoruShield", 18), ["YoruShield.java:15,18-20"])
    check("S14", "Player allocates WebView despite disabled Web playback",
          "web=new WebView(this)" in line("PlayerActivity", 33)
          and "loadUrl" not in line("PlayerActivity", 74)
          and "error(" in line("PlayerActivity", 74), ["PlayerActivity.java:33,74,111"])
    check("S15", "Downloads refresh queries index on Handler/main path",
          "Looper.getMainLooper()" in line("DownloadsScreen", 13)
          and "refreshSafe()" in line("DownloadsScreen", 14)
          and "hub.all()" in line("DownloadsScreen", 19),
          ["DownloadsScreen.java:13-19", "DownloadHub.java:25"])
    check("S16", "Job cancellation only changes a boolean",
          "cancelled=true;returntrue;" in re.sub(r"\s+", "", line("YoruUpdateJobService", 23))
          and "interrupt" not in java["YoruUpdateJobService.java"], ["YoruUpdateJobService.java:8-23"])
    check("S17", "HLS extraction returns children, DASH maps heights to the same MPD URL",
          "out.put(height,absolute(url,l))" in line("VideoResolver", 45)
          and "out.put(Integer.parseInt(m.group(1)),url)" in line("VideoResolver", 47)
          and "setMaxVideoSize(Integer.MAX_VALUE,Integer.MAX_VALUE)" in line("PlayerActivity", 73),
          ["VideoResolver.java:45,47", "PlayerActivity.java:73"])
    check("S18", "Recursive iframe traversal has no cross-call visited set/depth parameter",
          "resolve(api,frame)" in line("VideoResolver", 81)
          and "visited" not in java["VideoResolver.java"], ["VideoResolver.java:16-34,81-83"])
    check("S19", "Source scoring copies JSON snapshot for each score evaluation",
          "snapshot().optJSONObject(s)" in line("SourceEngine", 17)
          and "new JSONObject(raw.toString())" in line("SecureStore", 129),
          ["SourceEngine.java:12-23", "SecureStore.java:129"])
    check("S20", "Bundled alternative routes point back to the same URLs",
          all(r["to"] == [r["from"]] for r in profile["routes"]),
          ["assets/yoru_profile.json:4-22"])
    check("S21", "Release optimizer removes error and warning logging",
          all("public static *** " + level + "(...);" in proguard for level in ("e", "w")),
          ["app/proguard-rules.pro:3-9"])
    check("S22", "Handoff includes a missing Gradle subproject",
          "include ':yuroguard'" in settings
          and not any(n.startswith("yoru-android/yuroguard/") for n in names),
          ["settings.gradle:5", "archive file inventory"])
    check("S23", "Library constructs all cards in ScrollView",
          "LinearLayout col=scrolling()" in line("MainActivity", 74)
          and "else renderCards(col,rows)" in line("MainActivity", 74)
          and "i<list.size()" in line("MainActivity", 61), ["MainActivity.java:43,61,74"])
    check("S24", "Schedule fetch has eight ongoing and three premiere pages",
          "page<=8" in line("ApiRepository", 222)
          and "page<=3" in line("ApiRepository", 222), ["ApiRepository.java:222"])

    return {
        "archive": archive.name,
        "sha256": digest,
        "scope": "Read-only static evidence and synthetic algorithm models; NOT Android execution",
        "inventory": {
            "regular_files": len(files), "java_files": len(java),
            "java_bytes": sum(len(s.encode("utf-8")) for s in java.values()),
            "java_physical_lines": sum(len(s.splitlines()) for s in java.values()),
            "test_source_files": [n for n in sorted(names) if "/test/" in n or "/androidTest/" in n],
            "empty_catch_blocks_text_pattern": sum(len(re.findall(r"catch\s*\([^)]*\)\s*\{\s*\}", s)) for s in java.values()),
            "fixed_pool_creation_sites": sum(s.count("Executors.newFixedThreadPool") for s in java.values()),
            "java_file_names": sorted(java)
        },
        "static_evidence_count": len(evidence),
        "static_evidence": evidence,
        "synthetic_models": model_results(),
        "not_performed": ["APK build", "Android instrumentation", "Perfetto/FPS/heap profiling",
                          "Provider HTTP latency tests", "Device battery/thermal tests"]
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    try:
        result = audit(args.archive)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print("Audit verification failed: " + str(error), file=sys.stderr)
        return 2
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
