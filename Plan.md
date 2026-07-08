# Kế Hoạch Phát Triển: AI-Powered English Learning Analysis Platform

## 1. Tổng Quan Dự Án

**Mục tiêu:** Xây dựng nền tảng học tiếng Anh trực tuyến có khả năng ghi hình buổi học, dùng AI để phân tích phát âm, ngữ pháp, từ vựng của học viên, từ đó phát hiện những từ vựng và cấu trúc ngữ pháp mà học viên hay quên hoặc dùng sai lặp đi lặp lại nhiều lần, và tự động đề xuất, tạo bài luyện tập cá nhân hóa để củng cố đúng những điểm yếu lặp lại đó.

**Chiến lược triển khai:** Chia thành 8 giai đoạn (Phase), đi từ hạ tầng cơ bản → xử lý AI cốt lõi → cá nhân hóa → dashboard → hoàn thiện & vận hành. Mỗi phase đều cho ra một sản phẩm chạy được (MVP tăng dần), tránh việc phải chờ toàn bộ hệ thống xong mới test được.

**Gợi ý tech stack tổng thể** (có thể điều chỉnh theo năng lực đội ngũ):
- Frontend: React/Next.js
- Backend: Node.js (NestJS) hoặc Python (FastAPI) — Python phù hợp hơn nếu xử lý AI nhiều ở backend
- Database: PostgreSQL (dữ liệu quan hệ) + Redis (cache/queue) + S3-compatible storage (video/audio)
- AI/ML: OpenAI Whisper hoặc AWS Transcribe (Speech-to-Text), Azure Pronunciation Assessment hoặc mô hình tự huấn luyện (Pronunciation), LLM (GPT-4/Claude API) cho Grammar & Vocabulary analysis
- Video: WebRTC (ghi hình realtime) + FFmpeg (xử lý video)
- Hạ tầng: Docker, CI/CD (GitHub Actions), Cloud (AWS/GCP/Azure)

---

## Phase 0: Chuẩn Bị & Xác Định Yêu Cầu (2–3 tuần)

**Mục tiêu:** Làm rõ phạm vi, kiến trúc, và giảm rủi ro kỹ thuật trước khi code.

**Công việc:**
- Xác định đối tượng người dùng chính (học viên cá nhân, trung tâm Anh ngữ, hay B2B)
- Thiết kế kiến trúc hệ thống tổng thể (system architecture diagram)
- Lựa chọn nhà cung cấp AI (Speech-to-Text, Pronunciation Scoring, LLM) — so sánh chi phí, độ chính xác, giới hạn tiếng Anh không chuẩn (non-native accent)
- Thiết kế database schema sơ bộ (users, lessons, recordings, transcripts, errors, exercises, progress)
- Thiết kế UI/UX wireframe cho các màn hình chính
- Lập ngân sách chi phí AI API (rất quan trọng vì chi phí theo phút ghi âm có thể tăng nhanh)

**Deliverables:**
- Tài liệu kiến trúc hệ thống (Architecture Document)
- Database schema v1
- Wireframe/mockup UI
- Bảng so sánh & lựa chọn AI vendor

---

## Phase 1: Hạ Tầng Cốt Lõi & Lesson Recording (4–6 tuần)

**Mục tiêu:** Xây dựng nền tảng cơ bản: đăng ký/đăng nhập, tạo lớp học, ghi hình, lưu trữ và phát lại video.

**Công việc:**
- Authentication & phân quyền (học viên / giáo viên / admin)
- Module quản lý lớp học (tạo lớp, lịch học, danh sách học viên)
- Tích hợp ghi hình video buổi học (WebRTC hoặc tích hợp Zoom/Google Meet recording API)
- Upload & lưu trữ video (cloud storage + CDN)
- Trang phát lại (playback) video buổi học đã ghi
- Xử lý video cơ bản: tách audio từ video (chuẩn bị cho bước Speech-to-Text)

**Deliverables:**
- Người dùng có thể đăng nhập, vào lớp học, ghi hình, và xem lại video đã ghi
- Pipeline lưu trữ video/audio hoạt động ổn định

---

## Phase 2: Speech-to-Text Processing (3–4 tuần)

**Mục tiêu:** Chuyển giọng nói học viên thành văn bản chính xác để làm đầu vào cho các bước phân tích sau.

**Công việc:**
- Tích hợp AI Speech-to-Text (Whisper/AWS Transcribe/Google STT)
- Xử lý hàng đợi (queue/worker) để xử lý audio bất đồng bộ (tránh block hệ thống khi video dài)
- Tách người nói (speaker diarization) nếu có nhiều người trong buổi học (học viên vs giáo viên)
- Gắn timestamp cho từng câu nói để liên kết lại với video gốc
- Lưu transcript vào database, hiển thị transcript kèm video khi phát lại

**Deliverables:**
- Transcript tự động sinh ra sau mỗi buổi học, đồng bộ thời gian với video
- Hệ thống queue xử lý bất đồng bộ ổn định, có retry khi lỗi

---

## Phase 3: Pronunciation Analysis (4–6 tuần)

**Mục tiêu:** Phát hiện lỗi phát âm ở cấp độ từ và âm vị (phoneme).

**Công việc:**
- Tích hợp công cụ chấm điểm phát âm (Azure Pronunciation Assessment, hoặc mô hình phoneme alignment như Montreal Forced Aligner + mô hình so sánh)
- So sánh phát âm học viên với chuẩn phát âm bản ngữ (native reference)
- Sinh điểm số & feedback chi tiết theo từng từ/âm vị
- Xây dựng giao diện hiển thị lỗi phát âm trực quan (highlight từ sai, gợi ý cách phát âm đúng)

**Deliverables:**
- Báo cáo phát âm chi tiết cho mỗi buổi học (từ nào sai, âm nào sai, gợi ý cải thiện)

**Lưu ý rủi ro:** Đây là phần khó nhất về mặt kỹ thuật AI — nên làm POC (proof of concept) sớm để kiểm chứng độ chính xác trước khi đầu tư nhiều.

---

## Phase 4: Grammar Evaluation (3–4 tuần)

**Mục tiêu:** Phát hiện lỗi ngữ pháp trong câu nói và đề xuất sửa lỗi.

**Công việc:**
- Dùng LLM (GPT-4/Claude API) với prompt engineering chuyên biệt để phân tích transcript, tìm lỗi ngữ pháp
- Phân loại lỗi (thì, chủ vị, giới từ, mạo từ, cấu trúc câu...)
- Sinh giải thích lỗi bằng ngôn ngữ dễ hiểu + câu đúng gợi ý
- Xây dựng cơ chế cache/kiểm soát chi phí gọi LLM (tránh gọi API quá nhiều lần trùng lặp)

**Deliverables:**
- Danh sách lỗi ngữ pháp kèm giải thích và câu sửa đúng cho mỗi buổi học

---

## Phase 5: Vocabulary Analysis (2–3 tuần)

**Mục tiêu:** Phát hiện từ vựng dùng sai hoặc lặp lại nhiều, gợi ý từ thay thế.

**Công việc:**
- Phân tích tần suất từ vựng sử dụng qua các buổi học (dùng LLM + thống kê)
- Phát hiện từ dùng sai ngữ cảnh (collocation errors)
- Gợi ý từ đồng nghĩa/thay thế phù hợp ngữ cảnh, kèm ví dụ minh họa
- Theo dõi xu hướng từ vựng theo thời gian (từ nào hay bị lặp, từ nào mới học được)

**Deliverables:**
- Báo cáo từ vựng: từ dùng sai, từ lặp lại nhiều, gợi ý thay thế

---

## Phase 6: Personalized Learning Recommendations (4–5 tuần)

**Mục tiêu:** Tự động tạo bài luyện tập cá nhân hóa dựa trên các lỗi đã phát hiện.

**Công việc:**
- Xây dựng engine tổng hợp lỗi từ 3 module (phát âm, ngữ pháp, từ vựng) thành hồ sơ điểm yếu của từng học viên
- Sinh bài tập luyện tập tự động bằng LLM (câu luyện phát âm, bài tập ngữ pháp, flashcard từ vựng)
- Cơ chế theo dõi tiến độ luyện tập (đã làm bao nhiêu bài, đúng bao nhiêu %)
- Thuật toán điều chỉnh độ khó/nội dung bài tập theo thời gian (adaptive learning)

**Deliverables:**
- Học viên nhận được bộ bài tập cá nhân hóa sau mỗi buổi học, tự động cập nhật theo tiến độ

---

## Phase 7: Learning Dashboard (3–4 tuần)

**Mục tiêu:** Trực quan hóa dữ liệu học tập, giúp học viên và giáo viên theo dõi tiến bộ.

**Công việc:**
- Thiết kế dashboard hiển thị: độ chính xác phát âm theo thời gian, tỷ lệ lỗi ngữ pháp giảm dần, vốn từ vựng tăng trưởng
- Biểu đồ xu hướng lỗi (error trends) theo tuần/tháng
- Báo cáo tổng hợp cho giáo viên/phụ huynh (nếu có)
- Xuất báo cáo (PDF/Excel) nếu cần

**Deliverables:**
- Dashboard đầy đủ với biểu đồ trực quan cho học viên và giáo viên

---

## Phase 8: Kiểm Thử, Tối Ưu & Ra Mắt (4–6 tuần)

**Mục tiêu:** Đảm bảo chất lượng, hiệu năng, và sẵn sàng vận hành thực tế.

**Công việc:**
- Kiểm thử toàn diện (unit test, integration test, load test cho pipeline xử lý AI)
- Tối ưu chi phí AI (giảm gọi API dư thừa, cache kết quả, xử lý song song)
- Kiểm thử bảo mật dữ liệu (video/audio học viên là dữ liệu nhạy cảm — cần mã hóa, tuân thủ quy định bảo mật)
- Beta test với nhóm người dùng thật, thu thập phản hồi
- Chuẩn bị hạ tầng production (auto-scaling, monitoring, alerting)
- Ra mắt chính thức (soft launch → full launch)

**Deliverables:**
- Sản phẩm ổn định, sẵn sàng phục vụ người dùng thật với khả năng mở rộng

---

## 2. Tổng Kết Thời Gian Dự Kiến

| Giai đoạn | Nội dung | Thời gian ước tính |
|---|---|---|
| Phase 0 | Chuẩn bị & yêu cầu | 2–3 tuần |
| Phase 1 | Hạ tầng & Recording | 4–6 tuần |
| Phase 2 | Speech-to-Text | 3–4 tuần |
| Phase 3 | Pronunciation Analysis | 4–6 tuần |
| Phase 4 | Grammar Evaluation | 3–4 tuần |
| Phase 5 | Vocabulary Analysis | 2–3 tuần |
| Phase 6 | Personalized Recommendations | 4–5 tuần |
| Phase 7 | Learning Dashboard | 3–4 tuần |
| Phase 8 | Testing & Launch | 4–6 tuần |
| **Tổng** | | **~30–41 tuần (~7–10 tháng)** |

*Lưu ý: Một số phase có thể chạy song song (ví dụ Phase 4, 5 có thể làm cùng lúc với Phase 3 nếu có đủ nhân lực) để rút ngắn tổng thời gian.*

## 3. Rủi Ro Cần Lưu Ý

- **Chi phí AI API:** Speech-to-Text + LLM + Pronunciation scoring chạy trên số lượng lớn video có thể tốn chi phí đáng kể — cần tính toán kỹ trước khi scale.
- **Độ chính xác Pronunciation Analysis:** Đây là phần khó nhất, nên làm POC/thử nghiệm sớm ở Phase 0 hoặc đầu Phase 3.
- **Độ trễ xử lý:** Video dài → xử lý AI mất thời gian → cần thiết kế hệ thống bất đồng bộ (queue/background job) ngay từ đầu.
- **Bảo mật dữ liệu:** Video/audio học viên là dữ liệu cá nhân nhạy cảm, cần chính sách lưu trữ và mã hóa rõ ràng.

## 4. Gợi Ý Bước Tiếp Theo

1. Xác nhận lại phạm vi MVP (Phase 0–3 là đủ để có bản demo giá trị, hay cần đầy đủ tới Phase 6?)
2. Chốt tech stack và nhà cung cấp AI
3. Bắt đầu Phase 0 với tài liệu kiến trúc chi tiết
