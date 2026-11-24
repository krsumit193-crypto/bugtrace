package com.bugtrace.parser;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Token Jaccard similarity used only to suggest "possibly related" incidents.
 * It never merges incidents automatically.
 */
@Component
public class SimilarityScorer {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "to", "of", "in", "on", "at", "for",
            "is", "was", "were", "be", "been", "with", "from", "by", "as", "it",
            "this", "that", "has", "have", "had", "not", "no", "when", "while",
            "after", "before", "into", "out", "up", "down", "than", "then"
    );

    /**
     * @return Jaccard similarity in the range 0..1
     */
    public double score(String first, String second) {
        Set<String> left = tokens(first);
        Set<String> right = tokens(second);
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    Set<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() > 1)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toUnmodifiableSet());
    }
}
