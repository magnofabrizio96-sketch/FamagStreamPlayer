package com.famag.streamplayer;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.graphics.Color;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
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
            Toast.makeText(
                    this,
                    "Indirizzo video mancante",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(playerView);

        try {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {

                @Override
                public void onPlayerError(PlaybackException error) {
                    String message = "Errore riproduzione: "
                            + error.getErrorCodeName()
                            + " (codice "
                            + error.errorCode
                            + ")";

                    Toast.makeText(
                            PlayerActivity.this,
                            message,
                            Toast.LENGTH_LONG
                    ).show();
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        Toast.makeText(
                                PlayerActivity.this,
                                "Video pronto",
                                Toast.LENGTH_SHORT
                        ).show();
                    } else if (playbackState == Player.STATE_ENDED) {
                        Toast.makeText(
                                PlayerActivity.this,
                                "Riproduzione terminata",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
            });

            player.setMediaItem(MediaItem.fromUri(streamUrl));
            player.prepare();
            player.play();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Errore player: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG
            ).show();
        }
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
