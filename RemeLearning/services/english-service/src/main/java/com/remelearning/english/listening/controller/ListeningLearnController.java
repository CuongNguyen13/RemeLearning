package com.remelearning.english.listening.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.listening.history.dto.ListeningHistoryEntryDto;
import com.remelearning.english.listening.history.service.ListeningHistoryService;
import com.remelearning.english.listening.dto.GenerateListeningPracticeRequest;
import com.remelearning.english.listening.dto.ListeningAttemptDetailDto;
import com.remelearning.english.listening.dto.ListeningAttemptHistoryEntryDto;
import com.remelearning.english.listening.dto.ListeningAttemptResultDto;
import com.remelearning.english.listening.dto.ListeningAudioResource;
import com.remelearning.english.listening.dto.ListeningPracticeItemDto;
import com.remelearning.english.listening.dto.SubmitListeningAttemptRequest;
import com.remelearning.english.listening.service.ListeningLearnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Listening Learn", description = "AI-generated listening-comprehension passages (MCQ/keyword/open questions) and graded attempts")
@RestController
@RequestMapping("/api/v1/learn/listening")
@RequiredArgsConstructor
public class ListeningLearnController {

	private final ListeningLearnService listeningLearnService;
	private final ListeningHistoryService listeningHistoryService;

	@Operation(summary = "Generate one AI listening passage (Gemini transcript+questions, Supertonic audio), targeting the given focus keywords or (if omitted) the learner's own recently-missed keywords")
	@PostMapping("/{userId}/generate")
	public ApiResponse<ListeningPracticeItemDto> generate(
			@PathVariable String userId, @RequestBody(required = false) GenerateListeningPracticeRequest request) {
		return ApiResponse.ok(listeningLearnService.generate(userId, request == null ? new GenerateListeningPracticeRequest() : request));
	}

	@Operation(summary = "Full detail (questions, no transcript/answers) for one practice item")
	@GetMapping("/items/{itemId}")
	public ApiResponse<ListeningPracticeItemDto> getItem(@PathVariable Long itemId) {
		return ApiResponse.ok(listeningLearnService.getItem(itemId));
	}

	@Operation(summary = "A learner's generated practice items, newest first")
	@GetMapping("/{userId}/items")
	public ApiResponse<List<ListeningPracticeItemDto>> listItems(@PathVariable String userId) {
		return ApiResponse.ok(listeningLearnService.listItems(userId));
	}

	@Operation(summary = "Stream one practice item's synthesized audio")
	@GetMapping("/items/{itemId}/audio")
	public ResponseEntity<InputStreamResource> getAudio(@PathVariable Long itemId) {
		ListeningAudioResource audio = listeningLearnService.loadAudio(itemId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(audio.contentType()))
				.contentLength(audio.contentLength())
				.header("Content-Disposition", ContentDisposition.inline().filename(audio.filename()).build().toString())
				.body(new InputStreamResource(audio.stream()));
	}

	@Operation(summary = "Grade a submitted attempt (MCQ/keyword exact+WER, open free-text via LLM); reveals the transcript/translation and feeds each graded question into the existing weak-point/spaced-repetition pipeline")
	@PostMapping("/attempts")
	public ApiResponse<ListeningAttemptResultDto> submit(@Valid @RequestBody SubmitListeningAttemptRequest request) {
		return ApiResponse.ok(listeningLearnService.submit(request));
	}

	@Operation(summary = "A learner's past listening-practice attempts, newest first")
	@GetMapping("/history/{userId}")
	public ApiResponse<List<ListeningAttemptHistoryEntryDto>> getHistory(@PathVariable String userId) {
		return ApiResponse.ok(listeningLearnService.getHistory(userId));
	}

	@Operation(summary = "Full detail for one of a learner's own past attempts")
	@GetMapping("/history/{userId}/{attemptId}")
	public ApiResponse<ListeningAttemptDetailDto> getAttemptDetail(@PathVariable String userId, @PathVariable Long attemptId) {
		return ApiResponse.ok(listeningLearnService.getAttemptDetail(userId, attemptId));
	}

	@Operation(summary = "Generate AI practice targeted at one specific past attempt's mistakes (the \"Luyện tập với AI\" action from a history row)")
	@PostMapping("/history/{userId}/{attemptId}/ai-practice")
	public ApiResponse<List<ListeningPracticeItemDto>> generateFromAttempt(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return ApiResponse.ok(listeningLearnService.generatePracticeFromAttempt(userId, attemptId));
	}

	@Operation(summary = "A learner's merged listening history: \"học thường\" attempts + Thư viện section attempts in one time-sorted list, tagged by source")
	@GetMapping("/merged-history/{userId}")
	public ApiResponse<List<ListeningHistoryEntryDto>> getMergedHistory(@PathVariable String userId) {
		return ApiResponse.ok(listeningHistoryService.getMergedHistory(userId));
	}
}
