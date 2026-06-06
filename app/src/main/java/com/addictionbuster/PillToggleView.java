package com.addictionbuster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class PillToggleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;

    PillToggleView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
    }

    void setChecked(boolean checked) {
        if (this.checked == checked) {
            return;
        }
        this.checked = checked;
        invalidate();
    }

    boolean isChecked() {
        return checked;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(dp(52), dp(30));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float radius = height / 2f;
        RectF track = new RectF(0, 0, width, height);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? Color.rgb(37, 99, 235) : Color.WHITE);
        canvas.drawRoundRect(track, radius, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(checked ? Color.rgb(37, 99, 235) : Color.rgb(203, 213, 225));
        canvas.drawRoundRect(track, radius, radius, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? Color.WHITE : Color.rgb(203, 213, 225));
        float knobRadius = dp(11);
        float centerX = checked ? width - radius : radius;
        canvas.drawCircle(centerX, height / 2f, knobRadius, paint);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
