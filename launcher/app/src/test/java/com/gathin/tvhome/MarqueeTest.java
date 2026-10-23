package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class MarqueeTest {
    @Test public void emptyAndNullGiveNothing() {
        assertEquals("", Marquee.normalize(null));
        assertEquals("", Marquee.normalize("  \n\n \r\n"));
    }
    @Test public void eachLineIsItsOwnItem() {
        assertEquals("Dinner at 8     •     Match on HDMI 3", Marquee.normalize("Dinner at 8\r\n\n  Match on HDMI 3  \n"));
    }
    @Test public void collapsesInnerWhitespace() {
        assertEquals("Hello there", Marquee.normalize("Hello \t  there"));
    }
    @Test public void cutsVeryLongText() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 100; i++) b.append("word ");
        String out = Marquee.normalize(b.toString());
        assertTrue(out.endsWith("…"));
        assertTrue(out.length() <= Marquee.MAX_LENGTH + 1);
    }
    @Test public void textBoxRoundTripsTheFileLines() {
        assertEquals("Dinner at 8 | Match on HDMI 3", Marquee.toField("Dinner at 8\n\n Match on HDMI 3 \n"));
        assertEquals("Dinner at 8\nMatch on HDMI 3", Marquee.fromField(" Dinner at 8 || Match on HDMI 3 | "));
        assertEquals("", Marquee.fromField(" | "));
    }
}
