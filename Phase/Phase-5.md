# Phase 5: Vocabulary Analysis

**Thời gian ước tính:** 2–3 tuần

**Mục tiêu:** Phát hiện từ vựng dùng sai hoặc lặp lại nhiều, gợi ý từ thay thế.

**Tech stack liên quan:**
- Spring Boot service kết hợp LLM API + xử lý thống kê (Java Streams API) để phân tích tần suất từ vựng
- MyBatis lưu lịch sử từ vựng theo học viên theo thời gian
- Apache Kafka: service `vocabulary-service` là 1 trong 3 consumer group độc lập lắng nghe topic `transcript.ready` (publish ở [[Phase-2]])

**Kafka topic:**
- Input: consume `transcript.ready` (consumer group `vocabulary-service`)
- Output: publish `vocabulary.analyzed` (payload: recordingId, misusedWords, repeatedWords, suggestions) để [[Phase-6]] tổng hợp

**Công việc:**
- Phân tích tần suất từ vựng sử dụng qua các buổi học (dùng LLM + thống kê bằng Java Streams/Collectors)
- Phát hiện từ dùng sai ngữ cảnh (collocation errors) qua LLM
- Gợi ý từ đồng nghĩa/thay thế phù hợp ngữ cảnh, kèm ví dụ minh họa
- Theo dõi xu hướng từ vựng theo thời gian (từ nào hay bị lặp, từ nào mới học được) — lưu trạng thái theo học viên trong bảng riêng (vocabulary_history)

**Deliverables:**
- Báo cáo từ vựng: từ dùng sai, từ lặp lại nhiều, gợi ý thay thế
