package com.kyrvpn.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(9, 11, 24);
    private final int CARD = Color.rgb(20, 23, 43);
    private final int MUTED = Color.rgb(157, 166, 194);
    private final int WHITE = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(10, 13, 31), Color.rgb(18, 15, 43), BG}
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(18), dp(22), dp(26));
        scroll.addView(page);
        root.addView(scroll);
        setContentView(root);

        // Brand header
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
        TextView name = text("KyrVPN", 23, WHITE, true);
        TextView tagline = text("ТВОЯ ЗАЩИЩЁННАЯ СЕТЬ", 9, MUTED, true);
        brand.addView(name);
        brand.addView(tagline);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView beta = text("BETA", 10, Color.rgb(204, 181, 255), true);
        beta.setGravity(Gravity.CENTER);
        beta.setPadding(dp(13), dp(8), dp(13), dp(8));
        beta.setBackground(pill(Color.rgb(48, 35, 86), Color.rgb(116, 78, 204)));
        header.addView(beta);
        page.addView(header);

        addSpace(page, 34);
        TextView eyebrow = text("ИНТЕРНЕТ БЕЗ ГРАНИЦ", 11, Color.rgb(160, 139, 255), true);
        page.addView(eyebrow);
        addSpace(page, 9);

        TextView headline = text("Твой интернет.\nТвои правила.", 31, WHITE, true);
        headline.setLineSpacing(dp(1), 1f);
        page.addView(headline);
        addSpace(page, 9);

        TextView intro = text("KyrVPN готовится открыть для тебя безопасный и стабильный доступ к сети.", 14, MUTED, false);
        intro.setLineSpacing(dp(4), 1f);
        page.addView(intro);

        addSpace(page, 26);

        // Connection status card
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
        TextView state = text("Не подключено", 19, WHITE, true);
        statusTexts.addView(state);
        statusRow.addView(statusTexts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView dot = text("●  OFF", 10, Color.rgb(255, 157, 171), true);
        dot.setPadding(dp(10), dp(7), dp(10), dp(7));
        dot.setBackground(pill(Color.rgb(66, 31, 50), Color.TRANSPARENT));
        statusRow.addView(dot);
        statusCard.addView(statusRow);

        addSpace(statusCard, 16);
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(48, 51, 76));
        statusCard.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        addSpace(statusCard, 14);

        TextView explanation = text("Серверное подключение ещё настраивается. Мы не показываем фиктивный статус соединения.", 13, MUTED, false);
        explanation.setLineSpacing(dp(3), 1f);
        statusCard.addView(explanation);
        page.addView(statusCard);

        addSpace(page, 18);

        // Main action
        TextView connect = text("ПОДКЛЮЧИТЬСЯ", 14, WHITE, true);
        connect.setGravity(Gravity.CENTER);
        connect.setLetterSpacing(0.08f);
        connect.setBackground(gradient(
            new int[]{Color.rgb(89, 75, 220), Color.rgb(139, 74, 235)},
            dp(18)
        ));
        connect.setElevation(dp(5));
        connect.setPadding(dp(16), dp(19), dp(16), dp(19));
        page.addView(connect, new LinearLayout.LayoutParams(-1, dp(58)));
        connect.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("KyrVPN скоро запустится")
            .setMessage("Интерфейс уже обновлён, но настоящий туннель WireGuard ещё не интегрирован. Кнопка подключения станет активной после завершения настройки сервера.")
            .setPositiveButton("Понятно", null)
            .show());

        addSpace(page, 29);

        // Server card
        LinearLayout serverHeader = new LinearLayout(this);
        serverHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView serverTitle = text("СЕРВЕР", 11, MUTED, true);
        serverHeader.addView(serverTitle, new LinearLayout.LayoutParams(0, -2, 1));
        serverHeader.addView(text("ОСНОВНОЙ", 10, Color.rgb(164, 143, 255), true));
        page.addView(serverHeader);
        addSpace(page, 10);

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

        TextView pending = text("НАСТРОЙКА", 9, Color.rgb(255, 202, 131), true);
        pending.setPadding(dp(9), dp(7), dp(9), dp(7));
        pending.setBackground(pill(Color.rgb(67, 49, 34), Color.TRANSPARENT));
        serverCard.addView(pending);
        page.addView(serverCard);

        addSpace(page, 22);

        // Bottom reassurance
        LinearLayout note = new LinearLayout(this);
        note.setGravity(Gravity.CENTER_VERTICAL);
        note.setPadding(dp(4), dp(8), dp(4), dp(8));
        TextView lock = text("♢", 19, Color.rgb(143, 126, 255), true);
        note.addView(lock);
        TextView noteText = text("Безопасность прежде всего — подключение появится после проверки туннеля.", 11, MUTED, false);
        noteText.setPadding(dp(9), 0, 0, 0);
        note.addView(noteText, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(note);
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
}
