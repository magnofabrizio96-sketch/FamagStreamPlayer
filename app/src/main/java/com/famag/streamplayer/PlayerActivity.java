package com.famag.streamplayer;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.graphics.Color;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {

    private static final String TAG = "FamagPlayer";

    private ExoPlayer player;
    private PlayerView playerView;

    private void showMessage(String message) {
        runOnUiThread(() ->
                Toast.makeText(
                        PlayerActivity.this,
                        message,
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String streamUrl = getIntent().getStringExtra("stream_url");

        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            showMessage("Errore: indirizzo video mancante");
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
                    Log.e(TAG, "Errore di riproduzione", error);

                    String message = "Errore LIVE: "
                            + error.getErrorCodeName()
                            + " (codice " + error.errorCode + ")";

                    if (error.getMessage() != null) {
                        message += " - " + error.getMessage();
                    }

                    showMessage(message);
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        Log.d(TAG, "Player pronto");
                        showMessage("Player pronto");
                    } else if (state == Player.STATE_BUFFERING) {
                        Log.d(TAG, "Caricamento video...");
                    } else if (state == Player.STATE_ENDED) {
                        Log.d(TAG, "Riproduzione terminata");
                    }
                }
            });

            player.setMediaItem(MediaItem.fromUri(streamUrl));
            player.prepare();
            player.play();

        } catch (Exception e) {
            Log.e(TAG, "Eccezione durante l'avvio del player", e);

            String message = "Errore player: "
                    + e.getClass().getSimpleName();

            if (e.getMessage() != null) {
                message += " - " + e.getMessage();
            }

            showMessage(message);
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
