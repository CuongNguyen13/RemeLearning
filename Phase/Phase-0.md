# Phase 0: Chuẩn Bị & Xác Định Yêu Cầu

**Thời gian ước tính:** 2–3 tuần

**Mục tiêu:** Làm rõ phạm vi, kiến trúc, và giảm rủi ro kỹ thuật trước khi code.

**Tech stack liên quan:**
- Backend: Java Spring Boot, kiến trúc **microservices theo chuẩn Spring Cloud** — mỗi domain (user, lesson/recording, pronunciation, grammar, vocabulary, recommendation, dashboard...) là 1 service độc lập
- Hạ tầng Spring Cloud: Eureka (service discovery), Spring Cloud Gateway (API Gateway — điểm vào duy nhất cho frontend), Spring Cloud Config Server (config tập trung), Resilience4j/Spring Cloud Circuit Breaker (chịu lỗi khi gọi REST giữa các service)
- Database: PostgreSQL theo mô hình **database-per-service** + Redis (cache/queue) + S3-compatible storage (video/audio)
- AI/ML vendor cần chốt: Speech-to-Text, Pronunciation Assessment, LLM (GPT-4/Claude API)

**Công việc:**
- Xác định đối tượng người dùng chính (học viên cá nhân, trung tâm Anh ngữ, hay B2B)
- Xác định 2 nguồn dữ liệu ghi âm/ghi hình đầu vào: (1) ghi màn hình/webcam realtime ngay trên web app (browser-based screen/webcam recording), (2) tải lên file có sẵn từ máy local — cả hai đều phải hội tụ về cùng 1 pipeline xử lý ở Phase 1/2
- Thiết kế kiến trúc hệ thống tổng thể (system architecture diagram) — kiến trúc **microservices theo chuẩn Spring Cloud**, xác định ranh giới từng service (user-service, recording-service, pronunciation-service, grammar-service, vocabulary-service, recommendation-service, dashboard-service...), cơ chế giao tiếp đồng bộ (REST qua Gateway) và bất đồng bộ (Kafka) giữa các service
- Thiết kế shared library `common` (dạng jar nội bộ) chứa code hạ tầng dùng chung cho mọi service: S3 client, Kafka event wrapper, LLM client, STT client, error handling chuẩn hóa, security/audit — publish qua artifact repo nội bộ (Nexus/Artifactory) để từng service import
- Lựa chọn nhà cung cấp AI (Speech-to-Text, Pronunciation Scoring, LLM) — so sánh chi phí, độ chính xác, giới hạn tiếng Anh không chuẩn (non-native accent)
- Thiết kế database schema sơ bộ (users, lessons, recordings, transcripts, errors, exercises, progress) — dùng MyBatis Mapper + quản lý migration bằng Flyway
- Thiết kế UI/UX wireframe cho các màn hình chính
- Lập ngân sách chi phí AI API (rất quan trọng vì chi phí theo phút ghi âm có thể tăng nhanh)
- Chọn cơ chế xử lý bất đồng bộ cho Spring (Spring Batch, Spring Cloud Stream, hoặc RabbitMQ/Kafka + @Async)

**Deliverables:**
- Tài liệu kiến trúc hệ thống (Architecture Document)
- Database schema v1 (ERD + Flyway migration script draft)
- Wireframe/mockup UI
- Bảng so sánh & lựa chọn AI vendor

**Rủi ro cần lưu ý:**
- Chi phí AI API tăng nhanh theo số phút ghi âm
- Độ chính xác Pronunciation Analysis là phần khó nhất — cân nhắc làm POC ngay trong phase này
- Ghi màn hình realtime trên trình duyệt (Screen/MediaRecorder API) có giới hạn về codec, dung lượng chunk, và độ ổn định khi mất kết nối giữa chừng — cần POC upload theo chunk (resumable upload) sớm
- File upload từ local có thể ở nhiều định dạng/codec khác nhau (mp4, webm, mov...) — cần chuẩn hóa qua FFmpeg trước khi đưa vào pipeline chung
