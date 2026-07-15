package com.remelearning.recording.service.impl;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.S3StorageClient;
import com.remelearning.recording.domain.Recording;
import com.remelearning.recording.dto.RecordingResponse;
import com.remelearning.recording.event.RecordingUploadedEvent;
import com.remelearning.recording.kafka.RecordingUploadedProducer;
import com.remelearning.recording.mapper.RecordingMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingServiceImplTest {

	private static final String RECORDING_BUCKET = "reme-recordings";

	private final RecordingMapper recordingMapper = mock(RecordingMapper.class);
	private final S3StorageClient s3StorageClient = mock(S3StorageClient.class);
	private final RecordingUploadedProducer recordingUploadedProducer = mock(RecordingUploadedProducer.class);
	private final RecordingServiceImpl service = new RecordingServiceImpl(
			recordingMapper, s3StorageClient, recordingUploadedProducer, RECORDING_BUCKET);

	@Test
	void uploadStoresFileInsertsRecordingAndPublishesEvent() {
		byte[] content = "content".getBytes();
		MockMultipartFile file = new MockMultipartFile("file", "lesson.mp4", "video/mp4", content);

		RecordingResponse response = service.upload(file, "user-1", "en");

		assertThat(response.userId()).isEqualTo("user-1");
		assertThat(response.status()).isEqualTo("UPLOADED");
		assertThat(response.s3Bucket()).isEqualTo(RECORDING_BUCKET);
		assertThat(response.s3Key()).contains("user-1").contains("lesson.mp4");
		assertThat(response.recordingId()).isNotBlank();

		verify(s3StorageClient, times(1))
				.upload(eq(RECORDING_BUCKET), any(), any(), eq((long) content.length));
		verify(recordingMapper, times(1)).insert(any(Recording.class));
		verify(recordingUploadedProducer, times(1)).publish(any(RecordingUploadedEvent.class));
	}

	@Test
	void uploadDefaultsLanguageCodeWhenBlank() {
		MockMultipartFile file = new MockMultipartFile("file", "lesson.mp4", "video/mp4", "content".getBytes());

		service.upload(file, "user-1", " ");

		verify(recordingMapper, times(1)).insert(argThat(r -> "en".equals(r.getLanguageCode())));
	}

	@Test
	void uploadRejectsBlankUserId() {
		MockMultipartFile file = new MockMultipartFile("file", "lesson.mp4", "video/mp4", "content".getBytes());

		assertThatThrownBy(() -> service.upload(file, " ", "en"))
				.isInstanceOf(BusinessException.class);

		verify(recordingMapper, never()).insert(any());
		verify(recordingUploadedProducer, never()).publish(any());
	}

	@Test
	void uploadRejectsEmptyFile() {
		MockMultipartFile emptyFile = new MockMultipartFile("file", "lesson.mp4", "video/mp4", new byte[0]);

		assertThatThrownBy(() -> service.upload(emptyFile, "user-1", "en"))
				.isInstanceOf(BusinessException.class);

		verify(recordingMapper, never()).insert(any());
		verify(recordingUploadedProducer, never()).publish(any());
	}

	@Test
	void getByRecordingIdThrowsWhenNotFound() {
		when(recordingMapper.findByRecordingId("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getByRecordingId("missing"))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void getByRecordingIdReturnsResponseWhenFound() {
		Recording recording = Recording.builder()
				.recordingId("rec-1")
				.userId("user-1")
				.s3Bucket(RECORDING_BUCKET)
				.s3Key("user-1/rec-1/lesson.mp4")
				.languageCode("en")
				.status("UPLOADED")
				.build();
		when(recordingMapper.findByRecordingId("rec-1")).thenReturn(Optional.of(recording));

		RecordingResponse response = service.getByRecordingId("rec-1");

		assertThat(response.recordingId()).isEqualTo("rec-1");
		assertThat(response.userId()).isEqualTo("user-1");
	}

	@Test
	void getByUserIdDelegatesToMapper() {
		Recording recording = Recording.builder().recordingId("rec-1").userId("user-1").status("UPLOADED").build();
		when(recordingMapper.findByUserId("user-1")).thenReturn(List.of(recording));

		List<RecordingResponse> responses = service.getByUserId("user-1");

		assertThat(responses).hasSize(1);
		assertThat(responses.get(0).recordingId()).isEqualTo("rec-1");
	}
}
