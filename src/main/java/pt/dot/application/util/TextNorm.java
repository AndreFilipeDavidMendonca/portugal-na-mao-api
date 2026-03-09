package pt.dot.application.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TextNorm {

    private TextNorm() {}

    public static String normalize(String s) {
        if (s == null || s.isBlank()) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}+", "");
        n = n.replaceAll("[^a-z0-9]+", " ").trim();
        return n;
    }

    public static List<String> tokensOf(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(normalize(s).split("\\s+"))
                .filter(t -> t.length() >= 3)
                .toList();
    }
}