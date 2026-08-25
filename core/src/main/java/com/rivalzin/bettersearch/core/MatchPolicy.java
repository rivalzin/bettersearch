package com.rivalzin.bettersearch.core;

// one object per pass: what a pass is allowed to do is decided before it starts
public final class MatchPolicy {
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
        return new MatchPolicy(allowTypos, settings.matchInitials, settings.ignoreSpaces);
    }
}
