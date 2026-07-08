# Phase 6: Personalized Learning Recommendations

**Thời gian ước tính:** 4–5 tuần

**Mục tiêu:** Tự động tạo bài luyện tập cá nhân hóa dựa trên các lỗi đã phát hiện.

**Tech stack liên quan:**
- Spring Boot service tổng hợp dữ liệu (aggregation) từ 3 module: pronunciation-service, grammar-service, vocabulary-service
- Apache Kafka Streams (hoặc consumer thường + state tạm trong Redis) để join 3 event stream theo recordingId
- LLM API để sinh bài tập tự động

**Kafka topic & luồng xử lý:**
- 3 module ở Phase 3/4/5 mỗi module xong việc thì publish: `pronunciation.analyzed`, `grammar.analyzed`, `vocabulary.analyzed` (đều key theo `recordingId`)
- `recommendation-service` là consumer group lắng nghe cả 3 topic. Vì 3 kết quả trả về độc lập và không đồng thời, dùng 1 trong 2 cách:
  - **Kafka Streams join**: dùng windowed join (ví dụ time window 30 phút) trên `recordingId` để chờ đủ 3 event rồi mới trigger tổng hợp
  - **Đơn giản hơn**: mỗi lần nhận 1 event thì `UPSERT` kết quả vào bảng tạm `analysis_status` (recordingId, pronunciationDone, grammarDone, vocabularyDone) trong Redis/PostgreSQL; khi cả 3 cờ đều true thì mới gọi LLM tổng hợp bài tập — cách này dễ triển khai và debug hơn Kafka Streams join, phù hợp khi team chưa quen Kafka Streams
- Sau khi tổng hợp xong, publish topic `recommendation.generated` để notification-service báo cho học viên (email/push) và dashboard-service cập nhật số liệu

**Công việc:**
- Xây dựng engine tổng hợp lỗi từ 3 module (phát âm, ngữ pháp, từ vựng) thành hồ sơ điểm yếu của từng học viên (student weakness profile) trong PostgreSQL qua MyBatis Mapper, trigger qua Kafka listener khi đủ 3 kết quả
- Sinh bài tập luyện tập tự động bằng LLM (câu luyện phát âm, bài tập ngữ pháp, flashcard từ vựng)
- Cơ chế theo dõi tiến độ luyện tập (đã làm bao nhiêu bài, đúng bao nhiêu %) qua REST API
- Thuật toán điều chỉnh độ khó/nội dung bài tập theo thời gian (adaptive learning) — có thể implement dạng rule-based service trong Spring, nâng cấp dần
- Xử lý trường hợp timeout: nếu sau X phút vẫn chưa đủ 3 kết quả (1 module lỗi/chậm), có cơ chế fallback tổng hợp với dữ liệu hiện có + retry module còn thiếu

**Deliverables:**
- Học viên nhận được bộ bài tập cá nhân hóa sau mỗi buổi học, tự động cập nhật theo tiến độ
- Cơ chế tổng hợp event Kafka từ 3 module chạy ổn định, có xử lý timeout/fallback
