package com.famag.streamplayer;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String streamUrl = getIntent().getStringExtra("stream_url");
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            Toast.makeText(this, "Indirizzo video mancante", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        getWindow().getDecorView().setBackgroundColor(android.graphics.Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(playerView);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(streamUrl));
        player.prepare();
        player.play();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
