package com.rivalzin.bettersearch.forge.nei;

public final class NeiIntegration {
    private static boolean installed;

    private NeiIntegration() {
    }

    public static void install() throws Exception {
        if (installed) {
            return;
        }
        boolean modern;
        try {
            Class.forName("codechicken.nei.SearchTokenParser");
            modern = true;
        } catch (ClassNotFoundException noParser) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(noParser);
            modern = false;
        }

        String gate = modern
                ? "com.rivalzin.bettersearch.forge.nei.NeiIntegrationModern"
                : "com.rivalzin.bettersearch.forge.nei.NeiIntegrationLegacy";
        Class.forName(gate).getMethod("install").invoke(null);

        installed = true;
    }
}
