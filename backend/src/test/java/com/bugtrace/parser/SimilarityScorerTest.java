package com.bugtrace.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SimilarityScorerTest {

    private final SimilarityScorer scorer = new SimilarityScorer();

    @Test
    void identicalMessagesScoreOne() {
        assertThat(scorer.score("database connection failed", "database connection failed"))
                .isCloseTo(1.0, within(0.0001));
    }

    @Test
    void unrelatedMessagesScoreZero() {
        assertThat(scorer.score("database connection failed", "health check passed"))
                .isEqualTo(0.0);
    }

    @Test
    void partialOverlapScoresBetweenZeroAndOne() {
        double score = scorer.score(
                "database connection failed for payment service",
                "database connection timeout for order service");

        assertThat(score).isBetween(0.4, 0.9);
    }

    @Test
    void ignoresStopWordsAndShortTokens() {
        assertThat(scorer.tokens("the a an is database failed")).containsExactlyInAnyOrder("database", "failed");
    }
}
