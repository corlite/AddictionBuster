package com.addictionbuster;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class UiKit {
    static final int COLOR_BACKGROUND = Color.rgb(248, 250, 252);
    static final int COLOR_SURFACE = Color.WHITE;
    static final int COLOR_TEXT = Color.rgb(15, 23, 42);
    static final int COLOR_MUTED = Color.rgb(100, 116, 139);
    static final int COLOR_BODY = Color.rgb(51, 65, 85);
    static final int COLOR_PRIMARY = Color.rgb(37, 99, 235);
    static final int COLOR_SUCCESS = Color.rgb(22, 163, 74);
    static final int COLOR_DANGER = Color.rgb(220, 38, 38);
    static final int COLOR_BORDER = Color.rgb(226, 232, 240);

    private UiKit() {
    }

    static ScrollView scrollScreen(Context context, LinearLayout content) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);
        scrollView.addView(content, matchWrap());
        return scrollView;
    }

    static LinearLayout screen(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 22), dp(context, 26), dp(context, 22), dp(context, 18));
        root.setBackgroundColor(COLOR_BACKGROUND);
        return root;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        card.setBackground(rounded(COLOR_SURFACE, COLOR_BORDER, dp(context, 8)));
        return card;
    }

    static TextView title(Context context, String value) {
        return text(context, value, 30, COLOR_TEXT, true);
    }

    static TextView subtitle(Context context, String value) {
        TextView view = text(context, value, 15, Color.rgb(71, 85, 105), false);
        view.setPadding(0, dp(context, 8), 0, dp(context, 18));
        return view;
    }

    static TextView sectionTitle(Context context, String value) {
        TextView view = text(context, value, 16, COLOR_TEXT, true);
        view.setPadding(0, 0, 0, dp(context, 8));
        return view;
    }

    static TextView body(Context context, String value) {
        return text(context, value, 14, COLOR_BODY, false);
    }

    static TextView hint(Context context, String value) {
        return text(context, value, 13, COLOR_MUTED, false);
    }

    static void addInfoRow(LinearLayout parent, String label, String value) {
        Context context = parent.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 4), 0, dp(context, 4));

        TextView labelView = text(context, label, 14, COLOR_MUTED, false);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = text(context, value, 15, COLOR_TEXT, true);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        parent.addView(row, matchWrap());
    }

    static Button entryButton(Context context, String title, String subtitle) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(title + "\n" + subtitle);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        button.setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11));
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(16);
        button.setBackground(rounded(COLOR_SURFACE, COLOR_BORDER, dp(context, 8)));
        return button;
    }

    static Button primaryButton(Context context, String label) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        button.setBackground(rounded(COLOR_PRIMARY, COLOR_PRIMARY, dp(context, 8)));
        return button;
    }

    static Button dangerButton(Context context, String label) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(COLOR_DANGER);
        button.setTextSize(16);
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        button.setBackground(rounded(COLOR_SURFACE, Color.rgb(254, 202, 202), dp(context, 8)));
        return button;
    }

    static TextView statusPill(Context context, String value, boolean ok) {
        int color = ok ? COLOR_SUCCESS : COLOR_DANGER;
        TextView pill = text(context, value, 13, color, true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
        int background = ok ? Color.rgb(240, 253, 244) : Color.rgb(254, 242, 242);
        int border = ok ? Color.rgb(187, 247, 208) : Color.rgb(254, 202, 202);
        pill.setBackground(rounded(background, border, dp(context, 999)));
        return pill;
    }

    static TextView text(Context context, String value, int sp, int color, boolean bold) {
        TextView textView = new TextView(context);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), Typeface.BOLD);
        }
        return textView;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    static LinearLayout.LayoutParams spaced(Context context, int top) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(context, top), 0, 0);
        return params;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, stroke);
        return drawable;
    }
}
