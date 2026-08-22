package com.rivalzin.bettersearch.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// entries are flat arrays: one pass per keystroke, no allocation
public final class SearchIndex<T> {
    public static final class Entry<T> {
        public final T value;
        public final SearchField[] fields;

        public final String modId;

        public Entry(T value, SearchField[] fields, String modId) {
            this.value = value;
            this.fields = fields;
            this.modId = modId == null ? "" : modId;
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
    private static final int SCORE_OFFSET = 1_000_000;

    private final List<Entry<T>> entries;

    public SearchIndex(List<Entry<T>> entries) {
        this.entries = entries;
    }

    public int size() {
        return entries.size();
    }

    public List<Entry<T>> entries() {
        return entries;
    }

    // passes run cheapest first and stop as soon as one fills the list
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
        }

        int limit = settings.maxResults > 0 ? Math.min(settings.maxResults, w) : w;
        List<T> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(entries.get((int) (packed[i] & 0xFFFFFFFFL)).value);
        }
        return out;
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
        score += (int) ((long) COVERAGE_WEIGHT * matchedChars / Math.max(1, field.text.length()));
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
