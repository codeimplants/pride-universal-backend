package com.prideuniversal.domain.vehicle;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Number-plate text: what a flat registered (free text such as {@code "MH 12 AB 1234, MH14CD5678"})
 * compared with what a guard typed or the camera read.
 *
 * <p>Three strengths of match, strongest first:
 * <ul>
 *   <li>{@link Match#EXACT}: the same characters once spaces, dashes and case are ignored.</li>
 *   <li>{@link Match#CLOSE}: equal after folding the characters OCR confuses (O/0, I/1, B/8, ...),
 *       or the scan contains the whole registered plate (the "IND" strip on the plate gets read too).</li>
 *   <li>{@link Match#PARTIAL}: the guard typed only the end of the plate, usually its four digits.</li>
 * </ul>
 * Folding is applied to both sides, so it never needs to know which side was misread.
 */
public final class Plates {

    private Plates() {}

    /** Shortest query worth answering; anything shorter matches too much of the register. */
    public static final int MIN_QUERY = 4;

    /** Below this a registered plate is too short to be found inside a longer scan. */
    private static final int MIN_CONTAINED = 8;

    public enum Match {
        EXACT, CLOSE, PARTIAL;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** {@code "mh-12 ab 1234"} → {@code "MH12AB1234"}. Null-safe. */
    public static String normalise(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** The plates in a flat's free-text field. Commas, semicolons, slashes and new lines separate them. */
    public static List<String> registered(String numbers) {
        if (numbers == null || numbers.isBlank()) {
            return List.of();
        }
        return Arrays.stream(numbers.split("[,;/|\\n]+"))
                .map(String::trim)
                .filter(p -> normalise(p).length() >= MIN_QUERY)
                .toList();
    }

    /** Maps the letters OCR mistakes for digits onto those digits. Expects {@link #normalise}d text. */
    static String fold(String normalised) {
        StringBuilder sb = new StringBuilder(normalised.length());
        for (char c : normalised.toCharArray()) {
            sb.append(switch (c) {
                case 'O', 'Q', 'D' -> '0';
                case 'I', 'L' -> '1';
                case 'Z' -> '2';
                case 'S' -> '5';
                case 'G' -> '6';
                case 'B' -> '8';
                default -> c;
            });
        }
        return sb.toString();
    }

    /** How well a registered plate answers a query, or null if it does not. Both arguments are raw text. */
    public static Match compare(String query, String registered) {
        String q = normalise(query);
        String r = normalise(registered);
        if (q.length() < MIN_QUERY || r.isEmpty()) {
            return null;
        }
        if (q.equals(r)) {
            return Match.EXACT;
        }
        String fq = fold(q);
        String fr = fold(r);
        if (fq.equals(fr) || (r.length() >= MIN_CONTAINED && fq.contains(fr))) {
            return Match.CLOSE;
        }
        if (q.length() < r.length() && fr.endsWith(fq)) {
            return Match.PARTIAL;
        }
        return null;
    }
}
