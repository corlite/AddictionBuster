package com.addictionbuster;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MascotUi {
    private MascotUi() {
    }

    public static LinearLayout compactStatus(Context context) {
        MascotProfile profile = MascotStore.getProfile(context);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, UiKit.dp(context, 10));

        row.addView(iconOrBadge(context, profile, false), new LinearLayout.LayoutParams(
                UiKit.dp(context, 48),
                UiKit.dp(context, 48)
        ));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(UiKit.dp(context, 10), 0, 0, 0);
        copy.addView(UiKit.text(
                context,
                profile == MascotProfile.NONE ? context.getString(R.string.mascot_disabled) : profile.displayName(context),
                15,
                UiKit.COLOR_TEXT,
                true
        ), UiKit.matchWrap());
        copy.addView(UiKit.hint(
                context,
                profile == MascotProfile.NONE ? context.getString(R.string.mascot_setup_hint) : profile.description(context)
        ), UiKit.matchWrap());
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    public static LinearLayout overlayHeader(Context context) {
        MascotProfile profile = MascotStore.getProfile(context);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, 0, 0, UiKit.dp(context, 16));
        if (profile == MascotProfile.NONE) {
            return box;
        }
        box.addView(iconOrBadge(context, profile, true), new LinearLayout.LayoutParams(
                UiKit.dp(context, 64),
                UiKit.dp(context, 64)
        ));
        TextView label = new TextView(context);
        label.setText(profile.displayName(context));
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, UiKit.dp(context, 6), 0, 0);
        box.addView(label, UiKit.matchWrap());
        return box;
    }

    public static LinearLayout emptyState(Context context, String message) {
        LinearLayout box = UiKit.card(context);
        box.setGravity(Gravity.CENTER);
        box.addView(iconOrBadge(context, MascotStore.getProfile(context), false), new LinearLayout.LayoutParams(
                UiKit.dp(context, 56),
                UiKit.dp(context, 56)
        ));
        TextView text = UiKit.hint(context, message);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, UiKit.dp(context, 10), 0, 0);
        box.addView(text, UiKit.matchWrap());
        return box;
    }

    private static android.view.View iconOrBadge(Context context, MascotProfile profile, boolean dark) {
        String uri = profile == MascotProfile.NONE ? "" : MascotStore.getIconUri(context, profile);
        if (!uri.isEmpty()) {
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                image.setImageURI(Uri.parse(uri));
                image.setBackground(circle(dark ? Color.rgb(30, 41, 59) : Color.rgb(239, 246, 255)));
                image.setClipToOutline(false);
                return image;
            } catch (RuntimeException ignored) {
                DiagnosticLogger.log(context, "mascot", "icon load failed profile=" + profile.name());
            }
        }
        TextView badge = new TextView(context);
        badge.setText(initials(profile));
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(16);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(dark ? Color.WHITE : UiKit.COLOR_PRIMARY);
        badge.setBackground(circle(dark ? Color.rgb(30, 41, 59) : Color.rgb(239, 246, 255)));
        return badge;
    }

    private static String initials(MascotProfile profile) {
        switch (profile) {
            case GUGA:
                return "咕";
            case DORO:
                return "D";
            case CUSTOM:
                return "自";
            case NONE:
            default:
                return "-";
        }
    }

    private static GradientDrawable circle(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        drawable.setStroke(1, UiKit.COLOR_BORDER);
        return drawable;
    }
}
