package com.remelearning.common.library;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Shared LOCKED/IN_PROGRESS/PASSED gating for topic-based libraries
// (vocabulary, grammar, listening, speaking): a topic unlocks only once the
// immediately preceding topic (by sequence_order) has been PASSED.
public final class TopicProgressCalculator {

    private TopicProgressCalculator() {
    }

    public static Map<Long, TopicStatus> compute(
            List<Long> topicIdsBySequenceOrder, Set<Long> passedTopicIds) {
        Map<Long, TopicStatus> result = new LinkedHashMap<>();
        boolean previousPassed = true;
        for (Long topicId : topicIdsBySequenceOrder) {
            // A topic is LOCKED if the previous topic was not PASSED.
            if (!previousPassed) {
                result.put(topicId, TopicStatus.LOCKED);
                continue;
            }
            // If we reach here, the previous topic was PASSED (or is the first topic),
            // so this topic is either PASSED (if already passed) or IN_PROGRESS.
            result.put(topicId, passedTopicIds.contains(topicId)
                    ? TopicStatus.PASSED
                    : TopicStatus.IN_PROGRESS);
            previousPassed = passedTopicIds.contains(topicId);
        }
        return result;
    }
}
