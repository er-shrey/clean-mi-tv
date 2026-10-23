package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * The marquee's clip beside the greeting: no background, only a short fade at both ends so the sliding text
 * comes in and leaves softly instead of being cut. View.draw does the fade for any view whose edge strengths
 * are above zero, over just the edge strips.
 */
public final class MarqueeBand extends FrameLayout {
    public MarqueeBand(Context context, AttributeSet attrs) {
        super(context, attrs);
        setHorizontalFadingEdgeEnabled(true);
        setFadingEdgeLength((int) (20 * getResources().getDisplayMetrics().density));
        setWillNotDraw(false);
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        return 1f;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        return 1f;
    }
}
