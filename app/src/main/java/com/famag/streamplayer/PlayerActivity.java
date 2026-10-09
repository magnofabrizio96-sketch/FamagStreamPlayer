package com.famag.streamplayer;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.PrintWriter;
import java.io.StringWriter;

public class PlayerActivity extends Activity {

    private ExoPlayer player;

    private void showError(String message) {
        Log.e("FamagPlayer", message);

        TextView text = new TextView(this);
        text.setTextColor(Color.WHITE);
        text.setBackgroundColor(Color.BLACK);
        text.setTextSize(15);
        text.setPadding(24, 24, 24, 24);
        text.setText(message);

        setContentView(text);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String streamUrl = getIntent().getStringExtra("stream_url");

        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            showError("Indirizzo video mancante");
            return;
        }

        try {
            PlayerView playerView = new PlayerView(this);
            playerView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            setContentView(playerView);

            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(PlaybackException error) {
                    StringWriter sw = new StringWriter();
                    error.printStackTrace(new PrintWriter(sw));
                    showError("ERRORE RIPRODUZIONE:\n" + sw);
                }
            });

            player.setMediaItem(MediaItem.fromUri(streamUrl));
            player.prepare();
            player.play();

        } catch (Throwable error) {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            showError("ERRORE COMPLETO:\n" + sw);
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
