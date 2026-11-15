package com.kyrvpn.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.text.InputType;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.crypto.Key;
import com.wireguard.config.Config;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int VPN_PERMISSION_REQUEST = 701;
    private static final int CONFIG_FILE_REQUEST = 702;

    private final int BG = Color.rgb(9, 11, 24);
    private final int CARD = Color.rgb(20, 23, 43);
    private final int MUTED = Color.rgb(157, 166, 194);
    private final int WHITE = Color.WHITE;

    private EditText configInput;
    private TextView status;
    private TextView statusBadge;
    private TextView configToggle;
    private TextView connectButton;
    private boolean tunnelUp = false;
    private boolean connecting = false;
    private Animation pulseAnimation;
    private GoBackend backend;
    private String pendingConfig = "";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable handshakeCheck;
    private long connectionStartedAt;
    private static final long HANDSHAKE_TIMEOUT_MS = 25000L;
    private static final String CONFIG_KEY_ALIAS = "kyrvpn_config_aes";
    private static final String CONFIG_PREFS = "kyrvpn_secure_config";

    private final Tunnel tunnel = new Tunnel() {
        @Override public String getName() { return "KyrVPN"; }
        @Override public void onStateChange(State newState) {
            runOnUiThread(() -> {
                tunnelUp = newState == State.UP;
                if (!tunnelUp) {
                    connecting = false;
                    if (handshakeCheck != null) mainHandler.removeCallbacks(handshakeCheck);
                    if (status != null) status.setText("Туннель выключен");
                    setBadge("ВЫКЛЮЧЕН", Color.rgb(66, 31, 50), Color.rgb(255, 157, 171));
                } else if (!connecting && status != null) {
                    status.setText("Туннель включён, проверяем связь с сервером");
                }
                updateConnectButton();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        backend = new GoBackend(getApplicationContext());
        buildScreen();
        String savedConfig = loadEncryptedConfig();
        if (!savedConfig.isEmpty()) {
            pendingConfig = savedConfig;
            configInput.setText(savedConfig);
            status.setText("Защищённая конфигурация сохранена на устройстве");
            setBadge("ГОТОВО", Color.rgb(38, 65, 83), Color.rgb(145, 211, 255));
        } else {
            status.setText("Нужен существующий ключ WireGuard");
            setBadge("НУЖЕН КЛЮЧ", Color.rgb(55, 48, 81), Color.rgb(204, 181, 255));
            mainHandler.post(this::showPrivateKeyDialog);
        }
    }

    private void buildScreen() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(10, 13, 31), Color.rgb(18, 15, 43), BG}
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(18), dp(22), dp(26));
        scroll.addView(page);
        frame.addView(scroll);
        setContentView(frame);

        // KyrVPN brand
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.kyrvpn_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable logoShape = new GradientDrawable();
        logoShape.setColor(Color.BLACK);
        logoShape.setCornerRadius(dp(17));
        logo.setBackground(logoShape);
        logo.setClipToOutline(true);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12), 0, 0, 0);
        brand.addView(text("KyrVPN", 23, WHITE, true));
        brand.addView(text("ТВОЯ ЗАЩИЩЁННАЯ СЕТЬ", 9, MUTED, true));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView beta = text("BETA", 10, Color.rgb(204, 181, 255), true);
        beta.setGravity(Gravity.CENTER);
        beta.setPadding(dp(13), dp(8), dp(13), dp(8));
        beta.setBackground(pill(Color.rgb(48, 35, 86), Color.rgb(116, 78, 204)));
        header.addView(beta);
        page.addView(header);

        addSpace(page, 30);
        page.addView(text("ИНТЕРНЕТ БЕЗ ГРАНИЦ", 11, Color.rgb(160, 139, 255), true));
        addSpace(page, 9);
        TextView headline = text("Твой интернет.\nТвои правила.", 31, WHITE, true);
        headline.setLineSpacing(dp(1), 1f);
        page.addView(headline);
        addSpace(page, 9);
        TextView intro = text("Подключайся к защищённой сети одним касанием.", 14, MUTED, false);
        intro.setLineSpacing(dp(4), 1f);
        page.addView(intro);

        addSpace(page, 25);

        // Live WireGuard status
        LinearLayout statusCard = card();
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView shield = text("◈", 25, Color.rgb(178, 143, 255), true);
        shield.setGravity(Gravity.CENTER);
        shield.setBackground(pill(Color.rgb(47, 34, 83), Color.TRANSPARENT));
        statusRow.addView(shield, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout statusTexts = new LinearLayout(this);
        statusTexts.setOrientation(LinearLayout.VERTICAL);
        statusTexts.setPadding(dp(12), 0, 0, 0);
        statusTexts.addView(text("СОСТОЯНИЕ VPN", 10, MUTED, true));
        status = text("Отключено", 18, WHITE, true);
        statusTexts.addView(status);
        statusRow.addView(statusTexts, new LinearLayout.LayoutParams(0, -2, 1));

        statusBadge = text("ОЖИДАЕТ", 9, Color.rgb(204, 181, 255), true);
        statusBadge.setPadding(dp(10), dp(7), dp(10), dp(7));
        statusBadge.setBackground(pill(Color.rgb(55, 48, 81), Color.TRANSPARENT));
        statusRow.addView(statusBadge);
        statusCard.addView(statusRow);

        addSpace(statusCard, 15);
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(48, 51, 76));
        statusCard.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        addSpace(statusCard, 13);
        TextView statusHint = text("Статус обновляется по состоянию настоящего WireGuard-туннеля.", 12, MUTED, false);
        statusHint.setLineSpacing(dp(3), 1f);
        statusCard.addView(statusHint);
        page.addView(statusCard);

        addSpace(page, 17);

        // One large circular connect/disconnect control
        LinearLayout connectArea = new LinearLayout(this);
        connectArea.setGravity(Gravity.CENTER);
        connectArea.setOrientation(LinearLayout.VERTICAL);
        connectButton = text("⏻\nПОДКЛЮЧИТЬ", 17, WHITE, true);
        connectButton.setGravity(Gravity.CENTER);
        connectButton.setLineSpacing(dp(5), 1f);
        connectButton.setBackground(circleGradient(new int[]{
            Color.rgb(91, 75, 226), Color.rgb(144, 71, 239)}));
        connectButton.setElevation(dp(18));
        connectArea.addView(connectButton, new LinearLayout.LayoutParams(dp(218), dp(218)));
        page.addView(connectArea, new LinearLayout.LayoutParams(-1, dp(238)));
        connectButton.setOnClickListener(v -> {
            if (connecting) return;
            if (tunnelUp) disconnect();
            else requestAndConnect();
        });
        updateConnectButton();

        addSpace(page, 26);

        // Server details
        LinearLayout serverHeader = new LinearLayout(this);
        serverHeader.setGravity(Gravity.CENTER_VERTICAL);
        serverHeader.addView(text("СЕРВЕР", 11, MUTED, true),
            new LinearLayout.LayoutParams(0, -2, 1));
        serverHeader.addView(text("ОСНОВНОЙ", 10, Color.rgb(164, 143, 255), true));
        page.addView(serverHeader);
        addSpace(page, 9);

        LinearLayout serverCard = card();
        serverCard.setGravity(Gravity.CENTER_VERTICAL);
        serverCard.setOrientation(LinearLayout.HORIZONTAL);
        serverCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        TextView globe = text("◎", 27, Color.rgb(110, 185, 255), true);
        globe.setGravity(Gravity.CENTER);
        globe.setBackground(pill(Color.rgb(26, 52, 83), Color.TRANSPARENT));
        serverCard.addView(globe, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout serverInfo = new LinearLayout(this);
        serverInfo.setOrientation(LinearLayout.VERTICAL);
        serverInfo.setPadding(dp(12), 0, 0, 0);
        serverInfo.addView(text("Natbox • Основной", 15, WHITE, true));
        addSpace(serverInfo, 4);
        serverInfo.addView(text("92.242.190.191  ·  UDP 13751", 11, MUTED, false));
        serverCard.addView(serverInfo, new LinearLayout.LayoutParams(0, -2, 1));
        TextView serverState = text("НАСТРОЙКА", 9, Color.rgb(255, 202, 131), true);
        serverState.setPadding(dp(9), dp(7), dp(9), dp(7));
        serverState.setBackground(pill(Color.rgb(67, 49, 34), Color.TRANSPARENT));
        serverCard.addView(serverState);
        page.addView(serverCard);

        configInput = new EditText(this);
        configInput.setVisibility(View.GONE);
        TextView note = text("🔒  Ключ вводится один раз и хранится на устройстве в зашифрованном виде.", 11, MUTED, false);
        page.addView(note);
    }

    private GradientDrawable circleGradient(int[] colors) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setShape(GradientDrawable.OVAL);
        d.setStroke(dp(2), Color.rgb(180, 155, 255));
        return d;
    }

    private void updateConnectButton() {
        if (connectButton == null) return;
        if (pulseAnimation != null) {
            connectButton.clearAnimation();
            pulseAnimation = null;
        }
        if (connecting) {
            connectButton.setText("◌\nПОДКЛЮЧЕНИЕ…");
            connectButton.setBackground(circleGradient(new int[]{
                Color.rgb(112, 76, 225), Color.rgb(174, 74, 235)}));
            AlphaAnimation pulse = new AlphaAnimation(0.62f, 1f);
            pulse.setDuration(750);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(Animation.INFINITE);
            connectButton.startAnimation(pulse);
            pulseAnimation = pulse;
        } else if (tunnelUp) {
            connectButton.setText("⏻\nОТКЛЮЧИТЬ");
            connectButton.setBackground(circleGradient(new int[]{
                Color.rgb(24, 126, 100), Color.rgb(42, 180, 137)}));
            connectButton.setAlpha(1f);
        } else {
            connectButton.setText("⏻\nПОДКЛЮЧИТЬ");
            connectButton.setBackground(circleGradient(new int[]{
                Color.rgb(91, 75, 226), Color.rgb(144, 71, 239)}));
            connectButton.setAlpha(1f);
        }
    }

    private void setBadge(String label, int background, int foreground) {
        if (statusBadge == null) return;
        statusBadge.setText(label);
        statusBadge.setTextColor(foreground);
        statusBadge.setBackground(pill(background, Color.TRANSPARENT));
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setBackground(roundRect(CARD, dp(22), Color.rgb(43, 47, 76), dp(1)));
        return box;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable roundRect(int color, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    private GradientDrawable pill(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(40));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
        return d;
    }

    private GradientDrawable gradient(int[] colors, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setCornerRadius(radius);
        return d;
    }

    private void addSpace(LinearLayout parent, int height) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Config parseConfig(String value) throws Exception {
        return Config.parse(new BufferedReader(new StringReader(value)));
    }

    private SecretKey getOrCreateConfigKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(CONFIG_KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry)
                keyStore.getEntry(CONFIG_KEY_ALIAS, null);
            return entry.getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(CONFIG_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    private void storeEncryptedConfig(String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateConfigKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String stored = Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            getSharedPreferences(CONFIG_PREFS, MODE_PRIVATE).edit()
                .putString("config", stored).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось безопасно сохранить конфигурацию", Toast.LENGTH_LONG).show();
        }
    }

    private String loadEncryptedConfig() {
        String stored = getSharedPreferences(CONFIG_PREFS, MODE_PRIVATE)
            .getString("config", "");
        if (stored == null || stored.isEmpty()) return "";
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) throw new IllegalStateException("Повреждённая конфигурация");
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateConfigKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            getSharedPreferences(CONFIG_PREFS, MODE_PRIVATE).edit().remove("config").apply();
            return "";
        }
    }

    private void saveConfig() {
        String value = configInput.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "Сначала импортируйте или вставьте конфигурацию", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            parseConfig(value);
            pendingConfig = value;
            storeEncryptedConfig(value);
            status.setText("Конфигурация проверена и сохранена защищённо на устройстве.");
            setBadge("ГОТОВО", Color.rgb(38, 65, 83), Color.rgb(145, 211, 255));
            Toast.makeText(this, "Конфигурация WireGuard проверена", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка конфигурации: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    private void showPrivateKeyDialog() {
        if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
        EditText keyField = new EditText(this);
        keyField.setSingleLine(true);
        keyField.setTextSize(14);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setHint("Вставь существующий приватный ключ");
        keyField.setPadding(dp(14), dp(12), dp(14), dp(12));
        keyField.setBackground(roundRect(Color.rgb(25, 28, 47), dp(12),
            Color.rgb(60, 64, 95), dp(1)));
        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setPadding(dp(22), dp(8), dp(22), 0);
        dialogContent.addView(keyField, new LinearLayout.LayoutParams(-1, dp(52)));
        new AlertDialog.Builder(this)
            .setTitle("Настройка KyrVPN")
            .setMessage("Введи приватный ключ, соответствующий уже зарегистрированному на сервере ключу. Он не отправляется в чат и сохраняется только на этом устройстве.")
            .setView(dialogContent)
            .setNegativeButton("Позже", (dialog, which) -> {
                status.setText("Ожидается ввод существующего ключа");
                setBadge("НУЖЕН КЛЮЧ", Color.rgb(55, 48, 81), Color.rgb(204, 181, 255));
            })
            .setPositiveButton("Сохранить", (dialog, which) -> {
                String privateKey = keyField.getText().toString().trim().replaceAll("\\s+", "");
                if (privateKey.isEmpty()) {
                    Toast.makeText(this, "Введи приватный ключ", Toast.LENGTH_LONG).show();
                    return;
                }
                String generatedConfig =
                    "[Interface]\n" +
                    "PrivateKey = " + privateKey + "\n" +
                    "Address = 10.66.66.2/32\n" +
                    "DNS = 1.1.1.1\n\n" +
                    "[Peer]\n" +
                    "PublicKey = 8jbM003KrMpEnKRt1vNjsqgK74u3qIfI0b15qw0sVlk=\n" +
                    "AllowedIPs = 0.0.0.0/0\n" +
                    "Endpoint = 92.242.190.191:13751\n" +
                    "PersistentKeepalive = 25\n";
                try {
                    parseConfig(generatedConfig);
                    pendingConfig = generatedConfig;
                    configInput.setText(generatedConfig);
                    storeEncryptedConfig(generatedConfig);
                    status.setText("Ключ сохранён защищённо. Можно подключаться.");
                    setBadge("ГОТОВО", Color.rgb(38, 65, 83), Color.rgb(145, 211, 255));
                    Toast.makeText(this, "Настройка завершена", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    status.setText("Не удалось проверить ключ WireGuard");
                    setBadge("ОШИБКА КЛЮЧА", Color.rgb(75, 37, 45), Color.rgb(255, 157, 171));
                    Toast.makeText(this, "Проверь приватный ключ WireGuard", Toast.LENGTH_LONG).show();
                }
            })
            .show();
    }

    private void requestAndConnect() {
        String value = configInput.getText().toString().trim();
        if (value.isEmpty()) value = pendingConfig;
        if (value.isEmpty()) {
            showPrivateKeyDialog();
            return;
        }
        try {
            parseConfig(value);
        } catch (Exception e) {
            Toast.makeText(this, "Неверная конфигурация: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        pendingConfig = value;
        storeEncryptedConfig(value);
        connecting = true;
        status.setText("Ожидаем разрешение и подключение…");
        setBadge("ОЖИДАНИЕ", Color.rgb(55, 48, 81), Color.rgb(204, 181, 255));
        updateConnectButton();
        Intent permissionIntent = VpnService.prepare(this);
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, VPN_PERMISSION_REQUEST);
        } else {
            connectWithConfig(value);
        }
    }

    private void connectWithConfig(String value) {
        connecting = true;
        updateConnectButton();
        status.setText("Ожидаем подключение WireGuard…");
        setBadge("ПОДКЛЮЧЕНИЕ", Color.rgb(55, 48, 81), Color.rgb(204, 181, 255));
        executor.execute(() -> {
            try {
                Config config = parseConfig(value);
                Tunnel.State state = backend.setState(tunnel, Tunnel.State.UP, config);
                if (state != Tunnel.State.UP || backend.getState(tunnel) != Tunnel.State.UP) {
                    throw new IllegalStateException("Android не подтвердил запуск VPN-туннеля");
                }
                runOnUiThread(() -> {
                    tunnelUp = true;
                    connecting = true;
                    status.setText("Туннель включён. Проверяем handshake с сервером…");
                    setBadge("ПРОВЕРКА СЕРВЕРА", Color.rgb(55, 48, 81), Color.rgb(204, 181, 255));
                    updateConnectButton();
                    startHandshakeCheck();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    connecting = false;
                    tunnelUp = false;
                    status.setText("Не удалось подключиться: " + e.getMessage());
                    setBadge("ОШИБКА", Color.rgb(75, 37, 45), Color.rgb(255, 157, 171));
                    updateConnectButton();
                    Toast.makeText(this, "Ошибка WireGuard: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void startHandshakeCheck() {
        if (handshakeCheck != null) mainHandler.removeCallbacks(handshakeCheck);
        connectionStartedAt = SystemClock.elapsedRealtime();
        handshakeCheck = new Runnable() {
            @Override public void run() {
                if (!tunnelUp) return;
                executor.execute(() -> {
                    boolean handshakeFound = false;
                    try {
                        Statistics statistics = backend.getStatistics(tunnel);
                        for (Key peer : statistics.peers()) {
                            Statistics.PeerStats peerStats = statistics.peer(peer);
                            if (peerStats != null && peerStats.latestHandshakeEpochMillis() > 0) {
                                handshakeFound = true;
                                break;
                            }
                        }
                    } catch (Exception ignored) { }
                    final boolean connectedToServer = handshakeFound;
                    runOnUiThread(() -> {
                        if (!tunnelUp) return;
                        if (connectedToServer) {
                            connecting = false;
                            status.setText("Защищённое соединение с сервером установлено");
                            setBadge("СЕРВЕР НА СВЯЗИ", Color.rgb(25, 75, 65), Color.rgb(133, 255, 205));
                            updateConnectButton();
                        } else if (SystemClock.elapsedRealtime() - connectionStartedAt >= HANDSHAKE_TIMEOUT_MS) {
                            connecting = false;
                            status.setText("Туннель включён, но сервер не ответил. Проверьте ключи, Endpoint и UDP-порт.");
                            setBadge("НЕТ HANDSHAKE", Color.rgb(75, 37, 45), Color.rgb(255, 157, 171));
                            updateConnectButton();
                        } else {
                            mainHandler.postDelayed(this, 1000L);
                        }
                    });
                });
            }
        };
        mainHandler.post(handshakeCheck);
    }

    private void disconnect() {
        if (handshakeCheck != null) mainHandler.removeCallbacks(handshakeCheck);
        connecting = true;
        status.setText("Отключаем…");
        updateConnectButton();
        executor.execute(() -> {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null);
                runOnUiThread(() -> {
                    tunnelUp = false;
                    connecting = false;
                    status.setText("Туннель выключен");
                    setBadge("ВЫКЛЮЧЕН", Color.rgb(66, 31, 50), Color.rgb(255, 157, 171));
                    updateConnectButton();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    connecting = false;
                    status.setText("Ошибка отключения: " + e.getMessage());
                    updateConnectButton();
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VPN_PERMISSION_REQUEST) {
            if (resultCode == RESULT_OK) {
                if (pendingConfig.isEmpty()) {
                    connecting = false;
                    status.setText("Сначала добавьте конфигурацию");
                    updateConnectButton();
                } else connectWithConfig(pendingConfig);
            } else {
                connecting = false;
                status.setText("Разрешение VPN не предоставлено");
                setBadge("НЕТ ДОСТУПА", Color.rgb(66, 31, 50), Color.rgb(255, 157, 171));
                updateConnectButton();
            }
            return;
        }

        if (requestCode == CONFIG_FILE_REQUEST && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            try (InputStream in = getContentResolver().openInputStream(data.getData());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) throw new IllegalStateException("Не удалось открыть файл");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                String value = new String(out.toByteArray(), StandardCharsets.UTF_8);
                configInput.setText(value);
                configInput.setVisibility(View.VISIBLE);
                configToggle.setText("Скрыть ручной ввод  ▴");
                saveConfig();
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось импортировать файл: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (handshakeCheck != null) mainHandler.removeCallbacks(handshakeCheck);
        executor.shutdown();
        super.onDestroy();
    }
}
