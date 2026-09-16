package com.rivalzin.bettersearch.core;

public final class QuickMatcher {
    public static final int NO_MATCH = Integer.MIN_VALUE;

    private QuickMatcher() {
    }

    public static boolean matches(String rawText, String rawQuery, SearchSettings settings) {
        return new Session(rawQuery, settings).score(rawText) != NO_MATCH;
    }

    public static final class Session {
        private final SearchQuery query;
        private final SearchSettings settings;
        private final MatchPolicy policy;
        private final FuzzyMatcher.Scratch scratch = new FuzzyMatcher.Scratch();

        public Session(String rawQuery, SearchSettings settings) {
            this.settings = settings.copy();
            this.query = SearchQuery.parse(rawQuery, this.settings);
            this.policy = MatchPolicy.of(this.settings, this.settings.typoTolerance > 0);
        }

        public boolean isEmpty() {
            return query.tokens.length == 0;
        }

        public int score(String rawText) {
            if (rawText == null || rawText.isEmpty()) {
                return NO_MATCH;
            }
            if (query.tokens.length == 0) {
                return 0;
            }
            String normalized = TextNormalizer.normalize(rawText);
            if (normalized.isEmpty()) {
                return NO_MATCH;
            }
            SearchField field = new SearchField(normalized, SearchField.SOURCE_NATIVE);

            return FieldScorer.score(field, query, policy, scratch);
        }

        public SearchSettings settings() {
            return settings.copy();
        }
    }
}
