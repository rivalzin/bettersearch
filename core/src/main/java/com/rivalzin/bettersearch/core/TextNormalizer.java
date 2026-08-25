package com.rivalzin.bettersearch.core;

// strips accents, so bau finds the same thing as the accented spelling
import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {
    private TextNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String lower = input.toLowerCase(Locale.ROOT);

        StringBuilder expanded = new StringBuilder(lower.length() + 4);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case 'ß':
                    expanded.append("ss");
                    break;
                case 'æ':
                    expanded.append("ae");
                    break;
                case 'œ':
                    expanded.append("oe");
                    break;
                case 'ø':
                    expanded.append('o');
                    break;
                case 'đ':
                    expanded.append('d');
                    break;
                case 'ð':
                    expanded.append('d');
                    break;
                case 'þ':
                    expanded.append("th");
                    break;
                case 'ł':
                    expanded.append('l');
                    break;
                case 'ı':
                    expanded.append('i');
                    break;
                case 'ħ':
                    expanded.append('h');
                    break;
                case 'ŋ':
                    expanded.append('n');
                    break;
                case 'å':
                    expanded.append('a');
                    break;
                case 'ʔ':
                case 'ʼ':
                case '’':
                    expanded.append(' ');
                    break;
                default:
                    expanded.append(c);
                    break;
            }
        }

        String decomposed = Normalizer.normalize(expanded, Normalizer.Form.NFKD);

        StringBuilder out = new StringBuilder(decomposed.length());
        boolean pendingSpace = false;
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            int type = Character.getType(c);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace && out.length() > 0) {
                    out.append(' ');
                }
                pendingSpace = false;
                // again: NFKD expands compatibility chars in UPPERCASE (\u2122 -> TM)
                out.append(Character.toLowerCase(c));
            } else {
                pendingSpace = true;
            }
        }
        return out.toString();
    }

    public static long charMask(String normalized) {
        long mask = 0L;
        for (int i = 0; i < normalized.length(); i++) {
            mask |= 1L << (normalized.charAt(i) & 63);
        }
        return mask;
    }
}
