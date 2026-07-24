package com.remelearning.english.speaking.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.speaking.history.dto.SpeakingHistoryEntryDto;
import com.remelearning.english.speaking.history.service.SpeakingHistoryService;
import com.remelearning.english.speaking.dto.GenerateSpeakingPracticeRequest;
import com.remelearning.english.speaking.dto.SpeakingAttemptDetailDto;
import com.remelearning.english.speaking.dto.SpeakingAttemptHistoryEntryDto;
import com.remelearning.english.speaking.dto.SpeakingAttemptResultDto;
import com.remelearning.english.speaking.dto.SpeakingAudioResource;
import com.remelearning.english.speaking.dto.SpeakingPracticeItemDto;
import com.remelearning.english.speaking.service.SpeakingLearnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Speaking Learn", description = "AI-generated speaking-practice sentences (Supertonic sample audio) and GOP-scored recorded attempts")
@RestController
@RequestMapping("/api/v1/learn/speaking")
@RequiredArgsConstructor
public class SpeakingLearnController {

	private final SpeakingLearnService speakingLearnService;
	private final SpeakingHistoryService speakingHistoryService;

	@Operation(summary = "Generate one AI speaking-practice sentence with a Supertonic sample recording, targeting the given focus words or (if omitted) the learner's own top pronunciation weak points")
	@PostMapping("/{userId}/generate")
	public ApiResponse<SpeakingPracticeItemDto> generate(
			@PathVariable String userId, @RequestBody(required = false) GenerateSpeakingPracticeRequest request) {
		return ApiResponse.ok(speakingLearnService.generate(userId, request == null ? new GenerateSpeakingPracticeRequest() : request));
	}

	@Operation(summary = "Full detail (target text + sample audio URL) for one practice item")
	@GetMapping("/items/{itemId}")
	public ApiResponse<SpeakingPracticeItemDto> getItem(@PathVariable Long itemId) {
		return ApiResponse.ok(speakingLearnService.getItem(itemId));
	}

	@Operation(summary = "A learner's generated practice items, newest first")
	@GetMapping("/{userId}/items")
	public ApiResponse<List<SpeakingPracticeItemDto>> listItems(@PathVariable String userId) {
		return ApiResponse.ok(speakingLearnService.listItems(userId));
	}

	@Operation(summary = "Stream one practice item's Supertonic sample (model) audio")
	@GetMapping("/items/{itemId}/sample-audio")
	public ResponseEntity<InputStreamResource> getSampleAudio(@PathVariable Long itemId) {
		SpeakingAudioResource audio = speakingLearnService.loadSampleAudio(itemId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(audio.contentType()))
				.contentLength(audio.contentLength())
				.header("Content-Disposition", ContentDisposition.inline().filename(audio.filename()).build().toString())
				.body(new InputStreamResource(audio.stream()));
	}

	@Operation(summary = "Submit a learner's recorded attempt (multipart audio); scores it via ai-service's wav2vec2 GOP model and feeds each scored word into the existing weak-point/spaced-repetition pipeline")
	@PostMapping(value = "/{userId}/attempts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<SpeakingAttemptResultDto> submit(
			@PathVariable String userId,
			@RequestParam Long practiceItemId,
			@RequestParam MultipartFile audio) {
		return ApiResponse.ok(speakingLearnService.submit(userId, practiceItemId, audio));
	}

	@Operation(summary = "A learner's past speaking-practice attempts, newest first")
	@GetMapping("/history/{userId}")
	public ApiResponse<List<SpeakingAttemptHistoryEntryDto>> getHistory(@PathVariable String userId) {
		return ApiResponse.ok(speakingLearnService.getHistory(userId));
	}

	@Operation(summary = "Full detail for one of a learner's own past attempts")
	@GetMapping("/history/{userId}/{attemptId}")
	public ApiResponse<SpeakingAttemptDetailDto> getAttemptDetail(@PathVariable String userId, @PathVariable Long attemptId) {
		return ApiResponse.ok(speakingLearnService.getAttemptDetail(userId, attemptId));
	}

	@Operation(summary = "Generate AI practice targeted at one specific past attempt's mispronounced phonemes (the \"Luyện tập với AI\" action from a history row)")
	@PostMapping("/history/{userId}/{attemptId}/ai-practice")
	public ApiResponse<List<SpeakingPracticeItemDto>> generateFromAttempt(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return ApiResponse.ok(speakingLearnService.generatePracticeFromAttempt(userId, attemptId));
	}

	@Operation(summary = "A learner's merged speaking history: \"học thường\" attempts + Thư viện sentence attempts in one time-sorted list, tagged by source")
	@GetMapping("/merged-history/{userId}")
	public ApiResponse<List<SpeakingHistoryEntryDto>> getMergedHistory(@PathVariable String userId) {
		return ApiResponse.ok(speakingHistoryService.getMergedHistory(userId));
	}
}
