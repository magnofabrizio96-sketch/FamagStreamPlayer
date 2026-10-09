
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
    private LinearLayout sidebar;
    private LinearLayout pageArea;

    private XtreamApi api;

    private boolean connecting = false;
    private int generation = 0;

    private String currentSection = "home";
    private String currentCategoryId = "";
    private String currentCategoryName = "Tutti";
    private String currentPageTitle = "";

    private JSONArray currentItems = new JSONArray();
    private JSONArray currentCategories = new JSONArray();

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
        view.setTextSize(14);
        view.setAllCaps(false);
        view.setBackground(background(BLUE, 10));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void setupRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
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

    private void showHome() {
        currentSection = "home";
        currentCategoryId = "";
        currentCategoryName = "Tutti";

        generation++;
        setupRoot();

        TextView logo = text("FAMAG", 30);
        logo.setTextColor(LIGHT_BLUE);
        logo.setTypeface(null, Typeface.BOLD);
        root.addView(logo);

        TextView subtitle = text("STREAM PLAYER", 14);
        subtitle.setTextColor(MUTED);
        root.addView(subtitle);

        addMenuButton("▶  LIVE TV", () -> loadSection("live"));
        addMenuButton("▣  FILM", () -> loadSection("movies"));
        addMenuButton("◉  SERIE TV", () -> loadSection("series"));

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(
                1, 0, 1));

        addMenuButton("⚙  ESCI", this::logout);

        TextView welcome =
                text("Seleziona una sezione per iniziare.", 16);
        welcome.setTextColor(MUTED);
        root.addView(welcome);
    }

    private void addMenuButton(String label, Runnable action) {
        Button item = button(label, action);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), 0, dp(8), 0);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        params.setMargins(0, dp(5), 0, dp(5));
        root.addView(item, params);
    }

    private void logout() {
        generation++;
        api = null;

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().clear().apply();

        showLogin();
    }

    private void buildSidebar(String section) {
        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(5), dp(5), dp(5), dp(5));
        sidebar.setBackground(background(PANEL, 12));

        TextView brand = text("FAMAG", 17);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setTextColor(LIGHT_BLUE);
        sidebar.addView(brand);

        addSidebarButton("⌂ Home", this::showHome);
        addSidebarButton("▶ Live TV", () -> loadSection("live"));
        addSidebarButton("▣ Film", () -> loadSection("movies"));
        addSidebarButton("◉ Serie TV", () -> loadSection("series"));

        TextView separator = text("CATEGORIE", 11);
        separator.setTextColor(MUTED);
        separator.setTypeface(null, Typeface.BOLD);
        sidebar.addView(separator);

        ScrollView categoryScroll = new ScrollView(this);
        LinearLayout categoryList = new LinearLayout(this);
        categoryList.setOrientation(LinearLayout.VERTICAL);
        categoryScroll.addView(categoryList);

        sidebar.addView(categoryScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (!section.equals("home")) {
            addCategoryButton(categoryList, "Tutti", "",
                    currentCategoryId.isEmpty());

            for (int i = 0; i < currentCategories.length(); i++) {
                JSONObject category =
                        currentCategories.optJSONObject(i);
                if (category == null) continue;

                String id = category.optString("category_id", "");
                String name = category.optString(
                        "category_name", "Categoria");

                addCategoryButton(categoryList, name, id,
                        id.equals(currentCategoryId));
            }
        }

        addSidebarButton("⚙ Esci", this::logout);
    }

    private void addSidebarButton(String label, Runnable action) {
        Button item = button(label, action);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setTextSize(12);
        item.setPadding(dp(6), 0, dp(3), 0);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(43));
        params.setMargins(0, dp(2), 0, dp(2));
        sidebar.addView(item, params);
    }

    private void addCategoryButton(
            LinearLayout list,
            String name,
            String id,
            boolean selected) {

        Button item = new Button(this);
        item.setText(name);
        item.setTextColor(WHITE);
        item.setTextSize(11);
        item.setAllCaps(false);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(4), 0, dp(2), 0);
        item.setBackground(background(
                selected ? BLUE : PANEL, 8));

        item.setOnClickListener(v -> {
            currentCategoryId = id;
            currentCategoryName = name;
            showSectionPage(currentSection);
        });

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(2), 0, dp(2));
        list.addView(item, params);
    }

    private void showSectionPage(String section) {
        generation++;
        int token = generation;

        setupRoot();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(layout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        buildSidebar(section);

        int sidebarWidth = Math.min(dp(155),
                (int) (getResources().getDisplayMetrics().widthPixels
                        * 0.36f));

        layout.addView(sidebar, new LinearLayout.LayoutParams(
                sidebarWidth, ViewGroup.LayoutParams.MATCH_PARENT));

        pageArea = new LinearLayout(this);
        pageArea.setOrientation(LinearLayout.VERTICAL);
        pageArea.setPadding(dp(7), dp(4), dp(2), dp(4));

        layout.addView(pageArea, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        String title = section.equals("live") ? "LIVE TV"
                : section.equals("movies") ? "FILM" : "SERIE TV";

        TextView heading = text(title, 21);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(LIGHT_BLUE);
        pageArea.addView(heading);

        TextView categoryHeading =
                text(currentCategoryName, 14);
        categoryHeading.setTextColor(MUTED);
        pageArea.addView(categoryHeading);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(12));
        scroll.addView(content);

        pageArea.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (currentItems.length() > 0) {
            renderFilteredItems(section, token);
        } else {
            content.addView(text("Caricamento contenuti...", 15));
        }
    }

    private void loadSection(String section) {
        if (api == null) return;

        currentSection = section;
        currentCategoryId = "";
        currentCategoryName = "Tutti";
        currentItems = new JSONArray();
        currentCategories = new JSONArray();

        showSectionPage(section);
        int token = generation;

        executor.execute(() -> {
            try {
                JSONArray categories;
                JSONArray items;

                if (section.equals("live")) {
                    categories = api.getLiveCategories();
                    items = api.getLiveStreams();
                } else if (section.equals("movies")) {
                    categories = api.getVodCategories();
                    items = api.getVodStreams();
                } else {
                    categories = api.getSeriesCategories();
                    items = api.getSeries();
                }

                handler.post(() -> {
                    if (token != generation) return;

                    currentCategories = categories;
                    currentItems = items;

                    showSectionPage(section);
                });

            } catch (Exception e) {
                handler.post(() -> {
                    if (token != generation) return;
                    content.removeAllViews();
                    content.addView(text(
                            "Errore nel caricamento. Verifica il server.",
                            15));
                });
            }
        });
    }

    private void renderFilteredItems(String section, int token) {
        content.removeAllViews();

        List<JSONObject> filtered = new ArrayList<>();

        for (int i = 0; i < currentItems.length(); i++) {
            JSONObject item = currentItems.optJSONObject(i);
            if (item == null) continue;

            String categoryId = item.optString("category_id", "");

            if (currentCategoryId.isEmpty()
                    || currentCategoryId.equals(categoryId)) {
                filtered.add(item);
            }
        }

        TextView count = text(
                filtered.size() + " contenuti", 12);
        count.setTextColor(MUTED);
        content.addView(count);

        if (filtered.isEmpty()) {
            content.addView(text(
                    "Nessun contenuto in questa categoria.", 15));
            return;
        }

        JSONArray result = new JSONArray();
        for (JSONObject item : filtered) {
            result.put(item);
        }

        if (section.equals("live")) {
            addLiveRows(result);
        } else {
            addPosterGrid(result, section);
        }
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

        generation++;
        int token = generation;
        setupRoot();

        root.addView(button("← INDIETRO", () ->
                showSectionPage("series")));

        TextView heading = text(seriesName, 22);
        heading.setTextColor(LIGHT_BLUE);
        root.addView(heading);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

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

                        for (int i = 0; i < seasonEpisodes.length(); i++) {
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

                            String label = episodeName;
                            if (!episodeNumber.isEmpty()
                                    && !episodeNumber.equals("null")) {
                                label = "E" + episodeNumber
                                        + " - " + episodeName;
                            }

                            final String finalId = episodeId;
                            final String finalName = episodeName;
                            final String finalExtension = episodeExtension;
                            final String finalLabel = label;

                            Button episodeButton = button(
                                    "▶  " + finalLabel, () -> {
                                if (finalId.isEmpty()) {
                                    Toast.makeText(this,
                                            "ID episodio non disponibile",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    playStream("series", finalId,
                                            finalExtension, finalName);
                                }
                            });

                            content.addView(episodeButton,
                                    new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT));
                        }
                    }
                });

            } catch (Exception e) {
                handler.post(() -> {
                    if (token != generation) return;
                    content.removeAllViews();
                    content.addView(text(
                            "Errore nel caricamento della serie. Riprova.",
                            16));
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
    public void onBackPressed() {
        if (!currentSection.equals("home") && api != null) {
            showSectionPage(currentSection);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        generation++;
        executor.shutdownNow();
        imageExecutor.shutdownNow();
        super.onDestroy();
    }
}
