package com.rivalzin.bettersearch.core;

final class FieldScorer {
    private FieldScorer() {
    }

    static int score(SearchField field, SearchQuery query, MatchPolicy policy, FuzzyMatcher.Scratch scratch) {
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
        int score = minTier * 100;
        if (inOrder) {
            score += 250;
        }
        if (startsAtBeginning) {
            score += 150;
        }
        score += (int) Math.min(400L, 400L * matchedChars / Math.max(1, field.text.length()));
        return score - 150 * totalDistance;
    }
}
