package com.rivalzin.bettersearch.core;

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
            if (lower.charAt(i) == '\u00a7' && i + 1 < lower.length()
                    && "0123456789abcdefklmnorx".indexOf(lower.charAt(i + 1)) >= 0) {
                i++;
                continue;
            }
            appendExpanded(expanded, lower.charAt(i));
        }

        String decomposed = Normalizer.normalize(expanded, Normalizer.Form.NFKD);

        StringBuilder out = new StringBuilder(decomposed.length());
        boolean pendingSpace = false;

        for (int i = 0; i < decomposed.length(); ) {
            int cp = decomposed.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(cp)) {
                if (pendingSpace && out.length() > 0) {
                    out.append(' ');
                }
                pendingSpace = false;

                int low = Character.toLowerCase(cp);

                if (low == '\u0294' || low == '\u02bc' || low == '\u2019') {
                    pendingSpace = true;
                    continue;
                }
                if (low <= 0xFFFF) {
                    appendExpanded(out, (char) low);
                } else {
                    out.appendCodePoint(low);
                }
            } else {
                pendingSpace = true;
            }
        }
        return out.toString();
    }

    private static void appendExpanded(StringBuilder expanded, char c) {
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

    public static long charMask(String normalized) {
        long mask = 0L;
        for (int i = 0; i < normalized.length(); i++) {
            mask |= 1L << (normalized.charAt(i) & 63);
        }
        return mask;
    }
}
