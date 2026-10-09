package com.famag.streamplayer;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText serverInput, usernameInput, passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setBackgroundColor(Color.rgb(18, 18, 18));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(28, 40, 28, 24);
        layout.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView title = new TextView(this);
        title.setText("FAMAG STREAM PLAYER");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        layout.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Accedi con il tuo servizio IPTV");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.setMargins(0, 12, 0, 24);
        layout.addView(subtitle, subtitleParams);

        serverInput = field("URL server (es. https://server.example:8080)");
        usernameInput = field("Nome utente");
        passwordInput = field("Password");
        passwordInput.setInputType(129);
        layout.addView(serverInput);
        layout.addView(usernameInput);
        layout.addView(passwordInput);

        Button login = new Button(this);
        login.setText("ACCEDI");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.setMargins(0, 18, 0, 0);
        layout.addView(login, buttonParams);

        login.setOnClickListener(v -> {
            if (serverInput.getText().toString().trim().isEmpty()
                    || usernameInput.getText().toString().trim().isEmpty()
                    || passwordInput.getText().toString().isEmpty()) {
                Toast.makeText(this, "Compila tutti i campi", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Schermata pronta. Il collegamento al server sarà aggiunto nel prossimo passaggio.", Toast.LENGTH_LONG).show();
        });

        setContentView(layout);
    }

    private EditText field(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE);
        input.setPadding(18, 10, 18, 10);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(0, 200, 83)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 7, 0, 7);
        input.setLayoutParams(params);
        return input;
    }
}
