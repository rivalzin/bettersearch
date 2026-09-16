package com.rivalzin.bettersearch.core;

public final class MatchPolicy {
    private static final MatchPolicy[] POLICIES = new MatchPolicy[8];

    static {
        for (int i = 0; i < POLICIES.length; i++) {
            POLICIES[i] = new MatchPolicy((i & 1) != 0, (i & 2) != 0, (i & 4) != 0);
        }
    }
    private final boolean allowTypos;
    private final boolean allowInitials;
    private final boolean allowCompact;

    public MatchPolicy(boolean allowTypos, boolean allowInitials, boolean allowCompact) {
        this.allowTypos = allowTypos;
        this.allowInitials = allowInitials;
        this.allowCompact = allowCompact;
    }

    public boolean allowTypos() {
        return allowTypos;
    }

    public boolean allowInitials() {
        return allowInitials;
    }

    public boolean allowCompact() {
        return allowCompact;
    }

    public static MatchPolicy of(SearchSettings settings, boolean allowTypos) {
        return POLICIES[(allowTypos ? 1 : 0) | (settings.matchInitials ? 2 : 0) | (settings.ignoreSpaces ? 4 : 0)];
    }
}
