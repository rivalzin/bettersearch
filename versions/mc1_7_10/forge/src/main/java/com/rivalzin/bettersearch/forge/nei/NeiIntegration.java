package com.rivalzin.bettersearch.forge.nei;

public final class NeiIntegration {
    private static boolean installed;

    private NeiIntegration() {
    }

    public static void install() throws Exception {
        if (installed) {
            return;
        }
        installed = true;
        boolean modern;
        try {
            Class.forName("codechicken.nei.SearchTokenParser");
            modern = true;
        } catch (ClassNotFoundException noParser) {
            modern = false;
        }
        // one gate class per flavor: a class touching both APIs dies at load, not at the if
        String gate = modern
                ? "com.rivalzin.bettersearch.forge.nei.NeiIntegrationModern"
                : "com.rivalzin.bettersearch.forge.nei.NeiIntegrationLegacy";
        Class.forName(gate).getMethod("install").invoke(null);
    }
}
