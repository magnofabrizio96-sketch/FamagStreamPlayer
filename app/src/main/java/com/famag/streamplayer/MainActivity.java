
package com.famag.streamplayer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "FamagSession";
    private static final String KEY_SERVER = "server";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    private static final int BG = Color.rgb(8, 13, 25);
    private static final int PANEL = Color.rgb(17, 27, 46);
    private static final int BLUE = Color.rgb(24, 105, 230);
    private static final int LIGHT_BLUE = Color.rgb(68, 157, 255);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(174, 190, 212);

    private final ExecutorService executor =
        Executors.newSingleThreadExecutor();

    private final ExecutorService imageExecutor =
        Executors.newFixedThreadPool(4);

    private final Handler mainHandler =
        new Handler(Looper.getMainLooper());

    private EditText serverInput;
    private EditText usernameInput;
    private EditText passwordInput;

    private LinearLayout root;
    private LinearLayout listLayout;
    private ScrollView listScroll;
    private XtreamApi api;

    private boolean connecting = false;
    private int pageGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs =
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String server = prefs.getString(KEY_SERVER, "");
        String username = prefs.getString(KEY_USERNAME, "");
        String password = prefs.getString(KEY_PASSWORD, "");

        if (!server.isEmpty()
                && !username.isEmpty()
                && !password.isEmpty()) {
            connectSavedAccount(server, username, password);
        } else {
            showLogin();
        }
    }

    private int dp(float value) {
        return (int) (value * getResources()
            .getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable background(
            int color, int radius, int strokeColor) {

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        if (strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), strokeColor);
        }

        return drawable;
    }

    private void setupRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(12));
        root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);
        setContentView(root);
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(WHITE);
        t.setTextSize(size);
        t.setPadding(dp(6), dp(6), dp(6), dp(6));
        return t;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setTextSize(15);
        e.setPadding(dp(14), dp(8), dp(14), dp(8));
        e.setBackground(background(PANEL, 12, Color.rgb(43, 65, 98)));
        return e;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(WHITE);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setBackground(background(BLUE, 12, Color.TRANSPARENT));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void connectSavedAccount(
            String server, String username, String password) {

        if (connecting) return;
        connecting = true;

        setupRoot();
        root.setGravity(Gravity.CENTER);

        TextView logo = text("FAMAG", 34);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setTextColor(LIGHT_BLUE);
        root.addView(logo);

        TextView status = text("Connessione al tuo account...", 16);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        executor.execute(() -> {
            try {
                XtreamApi candidate =
                    new XtreamApi(server, username, password);
                candidate.login();

                mainHandler.post(() -> {
                    api = candidate;
                    connecting = false;
                    showHome();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    connecting = false;
                    api = null;

                    Toast.makeText(
                        this,
                        "Connessione non riuscita. Verifica server e credenziali.",
                        Toast.LENGTH_LONG
                    ).show();

                    showLogin();
                    serverInput.setText(server);
                    usernameInput.setText(username);
                    passwordInput.setText(password);
                });
            }
        });
    }

    private void showLogin() {
        setupRoot();
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(4), dp(12), dp(4), dp(12));

        TextView logo = text("FAMAG", 36);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setTextColor(LIGHT_BLUE);
        content.addView(logo);

        TextView brand = text("STREAM PLAYER", 19);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(null, Typeface.BOLD);
        content.addView(brand);

        TextView subtitle = text(
            "Tutto il tuo intrattenimento, in un unico posto", 14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(MUTED);
        content.addView(subtitle);

        serverInput = field("URL del server");
        usernameInput = field("Nome utente");
        passwordInput = field("Password");
        passwordInput.setInputType(129);

        LinearLayout.LayoutParams inputParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        inputParams.setMargins(0, dp(7), 0, dp(7));

        content.addView(serverInput, inputParams);
        content.addView(usernameInput, inputParams);
        content.addView(passwordInput, inputParams);

        Button loginButton = button("ACCEDI", this::login);
        loginButton.setTextSize(17);
        loginButton.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams loginParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        loginParams.setMargins(0, dp(16), 0, dp(8));

        content.addView(loginButton, loginParams);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void login() {
        if (connecting) return;

        String server = serverInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Compila tutti i campi",
                Toast.LENGTH_SHORT).show();
            return;
        }

        connecting = true;
        Toast.makeText(this, "Connessione in corso...",
            Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                XtreamApi candidate =
                    new XtreamApi(server, username, password);
                candidate.login();

                mainHandler.post(() -> {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putString(KEY_SERVER, server)
                        .putString(KEY_USERNAME, username)
                        .putString(KEY_PASSWORD, password)
                        .apply();

                    api = candidate;
                    connecting = false;
                    showHome();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    connecting = false;
                    Toast.makeText(this,
                        "Accesso non riuscito. Controlla server e credenziali.",
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void addTile(
            LinearLayout row, String icon, String title,
            String subtitle, int color, Runnable action) {

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(12), dp(8), dp(12));
        tile.setBackground(background(color, 18, LIGHT_BLUE));
        tile.setClickable(true);
        tile.setFocusable(true);

        tile.setOnFocusChangeListener((v, focused) ->
            v.setBackground(background(
                focused ? LIGHT_BLUE : color, 18,
                focused ? WHITE : LIGHT_BLUE)));

        TextView iconView = text(icon, 32);
        iconView.setGravity(Gravity.CENTER);
        tile.addView(iconView);

        TextView titleView = text(title, 17);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(null, Typeface.BOLD);
        tile.addView(titleView);

        TextView sub = text(subtitle, 12);
        sub.setGravity(Gravity.CENTER);
        sub.setTextColor(Color.rgb(221, 233, 250));
        tile.addView(sub);

        tile.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(0, dp(142), 1);
        p.setMargins(dp(5), dp(5), dp(5), dp(5));
        row.addView(tile, p);
    }

    private void showHome() {
        pageGeneration++;
        setupRoot();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);

        TextView logo = text("FAMAG", 30);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setTextColor(LIGHT_BLUE);
        home.addView(logo);

        TextView heading = text("STREAM PLAYER", 17);
        heading.setTypeface(null, Typeface.BOLD);
        home.addView(heading);

        TextView welcome = text("Benvenuto! Cosa vuoi guardare?", 14);
        welcome.setTextColor(MUTED);
        home.addView(welcome);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        addTile(row1, "\u25B6", "LIVE TV", "Canali in diretta",
            Color.rgb(17, 71, 151), () -> loadList("live"));
        addTile(row1, "\u25A3", "FILM", "Film disponibili",
            Color.rgb(22, 54, 106), () -> loadList("movies"));
        home.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        addTile(row2, "\u25C9", "SERIE TV", "Le tue serie",
            Color.rgb(20, 77, 126), () -> loadList("series"));
        addTile(row2, "\u2699", "ESCI", "Disconnetti account",
            Color.rgb(35, 45, 65), this::logout);
        home.addView(row2);

        TextView sectionTitle = text("I TUOI CONTENUTI", 17);
        sectionTitle.setTypeface(null, Typeface.BOLD);
        home.addView(sectionTitle);

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        home.addView(listLayout);

        scroll.addView(home);
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.requestFocus();
    }

    private void logout() {
        pageGeneration++;

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().clear().apply();

        api = null;
        showLogin();
    }

    private Bitmap downloadBitmap(String address) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(address);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "FamagStreamPlayer");

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return null;

            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void loadPoster(ImageView image, String address, int generation) {
        image.setImageDrawable(null);
        image.setBackground(background(PANEL, 10, Color.TRANSPARENT));

        if (address == null || address.trim().isEmpty()
                || address.equals("null")) {
            return;
        }

        image.setTag(address);

        imageExecutor.execute(() -> {
            Bitmap bitmap = downloadBitmap(address);

            if (bitmap != null) {
                mainHandler.post(() -> {
                    if (generation != pageGeneration) return;
                    if (address.equals(image.getTag())) {
                        image.setImageBitmap(bitmap);
                        image.setBackgroundColor(Color.TRANSPARENT);
                    }
                });
            }
        });
    }

    private String firstImage(JSONObject item, String... keys) {
        for (String key : keys) {
            String value = item.optString(key, "");
            if (!value.isEmpty() && !value.equals("null")) {
                return value;
            }
        }
        return "";
    }

    private void loadList(String section) {
        if (api == null) return;

        int generation = ++pageGeneration;

        listLayout.removeAllViews();

        listLayout.addView(button("\u2190 TORNA ALLA HOME",
            this::showHome));
        listLayout.addView(text("Caricamento...", 16));

        executor.execute(() -> {
            try {
                JSONArray items;

                if (section.equals("live")) {
                    items = api.getLiveStreams();
                } else if (section.equals("movies")) {
                    items = api.getVodStreams();
                } else {
                    items = api.getSeries();
                }

                mainHandler.post(() -> {
                    if (generation != pageGeneration || listLayout == null) {
                        return;
                    }

                    listLayout.removeAllViews();
                    listLayout.addView(button("\u2190 TORNA ALLA HOME",
                        this::showHome));

                    String heading = section.equals("live") ? "Canali TV"
                        : section.equals("movies") ? "Film" : "Serie TV";

                    TextView title = text(heading, 23);
                    title.setTypeface(null, Typeface.BOLD);
                    listLayout.addView(title);

                    if (items.length() == 0) {
                        listLayout.addView(text(
                            "Nessun elemento disponibile.", 16));
                        return;
                    }

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;

                        String name = item.optString("name", "Senza titolo");
                        String id = item.optString(
                            section.equals("series") ? "series_id" : "stream_id",
                            "");

                        String extension = item.optString(
                            "container_extension",
                            section.equals("live") ? "ts" : "mp4");

                        String poster = section.equals("series")
                            ? firstImage(item, "cover", "cover_big", "stream_icon")
                            : firstImage(item, "stream_icon", "cover", "cover_big");

                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.HORIZONTAL);
                        card.setGravity(Gravity.CENTER_VERTICAL);
                        card.setPadding(dp(8), dp(8), dp(8), dp(8));
                        card.setBackground(background(PANEL, 12,
                            Color.rgb(32, 49, 76)));

                        ImageView posterView = new ImageView(this);
                        posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);

                        int posterWidth = section.equals("live") ? 64 : 88;
                        int posterHeight = section.equals("live") ? 64 : 118;

                        LinearLayout.LayoutParams imageParams =
                            new LinearLayout.LayoutParams(
                                dp(posterWidth), dp(posterHeight));
                        imageParams.setMargins(0, 0, dp(12), 0);

                        card.addView(posterView, imageParams);
                        loadPoster(posterView, poster, generation);

                        LinearLayout details = new LinearLayout(this);
                        details.setOrientation(LinearLayout.VERTICAL);
                        details.setGravity(Gravity.CENTER_VERTICAL);

                        TextView itemName = text(name, 15);
                        itemName.setTypeface(null, Typeface.BOLD);
                        itemName.setPadding(0, dp(4), 0, dp(4));
                        details.addView(itemName);

                        TextView typeLabel = text(
                            section.equals("live") ? "Canale TV"
                                : section.equals("movies") ? "Film" : "Serie TV",
                            12);
                        typeLabel.setTextColor(MUTED);
                        typeLabel.setPadding(0, dp(2), 0, dp(4));
                        details.addView(typeLabel);

                        card.addView(details, new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                        card.setFocusable(true);
                        card.setClickable(true);

                        card.setOnFocusChangeListener((v, focused) ->
                            v.setBackground(background(
                                focused ? Color.rgb(28, 62, 108) : PANEL,
                                12,
                                focused ? LIGHT_BLUE : Color.rgb(32, 49, 76))));

                        card.setOnClickListener(v -> {
                            if (section.equals("series")) {
                                Toast.makeText(
                                    this,
                                    "La riproduzione delle serie sarà aggiunta in un prossimo aggiornamento.",
                                    Toast.LENGTH_SHORT).show();
                                return;
                            }

                            String type = section.equals("live")
                                ? "live" : "movie";

                            String url = api.buildStreamUrl(
                                type, id, extension);

                            if (url.isEmpty()) {
                                Toast.makeText(this,
                                    "Indirizzo video non valido.",
                                    Toast.LENGTH_SHORT).show();
                                return;
                            }

                            Intent intent = new Intent(
                                MainActivity.this, PlayerActivity.class);
                            intent.putExtra("stream_url", url);
                            startActivity(intent);
                        });

                        LinearLayout.LayoutParams cardParams =
                            new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        cardParams.setMargins(0, dp(4), 0, dp(4));

                        listLayout.addView(card, cardParams);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (generation != pageGeneration || listLayout == null) {
                        return;
                    }

                    listLayout.removeAllViews();
                    listLayout.addView(button("\u2190 TORNA ALLA HOME",
                        this::showHome));
                    listLayout.addView(text(
                        "Impossibile caricare la lista. Controlla il server.",
                        16));
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        imageExecutor.shutdownNow();
    }
}
