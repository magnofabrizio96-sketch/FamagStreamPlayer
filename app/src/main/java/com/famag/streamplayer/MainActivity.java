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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String PREFS = "FamagSession";
    private static final int BG = Color.rgb(7, 11, 21);
    private static final int PANEL = Color.rgb(17, 27, 46);
    private static final int BLUE = Color.rgb(24, 105, 230);
    private static final int LIGHT_BLUE = Color.rgb(68, 157, 255);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(174, 190, 212);

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService imageExecutor =
            Executors.newFixedThreadPool(4);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText serverInput;
    private EditText usernameInput;
    private EditText passwordInput;

    private LinearLayout root;
    private LinearLayout content;
    private XtreamApi api;

    private boolean connecting = false;
    private int generation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String server = p.getString("server", "");
        String username = p.getString("username", "");
        String password = p.getString("password", "");

        if (!server.isEmpty() && !username.isEmpty()
                && !password.isEmpty()) {
            connect(server, username, password, false);
        } else {
            showLogin();
        }
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(WHITE);
        t.setTextSize(size);
        t.setPadding(dp(6), dp(6), dp(6), dp(6));
        return t;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setBackground(bg(BLUE, 12));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void setupRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.setBackgroundColor(BG);
        setContentView(root);
    }

    private void addHeader(String title) {
        TextView logo = text("FAMAG  •  STREAM PLAYER", 22);
        logo.setTextColor(LIGHT_BLUE);
        logo.setTypeface(null, Typeface.BOLD);
        root.addView(logo);

        TextView heading = text(title, 16);
        heading.setTextColor(MUTED);
        root.addView(heading);
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        e.setBackground(bg(PANEL, 12));
        return e;
    }

    private void showLogin() {
        generation++;
        setupRoot();

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(dp(4), dp(30), dp(4), dp(20));

        TextView logo = text("FAMAG", 36);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setTextColor(LIGHT_BLUE);
        form.addView(logo);

        TextView subtitle = text("STREAM PLAYER", 20);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTypeface(null, Typeface.BOLD);
        form.addView(subtitle);

        TextView description = text(
                "TV in diretta, film e serie TV", 14);
        description.setTextColor(MUTED);
        description.setGravity(Gravity.CENTER);
        form.addView(description);

        serverInput = field("URL del server");
        usernameInput = field("Nome utente");
        passwordInput = field("Password");
        passwordInput.setInputType(129);

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        ip.setMargins(0, dp(7), 0, dp(7));

        form.addView(serverInput, ip);
        form.addView(usernameInput, ip);
        form.addView(passwordInput, ip);

        Button loginButton = button("ACCEDI", this::login);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        bp.setMargins(0, dp(15), 0, 0);
        form.addView(loginButton, bp);

        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void login() {
        if (connecting) return;

        String server = serverInput.getText().toString().trim();
        String user = usernameInput.getText().toString().trim();
        String pass = passwordInput.getText().toString();

        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Compila tutti i campi",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        connect(server, user, pass, true);
    }

    private void connect(
            String server, String user, String pass, boolean save) {

        if (connecting) return;
        connecting = true;

        setupRoot();
        root.setGravity(Gravity.CENTER);

        TextView status = text("Connessione al server...", 18);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        executor.execute(() -> {
            try {
                XtreamApi candidate = new XtreamApi(server, user, pass);
                candidate.login();

                handler.post(() -> {
                    connecting = false;
                    api = candidate;

                    if (save) {
                        getSharedPreferences(PREFS, MODE_PRIVATE)
                                .edit()
                                .putString("server", server)
                                .putString("username", user)
                                .putString("password", pass)
                                .apply();
                    }

                    showHome();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    connecting = false;
                    Toast.makeText(this,
                            "Accesso non riuscito. Controlla server e credenziali.",
                            Toast.LENGTH_LONG).show();
                    showLogin();
                    serverInput.setText(server);
                    usernameInput.setText(user);
                    passwordInput.setText(pass);
                });
            }
        });
    }

    private void addTile(
            LinearLayout row, String symbol, String title,
            String subtitle, int color, Runnable action) {

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(5), dp(10), dp(5), dp(10));
        tile.setBackground(bg(color, 16));
        tile.setFocusable(true);
        tile.setClickable(true);

        TextView icon = text(symbol, 30);
        icon.setGravity(Gravity.CENTER);
        tile.addView(icon);

        TextView name = text(title, 16);
        name.setTypeface(null, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        tile.addView(name);

        TextView sub = text(subtitle, 11);
        sub.setTextColor(MUTED);
        sub.setGravity(Gravity.CENTER);
        tile.addView(sub);

        tile.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, dp(135), 1);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        row.addView(tile, p);
    }

    private void showHome() {
        generation++;
        setupRoot();
        addHeader("Benvenuto! Cosa vuoi guardare?");

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        addTile(row1, "▶", "LIVE TV", "Canali in diretta",
                Color.rgb(17, 71, 151), () -> loadList("live"));
        addTile(row1, "▣", "FILM", "Film disponibili",
                Color.rgb(22, 54, 106), () -> loadList("movies"));
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        addTile(row2, "◉", "SERIE TV", "Le tue serie",
                Color.rgb(20, 77, 126), () -> loadList("series"));
        addTile(row2, "⚙", "ESCI", "Disconnetti account",
                Color.rgb(35, 45, 65), this::logout);
        root.addView(row2);

        TextView note = text(
                "Seleziona una categoria per iniziare.", 14);
        note.setTextColor(MUTED);
        root.addView(note);
    }

    private void logout() {
        generation++;
        api = null;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().clear().apply();
        showLogin();
    }

    private void showPage(String title) {
        generation++;
        setupRoot();
        root.addView(button("← HOME", this::showHome));

        TextView heading = text(title, 24);
        heading.setTextColor(LIGHT_BLUE);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(14));
        scroll.addView(content);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void loadList(String section) {
        if (api == null) return;

        showPage(section.equals("live") ? "LIVE TV"
                : section.equals("movies") ? "FILM" : "SERIE TV");

        int token = generation;
        content.addView(text("Caricamento contenuti...", 16));

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

                handler.post(() -> {
                    if (token != generation) return;
                    content.removeAllViews();

                    TextView count = text(
                            items.length() + " contenuti disponibili", 13);
                    count.setTextColor(MUTED);
                    content.addView(count);

                    if (items.length() == 0) {
                        content.addView(text(
                                "Nessun contenuto disponibile.", 16));
                        return;
                    }

                    if (section.equals("live")) {
                        addLiveRows(items);
                    } else {
                        addPosterGrid(items, section);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (token != generation) return;
                    content.removeAllViews();
                    content.addView(text(
                            "Errore nel caricamento. Verifica il server.",
                            16));
                });
            }
        });
    }

    private String imageUrl(JSONObject item, String... keys) {
        for (String key : keys) {
            String value = item.optString(key, "");
            if (!value.isEmpty() && !value.equals("null")) {
                return value;
            }
        }
        return "";
    }

    private Bitmap downloadBitmap(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)
                    new URL(address).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "FamagStreamPlayer");

            if (connection.getResponseCode() < 200
                    || connection.getResponseCode() >= 300) {
                return null;
            }

            try (InputStream in = connection.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void loadPoster(
            ImageView image, String address, int token) {

        image.setBackground(bg(PANEL, 8));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setTag(address);

        if (address == null || address.isEmpty()
                || address.equals("null")) return;

        imageExecutor.execute(() -> {
            Bitmap bitmap = downloadBitmap(address);
            if (bitmap == null) return;

            handler.post(() -> {
                if (token != generation) return;
                if (!address.equals(image.getTag())) return;
                image.setImageBitmap(bitmap);
            });
        });
    }

    private void addPosterGrid(JSONArray items, String section) {
        int token = generation;
        int columns = 3;

        for (int start = 0; start < items.length(); start += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);

            for (int col = 0; col < columns; col++) {
                int index = start + col;

                if (index >= items.length()) {
                    View empty = new View(this);
                    row.addView(empty, new LinearLayout.LayoutParams(
                            0, dp(190), 1));
                    continue;
                }

                JSONObject item = items.optJSONObject(index);
                if (item == null) continue;

                String name = item.optString("name", "Senza titolo");
                String id = item.optString(
                        section.equals("series") ? "series_id" : "stream_id",
                        "");
                String extension = item.optString(
                        "container_extension", "mp4");

                String poster = section.equals("series")
                        ? imageUrl(item, "cover", "cover_big", "stream_icon")
                        : imageUrl(item, "stream_icon", "cover", "cover_big");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                card.setPadding(dp(3), dp(3), dp(3), dp(8));
                card.setFocusable(true);
                card.setClickable(true);

                ImageView image = new ImageView(this);
                LinearLayout.LayoutParams ip =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, dp(145));
                image.setLayoutParams(ip);
                loadPoster(image, poster, token);
                card.addView(image);

                TextView label = text(name, 12);
                label.setMaxLines(2);
                label.setGravity(Gravity.CENTER);
                card.addView(label);

                card.setOnClickListener(v -> {
                    if (section.equals("series")) {
                        openSeries(id, name);
                    } else {
                        playStream("movie", id, extension, name);
                    }
                });

                LinearLayout.LayoutParams cp =
                        new LinearLayout.LayoutParams(0,
                                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                cp.setMargins(dp(2), dp(2), dp(2), dp(2));
                row.addView(card, cp);
            }

            content.addView(row);
        }
    }

    private void addLiveRows(JSONArray items) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            String name = item.optString("name", "Canale TV");
            String id = item.optString("stream_id", "");
            String extension = item.optString(
                    "container_extension", "m3u8");

            Button b = button("▶  " + name, () ->
                    playStream("live", id, extension, name));

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
            p.setMargins(0, dp(3), 0, dp(3));
            content.addView(b, p);
        }
    }

    // =========================
    // DETTAGLI SERIE E STAGIONI
    // =========================

    private void openSeries(String seriesId, String seriesName) {
        if (api == null || seriesId.isEmpty()) {
            Toast.makeText(this, "ID della serie non disponibile",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        showPage(seriesName);
        int token = generation;
        content.addView(text("Caricamento stagioni ed episodi...", 16));

        executor.execute(() -> {
            try {
                JSONObject details = api.getSeriesInfo(seriesId);

                handler.post(() -> {
                    if (token != generation) return;
                    content.removeAllViews();

                    JSONObject info = details.optJSONObject("info");
                    if (info != null) {
                        String plot = info.optString("plot", "");
                        if (!plot.isEmpty() && !plot.equals("null")) {
                            TextView description = text(plot, 14);
                            description.setTextColor(MUTED);
                            content.addView(description);
                        }
                    }

                    JSONObject episodes = details.optJSONObject("episodes");
                    JSONArray seasons = details.optJSONAtring title,
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

        TextView welcome = text(
                "Benvenuto! Cosa vuoi guardare?", 14);
        welcome.setTextColor(MUTED);
        home.addView(welcome);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        addTile(row1, "\u25B6", "LIVE TV",
                "Canali in diretta", Color.rgb(17, 71, 151),
                () -> loadList("live"));
        addTile(row1, "\u25A3", "FILM",
                "Film disponibili", Color.rgb(22, 54, 106),
                () -> loadList("movies"));
        home.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        addTile(row2, "\u25C9", "SERIE TV",
                "Le tue serie", Color.rgb(20, 77, 126),
                () -> loadList("series"));
        addTile(row2, "\u2699", "ESCI",
                "Disconnetti account", Color.rgb(35, 45, 65),
                this::logout);
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
            connection.setRequestProperty(
                    "User-Agent", "FamagStreamPlayer");

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

    private void loadPoster(
            ImageView image, String address, int generation) {

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

        setupRoot();

        listScroll = new ScrollView(this);
        listScroll.setFillViewport(true);

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(0, 0, 0, dp(12));

        listLayout.addView(button("\u2190 TORNA ALLA HOME",
                this::showHome));
        listLayout.addView(text("Caricamento...", 16));

        listScroll.addView(listLayout);
        root.addView(listScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

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
                    if (generation != pageGeneration
                            || listLayout == null) {
                        return;
                    }

                    listLayout.removeAllViews();
                    listLayout.addView(button(
                            "\u2190 TORNA ALLA HOME", this::showHome));

                    String heading = section.equals("live")
                            ? "Canali TV"
                            : section.equals("movies") ? "Film" : "Serie TV";

                    TextView title = text(heading, 25);
                    title.setTypeface(null, Typeface.BOLD);
                    title.setTextColor(LIGHT_BLUE);
                    listLayout.addView(title);

                    TextView count = text(
                            items.length() + " contenuti disponibili", 13);
                    count.setTextColor(MUTED);
                    listLayout.addView(count);

                    if (items.length() == 0) {
                        listLayout.addView(text(
                                "Nessun elemento disponibile.", 16));
                        return;
                    }

                    if (section.equals("live")) {
                        addLiveRows(items, generation);
                    } else {
                        addCinemaGrid(items, section, generation);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (generation != pageGeneration) return;
                    listLayout.removeAllViews();
                    listLayout.addView(button(
                            "\u2190 TORNA ALLA HOME", this::showHome));
                    listLayout.addView(text(
                            "Errore nel caricamento dei contenuti.", 16));
                });
            }
        });
    }

    /*
     * Griglia cinematografica: tre locandine per riga.
     * Le larghezze si adattano allo spazio disponibile.
     */
    private void addCinemaGrid(
            JSONArray items, String section, int generation) {

        int columns = 3;
        int index = 0;

        while (index < items.length()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setWeightSum(columns);

            for (int column = 0;
                    column < columns && index < items.length();
                    column++, index++) {

                JSONObject item = items.optJSONObject(index);
                if (item == null) {
                    addEmptyCell(row);
                    continue;
                }

                String name = item.optString("name", "Senza titolo");
                String id = item.optString(
                        section.equals("series")
                                ? "series_id" : "stream_id", "");

                String extension = item.optString(
                        "container_extension", "mp4");

                String poster = section.equals("series")
                        ? firstImage(item, "cover", "cover_big",
                                "stream_icon")
                        : firstImage(item, "stream_icon", "cover",
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
