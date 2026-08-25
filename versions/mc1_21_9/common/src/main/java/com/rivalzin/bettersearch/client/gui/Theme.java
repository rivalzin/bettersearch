package com.rivalzin.bettersearch.client.gui;

// one place for the colors, so every widget on the screen agrees on them
public final class Theme {
    public static final int ACCENT = 0xFF6FD9E8;

    public static final int TITLE = 0xFFFFFFFF;
    public static final int TEXT = 0xFFE0E0E0;
    public static final int TEXT_DIM = 0xFF9A9A9A;

    public static final int BORDER = 0xFF000000;
    public static final int PANEL_BG = 0xB4101014;
    public static final int ROW_BG = 0x66000000;
    public static final int ROW_BG_HOVER = 0x99000000;

    public static final int TAB_ACTIVE = 0xE02A2A32;
    public static final int TAB_HOVER = 0xC01E1E24;
    public static final int TAB_IDLE = 0xA0121216;

    public static final int SWITCH_ON = 0xFF3FA34D;
    public static final int SWITCH_OFF = 0xFF5A5A5A;
    public static final int SWITCH_DISABLED = 0xFF3A3A3A;
    public static final int KNOB = 0xFFE6E6E6;
    public static final int KNOB_HOVER = 0xFFFFFFFF;
    public static final int KNOB_DISABLED = 0xFF8A8A8A;

    public static final int SCROLL_TRACK = 0x66000000;
    public static final int SCROLL_THUMB = 0xFFBFBFBF;

    public static final int FRAME_BG = 0x99000000;
    public static final int FRAME_BORDER = 0x556FD9E8;

    private Theme() {
    }

    public static int blend(int from, int to, float t) {
        float clamped = t < 0f ? 0f : (t > 1f ? 1f : t);
        int a = channel(from, 24, to, clamped);
        int r = channel(from, 16, to, clamped);
        int g = channel(from, 8, to, clamped);
        int b = channel(from, 0, to, clamped);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(int from, int shift, int to, float t) {
        int a = from >> shift & 0xFF;
        int b = to >> shift & 0xFF;
        return a + Math.round((b - a) * t);
    }
}
