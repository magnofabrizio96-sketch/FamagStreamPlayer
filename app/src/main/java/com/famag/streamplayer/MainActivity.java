package com.famag.streamplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EditText serverInput, usernameInput, passwordInput;
    private LinearLayout root, listLayout;
    private XtreamApi api;
    private boolean connecting = false;
    private int pageGeneration = 0;
    private final Button[] loginHolder = new Button[1];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showLogin();
    }

    private void setupRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        root.setFocusableInTouchMode(true);
        setContentView(root);
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(8, 12, 8, 12);
        return t;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.LTGRAY);
        e.setPadding(12, 8, 12, 8);
        return e;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void showLogin() {
        setupRoot();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = text("FAMAG STREAM PLAYER", 25);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = text("Accedi con il tuo servizio IPTV", 16);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        serverInput = field("URL server, es. https://server.example:8080");
        usernameInput = field("Nome utente");
        passwordInput = field("Password");
        passwordInput.setInputType(129);

        root.addView(serverInput);
        root.addView(usernameInput);
        root.addView(passwordInput);

        Button login = button("ACCEDI", () -> login(loginHolder[0]));
        loginHolder[0] = login;
        root.addView(login);
    }

    private void login(Button login) {
        if (connecting) return;

        String server = serverInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Compila tutti i campi", Toast.LENGTH_SHORT).show();
            return;
        }

        connecting = true;
        login.setEnabled(false);

        executor.execute(() -> {
            try {
                XtreamApi candidate = new XtreamApi(server, username, password);
                candidate.login();

                mainHandler.post(() -> {
                    api = candidate;
                    connecting = false;
                    showHome();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    connecting = false;
                    login.setEnabled(true);
                    Toast.makeText(this,
                            "Accesso non riuscito. Controlla server e credenziali.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showHome() {
        pageGeneration++;
        setupRoot();

        root.addView(text("FAMAG STREAM PLAYER", 24));
        root.addView(text("Scegli una sezione", 16));
        root.addView(button("LIVE TV", () -> loadList("live")));
        root.addView(button("FILM", () -> loadList("movies")));
        root.addView(button("SERIE", () -> loadList("series")));
        root.addView(button("ESCI", () -> {
            api = null;
            showLogin();
        }));

        ScrollView scroll = new ScrollView(this);
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listLayout);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.requestFocus();
    }

    private void loadList(String section) {
        if (api == null) return;

        int generation = ++pageGeneration;
        listLayout.removeAllViews();
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
                    if (generation != pageGeneration || listLayout == null) return;

                    listLayout.removeAllViews();
                    String heading = section.equals("live") ? "Canali TV"
                            : section.equals("movies") ? "Film" : "Serie";
                    listLayout.addView(text(heading, 20));

                    if (items.length() == 0) {
                        listLayout.addView(text("Nessun elemento disponibile.", 16));
                        return;
                    }

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;

                        String name = item.optString("name", "Senza titolo");
                        String id = item.optString(
                                section.equals("series") ? "series_id" : "stream_id", "");
                        String extension = item.optString(
                                "container_extension",
                                section.equals("live") ? "ts" : "mp4");

                        Button entry = button(name, () -> {
                            if (section.equals("series")) {
                                Toast.makeText(this,
                                        "La riproduzione degli episodi sarà aggiunta in un prossimo passaggio.",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            String type = section.equals("live") ? "live" : "movie";
                            String url = api.buildStreamUrl(type, id, extension);

                            if (url.isEmpty()) {
                                Toast.makeText(this, "Indirizzo video non valido.",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            Intent intent = new Intent(this, PlayerActivity.class);
                            intent.putExtra("stream_url", url);
                            startActivity(intent);
                        });

                        entry.setAllCaps(false);
                        entry.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        listLayout.addView(entry,
                                new LinearLayout.LayoutParams(-1, -2));
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (generation != pageGeneration || listLayout == null) return;
                    listLayout.removeAllViews();
                    listLayout.addView(text(
                            "Impossibile caricare la lista. Riprova o controlla il server.",
                            16));
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
        passwordInput = field("Password");
        passwordInput.setInputType(129);
        root.addView(serverInput);
        root.addView(usernameInput);
        root.addView(passwordInput);
        Button login = button("ACCEDI", () -> login(loginHolder[0]));
        loginHolder[0] = login;
        root.addView(login);
    }

    private final Button[] loginHolder = new Button[1];

    private void login(Button login) {
        if (connecting) return;
        String server = serverInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Compila tutti i campi", Toast.LENGTH_SHORT).show();
            return;
        }
        connecting = true;
        login.setEnabled(false);
        executor.execute(() -> {
            try {
                XtreamApi candidate = new XtreamApi(server, username, password);
                candidate.login();
                mainHandler.post(() -> {
                    api = candidate;
                    connecting = false;
                    showHome();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    connecting = false;
                    login.setEnabled(true);
                    Toast.makeText(this, "Accesso non riuscito. Controlla server e credenziali.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showHome() {
        pageGeneration++;
        setupRoot();
        root.addView(text("FAMAG STREAM PLAYER", 24));
        root.addView(text("Scegli una sezione", 16));
        root.addView(button("LIVE TV", () -> loadList("live")));
        root.addView(button("FILM", () -> loadList("movies")));
        root.addView(button("SERIE", () -> loadList("series")));
        root.addView(button("ESCI", () -> { api = null; showLogin(); }));
        ScrollView scroll = new ScrollView(this);
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listLayout);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setFocusableInTouchMode(true);
        root.requestFocus();
    }

    private void loadList(String section) {
        if (api == null) return;
        int generation = ++pageGeneration;
        listLayout.removeAllViews();
        listLayout.addView(text("Caricamento...", 16));
        executor.execute(() -> {
            try {
                JSONArray items;
                if (section.equals("live")) items = api.getLiveStreams();
                else if (section.equals("movies")) items = api.getVodStreams();
                else items = api.getSeries();
                mainHandler.post(() -> {
                    if (generation != pageGeneration || listLayout == null) return;
                    listLayout.removeAllViews();
                    listLayout.addView(text(section.equals("live") ? "Canali TV" : section.equals("movies") ? "Film" : "Serie", 20));
                    if (items.length() == 0) {
                        listLayout.addView(text("Nessun elemento disponibile.", 16));
                        return;
                    }
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;
                        String name = item.optString("name", "Senza titolo");
String id = item.optString(
        section.equals("series") ? "series_id" : "stream_id", "");
String extension = item.optString(
        "container_extension", section.equals("live") ? "ts" : "mp4");

Button entry = button(name, () -> {
    if (section.equals("series")) {
        Toast.makeText(this,
                "La riproduzione degli episodi sarà aggiunta nel prossimo passaggio.",
                Toast.LENGTH_SHORT).show();
        return;
    }

    String type = section.equals("live") ? "live" : "movie";
    String url = api.buildStreamUrl(type, id, extension);

    if (url.isEmpty()) {
        Toast.makeText(this, "Indirizzo video non valido.",
                Toast.LENGTH_SHORT).show();
        return;
    }

    Intent intent = new Intent(this, PlayerActivity.class);
    intent.putExtra("stream_url", url);
    startActivity(intent);
});

entry.setAllCaps(false);
entry.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
listLayout.addView(entry, new LinearLayout.LayoutParams(-1, -2));
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (generation != pageGeneration || listLayout == null) return;
                    listLayout.removeAllViews();
                    listLayout.addView(text("Impossibile caricare la lista. Riprova o controlla il server.", 16));
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
