# Phase 3: Pronunciation Analysis

**Thời gian ước tính:** 4–6 tuần

**Mục tiêu:** Phát hiện lỗi phát âm ở cấp độ từ và âm vị (phoneme).

**Tech stack liên quan:**
- Spring Boot client tích hợp Azure Pronunciation Assessment API (hoặc mô hình phoneme alignment Montreal Forced Aligner chạy như service riêng, gọi qua REST từ Spring)
- MyBatis lưu kết quả điểm số/feedback phát âm
- Apache Kafka: service `pronunciation-service` là 1 trong 3 consumer group độc lập lắng nghe topic `transcript.ready` (publish ở [[Phase-2]])

**Kafka topic:**
- Input: consume `transcript.ready` (consumer group riêng `pronunciation-service`, không cạnh tranh với `grammar-service`/`vocabulary-service` vì mỗi service là 1 consumer group khác nhau đọc cùng topic)
- Output: publish `pronunciation.analyzed` (payload: recordingId, score, wordLevelErrors) để [[Phase-6]] tổng hợp

**Công việc:**
- Tích hợp công cụ chấm điểm phát âm (Azure Pronunciation Assessment, hoặc mô hình phoneme alignment như Montreal Forced Aligner + mô hình so sánh) qua REST client
- So sánh phát âm học viên với chuẩn phát âm bản ngữ (native reference)
- Sinh điểm số & feedback chi tiết theo từng từ/âm vị, lưu qua MyBatis Mapper
- Xây dựng REST API cho frontend hiển thị lỗi phát âm trực quan (highlight từ sai, gợi ý cách phát âm đúng)

**Deliverables:**
- Báo cáo phát âm chi tiết cho mỗi buổi học (từ nào sai, âm nào sai, gợi ý cải thiện)

**Lưu ý rủi ro:** Đây là phần khó nhất về mặt kỹ thuật AI — nên làm POC (proof of concept) sớm để kiểm chứng độ chính xác trước khi đầu tư nhiều.
