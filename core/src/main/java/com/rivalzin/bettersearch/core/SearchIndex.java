package com.rivalzin.bettersearch.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class SearchIndex<T> {
    public static final class Entry<T> {
        public final T value;
        private final SearchField[] fields;

        public final String modId;

        public final String family;

        final String kind;
        final int place;

        public Entry(T value, SearchField[] fields, String modId, String family) {
            this.value = value;
            this.fields = Objects.requireNonNull(fields, "fields").clone();
            for (SearchField field : this.fields) {
                Objects.requireNonNull(field, "field");
            }
            this.modId = modId == null ? "" : modId;
            this.family = family == null ? "" : family;
            this.kind = ItemKinds.kindOf(this.family);
            this.place = ItemKinds.orderOf(this.family);
        }

        public SearchField[] fields() {
            return fields.clone();
        }
    }

    private static final int BONUS_NATIVE = 300;
    private static final int BONUS_ENGLISH = 180;
    private static final int BONUS_FOREIGN = 40;
    private static final int BONUS_ID = 60;
    private static final int BONUS_TOOLTIP = -250;
    private static final int TYPO_PENALTY = 150;
    private static final int CROSS_FIELD_PENALTY = 600;

    private static final int SCORE_OFFSET = 1_000_000;

    private static final long INDEX_MASK = 0x7FFFFFFFL;

    private static final int NO_WINNER = -1;

    private static final long CLEAR_WIN = 1000L;

    private final List<Entry<T>> entries;
    private final boolean grouped;
    private final int[] kindIds;
    private final int kindCount;
    private final AtomicReference<Workspace> idleWorkspace = new AtomicReference<>();

    private static final class Workspace {
        final FuzzyMatcher.Scratch matcher = new FuzzyMatcher.Scratch();
        final int[] scores;
        final long[] packed;
        final int[] ranks;
        final int[] winners;
        final long[] bestScores;

        Workspace(int size, int kinds) {
            scores = new int[size];
            packed = new long[size];
            ranks = new int[kinds];
            winners = new int[kinds];
            bestScores = new long[kinds];
        }
    }

    public SearchIndex(List<Entry<T>> entries) {
        this(entries, true);
    }

    public SearchIndex(List<Entry<T>> entries, boolean grouped) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(entries, "entries")));
        this.grouped = grouped;
        this.kindIds = new int[this.entries.size()];
        Map<String, Integer> kinds = new HashMap<>();
        int nextKind = 0;
        for (int i = 0; i < this.entries.size(); i++) {
            Entry<T> entry = Objects.requireNonNull(this.entries.get(i), "entry");
            Integer known = entry.family.isEmpty() ? null : kinds.get(entry.kind);
            if (known == null) {
                known = nextKind++;
                if (!entry.family.isEmpty()) {
                    kinds.put(entry.kind, known);
                }
            }
            kindIds[i] = known;
        }
        this.kindCount = nextKind;
    }

    public int size() {
        return entries.size();
    }

    public List<Entry<T>> entries() {
        return entries;
    }

    public List<T> search(SearchQuery query, SearchSettings settings) {
        if (query.tokens.length == 0) {
            int limit = settings.maxResults > 0 ? Math.min(settings.maxResults, entries.size()) : entries.size();
            List<T> all = new ArrayList<>(limit);
            for (Entry<T> e : entries) {
                if (matchesModFilter(e, query)) {
                    all.add(e.value);
                    if (all.size() == limit) {
                        break;
                    }
                }
            }
            return all;
        }

        Workspace workspace = idleWorkspace.getAndSet(null);
        if (workspace == null) {
            workspace = new Workspace(entries.size(), grouped ? kindCount : 0);
        }
        try {
            return search(query, settings, workspace);
        } finally {
            idleWorkspace.compareAndSet(null, workspace);
        }
    }

    private List<T> search(SearchQuery query, SearchSettings settings, Workspace workspace) {

        final int n = entries.size();
        final FuzzyMatcher.Scratch scratch = workspace.matcher;
        final int[] scores = workspace.scores;
        Arrays.fill(scores, Integer.MIN_VALUE);

        final MatchPolicy strict = MatchPolicy.of(settings, false);
        final MatchPolicy fuzzy = MatchPolicy.of(settings, true);

        int hits = scan(query, settings, scratch, scores, strict, strict);

        boolean wantsTypos = settings.typoTolerance > 0;
        if (wantsTypos && hits < settings.fuzzyThreshold) {
            hits += scan(query, settings, scratch, scores,
                    fuzzy, settings.foreignStrictOnly ? strict : fuzzy);
        }
        if (settings.crossFieldMatching && query.tokens.length >= 2 && hits < settings.crossFieldThreshold) {
            hits += scanCrossField(query, settings, scratch, scores,
                    wantsTypos ? fuzzy : strict,
                    wantsTypos && !settings.foreignStrictOnly ? fuzzy : strict);
        }

        if (hits == 0) {
            return Collections.emptyList();
        }

        long[] packed = workspace.packed;
        int w = 0;
        for (int i = 0; i < n && w < hits; i++) {
            if (scores[i] != Integer.MIN_VALUE) {
                packed[w++] = ((long) (SCORE_OFFSET - scores[i]) << 32) | (long) i;
            }
        }

        if (settings.sortByRelevance && !query.isBrowseOnly()) {
            Arrays.sort(packed, 0, w);
            if (grouped) {
                regroupByKind(packed, w, workspace);
            }
        }

        int limit = settings.maxResults > 0 ? Math.min(settings.maxResults, w) : w;
        List<T> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(entries.get((int) (packed[i] & INDEX_MASK)).value);
        }
        return out;
    }

    private void regroupByKind(long[] packed, int count, Workspace workspace) {
        int[] ranks = workspace.ranks;
        Arrays.fill(ranks, -1);
        int[] bestOfRank = workspace.winners;
        long[] bestScoreOfRank = workspace.bestScores;
        int next = 0;
        for (int i = 0; i < count; i++) {
            long score = packed[i] >>> 32;
            int index = (int) (packed[i] & INDEX_MASK);
            int rank;
            int kind = kindIds[index];
            int known = ranks[kind];
            if (known < 0) {

                rank = next++;
                bestOfRank[rank] = index;
                bestScoreOfRank[rank] = score;
                ranks[kind] = rank;
            } else {
                rank = known;

                if (score - bestScoreOfRank[rank] < CLEAR_WIN) {
                    bestOfRank[rank] = NO_WINNER;
                }
            }
            packed[i] = ((long) rank << 32) | (long) index;
        }
        Arrays.sort(packed, 0, count);
        orderInsideKinds(packed, count, bestOfRank);
    }

    private void orderInsideKinds(long[] packed, int count, int[] bestOfRank) {
        int start = 0;
        while (start < count) {
            long rank = packed[start] >>> 32;
            int end = start + 1;
            while (end < count && (packed[end] >>> 32) == rank) {
                end++;
            }
            if (end - start > 1) {
                sortRun(packed, start, end, bestOfRank[(int) rank], rank);
            }
            start = end;
        }
    }

    private void sortRun(long[] packed, int from, int to, int best, long rank) {
        if (alreadyInPlace(packed, from, to, best)) {
            return;
        }
        for (int i = from; i < to; i++) {
            int index = (int) (packed[i] & INDEX_MASK);
            long behindTheWinner = index == best ? 0L : 1L;
            long place = entries.get(index).place;
            packed[i] = (behindTheWinner << 62) | (place << 40) | (long) index;
        }
        Arrays.sort(packed, from, to);
        for (int i = from; i < to; i++) {
            packed[i] = (rank << 32) | (packed[i] & INDEX_MASK);
        }
    }

    private boolean alreadyInPlace(long[] packed, int from, int to, int best) {
        int previousPlace = -1;
        for (int i = from; i < to; i++) {
            int index = (int) (packed[i] & INDEX_MASK);
            if (index == best && i != from) {
                return false;
            }
            int place = entries.get(index).place;
            if (place < previousPlace) {
                return false;
            }
            previousPlace = place;
        }
        return true;
    }

    private int scan(SearchQuery query, SearchSettings settings, FuzzyMatcher.Scratch scratch,
                     int[] scores, MatchPolicy policy, MatchPolicy foreignPolicy) {
        MatchPolicy strict = MatchPolicy.of(settings, false);
        int found = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (scores[i] != Integer.MIN_VALUE) {
                continue;
            }
            Entry<T> entry = entries.get(i);
            if (!matchesModFilter(entry, query)) {
                continue;
            }
            int best = Integer.MIN_VALUE;
            if (query.tokens.length == 0) {
                best = 0;
            } else {
                for (SearchField field : entry.fields) {
                    if (field.source == SearchField.SOURCE_TOOLTIP && !settings.searchTooltips) {
                        continue;
                    }
                    if (field.source == SearchField.SOURCE_ID && !settings.searchItemIds) {
                        continue;
                    }
                    MatchPolicy fieldPolicy;
                    switch (field.source) {
                        case SearchField.SOURCE_NATIVE:
                        case SearchField.SOURCE_ENGLISH:
                            fieldPolicy = policy;
                            break;
                        case SearchField.SOURCE_FOREIGN:
                            fieldPolicy = foreignPolicy;
                            break;
                        default:
                            fieldPolicy = strict;
                            break;
                    }
                    int score = scoreField(field, query, fieldPolicy, scratch);
                    if (score > best) {
                        best = score;
                    }
                }
            }
            if (best != Integer.MIN_VALUE) {
                scores[i] = best;
                found++;
            }
        }
        return found;
    }

    private int scanCrossField(SearchQuery query, SearchSettings settings, FuzzyMatcher.Scratch scratch,
                               int[] scores, MatchPolicy policy, MatchPolicy foreignPolicy) {
        MatchPolicy strict = MatchPolicy.of(settings, false);
        int found = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (scores[i] != Integer.MIN_VALUE) {
                continue;
            }
            Entry<T> entry = entries.get(i);
            if (!matchesModFilter(entry, query)) {
                continue;
            }

            int minTier = Integer.MAX_VALUE;
            int totalDistance = 0;
            int bonusSum = 0;
            boolean allMatched = true;

            for (int t = 0; t < query.tokens.length && allMatched; t++) {
                int bestTier = FuzzyMatcher.NO_MATCH;
                int bestDistance = 0;
                int bestBonus = 0;
                for (SearchField field : entry.fields) {
                    if (field.source == SearchField.SOURCE_TOOLTIP && !settings.searchTooltips) {
                        continue;
                    }
                    if (field.source == SearchField.SOURCE_ID && !settings.searchItemIds) {
                        continue;
                    }
                    MatchPolicy fieldPolicy;
                    switch (field.source) {
                        case SearchField.SOURCE_NATIVE:
                        case SearchField.SOURCE_ENGLISH:
                            fieldPolicy = policy;
                            break;
                        case SearchField.SOURCE_FOREIGN:
                            fieldPolicy = foreignPolicy;
                            break;
                        default:
                            fieldPolicy = strict;
                            break;
                    }
                    int tier = FuzzyMatcher.matchToken(field, query.tokens[t], query.tokenMasks[t],
                            query.maxDistances[t], fieldPolicy, scratch);
                    int bonus = sourceBonus(field.source);
                    if (tier > bestTier || (tier == bestTier
                            && bonus - TYPO_PENALTY * scratch.distance
                            > bestBonus - TYPO_PENALTY * bestDistance)) {
                        bestTier = tier;
                        bestDistance = scratch.distance;
                        bestBonus = bonus;
                    }
                }
                if (bestTier == FuzzyMatcher.NO_MATCH) {
                    allMatched = false;
                } else {
                    minTier = Math.min(minTier, bestTier);
                    totalDistance += bestDistance;
                    bonusSum += bestBonus;
                }
            }

            if (allMatched) {
                scores[i] = minTier * 100
                        + bonusSum / query.tokens.length
                        - CROSS_FIELD_PENALTY
                        - TYPO_PENALTY * totalDistance;
                found++;
            }
        }
        return found;
    }

    private boolean matchesModFilter(Entry<T> entry, SearchQuery query) {
        if (query.modFilters.length == 0) {
            return true;
        }
        for (String filter : query.modFilters) {
            if (!entry.modId.contains(filter)) {
                return false;
            }
        }
        return true;
    }

    private static int scoreField(SearchField field, SearchQuery query, MatchPolicy policy,
                                  FuzzyMatcher.Scratch scratch) {
        int score = FieldScorer.score(field, query, policy, scratch);
        return score == Integer.MIN_VALUE ? score : score + sourceBonus(field.source);
    }
    private static int sourceBonus(byte source) {
        switch (source) {
            case SearchField.SOURCE_NATIVE:  return BONUS_NATIVE;
            case SearchField.SOURCE_ENGLISH: return BONUS_ENGLISH;
            case SearchField.SOURCE_FOREIGN: return BONUS_FOREIGN;
            case SearchField.SOURCE_ID:      return BONUS_ID;
            default:                         return BONUS_TOOLTIP;
        }
    }
}
