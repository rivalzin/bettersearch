package com.rivalzin.bettersearch.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CommandFuzzy {
    public static final int SAFETY_CAP = 100;

    private static final int SCORE_FLOOR = 380;

    private static final int NO_MATCH = Integer.MIN_VALUE;

    private static final int MIN_WORD = 2;

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

        List<Scored> hits = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            int score = score(query, candidate);
            if (score != NO_MATCH && score >= SCORE_FLOOR) {
                hits.add(new Scored(candidate, score));
            }
        }

        if (hits.isEmpty()) {
            String closest = closest(query, candidates);
            if (closest != null) {
                out.add(closest);
            }
            return out;
        }

        hits.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparingInt(hit -> hit.text().length())
                .thenComparing(Scored::text));
        for (int i = 0; i < hits.size() && out.size() < limit; i++) {
            out.add(hits.get(i).text());
        }
        return out;
    }

    public static List<String> best(String word, Collection<String> candidates) {
        return best(word, candidates, SAFETY_CAP);
    }

    private static String closest(String query, Collection<String> candidates) {
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
            double score = similarity(query, target);
            int cut = Math.max(candidate.lastIndexOf(':'), candidate.lastIndexOf('/'));
            if (cut >= 0 && cut + 1 < candidate.length()) {
                String tail = letters(fold(candidate.substring(cut + 1)));
                if (!tail.isEmpty()) {
                    score = Math.max(score, similarity(query, tail));
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
        int longest = Math.max(a.length(), b.length());
        return longest == 0 ? 0.0 : commonSubsequence(a, b) / (double) longest;
    }

    static int commonSubsequence(String a, String b) {
        int la = a.length();
        int lb = b.length();
        int[] previous = new int[lb + 1];
        int[] current = new int[lb + 1];
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
        int best = compare(query, letters(fold(candidate)));

        int cut = Math.max(candidate.lastIndexOf(':'), candidate.lastIndexOf('/'));
        String tail = cut >= 0 && cut + 1 < candidate.length() ? candidate.substring(cut + 1) : null;
        if (tail != null) {
            best = Math.max(best, demote(compare(query, letters(fold(tail))), 10));
        }

        if (query.length() >= 2) {
            best = Math.max(best, initialsScore(query, candidate));
            if (tail != null) {
                best = Math.max(best, demote(initialsScore(query, tail), 10));
            }
        }
        return best;
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

    private static int compare(String query, String target) {
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
        // suggestion lists are short, so a wrong guess is worse than none
        int max = maxEdits(query.length(), target.length());
        int distance = distance(query, target, max);
        if (distance >= 0) {
            return 680 - distance * 70;
        }
        if (query.length() >= 4 && isSubsequence(query, target)) {
            return 430 - Math.min(100, target.length() - query.length());
        }
        return NO_MATCH;
    }

    static int maxEdits(int queryLength, int targetLength) {
        int n = Math.max(queryLength, targetLength);
        int allowed = n <= 3 ? 1 : n <= 4 ? 2 : n <= 7 ? 3 : n <= 10 ? 4 : 5;
        return Math.max(1, Math.min(allowed, n / 2));
    }

    // two rolling rows, no full matrix - this runs per suggestion per keystroke
    static int distance(String a, String b, int max) {
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
        int[] beforePrevious = new int[lb + 1];
        int[] previous = new int[lb + 1];
        int[] current = new int[lb + 1];
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
        StringBuilder out = new StringBuilder(folded.length());
        for (int i = 0; i < folded.length(); i++) {
            char c = folded.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
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
