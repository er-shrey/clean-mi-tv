package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The scrolling line of text on Home (config.xml marquee, marquee_text). The text lives in marquee.txt in the
 * launcher's files directory, written from Launcher settings or over ADB through ThemeProvider; without that file
 * marquee_text from config.xml or a theme shows. Only translationX animates, and only while Home is resumed; the
 * file's timestamp is checked once per pass, so a new text shows up on the next pass without a restart.
 */
final class Marquee {
    static final String FILE_NAME = "marquee.txt";
    /** Keeps a pasted essay from building a texture wider than the GPU allows. */
    static final int MAX_LENGTH = 400;
    private static final String SEPARATOR = "     •     ";
    private static final float SPEED_DP_PER_S = 70f;

    private final View band;
    private final TextView text;
    private final File file;
    private final String fallback;
    private final boolean animate;
    private final float density;
    private ValueAnimator animator;
    private long fileStamp = Long.MIN_VALUE;
    private boolean running;

    Marquee(View band, TextView text, File dir, String fallback, boolean animate, float density) {
        this.band = band;
        this.text = text;
        this.file = dir == null ? null : new File(dir, FILE_NAME);
        this.fallback = normalize(fallback);
        this.animate = animate;
        this.density = density;
    }

    /** One line: each line of the file becomes its own item, blank lines dropped, cut at MAX_LENGTH. */
    static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\\r?\\n|\\r")) {
            String item = line.trim().replaceAll("\\s+", " ");
            if (item.isEmpty()) continue;
            if (out.length() > 0) out.append(SEPARATOR);
            out.append(item);
        }
        return out.length() > MAX_LENGTH ? out.substring(0, MAX_LENGTH).trim() + "…" : out.toString();
    }

    /** The file's lines as one line for the TV's text box, items separated by " | ". */
    static String toField(String fileText) {
        if (fileText == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : fileText.split("\\r?\\n|\\r")) {
            String item = line.trim();
            if (item.isEmpty()) continue;
            if (out.length() > 0) out.append(" | ");
            out.append(item);
        }
        return out.toString();
    }

    /** The text box's line back into the file's form: each "|"-separated item on its own line. */
    static String fromField(String field) {
        if (field == null) return "";
        StringBuilder out = new StringBuilder();
        for (String part : field.split("\\|")) {
            String item = part.trim();
            if (item.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(item);
        }
        return out.toString();
    }

    /** The file's text as typed (lines kept), or null when there is no file. */
    static String read(File dir) {
        File f = dir == null ? null : new File(dir, FILE_NAME);
        if (f == null || !f.isFile()) return null;
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Saves the text; an empty text still writes the file, so an emptied marquee stays empty over marquee_text. */
    static boolean write(File dir, String value) {
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return false;
        try (OutputStream out = new FileOutputStream(new File(dir, FILE_NAME))) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Removes the file, so marquee_text from config.xml or the theme shows again. */
    static void reset(File dir) {
        if (dir != null) new File(dir, FILE_NAME).delete();
    }

    void start() {
        running = true;
        reload(true);
    }

    void stop() {
        running = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private long stamp() {
        return file != null && file.isFile() ? file.lastModified() : -1L;
    }

    /** Reads the text again when the file changed (or when asked), then starts a pass. */
    private void reload(boolean force) {
        long now = stamp();
        if (force || now != fileStamp) {
            fileStamp = now;
            String fromFile = now < 0 ? null : read(file.getParentFile());
            String value = fromFile == null ? fallback : normalize(fromFile);
            if (!value.contentEquals(text.getText())) {
                text.setText(value);
                ViewGroup.LayoutParams lp = text.getLayoutParams();
                // An exact width wider than the band, so the line is never cut or wrapped; the band clips it.
                lp.width = (int) Math.ceil(text.getPaint().measureText(value)) + text.getPaddingLeft()
                        + text.getPaddingRight();
                text.setLayoutParams(lp);
            }
            band.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (band.getVisibility() == View.VISIBLE) band.post(this::pass);
    }

    /** One pass from the band's right edge until the text has left on the left, then a reload check. */
    private void pass() {
        if (!running) return;
        if (animator != null) animator.cancel();
        int width = band.getWidth() - band.getPaddingLeft() - band.getPaddingRight();
        int textWidth = text.getLayoutParams().width;
        if (!animate || width <= 0) {
            // Reduced motion or animations off: the start of the line, still.
            text.setTranslationX(0f);
            return;
        }
        float distance = width + textWidth;
        animator = ValueAnimator.ofFloat(width, -textWidth);
        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration((long) (distance / (SPEED_DP_PER_S * density) * 1000f));
        animator.addUpdateListener(a -> text.setTranslationX((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (!cancelled && running) reload(false);
            }
        });
        animator.start();
    }
}
