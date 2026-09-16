package org.lwjgl.input;

public final class Keyboard {

    public static final int KEY_O = 24;
    public static final int KEY_LMENU = 56;
    public static final int KEY_RMENU = 184;

    private Keyboard() {
    }

    public static int getEventKey() { throw new AssertionError("esboco de compilacao"); }
    public static boolean getEventKeyState() { throw new AssertionError("esboco de compilacao"); }
    public static boolean isKeyDown(int key) { throw new AssertionError("esboco de compilacao"); }
    public static void enableRepeatEvents(boolean enable) { throw new AssertionError("esboco de compilacao"); }
}
