package com.rivalzin.bettersearch.core;

public final class FuzzyMatcher {
    // higher tier wins first, score only breaks ties inside a tier
    public static final int TIER_EXACT = 100;

    public static final int TIER_PREFIX = 90;

    public static final int TIER_WORD_EXACT = 80;

    public static final int TIER_WORD_PREFIX = 70;

    public static final int TIER_COMPACT = 60;

    public static final int TIER_SUBSTRING = 50;

    // obwc -> Oak Boat with Chest, only when nothing better matched
    public static final int TIER_INITIALS = 40;

    public static final int TIER_TYPO = 30;

    public static final int NO_MATCH = -1;

    private FuzzyMatcher() {
    }

    public static final class Scratch {
        public int position;

        public int distance;

        // two rows reused across items, the matrix is never allocated
        int[] rowA = new int[64];
        int[] rowB = new int[64];
        int[] rowC = new int[64];

        void ensure(int size) {
            if (rowA.length < size) {
                rowA = new int[size];
                rowB = new int[size];
                rowC = new int[size];
            }
        }
    }

    public static int matchToken(SearchField field, String token, long tokenMask,
                                 int maxDist, MatchPolicy policy, Scratch scratch) {
        scratch.position = 0;
        scratch.distance = 0;

        final boolean allowTypos = policy.allowTypos();
        final String text = field.text;
        final int tokenLength = token.length();
        if (tokenLength == 0 || text.isEmpty()) {
            return NO_MATCH;
        }

        int missing = Long.bitCount(tokenMask & ~field.mask);
        int allowedMissing = allowTypos ? maxDist : 0;
        if (missing > allowedMissing) {
            return NO_MATCH;
        }

        if (missing == 0) {
            if (text.length() == tokenLength) {
                if (text.equals(token)) {
                    return TIER_EXACT;
                }
            } else if (text.startsWith(token)) {
                return TIER_PREFIX;
            }

            int[] starts = field.wordStarts;
            int bestWordTier = NO_MATCH;
            int bestWordPos = 0;
            for (int w = 0; w < starts.length; w++) {
                int s = starts[w];
                int e = field.wordEnd(w);
                if (e - s < tokenLength) {
                    continue;
                }
                if (text.regionMatches(s, token, 0, tokenLength)) {
                    if (e - s == tokenLength) {
                        scratch.position = s;
                        return TIER_WORD_EXACT;
                    }
                    if (bestWordTier < TIER_WORD_PREFIX) {
                        bestWordTier = TIER_WORD_PREFIX;
                        bestWordPos = s;
                    }
                }
            }
            if (bestWordTier != NO_MATCH) {
                scratch.position = bestWordPos;
                return bestWordTier;
            }

            String compact = field.compact;
            if (policy.allowCompact() && compact != null && compact.length() >= tokenLength) {
                int idx = compact.indexOf(token);
                if (idx >= 0) {
                    scratch.position = idx;
                    return TIER_COMPACT;
                }
            }

            int idx = text.indexOf(token);
            if (idx >= 0) {
                scratch.position = idx;
                return TIER_SUBSTRING;
            }

            String initials = field.initials;
            if (policy.allowInitials() && initials != null && tokenLength >= 2
                    && matchesInitials(initials, token)) {
                scratch.position = 0;
                return TIER_INITIALS;
            }
        }

        if (!allowTypos || maxDist <= 0) {
            return NO_MATCH;
        }

        int bestDistance = maxDist + 1;
        int bestPos = 0;
        int[] starts = field.wordStarts;
        for (int w = 0; w < starts.length; w++) {
            int s = starts[w];
            int e = field.wordEnd(w);
            if (e - s <= 0 || tokenLength - (e - s) > maxDist) {
                continue;
            }
            int d = prefixDistance(token, text, s, e, bestDistance - 1, scratch);
            if (d < bestDistance) {
                bestDistance = d;
                bestPos = s;
                if (d == 0) {
                    break;
                }
            }
        }

        String compact = policy.allowCompact() ? field.compact : null;
        if (bestDistance > 0 && compact != null && tokenLength - compact.length() <= maxDist) {
            int d = prefixDistance(token, compact, 0, compact.length(), bestDistance - 1, scratch);
            if (d < bestDistance) {
                bestDistance = d;
                bestPos = 0;
            }
        }

        if (bestDistance <= maxDist) {
            scratch.position = bestPos;
            scratch.distance = bestDistance;
            return TIER_TYPO;
        }
        return NO_MATCH;
    }

    static boolean matchesInitials(String initials, String token) {
        if (initials.isEmpty() || initials.charAt(0) != token.charAt(0)) {
            return false;
        }
        int matched = 0;
        for (int i = 0; i < initials.length() && matched < token.length(); i++) {
            if (initials.charAt(i) == token.charAt(matched)) {
                matched++;
            }
        }
        return matched == token.length();
    }

    public static int prefixDistance(String token, String target, int from, int to, int max, Scratch scratch) {
        final int n = token.length();
        if (max < 0) {
            return 1;
        }

        int limit = Math.min(to, from + n + max);
        scratch.ensure(n + 1);

        int[] prev2 = scratch.rowA;
        int[] prev = scratch.rowB;
        int[] cur = scratch.rowC;

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }
        int best = n;

        for (int i = from + 1; i <= limit; i++) {
            char tc = target.charAt(i - 1);
            int row = i - from;
            cur[0] = row;
            int rowMin = row;
            for (int j = 1; j <= n; j++) {
                char qc = token.charAt(j - 1);
                int cost = qc == tc ? 0 : 1;
                int v = prev[j - 1] + cost;
                int del = prev[j] + 1;
                if (del < v) {
                    v = del;
                }
                int ins = cur[j - 1] + 1;
                if (ins < v) {
                    v = ins;
                }
                if (row > 1 && j > 1 && qc == target.charAt(i - 2) && token.charAt(j - 2) == tc) {
                    int trans = prev2[j - 2] + 1;
                    if (trans < v) {
                        v = trans;
                    }
                }
                cur[j] = v;
                if (v < rowMin) {
                    rowMin = v;
                }
            }
            if (cur[n] < best) {
                best = cur[n];
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] tmp = prev2;
            prev2 = prev;
            prev = cur;
            cur = tmp;
        }

        scratch.rowA = prev2;
        scratch.rowB = prev;
        scratch.rowC = cur;
        return best;
    }
}
