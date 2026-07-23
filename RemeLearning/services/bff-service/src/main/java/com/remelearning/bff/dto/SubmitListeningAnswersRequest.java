package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Request body to score a learner's submitted answer set for one listening-library section. */
@Data
public class SubmitListeningAnswersRequest {
	private List<ListeningAnswerItemDto> answers;
}
