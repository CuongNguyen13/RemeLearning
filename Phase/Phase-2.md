# Phase 2: Speech-to-Text Processing

**Thời gian ước tính:** 3–4 tuần

**Mục tiêu:** Chuyển giọng nói học viên thành văn bản chính xác để làm đầu vào cho các bước phân tích sau.

**Tech stack liên quan:**
- Spring Boot tích hợp AI Speech-to-Text (Whisper API/AWS Transcribe SDK/Google STT client)
- Apache Kafka (spring-kafka) làm message broker chính cho pipeline xử lý bất đồng bộ
- MyBatis lưu transcript vào PostgreSQL

**Kafka topic & luồng xử lý:**
- Topic `recording.uploaded` (publish ở Phase 1 khi video lưu xong lên S3) → consumer group `audio-extractor-service` tách audio bằng FFmpeg, xong publish topic `audio.extracted` (payload: recordingId, audioS3Url)
- Consumer group `stt-service` lắng nghe `audio.extracted` → gọi Whisper/AWS Transcribe/Google STT → lưu transcript vào PostgreSQL → publish topic `transcript.ready` (payload: recordingId, transcriptId, timestamps) để các service phân tích ở Phase 3/4/5 tiêu thụ
- Dùng key = `recordingId` khi publish để đảm bảo message cùng buổi học vào cùng partition, giữ thứ tự xử lý
- Dead-letter-topic (DLT) riêng, ví dụ `audio.extracted.DLT`, `transcript.ready.DLT`, cấu hình qua `DefaultErrorHandler` + retry backoff của spring-kafka để tự động retry khi gọi STT API lỗi tạm thời (timeout, rate limit)

**Công việc:**
- Tích hợp AI Speech-to-Text (Whisper/AWS Transcribe/Google STT) qua REST client trong Spring
- Xây dựng `@KafkaListener` cho consumer group `stt-service` lắng nghe topic `audio.extracted`, xử lý bất đồng bộ — tránh block hệ thống khi video dài
- Tách người nói (speaker diarization) nếu có nhiều người trong buổi học (học viên vs giáo viên)
- Gắn timestamp cho từng câu nói để liên kết lại với video gốc
- Lưu transcript vào database qua MyBatis Mapper, publish `transcript.ready`, hiển thị transcript kèm video khi phát lại
- Cấu hình retry + dead-letter-topic cho message lỗi (spring-kafka `DefaultErrorHandler`, `ExponentialBackOff`)
- Thiết lập Schema Registry (Avro/JSON Schema) để version hóa contract của các topic, tránh breaking change giữa các service khi thêm field

**Deliverables:**
- Transcript tự động sinh ra sau mỗi buổi học, đồng bộ thời gian với video
- Pipeline Kafka xử lý bất đồng bộ ổn định, có retry/DLT khi lỗi
