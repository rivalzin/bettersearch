package com.rivalzin.bettersearch.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// each entry keeps its fields in a plain array: the search reads them, never rebuilds them
public final class SearchIndex<T> {
    public static final class Entry<T> {
        public final T value;
        public final SearchField[] fields;

        public final String modId;

        // what kind of thing this is, read off the end of the registry name: boots, button,
        // egg. Empty when there is no registry name to read it from.
        public final String family;

        // resolved here and not while sorting: the family never changes after the index is
        // built, and looking these two up per keystroke was the whole cost of grouping
        final String kind;
        final int place;

        public Entry(T value, SearchField[] fields, String modId, String family) {
            this.value = value;
            this.fields = fields;
            this.modId = modId == null ? "" : modId;
            this.family = family == null ? "" : family;
            this.kind = ItemKinds.kindOf(this.family);
            this.place = ItemKinds.orderOf(this.family);
        }
    }

    private static final int BONUS_NATIVE = 300;
    private static final int BONUS_ENGLISH = 180;
    private static final int BONUS_FOREIGN = 40;
    private static final int BONUS_ID = 60;
    private static final int BONUS_TOOLTIP = -250;
    private static final int BONUS_IN_ORDER = 250;
    private static final int BONUS_STARTS_AT_BEGINNING = 150;
    private static final int COVERAGE_WEIGHT = 400;
    private static final int TYPO_PENALTY = 150;
    private static final int CROSS_FIELD_PENALTY = 600;
    // sorting a long[] beats a Comparator by a mile, so score and position share one number;
    // the offset only keeps the high word positive for every score the matcher can produce
    private static final int SCORE_OFFSET = 1_000_000;

    // the low word of the packed key holds the position in the list
    private static final long INDEX_MASK = 0x7FFFFFFFL;

    // no position, so no member of the kind is hoisted out of its canonical place
    private static final int NO_WINNER = -1;

    // one full match tier: scoreField multiplies the tier by 100 and the tiers are 10 apart
    private static final long CLEAR_WIN = 1000L;

    private final List<Entry<T>> entries;
    private final boolean grouped;

    public SearchIndex(List<Entry<T>> entries) {
        this(entries, true);
    }

    /**
     * Grouping is for a list the player reads whole. A command suggestion box stops at twelve
     * lines, so grouping there only pushes the name being typed past the end of it.
     */
    public SearchIndex(List<Entry<T>> entries, boolean grouped) {
        this.entries = entries;
        this.grouped = grouped;
    }

    public int size() {
        return entries.size();
    }

    public List<Entry<T>> entries() {
        return entries;
    }

    // the strict pass always runs; the two expensive ones only when it found too little,
    // and an entry already scored is skipped, so no entry is ever read twice
    public List<T> search(SearchQuery query, SearchSettings settings) {
        if (query.isEmpty()) {
            List<T> all = new ArrayList<>(entries.size());
            for (Entry<T> e : entries) {
                all.add(e.value);
            }
            return all;
        }

        final int n = entries.size();
        final FuzzyMatcher.Scratch scratch = new FuzzyMatcher.Scratch();
        final int[] scores = new int[n];
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

        long[] packed = new long[hits];
        int w = 0;
        for (int i = 0; i < n && w < hits; i++) {
            if (scores[i] != Integer.MIN_VALUE) {
                packed[w++] = ((long) (SCORE_OFFSET - scores[i]) << 32) | (long) i;
            }
        }
        if (settings.sortByRelevance) {
            Arrays.sort(packed, 0, w);
            // "@mod" alone is browsing, not searching: that list stays as the game shows it
            if (grouped && query.tokens.length > 0) {
                regroupByKind(packed, w);
            }
        }

        int limit = settings.maxResults > 0 ? Math.min(settings.maxResults, w) : w;
        List<T> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(entries.get((int) (packed[i] & INDEX_MASK)).value);
        }
        return out;
    }

    /**
     * Puts every pair of boots next to the other boots, and the boots next to the helmet.
     * The score picks which kind of thing leads and which piece of it leads the kind; inside
     * the kind the pieces follow the order a player expects to read them in, and pieces of
     * the same family follow the order the list itself is in.
     */
    private void regroupByKind(long[] packed, int count) {
        Map<String, Integer> ranks = new HashMap<>();
        int[] bestOfRank = new int[count];
        long[] bestScoreOfRank = new long[count];
        int next = 0;
        for (int i = 0; i < count; i++) {
            long score = packed[i] >>> 32;
            int index = (int) (packed[i] & INDEX_MASK);
            Entry<T> entry = entries.get(index);
            boolean alone = entry.family.isEmpty();
            int rank;
            Integer known = alone ? null : ranks.get(entry.kind);
            if (known == null) {
                // packed arrives in score order, so the first one seen in a kind is the one
                // that matched best
                rank = next++;
                bestOfRank[rank] = index;
                bestScoreOfRank[rank] = score;
                if (!alone) {
                    ranks.put(entry.kind, rank);
                }
            } else {
                rank = known;
                // Only a win by a whole tier counts. A kind whose pieces all matched about as
                // well has no winner to pull out, and pulling one out on a few points of
                // difference is what would break the head-to-feet run of a set.
                if (score - bestScoreOfRank[rank] < CLEAR_WIN) {
                    bestOfRank[rank] = NO_WINNER;
                }
            }
            packed[i] = ((long) rank << 32) | (long) index;
        }
        Arrays.sort(packed, 0, count);
        orderInsideKinds(packed, count, bestOfRank);
    }

    // Each kind is now one run of the array. Only the run is reordered, so the kinds stay
    // where the score put them.
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

    // Every entry in the run carries the same rank, so the position alone rebuilds the packed
    // value: the run can be rewritten as plain sort keys, sorted, and packed again.
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

    // Almost every run is one family with no winner to hoist, and then the list is already
    // the way it should come out. Checking costs one pass and saves the sort.
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
        MatchPolicy strict = new MatchPolicy(false, policy.allowInitials(), policy.allowCompact());
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
        MatchPolicy strict = new MatchPolicy(false, policy.allowInitials(), policy.allowCompact());
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
                    if (tier > bestTier) {
                        bestTier = tier;
                        bestDistance = scratch.distance;
                        bestBonus = sourceBonus(field.source);
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
        int minTier = Integer.MAX_VALUE;
        int totalDistance = 0;
        int matchedChars = 0;
        int lastPosition = -1;
        boolean inOrder = true;
        boolean startsAtBeginning = false;

        for (int i = 0; i < query.tokens.length; i++) {
            String token = query.tokens[i];
            int tier = FuzzyMatcher.matchToken(field, token, query.tokenMasks[i],
                    query.maxDistances[i], policy, scratch);
            if (tier == FuzzyMatcher.NO_MATCH) {
                return Integer.MIN_VALUE;
            }
            minTier = Math.min(minTier, tier);
            totalDistance += scratch.distance;
            matchedChars += token.length();
            if (scratch.position < lastPosition) {
                inOrder = false;
            }
            lastPosition = scratch.position;
            if (i == 0 && scratch.position == 0) {
                startsAtBeginning = true;
            }
        }

        int score = minTier * 100 + sourceBonus(field.source);
        if (inOrder) {
            score += BONUS_IN_ORDER;
        }
        if (startsAtBeginning) {
            score += BONUS_STARTS_AT_BEGINNING;
        }
        score += (int) Math.min(COVERAGE_WEIGHT,
                (long) COVERAGE_WEIGHT * matchedChars / Math.max(1, field.text.length()));
        score -= TYPO_PENALTY * totalDistance;
        return score;
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
