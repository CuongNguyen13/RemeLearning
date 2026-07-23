package com.remelearning.common.library;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class TopicProgressCalculatorTest {

    @Test
    void firstTopicIsInProgressWhenNothingPassed() {
        Map<Long, TopicStatus> result = TopicProgressCalculator.compute(
            List.of(1L, 2L, 3L), Set.of());

        assertThat(result.get(1L)).isEqualTo(TopicStatus.IN_PROGRESS);
        assertThat(result.get(2L)).isEqualTo(TopicStatus.LOCKED);
        assertThat(result.get(3L)).isEqualTo(TopicStatus.LOCKED);
    }

    @Test
    void topicUnlocksOnlyAfterPreviousPassed() {
        Map<Long, TopicStatus> result = TopicProgressCalculator.compute(
            List.of(1L, 2L, 3L), Set.of(1L));

        assertThat(result.get(1L)).isEqualTo(TopicStatus.PASSED);
        assertThat(result.get(2L)).isEqualTo(TopicStatus.IN_PROGRESS);
        assertThat(result.get(3L)).isEqualTo(TopicStatus.LOCKED);
    }

    @Test
    void allPassedMeansAllPassed() {
        Map<Long, TopicStatus> result = TopicProgressCalculator.compute(
            List.of(1L, 2L, 3L), Set.of(1L, 2L, 3L));

        assertThat(result.values()).containsOnly(TopicStatus.PASSED);
    }

    @Test
    void emptyTopicListReturnsEmptyMap() {
        assertThat(TopicProgressCalculator.compute(List.of(), Set.of())).isEmpty();
    }
}
