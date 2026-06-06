package com.addictionbuster;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NotificationAccessGuideActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.log(this, "permission", "notification access guide opened");
        setContentView(buildContent());
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(18));
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = text("开启后台媒体阻断", 28, Color.rgb(15, 23, 42), true);
        root.addView(title, matchWrap());

        TextView summary = text(
                "下一步会进入系统“通知使用权”页面。请只开启最上面的“授予通知使用权”。",
                16,
                Color.rgb(51, 65, 85),
                false
        );
        summary.setPadding(0, dp(12), 0, dp(18));
        root.addView(summary, matchWrap());

        root.addView(section(
                "我们需要它做什么",
                "只用于读取媒体会话的包名，并在被限制应用后台播放声音时尝试暂停。比如哔哩哔哩退到后台还在播放时，瘾头破坏器会调用系统媒体暂停。"
        ), matchWrap());

        root.addView(section(
                "不用额外开启什么",
                "不用单独开启实时、对话、通知、静音等内容权限。那些是 Android 对通知使用权的统一风险说明，不是本应用要读取的内容。"
        ), matchWrap());

        root.addView(section(
                "做不到的边界",
                "普通 APK 不能真正冻结或强制停止其他应用。这里能做的是后台媒体暂停；前台打开应用仍由无障碍拦截服务处理。"
        ), matchWrap());

        Button openSettingsButton = new Button(this);
        openSettingsButton.setText("去授予通知使用权");
        openSettingsButton.setAllCaps(false);
        openSettingsButton.setOnClickListener(v -> {
            DiagnosticLogger.log(this, "permission", "open notification listener settings");
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });
        root.addView(openSettingsButton, matchWrap());

        Button backButton = new Button(this);
        backButton.setText("先不开启");
        backButton.setAllCaps(false);
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton, matchWrap());

        TextView hint = text(
                "不开启这个权限也可以使用前台拦截，只是后台播放声音可能无法自动暂停。",
                14,
                Color.rgb(100, 116, 139),
                false
        );
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(18), 0, 0);
        root.addView(hint, matchWrap());

        return root;
    }

    private LinearLayout section(String heading, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(14));

        TextView headingView = text(heading, 17, Color.rgb(30, 64, 175), true);
        box.addView(headingView, matchWrap());

        TextView bodyView = text(body, 15, Color.rgb(51, 65, 85), false);
        bodyView.setPadding(0, dp(5), 0, 0);
        box.addView(bodyView, matchWrap());
        return box;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
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
