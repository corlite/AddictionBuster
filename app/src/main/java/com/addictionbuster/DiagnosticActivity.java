package com.addictionbuster;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class DiagnosticActivity extends Activity {
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "diagnostic", "diagnostic screen opened");
        setContentView(buildContent());
        refreshLog();
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(14));
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = text("诊断日志", 26, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView hint = text("复现一次无法拦截的问题后，复制或分享这里的最后几十行。重点看 service、event、challenge 这些行。", 14, Color.rgb(71, 85, 105), false);
        hint.setPadding(0, dp(6), 0, dp(10));
        root.addView(hint, matchWrap());

        Button refreshButton = button("刷新日志");
        refreshButton.setOnClickListener(v -> refreshLog());
        root.addView(refreshButton, matchWrap());

        Button copyButton = button("复制日志");
        copyButton.setOnClickListener(v -> copyLog());
        root.addView(copyButton, matchWrap());

        Button shareButton = button("分享日志");
        shareButton.setOnClickListener(v -> shareLog());
        root.addView(shareButton, matchWrap());

        Button clearButton = button("清空日志");
        clearButton.setOnClickListener(v -> clearLog());
        root.addView(clearButton, matchWrap());

        ScrollView scrollView = new ScrollView(this);
        logView = text("", 12, Color.rgb(15, 23, 42), false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(12), 0, dp(12));
        scrollView.addView(logView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        return root;
    }

    private void refreshLog() {
        if (logView != null) {
            logView.setText(DiagnosticLogger.read(this));
        }
    }

    private void copyLog() {
        String log = DiagnosticLogger.read(this);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("AddictionBuster diagnostic log", log));
            Toast.makeText(this, "诊断日志已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareLog() {
        String log = DiagnosticLogger.read(this);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "瘾头破坏器诊断日志");
        send.putExtra(Intent.EXTRA_TEXT, log);
        startActivity(Intent.createChooser(send, "分享诊断日志"));
    }

    private void clearLog() {
        DiagnosticLogger.clear(this);
        refreshLog();
        Toast.makeText(this, "诊断日志已清空", Toast.LENGTH_SHORT).show();
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), Typeface.BOLD);
        }
        return textView;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
