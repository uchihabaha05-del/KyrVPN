package com.kyrvpn.app;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int VPN_PERMISSION_REQUEST = 701;
    private static final int CONFIG_FILE_REQUEST = 702;
    private static final String PREFS = "kyrvpn_private";
    private static final String CONFIG_KEY = "wireguard_config";

    private EditText configInput;
    private TextView status;
    private GoBackend backend;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Tunnel tunnel = new Tunnel() {
        @Override public String getName() { return "KyrVPN"; }
        @Override public void onStateChange(State newState) {
            runOnUiThread(() -> status.setText(
                    newState == State.UP ? "Туннель включён" : "Туннель выключен"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new GoBackend(getApplicationContext());
        buildScreen();
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CONFIG_KEY, "");
        configInput.setText(saved);
        status.setText(saved.isEmpty()
                ? "Добавьте конфигурацию WireGuard"
                : "Конфигурация сохранена. Можно подключаться.");
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(16, 18, 26));

        TextView title = new TextView(this);
        title.setText("🛡 KyrVPN");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        status = new TextView(this);
        status.setTextColor(Color.rgb(190, 205, 220));
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 22, 0, 22);
        root.addView(status, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("Вставьте конфигурацию WireGuard (.conf). Приватный ключ хранится только на этом телефоне.");
        hint.setTextColor(Color.LTGRAY);
        hint.setTextSize(14);
        root.addView(hint, matchWrap());

        configInput = new EditText(this);
        configInput.setTextColor(Color.WHITE);
        configInput.setHintTextColor(Color.GRAY);
        configInput.setHint("[Interface]\nPrivateKey = ...\nAddress = ...\n\n[Peer]\nPublicKey = ...\nEndpoint = ...");
        configInput.setGravity(Gravity.TOP | Gravity.START);
        configInput.setTextSize(13);
        configInput.setMinLines(8);
        configInput.setPadding(14, 14, 14, 14);
        configInput.setBackgroundColor(Color.rgb(34, 38, 50));
        root.addView(configInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));

        Button importButton = button("ИМПОРТИРОВАТЬ .CONF");
        importButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, CONFIG_FILE_REQUEST);
        });
        root.addView(importButton, matchWrap());

        Button saveButton = button("СОХРАНИТЬ КОНФИГУРАЦИЮ");
        saveButton.setOnClickListener(v -> saveConfig());
        root.addView(saveButton, matchWrap());

        Button connectButton = button("ПОДКЛЮЧИТЬ");
        connectButton.setOnClickListener(v -> requestAndConnect());
        root.addView(connectButton, matchWrap());

        Button disconnectButton = button("ОТКЛЮЧИТЬ");
        disconnectButton.setOnClickListener(v -> disconnect());
        root.addView(disconnectButton, matchWrap());

        scroll.addView(root);
        setContentView(scroll);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(12);
        return p;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void saveConfig() {
        String value = configInput.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "Сначала добавьте конфигурацию", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Config.parse(new BufferedReader(new StringReader(value)));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CONFIG_KEY, value).apply();
            status.setText("Конфигурация проверена и сохранена на телефоне");
            Toast.makeText(this, "Конфигурация сохранена", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка конфигурации: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestAndConnect() {
        String value = configInput.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "Сначала импортируйте или вставьте .conf", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Config.parse(new BufferedReader(new StringReader(value)));
        } catch (Exception e) {
            Toast.makeText(this, "Неверная конфигурация: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CONFIG_KEY, value).apply();
        Intent permissionIntent = VpnService.prepare(this);
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, VPN_PERMISSION_REQUEST);
        } else {
            connectWithConfig(value);
        }
    }

    private void connectWithConfig(String value) {
        status.setText("Подключаем WireGuard…");
        executor.execute(() -> {
            try {
                Config config = Config.parse(new BufferedReader(new StringReader(value)));
                backend.setState(tunnel, Tunnel.State.UP, config);
                runOnUiThread(() -> status.setText("Туннель WireGuard включён. Проверяйте доступ к интернету."));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Не удалось подключиться: " + e.getMessage());
                    Toast.makeText(this, "Ошибка WireGuard: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void disconnect() {
        status.setText("Отключаем…");
        executor.execute(() -> {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null);
                runOnUiThread(() -> status.setText("Туннель выключен"));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Ошибка отключения: " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == VPN_PERMISSION_REQUEST) {
            String value = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CONFIG_KEY, "");
            if (value.isEmpty()) {
                status.setText("Сначала добавьте конфигурацию");
            } else {
                connectWithConfig(value);
            }
        } else if (requestCode == CONFIG_FILE_REQUEST) {
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                if (in == null) throw new IllegalStateException("Не удалось открыть файл");
                byte[] bytes = new byte[in.available()];
                int read = in.read(bytes);
                String value = new String(bytes, 0, Math.max(0, read), java.nio.charset.StandardCharsets.UTF_8);
                configInput.setText(value);
                saveConfig();
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось импортировать файл: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Keep the executor alive until an in-flight tunnel operation completes.
    }
}
