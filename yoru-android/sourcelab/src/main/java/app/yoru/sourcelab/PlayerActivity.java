package app.yoru.sourcelab;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;

@UnstableApi
public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private TextView overlay;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        String url = getIntent().getStringExtra("url");
        String title = getIntent().getStringExtra("title");
        String referer = getIntent().getStringExtra("referer");
        String source = getIntent().getStringExtra("source");
        String quality = getIntent().getStringExtra("quality");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        PlayerView view = new PlayerView(this);
        view.setUseController(true);
        view.setKeepScreenOn(true);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));

        overlay = new TextView(this);
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(13);
        overlay.setShadowLayer(5, 0, 2, Color.BLACK);
        overlay.setPadding(dp(12), dp(8), dp(12), dp(8));
        overlay.setText((source == null ? "Source" : source) + " · " + (quality == null ? "stream" : quality) + "\n" + (title == null ? "" : title));
        FrameLayout.LayoutParams op = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.START);
        root.addView(overlay, op);
        setContentView(root);

        player = new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        try {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36")
                    .setConnectTimeoutMs(10000)
                    .setReadTimeoutMs(20000)
                    .setAllowCrossProtocolRedirects(true);
            if (referer != null && !referer.isEmpty()) http.setDefaultRequestProperties(java.util.Collections.singletonMap("Referer", referer));
            MediaItem item = MediaItem.fromUri(Uri.parse(url));
            MediaSource media;
            if (url != null && url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")) media = new HlsMediaSource.Factory(http).createMediaSource(item);
            else media = new androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(http).createMediaSource(item);
            player.setMediaSource(media);
            player.prepare();
            player.play();
        } catch (Throwable e) {
            overlay.setText("Ошибка запуска плеера:\n" + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n" + url);
        }
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) overlay.setVisibility(View.VISIBLE);
                else if (state == Player.STATE_READY) overlay.postDelayed(() -> overlay.setVisibility(View.GONE), 2200);
                else if (state == Player.STATE_ENDED) overlay.setVisibility(View.VISIBLE);
            }
            @Override public void onPlayerError(PlaybackException error) {
                overlay.setVisibility(View.VISIBLE);
                overlay.setText("Плеер не смог открыть поток:\n" + error.getMessage() + "\n" + url);
            }
        });
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (player != null) { player.release(); player = null; }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
