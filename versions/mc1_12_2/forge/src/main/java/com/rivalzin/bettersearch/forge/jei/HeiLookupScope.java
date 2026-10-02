package com.rivalzin.bettersearch.forge.jei;

public final class HeiLookupScope {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private HeiLookupScope() {
    }

    public static void enter() {
        Integer depth = DEPTH.get();
        DEPTH.set(depth == null ? 1 : depth + 1);
    }

    public static void exit() {
        Integer depth = DEPTH.get();
        if (depth == null || depth <= 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth - 1);
        }
    }

    public static boolean active() {
        return DEPTH.get() != null;
    }
}
