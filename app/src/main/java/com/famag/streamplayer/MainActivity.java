
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

    private final Handler handler =
            new Handler(Looper.getMainLooper());

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

        SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        String server = prefs.getString("server", "");
        String username = prefs.getString("username", "");
        String password = prefs.getString("password", "");

        if (!server.isEmpty()
                && !username.isEmpty()
                && !password.isEmpty()) {
            connect(server, username, password, false);
        } else {
            showLogin();
        }
    }

    private int dp(float value) {
        return (int) (value
                * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(WHITE);
        view.setTextSize(size);
        view.setPadding(dp(6), dp(6), dp(6), dp(6));
        return view;
    }

    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextColor(WHITE);
        view.setTextSize(15);
        view.setAllCaps(false);
        view.setBackground(background(BLUE, 12));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void setupRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.setBackgroundColor(BG);
        setContentView(root);
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

        TextView description =
                text("TV in diretta, film e serie TV", 14);
        description.setTextColor(MUTED);
        description.setGravity(Gravity.CENTER);
        form.addView(description);

        serverInput = field("URL del server");
        usernameInput = field("Nome utente");
        passwordInput = field("Password");
        passwordInput.setInputType(129);

        LinearLayout.LayoutParams fieldParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        fieldParams.setMargins(0, dp(7), 0, dp(7));

        form.addView(serverInput, fieldParams);
        form.addView(usernameInput, fieldParams);
        form.addView(passwordInput, fieldParams);

        Button loginButton = button("ACCEDI", this::login);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        buttonParams.setMargins(0, dp(15), 0, 0);
        form.addView(loginButton, buttonParams);

        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private EditText field(String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setTextColor(WHITE);
        edit.setHintTextColor(MUTED);
        edit.setPadding(dp(12), dp(8), dp(12), dp(8));
        edit.setBackground(background(PANEL, 12));
        return edit;
    }

    private void login() {
        if (connecting) return;

        String server = serverInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (server.isEmpty()
                || username.isEmpty()
                || password.isEmpty()) {
            Toast.makeText(this, "Compila tutti i campi",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        connect(server, username, password, true);
    }

    private void connect(
            String server,
            String username,
            String password,
            boolean save) {

        if (connecting) return;

        connecting = true;
        generation++;

        setupRoot();
        root.setGravity(Gravity.CENTER);

        TextView status = text("Connessione al server...", 18);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        executor.execute(() -> {
            try {
                XtreamApi candidate =
                        new XtreamApi(server, username, password);
                candidate.login();

                handler.post(() -> {
                    connecting = false;
                    api = candidate;

                    if (save) {
                        getSharedPreferences(PREFS, MODE_PRIVATE)
                                .edit()
                                .putString("server", server)
                                .putString("username", username)
                                .putString("password", password)
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
                    usernameInput.setText(username);
                    passwordInput.setText(password);
                });
            }
        });
    }

    private void addTile(
            LinearLayout row,
            String symbol,
            String title,
            String subtitle,
            int color,
            Runnable action) {

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(5), dp(10), dp(5), dp(10));
        tile.setBackground(background(color, 16));
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

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0, dp(135), 1);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        row.addView(tile, params);
    }

    private void showHome() {
        generation++;
        setupRoot();

        TextView logo = text("FAMAG  •  STREAM PLAYER", 22);
        logo.setTextColor(LIGHT_BLUE);
        logo.setTypeface(null, Typeface.BOLD);
        root.addView(logo);

        TextView heading =
                text("Benvenuto! Cosa vuoi guardare?", 16);
        heading.setTextColor(MUTED);
        root.addView(heading);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        addTile(row1, "▶", "LIVE TV", "Canali in diretta",
                Color.rgb(17, 71, 151),
                () -> loadList("live"));

        addTile(row1, "▣", "FILM", "Film disponibili",
                Color.rgb(22, 54, 106),
                () -> loadList("movies"));

        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        addTile(row2, "◉", "SERIE TV", "Le tue serie",
                Color.rgb(20, 77, 126),
                () -> loadList("series"));

        addTile(row2, "⚙", "ESCI", "Disconnetti account",
                Color.rgb(35, 45, 65),
                this::logout);

        root.addView(row2);

        TextView note =
                text("Seleziona una categoria per iniziare.", 14);
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

        String title = section.equals("live") ? "LIVE TV"
                : section.equals("movies") ? "FILM" : "SERIE TV";

        showPage(title);
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
            ImageView image, String address, int token) {

        image.setBackground(background(PANEL, 8));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setTag(address);

        if (address == null || address.isEmpty()
                || address.equals("null")) {
            return;
        }

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
                        section.equals("series")
                                ? "series_id" : "stream_id", "");

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
                image.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(145)));
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

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                params.setMargins(dp(2), dp(2), dp(2), dp(2));
                row.addView(card, params);
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

            Button playButton = button("▶  " + name,
                    () -> playStream("live", id, extension, name));

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
            params.setMargins(0, dp(3), 0, dp(3));
            content.addView(playButton, params);
        }
    }

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

                    JSONObject episodes =
                            details.optJSONObject("episodes");

                    if (episodes == null || episodes.length() == 0) {
                        content.addView(text(
                                "Nessun episodio disponibile.", 16));
                        return;
                    }

                    List<String> seasonKeys = new ArrayList<>();
                    Iterator<String> iterator = episodes.keys();

                    while (iterator.hasNext()) {
                        seasonKeys.add(iterator.next());
                    }

                    Collections.sort(seasonKeys, (a, b) -> {
                        try {
                            return Integer.compare(
                                    Integer.parseInt(a),
                                    Integer.parseInt(b));
                        } catch (NumberFormatException e) {
                            return a.compareTo(b);
                        }
                    });

                    JSONArray seasons = details.optJSONArray("seasons");
                    content.addView(text("STAGIONI", 20));

                    for (String seasonKey : seasonKeys) {
                        JSONArray seasonEpisodes =
                                episodes.optJSONArray(seasonKey);

                        if (seasonEpisodes == null) continue;

                        String seasonTitle = "Stagione " + seasonKey;

                        if (seasons != null) {
                            for (int i = 0; i < seasons.length(); i++) {
                                JSONObject season =
                                        seasons.optJSONObject(i);

                                if (season == null) continue;

                                String number = season.optString(
                                        "season_number", "");

                                if (number.equals(seasonKey)) {
                                    String seasonName = season.optString(
                                            "name", "");

                                    if (!seasonName.isEmpty()
                                            && !seasonName.equals("null")) {
                                        seasonTitle = seasonName;
                                    }
                                    break;
                                }
                            }
                        }

                        TextView seasonHeading =
                                text(seasonTitle, 18);
                        seasonHeading.setTypeface(null, Typeface.BOLD);
                        seasonHeading.setTextColor(LIGHT_BLUE);
                        content.addView(seasonHeading);

                        for (int i = 0;
                                i < seasonEpisodes.length(); i++) {

                            JSONObject episode =
                                    seasonEpisodes.optJSONObject(i);

                            if (episode == null) continue;

                            String episodeId =
                                    episode.optString("id", "");

                            String episodeName = episode.optString(
                                    "title", "Episodio " + (i + 1));

                            String episodeExtension = episode.optString(
                                    "container_extension", "mp4");

                            String episodeNumber = episode.optString(
                                    "episode_num", "");

                            String buttonTitle = episodeName;
                            if (!episodeNumber.isEmpty()
                                    && !episodeNumber.equals("null")) {
                                buttonTitle = "E" + episodeNumber
                                        + " - " + episodeName;
                            }

                            final String finalEpisodeId = episodeId;
                            final String finalEpisodeName = episodeName;
                            final String finalEpisodeExtension =
                                    episodeExtension;
                            final String finalButtonTitle = buttonTitle;

                            Button episodeButton = button(
                                    "▶  " + finalButtonTitle, () -> {
                                if (finalEpisodeId.isEmpty()) {
                                    Toast.makeText(this,
                                            "ID episodio non disponibile",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    playStream("series",
                                            finalEpisodeId,
                                            finalEpisodeExtension,
                                            finalEpisodeName);
                                }
                            });

                            LinearLayout.LayoutParams params =
                                    new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT);
                            params.setMargins(0, dp(2), 0, dp(2));
                            content.addView(episodeButton, params);
                        }
                    }
                });

            } catch (Exception e) {
                handler.post(() -> {
                    if (token != generation) return;
                    content.removeAllViews();
                    content.addView(text(
                            "Errore nel caricamento della serie. "
                                    + "Riprova più tardi.", 16));
                });
            }
        });
    }

    private void playStream(
            String type,
            String id,
            String extension,
            String title) {

        if (api == null || id == null || id.isEmpty()) {
            Toast.makeText(this, "Video non disponibile",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String streamUrl = api.buildStreamUrl(type, id, extension);

        if (streamUrl == null || streamUrl.isEmpty()) {
            Toast.makeText(this, "Impossibile creare l'URL video",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("stream_url", streamUrl);
        intent.putExtra("title", title);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        generation++;
        executor.shutdownNow();
        imageExecutor.shutdownNow();
        super.onDestroy();
    }
}
