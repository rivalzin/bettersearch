package com.rivalzin.bettersearch.core;

// id, tooltip and the language fields each score separately
public final class SearchField {
    public static final byte SOURCE_NATIVE = 0;

    public static final byte SOURCE_ENGLISH = 1;

    public static final byte SOURCE_FOREIGN = 2;

    public static final byte SOURCE_ID = 3;

    public static final byte SOURCE_TOOLTIP = 4;

    public final String text;

    public final long mask;

    public final int[] wordStarts;

    public final String initials;

    public final String compact;

    public final byte source;

    public SearchField(String normalizedText, byte source) {
        this.text = normalizedText;
        this.source = source;
        this.mask = TextNormalizer.charMask(normalizedText);

        int words = normalizedText.isEmpty() ? 0 : 1;
        for (int i = 0; i < normalizedText.length(); i++) {
            if (normalizedText.charAt(i) == ' ') {
                words++;
            }
        }

        int[] starts = new int[words];
        if (words > 0) {
            starts[0] = 0;
            int w = 1;
            for (int i = 0; i < normalizedText.length(); i++) {
                if (normalizedText.charAt(i) == ' ') {
                    starts[w++] = i + 1;
                }
            }
        }
        this.wordStarts = starts;

        if (words >= 2) {
            StringBuilder ini = new StringBuilder(words);
            StringBuilder flat = new StringBuilder(normalizedText.length());
            for (int i = 0; i < words; i++) {
                int s = starts[i];
                int e = wordEnd(normalizedText, starts, i);
                if (s < e) {
                    ini.append(normalizedText.charAt(s));
                    flat.append(normalizedText, s, e);
                }
            }
            this.initials = ini.toString();
            this.compact = flat.toString();
        } else {
            this.initials = null;
            this.compact = null;
        }
    }

    public int wordEnd(int index) {
        return wordEnd(text, wordStarts, index);
    }

    private static int wordEnd(String text, int[] starts, int index) {
        return index + 1 < starts.length ? starts[index + 1] - 1 : text.length();
    }

    public int wordCount() {
        return wordStarts.length;
    }

    @Override
    public String toString() {
        return text + " (src=" + source + ')';
    }
}
