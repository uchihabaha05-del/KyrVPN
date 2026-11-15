package com.kyrvpn.app;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
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
    private GoBackend backend;
    private String pendingConfig = "";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Tunnel tunnel = new Tunnel() {
        @Override public String getName() { return "KyrVPN"; }
        @Override public void onStateChange(State newState) {
            runOnUiThread(() -> {
                if (status != null) {
                    status.setText(newState == State.UP
                        ? "Туннель WireGuard активен"
                        : "Туннель выключен");
                }
                setBadge(newState == State.UP ? "АКТИВЕН" : "ВЫКЛЮЧЕН",
                    newState == State.UP
                        ? Color.rgb(38, 91, 75)
                        : Color.rgb(66, 31, 50),
                    newState == State.UP
                        ? Color.rgb(133, 255, 205)
                        : Color.rgb(255, 157, 171));
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
        status.setText("Добавьте конфигурацию WireGuard");
        setBadge("ОЖИДАЕТ", Color.rgb(55, 48, 81), Color.rgb(204, 181, 255));
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

        TextView connect = text("ПОДКЛЮЧИТЬСЯ", 14, WHITE, true);
        connect.setGravity(Gravity.CENTER);
        connect.setLetterSpacing(0.08f);
        connect.setBackground(gradient(
            new int[]{Color.rgb(89, 75, 220), Color.rgb(139, 74, 235)}, dp(18)));
        connect.setElevation(dp(5));
        connect.setPadding(dp(16), dp(18), dp(16), dp(18));
        page.addView(connect, new LinearLayout.LayoutParams(-1, dp(58)));
        connect.setOnClickListener(v -> requestAndConnect());

        addSpace(page, 10);
        TextView disconnect = text("ОТКЛЮЧИТЬ VPN", 12, Color.rgb(209, 199, 255), true);
        disconnect.setGravity(Gravity.CENTER);
        disconnect.setBackground(roundRect(Color.rgb(27, 27, 50), dp(16),
            Color.rgb(79, 67, 126), dp(1)));
        disconnect.setPadding(dp(16), dp(15), dp(16), dp(15));
        page.addView(disconnect, new LinearLayout.LayoutParams(-1, dp(50)));
        disconnect.setOnClickListener(v -> disconnect());

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

        addSpace(page, 24);
        LinearLayout configHeader = new LinearLayout(this);
        configHeader.setGravity(Gravity.CENTER_VERTICAL);
        configHeader.addView(text("КОНФИГУРАЦИЯ", 11, MUTED, true),
            new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(configHeader);
        addSpace(page, 8);

        TextView importButton = text("＋  ИМПОРТИРОВАТЬ ФАЙЛ .CONF", 12, WHITE, true);
        importButton.setGravity(Gravity.CENTER);
        importButton.setBackground(roundRect(Color.rgb(31, 34, 58), dp(15),
            Color.rgb(66, 72, 112), dp(1)));
        importButton.setPadding(dp(14), dp(15), dp(14), dp(15));
        page.addView(importButton, new LinearLayout.LayoutParams(-1, dp(50)));
        importButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, CONFIG_FILE_REQUEST);
        });

        addSpace(page, 8);
        configToggle = text("Вставить конфигурацию вручную  ▾", 12,
            Color.rgb(187, 173, 255), true);
        configToggle.setPadding(dp(4), dp(9), dp(4), dp(9));
        page.addView(configToggle);
        configInput = new EditText(this);
        configInput.setTextColor(WHITE);
        configInput.setHintTextColor(Color.rgb(111, 119, 148));
        configInput.setHint("[Interface]\nPrivateKey = ...\nAddress = ...\n\n[Peer]\nPublicKey = ...\nEndpoint = ...");
        configInput.setGravity(Gravity.TOP | Gravity.START);
        configInput.setTextSize(12);
        configInput.setMinLines(7);
        configInput.setPadding(dp(13), dp(13), dp(13), dp(13));
        configInput.setBackground(roundRect(Color.rgb(25, 28, 47), dp(14),
            Color.rgb(60, 64, 95), dp(1)));
        configInput.setVisibility(View.GONE);
        page.addView(configInput, new LinearLayout.LayoutParams(-1, dp(205)));

        configToggle.setOnClickListener(v -> {
            boolean show = configInput.getVisibility() != View.VISIBLE;
            configInput.setVisibility(show ? View.VISIBLE : View.GONE);
            configToggle.setText(show ? "Скрыть ручной ввод  ▴" : "Вставить конфигурацию вручную  ▾");
        });

        addSpace(page, 8);
        TextView save = text("ПРОВЕРИТЬ КОНФИГУРАЦИЮ", 11,
            Color.rgb(210, 199, 255), true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(roundRect(Color.rgb(39, 34, 68), dp(14),
            Color.rgb(82, 67, 131), dp(1)));
        save.setPadding(dp(12), dp(13), dp(12), dp(13));
        page.addView(save, new LinearLayout.LayoutParams(-1, dp(46)));
        save.setOnClickListener(v -> saveConfig());

        addSpace(page, 18);
        TextView note = text("🔒  Конфигурация WireGuard обрабатывается на устройстве.", 11, MUTED, false);
        page.addView(note);
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

    private void saveConfig() {
        String value = configInput.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "Сначала импортируйте или вставьте конфигурацию", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            parseConfig(value);
            pendingConfig = value;
            status.setText("Конфигурация проверена. Можно подключаться.");
            setBadge("ГОТОВО", Color.rgb(38, 65, 83), Color.rgb(145, 211, 255));
            Toast.makeText(this, "Конфигурация WireGuard проверена", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка конфигурации: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestAndConnect() {
        String value = configInput.getText().toString().trim();
        if (value.isEmpty()) value = pendingConfig;
        if (value.isEmpty()) {
            Toast.makeText(this, "Сначала импортируйте файл .conf или вставьте конфигурацию", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            parseConfig(value);
        } catch (Exception e) {
            Toast.makeText(this, "Неверная конфигурация: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        pendingConfig = value;
        Intent permissionIntent = VpnService.prepare(this);
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, VPN_PERMISSION_REQUEST);
        } else {
            connectWithConfig(value);
        }
    }

    private void connectWithConfig(String value) {
        status.setText("Подключаем WireGuard…");
        setBadge("ПОДКЛЮЧЕНИЕ", Color.rgb(55, 48, 81), Color.rgb(204, 181, 255));
        executor.execute(() -> {
            try {
                Config config = parseConfig(value);
                backend.setState(tunnel, Tunnel.State.UP, config);
                runOnUiThread(() -> {
                    status.setText("Туннель WireGuard активен");
                    setBadge("АКТИВЕН", Color.rgb(38, 91, 75), Color.rgb(133, 255, 205));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Не удалось подключиться: " + e.getMessage());
                    setBadge("ОШИБКА", Color.rgb(75, 37, 45), Color.rgb(255, 157, 171));
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
                runOnUiThread(() -> {
                    status.setText("Туннель выключен");
                    setBadge("ВЫКЛЮЧЕН", Color.rgb(66, 31, 50), Color.rgb(255, 157, 171));
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Ошибка отключения: " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VPN_PERMISSION_REQUEST) {
            if (resultCode == RESULT_OK) {
                if (pendingConfig.isEmpty()) status.setText("Сначала добавьте конфигурацию");
                else connectWithConfig(pendingConfig);
            } else {
                status.setText("Разрешение VPN не предоставлено");
                setBadge("НЕТ ДОСТУПА", Color.rgb(66, 31, 50), Color.rgb(255, 157, 171));
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
        executor.shutdown();
        super.onDestroy();
    }
}
