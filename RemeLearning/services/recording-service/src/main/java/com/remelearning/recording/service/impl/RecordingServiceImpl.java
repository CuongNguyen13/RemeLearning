package com.remelearning.recording.service.impl;

import com.remelearning.common.ai.audio.AudioTranscodeClient;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.exception.ErrorCode;
import com.remelearning.common.storage.AudioContentTypes;
import com.remelearning.common.storage.S3StorageClient;
import com.remelearning.recording.domain.Recording;
import com.remelearning.recording.dto.RecordingResponse;
import com.remelearning.recording.event.RecordingUploadedEvent;
import com.remelearning.recording.kafka.RecordingUploadedProducer;
import com.remelearning.recording.mapper.RecordingMapper;
import com.remelearning.recording.service.RecordingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RecordingServiceImpl implements RecordingService {

	private static final String UPLOADED_STATUS = "UPLOADED";
	private static final String DEFAULT_LANGUAGE_CODE = "en";

	private final RecordingMapper recordingMapper;
	private final S3StorageClient s3StorageClient;
	private final AudioTranscodeClient audioTranscodeClient;
	private final RecordingUploadedProducer recordingUploadedProducer;
	private final String recordingBucket;

	// Constructor injection (incl. the reme.s3.recording-bucket property) rather than field
	// injection, so this class can be constructed directly with mocks in unit tests.
	public RecordingServiceImpl(
			RecordingMapper recordingMapper,
			S3StorageClient s3StorageClient,
			AudioTranscodeClient audioTranscodeClient,
			RecordingUploadedProducer recordingUploadedProducer,
			@Value("${reme.s3.recording-bucket}") String recordingBucket) {
		this.recordingMapper = recordingMapper;
		this.s3StorageClient = s3StorageClient;
		this.audioTranscodeClient = audioTranscodeClient;
		this.recordingUploadedProducer = recordingUploadedProducer;
		this.recordingBucket = recordingBucket;
	}

	// Validates the incoming multipart request, uploads the file to S3 under a
	// userId/recordingId/filename key, persists the recording row, then publishes
	// recording.uploaded so ai-service can pick it up for STT + diarization. Audio-only uploads
	// (content-type "audio/*") are transcoded to Opus before storage to save space; video uploads
	// are stored as-is since ai-service's vision/face-recognition steps need the original video
	// track, which a lossy audio-only transcode would strip.
	@Override
	@Transactional
	public RecordingResponse upload(MultipartFile file, String userId, String languageCode) {
		if (userId == null || userId.isBlank()) {
			throw BusinessException.badRequest("userId must not be blank");
		}
		if (file == null || file.isEmpty()) {
			throw BusinessException.badRequest("file must not be empty");
		}

		String recordingId = UUID.randomUUID().toString();
		String resolvedLanguageCode = (languageCode == null || languageCode.isBlank())
				? DEFAULT_LANGUAGE_CODE : languageCode;
		boolean isAudioOnly = file.getContentType() != null
				&& file.getContentType().toLowerCase().startsWith("audio/");
		String s3Key = isAudioOnly
				? userId + "/" + recordingId + "/" + stripExtension(file.getOriginalFilename()) + AudioContentTypes.OPUS_EXTENSION
				: userId + "/" + recordingId + "/" + file.getOriginalFilename();
		String storedContentType = isAudioOnly ? "audio/ogg" : file.getContentType();

		try {
			if (isAudioOnly) {
				byte[] opusBytes = audioTranscodeClient.toOpus(file.getInputStream(), file.getOriginalFilename());
				s3StorageClient.upload(recordingBucket, s3Key, new ByteArrayInputStream(opusBytes), opusBytes.length);
			} else {
				s3StorageClient.upload(recordingBucket, s3Key, file.getInputStream(), file.getSize());
			}
		} catch (Exception e) {
			log.error("Failed to upload recording {} to s3://{}/{}", recordingId, recordingBucket, s3Key, e);
			throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR,
					"Failed to upload recording to S3", HttpStatus.BAD_GATEWAY);
		}

		Recording recording = Recording.builder()
				.recordingId(recordingId)
				.userId(userId)
				.s3Bucket(recordingBucket)
				.s3Key(s3Key)
				.languageCode(resolvedLanguageCode)
				.originalFilename(file.getOriginalFilename())
				.contentType(storedContentType)
				.status(UPLOADED_STATUS)
				.build();
		recordingMapper.insert(recording);

		recordingUploadedProducer.publish(RecordingUploadedEvent.builder()
				.recordingId(recordingId)
				.userId(userId)
				.s3Bucket(recordingBucket)
				.s3Key(s3Key)
				.languageCode(resolvedLanguageCode)
				.build());

		log.info("Stored recording {} for user {} at s3://{}/{}", recordingId, userId, recordingBucket, s3Key);
		return toResponse(recording);
	}

	// Loads a single recording by its public recordingId, or throws a 404-mapped BusinessException
	// if none was ever stored.
	@Override
	public RecordingResponse getByRecordingId(String recordingId) {
		Recording recording = recordingMapper.findByRecordingId(recordingId)
				.orElseThrow(() -> BusinessException.notFound("Recording not found for recordingId=" + recordingId));
		return toResponse(recording);
	}

	// Lists every recording uploaded by a given user, most recent first.
	@Override
	public List<RecordingResponse> getByUserId(String userId) {
		return recordingMapper.findByUserId(userId).stream()
				.map(this::toResponse)
				.toList();
	}

	// Drops the extension from an uploaded filename (e.g. "clip.wav" -> "clip") so the Opus
	// transcode can be stored under the same basename with a ".opus" extension instead.
	private static String stripExtension(String filename) {
		if (filename == null) {
			return "audio";
		}
		int dotIndex = filename.lastIndexOf('.');
		return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
	}

	private RecordingResponse toResponse(Recording recording) {
		return new RecordingResponse(recording.getRecordingId(), recording.getUserId(), recording.getStatus(),
				recording.getS3Bucket(), recording.getS3Key(), recording.getCreatedAt());
	}
}
