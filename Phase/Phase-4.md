# Phase 4: Grammar Evaluation

**Thời gian ước tính:** 3–4 tuần

**Mục tiêu:** Phát hiện lỗi ngữ pháp trong câu nói và đề xuất sửa lỗi.

**Tech stack liên quan:**
- Spring Boot service gọi LLM API (GPT-4/Claude API) qua RestTemplate/WebClient
- Spring Cache + Redis để cache kết quả LLM (giảm gọi API trùng lặp)
- Apache Kafka: service `grammar-service` là 1 trong 3 consumer group độc lập lắng nghe topic `transcript.ready` (publish ở [[Phase-2]])

**Kafka topic:**
- Input: consume `transcript.ready` (consumer group `grammar-service`)
- Output: publish `grammar.analyzed` (payload: recordingId, errorList, correctedSentences) để [[Phase-6]] tổng hợp

**Công việc:**
- Dùng LLM (GPT-4/Claude API) với prompt engineering chuyên biệt để phân tích transcript, tìm lỗi ngữ pháp — gọi qua Spring WebClient (reactive) hoặc RestTemplate
- Phân loại lỗi (thì, chủ vị, giới từ, mạo từ, cấu trúc câu...) và lưu vào PostgreSQL qua MyBatis Mapper
- Sinh giải thích lỗi bằng ngôn ngữ dễ hiểu + câu đúng gợi ý
- Xây dựng cơ chế cache/kiểm soát chi phí gọi LLM bằng Spring Cache (Redis) — cache theo hash của transcript đoạn văn, tránh gọi API quá nhiều lần trùng lặp

**Deliverables:**
- Danh sách lỗi ngữ pháp kèm giải thích và câu sửa đúng cho mỗi buổi học
