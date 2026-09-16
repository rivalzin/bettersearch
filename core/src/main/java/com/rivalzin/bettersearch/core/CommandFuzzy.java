package com.rivalzin.bettersearch.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.HashSet;
import java.util.Set;

public final class CommandFuzzy {
    public static final int SAFETY_CAP = 100;

    private static final int SCORE_FLOOR = 380;

    private static final int SUBSEQUENCE_FLOOR = 40;

    private static final int CLOSEST_MAX_OPTIONS = 200;

    private static final int NO_MATCH = Integer.MIN_VALUE;

    private static final int MIN_WORD = 2;
    private static final Comparator<Scored> BEST_FIRST = Comparator.comparingInt(Scored::score).reversed()
            .thenComparingInt(hit -> hit.text().length()).thenComparing(Scored::text);

    private CommandFuzzy() {
    }

    private static final class Scored {
        private final String text;
        private final int score;

        Scored(String text, int score) {
            this.text = text;
            this.score = score;
        }

        String text() {
            return text;
        }

        int score() {
            return score;
        }
    }

    public static List<String> best(String word, Collection<String> candidates, int limit) {
        List<String> out = new ArrayList<>();
        if (word == null || candidates == null || candidates.isEmpty() || limit <= 0) {
            return out;
        }
        String query = letters(fold(word));
        if (query.length() < MIN_WORD || !hasLetter(query)) {
            return out;
        }

        int capacity = Math.min(limit, candidates.size());
        PriorityQueue<Scored> hits = new PriorityQueue<>(Math.min(capacity, 64), BEST_FIRST.reversed());
        Set<String> selected = new HashSet<>();
        FuzzyMatcher.Scratch scratch = new FuzzyMatcher.Scratch();
        long queryMask = mask(query);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty() || selected.contains(candidate)) {
                continue;
            }
            if (!mightScore(query, queryMask, candidate)) {
                continue;
            }
            int score = score(query, candidate, scratch);
            if (score != NO_MATCH && score >= SCORE_FLOOR) {
                Scored worst = hits.peek();
                if (hits.size() == capacity && (score < worst.score()
                        || score == worst.score() && !shorterOrEarlier(candidate, worst.text()))) {
                    continue;
                }
                Scored hit = new Scored(candidate, score);
                if (hits.size() < capacity) {
                    hits.add(hit);
                    selected.add(candidate);
                } else if (BEST_FIRST.compare(hit, hits.peek()) < 0) {
                    selected.remove(hits.remove().text());
                    hits.add(hit);
                    selected.add(candidate);
                }
            }
        }

        if (hits.isEmpty()) {
            if (candidates.size() > CLOSEST_MAX_OPTIONS) {
                return out;
            }
            String closest = closest(query, candidates, scratch);
            if (closest != null) {
                out.add(closest);
            }
            return out;
        }

        List<Scored> ordered = new ArrayList<>(hits);
        ordered.sort(BEST_FIRST);
        for (Scored hit : ordered) {
            out.add(hit.text());
        }
        return out;
    }

    public static List<String> best(String word, Collection<String> candidates) {
        return best(word, candidates, SAFETY_CAP);
    }

    private static boolean mightScore(String query, long queryMask, String candidate) {
        final int len = candidate.length();
        final int qlen = query.length();
        int cut = -1;
        boolean tailUnderscore = false;
        long fullMask = 0L, tailMask = 0L;
        int fullLen = 0, tailLen = 0;
        int fullInitials = 0, tailInitials = 0;
        char fullFirstInitial = 0, tailFirstInitial = 0;
        boolean starting = true, tailStarting = true;
        char previous = 0;
        for (int i = 0; i < len; i++) {
            char c = candidate.charAt(i);
            if (c > 127) {
                return true;
            }
            if (c == '_' && cut >= 0) {
                tailUnderscore = true;
            }
            if (c == ':' || c == '/') {
                cut = i;
                tailUnderscore = false;
                tailMask = 0L;
                tailLen = 0;
                tailInitials = 0;
                tailFirstInitial = 0;
                tailStarting = true;
            }
            boolean letter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (letter) {
                char low = (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
                long bit = 1L << (low % 64);
                boolean camel = (c >= 'A' && c <= 'Z') && (previous >= 'a' && previous <= 'z');
                fullMask |= bit;
                fullLen++;
                if (starting || camel) {
                    if (fullInitials == 0) {
                        fullFirstInitial = low;
                    }
                    fullInitials++;
                }
                starting = false;
                if (cut >= 0) {
                    tailMask |= bit;
                    tailLen++;
                    if (tailStarting || camel) {
                        if (tailInitials == 0) {
                            tailFirstInitial = low;
                        }
                        tailInitials++;
                    }
                    tailStarting = false;
                }
            } else {
                starting = true;
                tailStarting = true;
            }
            previous = c;
        }
        if (regionAlive(qlen, queryMask, fullLen, fullMask)) {
            return true;
        }
        boolean tailExists = cut >= 0 && cut + 1 < len;
        if (tailExists && regionAlive(qlen, queryMask, tailLen, tailMask)) {
            return true;
        }

        if (tailExists && tailUnderscore && Long.bitCount(queryMask & ~tailMask) <= 1) {
            return true;
        }
        char q0 = query.charAt(0);
        if (fullInitials >= 2 && fullInitials >= qlen && fullFirstInitial == q0) {
            return true;
        }
        return tailExists && tailInitials >= 2 && tailInitials >= qlen && tailFirstInitial == q0;
    }

    private static boolean regionAlive(int qlen, long queryMask, int tlen, long targetMask) {
        if (tlen == 0) {
            return false;
        }
        int missing = Long.bitCount(queryMask & ~targetMask);
        if (missing == 0 && tlen >= qlen) {
            return true;
        }
        int extra = Long.bitCount(targetMask & ~queryMask);
        if (extra == 0 && tlen >= 4 && tlen * 2 >= qlen) {
            return true;
        }
        int max = maxEdits(qlen, tlen);
        return missing <= max && extra <= max && Math.abs(qlen - tlen) <= max;
    }

    private static String closest(String query, Collection<String> candidates, FuzzyMatcher.Scratch scratch) {
        String best = null;
        double bestScore = -1.0;
        long queryMask = mask(query);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            String target = letters(fold(candidate));
            if (target.isEmpty() || (mask(target) & queryMask) == 0L) {
                continue;
            }
            double score = similarity(query, target, scratch);
            int cut = Math.max(candidate.lastIndexOf(':'), candidate.lastIndexOf('/'));
            if (cut >= 0 && cut + 1 < candidate.length()) {
                String tail = letters(fold(candidate.substring(cut + 1)));
                if (!tail.isEmpty()) {
                    score = Math.max(score, similarity(query, tail, scratch));
                }
            }
            if (score > bestScore + 1e-9
                    || (best != null && Math.abs(score - bestScore) <= 1e-9 && shorterOrEarlier(candidate, best))) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean shorterOrEarlier(String candidate, String current) {
        if (candidate.length() != current.length()) {
            return candidate.length() < current.length();
        }
        return candidate.compareTo(current) < 0;
    }

    static double similarity(String a, String b) {
        return similarity(a, b, new FuzzyMatcher.Scratch());
    }

    private static double similarity(String a, String b, FuzzyMatcher.Scratch scratch) {
        int longest = Math.max(a.length(), b.length());
        return longest == 0 ? 0.0 : commonSubsequence(a, b, scratch) / (double) longest;
    }

    static int commonSubsequence(String a, String b) {
        return commonSubsequence(a, b, new FuzzyMatcher.Scratch());
    }

    private static int commonSubsequence(String a, String b, FuzzyMatcher.Scratch scratch) {
        int la = a.length();
        int lb = b.length();
        scratch.ensure(lb + 1);
        int[] previous = scratch.rowA;
        int[] current = scratch.rowB;
        java.util.Arrays.fill(previous, 0, lb + 1, 0);
        current[0] = 0;
        for (int i = 1; i <= la; i++) {
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                current[j] = ca == b.charAt(j - 1)
                        ? previous[j - 1] + 1
                        : Math.max(previous[j], current[j - 1]);
            }
            int[] recycled = previous;
            previous = current;
            current = recycled;
            current[0] = 0;
        }
        return previous[lb];
    }

    private static long mask(String text) {
        long mask = 0L;
        for (int i = 0; i < text.length(); i++) {
            mask |= 1L << (text.charAt(i) % 64);
        }
        return mask;
    }

    static int score(String query, String candidate) {
        return score(query, candidate, new FuzzyMatcher.Scratch());
    }

    private static int score(String query, String candidate, FuzzyMatcher.Scratch scratch) {
        int best = compare(query, letters(fold(candidate)), scratch);

        int cut = Math.max(candidate.lastIndexOf(':'), candidate.lastIndexOf('/'));
        String tail = cut >= 0 && cut + 1 < candidate.length() ? candidate.substring(cut + 1) : null;
        if (tail != null) {
            best = Math.max(best, demote(compare(query, letters(fold(tail)), scratch), 10));

            best = Math.max(best, demote(bestSegment(query, tail, scratch), 200));
        }

        if (query.length() >= 2) {
            best = Math.max(best, initialsScore(query, candidate));
            if (tail != null) {
                best = Math.max(best, demote(initialsScore(query, tail), 10));
            }
        }
        return best;
    }

    private static int bestSegment(String query, String tail, FuzzyMatcher.Scratch scratch) {
        int best = NO_MATCH;
        int start = 0;
        int segments = 0;
        for (int i = 0; i <= tail.length(); i++) {
            if (i == tail.length() || tail.charAt(i) == '_') {
                if (i > start) {
                    segments++;
                    if (segments > 1 || i < tail.length()) {
                        best = Math.max(best, compare(query, letters(fold(tail.substring(start, i))), scratch));
                    }
                }
                start = i + 1;
            }
        }

        return segments >= 2 ? best : NO_MATCH;
    }

    private static int initialsScore(String query, String raw) {
        String initials = initials(raw);
        if (initials.length() < 2) {
            return NO_MATCH;
        }
        if (initials.equals(query)) {
            return 730;
        }
        return initials.startsWith(query) ? 640 : NO_MATCH;
    }

    private static int demote(int score, int penalty) {
        return score == NO_MATCH ? NO_MATCH : score - penalty;
    }

    private static int compare(String query, String target, FuzzyMatcher.Scratch scratch) {
        if (target.isEmpty()) {
            return NO_MATCH;
        }
        if (target.equals(query)) {
            return 1000;
        }
        if (target.startsWith(query)) {
            return 900 - Math.min(80, target.length() - query.length());
        }

        if (query.length() >= 3 && target.length() >= 3 && target.contains(query)) {
            return 760 - Math.min(60, target.length() - query.length());
        }
        if (target.length() >= 4 && target.length() * 2 >= query.length() && query.contains(target)) {
            return 640 - Math.min(160, (query.length() - target.length()) * 40);
        }

        int max = maxEdits(query.length(), target.length());
        int distance = distance(query, target, max, scratch);

        int byDistance = distance >= 0 ? 680 - distance * 70 : NO_MATCH;

        int bySubsequence = query.length() >= 4 && isSubsequence(query, target)
                && query.length() * 100 >= target.length() * SUBSEQUENCE_FLOOR
                ? 430 - Math.min(100, target.length() - query.length())
                : NO_MATCH;
        if (byDistance == NO_MATCH) {
            return bySubsequence;
        }
        return bySubsequence == NO_MATCH ? byDistance : Math.max(byDistance, bySubsequence);
    }

    static int maxEdits(int queryLength, int targetLength) {
        int n = Math.max(queryLength, targetLength);
        int allowed = n <= 3 ? 1 : n <= 4 ? 2 : n <= 7 ? 3 : n <= 10 ? 4 : 5;
        return Math.max(1, Math.min(allowed, n / 2));
    }

    static int distance(String a, String b, int max) {
        return distance(a, b, max, new FuzzyMatcher.Scratch());
    }

    private static int distance(String a, String b, int max, FuzzyMatcher.Scratch scratch) {
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > max) {
            return -1;
        }
        if (la == 0) {
            return lb <= max ? lb : -1;
        }
        if (lb == 0) {
            return la <= max ? la : -1;
        }
        scratch.ensure(lb + 1);
        int[] beforePrevious = scratch.rowA;
        int[] previous = scratch.rowB;
        int[] current = scratch.rowC;
        for (int j = 0; j <= lb; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= la; i++) {
            current[0] = i;
            int rowBest = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                char cb = b.charAt(j - 1);
                int cost = ca == cb ? 0 : 1;
                int value = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + cost);
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    value = Math.min(value, beforePrevious[j - 2] + 1);
                }
                current[j] = value;
                rowBest = Math.min(rowBest, value);
            }
            if (rowBest > max) {
                return -1;
            }
            int[] recycled = beforePrevious;
            beforePrevious = previous;
            previous = current;
            current = recycled;
        }
        int distance = previous[lb];
        return distance > max ? -1 : distance;
    }

    static boolean isSubsequence(String query, String target) {
        int at = 0;
        for (int i = 0; i < target.length() && at < query.length(); i++) {
            if (target.charAt(i) == query.charAt(at)) {
                at++;
            }
        }
        return at == query.length();
    }

    static String initials(String raw) {
        StringBuilder out = new StringBuilder();
        boolean starting = true;
        char previous = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                starting = true;
                previous = c;
                continue;
            }
            boolean camel = Character.isUpperCase(c) && Character.isLowerCase(previous);
            if (starting || camel) {
                out.append(Character.toLowerCase(c));
            }
            starting = false;
            previous = c;
        }
        return out.toString();
    }

    static String fold(String input) {

        if (isAscii(input)) {
            return input.toLowerCase(Locale.ROOT);
        }
        String decomposed = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                out.append(c);
            }
        }
        return out.toString();
    }

    static String letters(String folded) {
        int first = 0;
        while (first < folded.length() && Character.isLetterOrDigit(folded.charAt(first))) {
            first++;
        }

        if (first == folded.length()) {
            return folded;
        }
        StringBuilder out = new StringBuilder(folded.length());
        for (int i = 0; i < folded.length(); i++) {
            char c = folded.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
    }

    public static int wordStart(String text, int at) {
        int index = Math.max(0, Math.min(at, text.length()));
        while (index > 0 && isWordChar(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    public static int wordEnd(String text, int at) {
        int index = Math.max(0, Math.min(at, text.length()));
        while (index < text.length() && isWordChar(text.charAt(index))) {
            index++;
        }
        return index;
    }
}
