package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

/**
 * A control center slider for the remote: a glass capsule that fills white up to its value, its icon at the left
 * and the percentage at the right. Left and Right step it (holding the key repeats); nothing else is drawn or
 * animated, so it costs one redraw per step.
 */
public final class GlassSlider extends View {
    /** Called on every step, with the new value between 0 and max. */
    interface Listener {
        void onValue(GlassSlider slider, int value);
    }

    /** The text at the right for a value; a percentage of max by default. */
    interface Format {
        String text(int value);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;
    private Drawable icon;
    private int max = 100;
    private int value;
    private int steps = 16;
    private Listener listener;
    private String title;
    private Format format;

    public GlassSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        track.setColor(0x26FFFFFF);
        fill.setColor(0xF2FFFFFF);
        rim.setStyle(Paint.Style.STROKE);
        label.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
        label.setTypeface(UiFont.get(context, R.string.font_medium));
        label.setTextAlign(Paint.Align.RIGHT);
        setFocusable(true);
        setClickable(true);
    }

    void setIcon(int res) {
        icon = getContext().getDrawable(res).mutate();
        invalidate();
    }

    /** The range, and how many presses go from 0 to max. */
    void setRange(int max, int steps) {
        this.max = Math.max(1, max);
        this.steps = Math.max(1, steps);
        invalidate();
    }

    void setValue(int value) {
        this.value = Math.max(0, Math.min(max, value));
        invalidate();
    }

    int value() {
        return value;
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** A name drawn after the icon, for sliders in a list (Launcher settings). */
    void setTitle(String title) {
        this.title = title;
        setContentDescription(title);
        invalidate();
    }

    /** Text size in sp for the name and the value (12 by default; Launcher settings uses its rows' 15). */
    void setTextSize(float sp) {
        label.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        invalidate();
    }

    void setFormat(Format format) {
        this.format = format;
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int dir = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
        if (dir == 0 || !isEnabled()) return super.onKeyDown(keyCode, event);
        int step = Math.max(1, Math.round(max / (float) steps));
        int next = Math.max(0, Math.min(max, value + dir * step));
        if (next != value) {
            value = next;
            invalidate();
            if (listener != null) listener.onValue(this, value);
        }
        return true;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = getHeight() / 2f;
        float inset = density;
        box.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawRoundRect(box, r, r, track);
        float fraction = value / (float) max;
        float right = box.left + Math.max(2 * r, box.width() * fraction);
        if (fraction > 0f) {
            canvas.save();
            canvas.clipRect(box.left, box.top, right, box.bottom);
            canvas.drawRoundRect(box, r, r, fill);
            canvas.restore();
        }
        boolean focused = isFocused();
        rim.setStrokeWidth((focused ? 2f : 1f) * density);
        rim.setColor(focused ? 0xE6FFFFFF : 0x2EFFFFFF);
        canvas.drawRoundRect(box, r, r, rim);
        if (icon != null) {
            int size = Math.round(18 * density);
            int left = Math.round(box.left + r - size / 2f);
            int top = Math.round(box.centerY() - size / 2f);
            icon.setBounds(left, top, left + size, top + size);
            // Dark on the white fill, white on the glass, as the tiles do.
            icon.setTint(fraction > 0f ? 0xFF111114 : Color.WHITE);
            icon.setAlpha(isEnabled() ? 255 : 110);
            icon.draw(canvas);
        }
        String text = format != null ? format.text(value) : Math.round(fraction * 100) + "%";
        float textX = box.right - r * 0.8f;
        Paint.FontMetrics fm = label.getFontMetrics();
        float baseline = box.centerY() - (fm.ascent + fm.descent) / 2f;
        label.setColor(right >= textX - label.measureText(text) ? 0xFF111114 : 0xD9FFFFFF);
        if (!isEnabled()) label.setColor(0x73FFFFFF);
        canvas.drawText(text, textX, baseline, label);
        if (title != null) {
            float titleX = box.left + (icon != null ? 2 * r + 4 * density : r * 0.8f);
            label.setTextAlign(Paint.Align.LEFT);
            label.setColor(right > titleX + label.measureText(title) / 2f ? 0xFF111114 : 0xF2FFFFFF);
            canvas.drawText(title, titleX, baseline, label);
            label.setTextAlign(Paint.Align.RIGHT);
        }
    }
}
