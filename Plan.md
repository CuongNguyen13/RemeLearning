# Kế Hoạch Phát Triển: AI-Powered English Learning Analysis Platform

## 1. Tổng Quan Dự Án

**Mục tiêu:** Xây dựng nền tảng học tiếng Anh trực tuyến có khả năng ghi hình buổi học, dùng AI để phân tích phát âm, ngữ pháp, từ vựng của học viên, từ đó phát hiện những từ vựng và cấu trúc ngữ pháp mà học viên hay quên hoặc dùng sai lặp đi lặp lại nhiều lần, và tự động đề xuất, tạo bài luyện tập cá nhân hóa để củng cố đúng những điểm yếu lặp lại đó.

**Chiến lược triển khai (đã cập nhật):** Thay vì đi tuần tự từ hạ tầng → AI → cá nhân hóa → dashboard, ưu tiên số 1 là hoàn thiện **lõi phân tích (core pipeline): Video/Audio → Text → Phát hiện từ vựng hay quên (forgetting pattern)** chạy được end-to-end, kể cả khi các phần xung quanh (auth đầy đủ, UI hoàn chỉnh, pronunciation, grammar, dashboard...) còn tối giản hoặc chưa có. Lý do: đây là phần khó nhất về AI/dữ liệu và là giá trị cốt lõi khác biệt của sản phẩm — cần validate sớm trước khi đầu tư công sức vào các module xung quanh. Sau khi lõi này chạy ổn định và cho kết quả tin cậy, mới mở rộng dần ra các phần còn lại (pronunciation, grammar, recommendation, dashboard, hạ tầng lớp học đầy đủ, vận hành).

**Hiện trạng đã có (tính đến thời điểm lập lại kế hoạch):**
- `ai-service` (Python) đã triển khai thật, không phải stub:
  - STT bằng `faster-whisper` (`app/stt/whisper_engine.py`)
  - Speaker diarization bằng `pyannote.audio` (`app/stt/diarization.py`)
  - Pipeline gộp STT + diarization theo time-overlap thành transcript có gắn speaker (`app/stt/pipeline.py`)
  - Phân tích "từ vựng/lỗi hay quên" theo mô hình suy giảm trí nhớ Ebbinghaus (`app/analysis/rule_based_analyzer.py`): tính điểm quên theo thời gian + số lần lặp lỗi, có boost khi lỗi lặp lại ngay trong transcript hiện tại, sinh gợi ý bằng tiếng Việt theo từng nhóm lỗi (ngữ pháp/từ vựng/phát âm)
  - API đồng bộ: `/api/v1/transcribe`, `/api/v1/upload`, `/api/v1/analyze`, `/health`
  - Kafka consumer/producer: nhận `recording.uploaded` → chạy STT+diarization → phát `transcript.ready`; nhận sự kiện yêu cầu phân tích → chạy `RuleBasedAnalyzer` → phát `learning.gap.analyzed`
- Các service Java (`recording-service`, `user-service`, `pronunciation-service`, `grammar-service`, `vocabulary-service`, `recommendation-service`, `dashboard-service`, `bff-service`) hiện chủ yếu là skeleton (chỉ có application class + OpenAPI config), **chưa có** controller/entity/mapper. Module `common` (event contract, cache/S3/JWT client) đã có sẵn để các service dùng.
- Chưa xác nhận `spawn_consumer_task` của Kafka consumer đã được gọi khi khởi động `app/main.py` hay chưa — cần kiểm tra/khớp nối khi bắt đầu Phase 1 dưới đây.

**Gợi ý tech stack tổng thể** (có thể điều chỉnh theo năng lực đội ngũ):
- Frontend: React/Next.js
- Backend: Java/Spring Boot (microservices hiện có) + Python FastAPI cho `ai-service`
- Database: PostgreSQL (dữ liệu quan hệ) + Redis (cache/queue) + S3-compatible storage (video/audio, MinIO cho local dev)
- AI/ML: faster-whisper (Speech-to-Text), pyannote.audio (diarization), rule-based Ebbinghaus decay cho forgetting analysis (đã có), LLM (GPT-4/Claude API) cho Grammar & Vocabulary analysis nâng cao (giai đoạn sau)
- Video: FFmpeg (xử lý audio/video), WebRTC/Zoom-Meet recording (giai đoạn sau khi cần ghi hình thật)
- Hạ tầng: Docker Compose (Kafka, Redis, MinIO, ai-service) cho local dev; CI/CD, Cloud (AWS/GCP/Azure) ở giai đoạn vận hành

---

## Phase 1 (Ưu tiên cao nhất): Hoàn thiện Core Pipeline — Video → Text → Từ Vựng Hay Quên (3–5 tuần)

**Mục tiêu:** Có một luồng end-to-end chạy thật: upload video/audio buổi học → sinh transcript có timestamp + speaker → phát hiện từ vựng/lỗi hay quên → trả kết quả xem được (qua API, chưa cần UI đẹp). Đây là phần "lõi phân tích" mà toàn bộ giá trị sản phẩm phụ thuộc vào. Chia nhỏ thành 6 bước để dễ theo dõi tiến độ, mỗi bước có milestone riêng thay vì chờ tới cuối mới verify.

### Phase 1.1 — Xác nhận hạ tầng AI hiện có (2–3 ngày)

**Việc cần làm:**
- Kiểm tra `app/main.py` của `ai-service`: xác nhận `spawn_consumer_task` cho cả 2 consumer (`recording_uploaded`, `analysis_requested`) được gọi thật trong lifespan/startup, không chỉ định nghĩa mà chưa khởi động
- Chạy `docker-compose up` (Kafka, Redis, MinIO, ai-service) ở local, xác nhận `ai-service` kết nối được Kafka/Redis/MinIO, không lỗi ở startup
- Publish thử 1 message `recording.uploaded` giả (qua CLI/script) và xác nhận consumer nhận được, log ra đúng luồng

**Milestone:** `ai-service` khởi động sạch, tự nhận event Kafka mà không cần gọi API `/api/v1/*` thủ công.

### Phase 1.2 — Recording ingestion tối thiểu (`recording-service`) (1–1.5 tuần)

**Việc cần làm:**
- Tạo API `POST /api/v1/recordings` trong `recording-service` (Java, hiện là skeleton): nhận file audio/video, upload lên S3/MinIO dùng `S3StorageClient` có sẵn trong `common`
- Sau khi upload xong, publish event `recording.uploaded` dùng `EventPublisher`/`KafkaEventPublisher` có sẵn (đúng schema `BaseEvent` + payload path/recordingId)
- **Không** làm module lớp học/lịch học/danh sách học viên ở bước này — chỉ cần 1 `recordingId` giả định hoặc truyền thẳng qua request

**Milestone:** Upload file thật qua API → thấy file trên MinIO → thấy event `recording.uploaded` tới được `ai-service` (log xác nhận).

### Phase 1.3 — Lưu trữ transcript (1 tuần)

**Việc cần làm:**
- Chọn 1 service Java để consume `transcript.ready` (đề xuất: `vocabulary-service`, vì đây là service sẽ dùng transcript nhiều nhất — tránh việc mỗi phase sau lại phải quyết định lại)
- Thiết kế bảng `transcripts`/`transcript_segments` (Postgres, Flyway migration), lưu full text + segments (timestamp, speaker, text)
- Expose API `GET /api/v1/transcripts/{recordingId}` trả transcript đã lưu

**Milestone:** Sau khi upload (Phase 1.2), gọi API GET thấy transcript đầy đủ, đúng segment/speaker/timestamp.

### Phase 1.4 — Lưu trữ & expose "từ vựng hay quên" (1 tuần)

**Việc cần làm:**
- Cùng service ở Phase 1.3, thêm consumer cho `learning.gap.analyzed`, lưu danh sách `weak_points` (label, category, forgetting-score, recommendation) vào Postgres
- Expose API `GET /api/v1/weak-points/{studentId}` (hoặc theo `recordingId` nếu chưa có khái niệm student rõ ràng) trả danh sách điểm yếu sắp theo forgetting-score giảm dần

**Milestone:** Từ 1 buổi học đã upload, gọi API thấy đúng danh sách từ vựng/lỗi hay quên kèm điểm số và gợi ý.

### Phase 1.5 — Kiểm chứng chất lượng & tinh chỉnh (0.5–1 tuần)

**Việc cần làm:**
- Test lại độ chính xác forgetting-score với dữ liệu lỗi mẫu thật (không chỉ dữ liệu giả định) — tinh chỉnh tham số decay (hiện đang dùng hằng số 7 ngày) nếu có dữ liệu thực
- Viết unit test cho `rule_based_analyzer.py` và pipeline STT/diarization (tận dụng `RawSegment`/`SpeakerTurn` đã tách khỏi ML deps theo convention trong CLAUDE.md)
- Đo thời gian xử lý thực tế cho video dài (vd. 30–60 phút) để biết pipeline có cần thêm cơ chế queue/backpressure hay không

**Milestone:** Có test coverage cho phần phân tích lõi + số liệu thời gian xử lý thực tế ghi lại.

### Phase 1.6 — Demo end-to-end (2–3 ngày)

**Việc cần làm:**
- Chạy full luồng thật từ đầu tới cuối: upload video thật → transcript → weak-vocabulary report, không qua thao tác thủ công can thiệp giữa chừng
- Viết tài liệu ngắn mô tả accuracy/hạn chế hiện tại của forgetting analysis để làm cơ sở cải tiến sau

**Milestone:** Demo được toàn bộ Phase 1 cho 1 video mẫu, output đúng và ổn định qua 2–3 lần chạy lại.

**Deliverables tổng của Phase 1:**
- Luồng end-to-end chạy được: upload → transcript → weak-vocabulary report, verify qua API/Postman, không phụ thuộc UI
- Test coverage cho phần phân tích lõi (STT merge logic, forgetting-score calculation)
- Tài liệu ngắn mô tả accuracy/hạn chế hiện tại của forgetting analysis

**Lưu ý rủi ro:** Đây là phần quyết định thành bại của sản phẩm — nếu forgetting-score hoặc transcript không đủ tin cậy ở Phase 1.5, cần dừng lại tinh chỉnh trước khi sang Phase 2.

---

## Phase 2: Hạ Tầng Xung Quanh Tối Thiểu Cho Core (Auth cơ bản + Recording UX) (3–4 tuần)

**Mục tiêu:** Đủ hạ tầng xung quanh để người dùng thật có thể tự upload/ghi buổi học mà không cần dev thao tác thủ công qua Postman.

**Công việc:**
- Authentication cơ bản (đăng ký/đăng nhập, JWT) — tận dụng `JwtTokenProvider`/`JwtProperties` đã có sẵn trong `common` nhưng hiện chưa được dùng ở đâu; đây là lúc thêm `SecurityConfig` + filter cho `bff-service`
- Module lớp học tối giản (tạo lớp, danh sách học viên) — chỉ đủ để gắn buổi học/video với đúng học viên, chưa cần lịch học đầy đủ
- Trang/API phát lại video kèm transcript và danh sách từ vựng hay quên đã phân tích ở Phase 1
- Nếu cần ghi hình trực tiếp (thay vì chỉ upload file có sẵn): tích hợp WebRTC hoặc Zoom/Meet recording API — có thể lùi việc này nếu chưa cấp thiết, ưu tiên upload file trước

**Deliverables:**
- Người dùng thật đăng nhập, upload buổi học, xem transcript + báo cáo từ vựng hay quên mà không cần can thiệp thủ công

---

## Phase 3: Mở Rộng Phân Tích — Pronunciation & Grammar (7–10 tuần)

**Mục tiêu:** Bổ sung hai trục phân tích còn lại (phát âm, ngữ pháp) bên cạnh trục từ vựng đã có ở Phase 1, làm giàu dữ liệu "điểm yếu" của học viên.

**Công việc:**
- **Pronunciation Analysis** (4–6 tuần): tích hợp công cụ chấm điểm phát âm (Azure Pronunciation Assessment hoặc Montreal Forced Aligner + mô hình so sánh), sinh điểm số & feedback theo từ/âm vị — làm POC sớm để kiểm chứng độ chính xác trước khi đầu tư nhiều, vì đây là phần khó nhất kỹ thuật
- **Grammar Evaluation** (3–4 tuần): dùng LLM (GPT-4/Claude API) với prompt engineering để phân tích transcript, phát hiện và phân loại lỗi ngữ pháp, sinh giải thích + câu sửa đúng; xây cơ chế cache để kiểm soát chi phí gọi LLM
- Cả hai module publish kết quả theo cùng pattern Kafka đã dùng ở Phase 1 (`pronunciation.analyzed`, `grammar.analyzed`) để hợp nhất vào cùng một "hồ sơ điểm yếu" của học viên

**Deliverables:**
- Báo cáo phát âm chi tiết theo từng buổi học
- Danh sách lỗi ngữ pháp kèm giải thích và câu sửa đúng

---

## Phase 4: Personalized Learning Recommendations (4–5 tuần)

**Mục tiêu:** Tự động tạo bài luyện tập cá nhân hóa dựa trên toàn bộ điểm yếu đã phát hiện (từ vựng hay quên từ Phase 1 + phát âm/ngữ pháp từ Phase 3).

**Công việc:**
- Engine tổng hợp lỗi từ 3 module (phát âm, ngữ pháp, từ vựng) thành hồ sơ điểm yếu của từng học viên — ưu tiên các điểm yếu có forgetting-score cao nhất trước
- Sinh bài tập luyện tập tự động bằng LLM (câu luyện phát âm, bài tập ngữ pháp, flashcard từ vựng hay quên)
- Cơ chế theo dõi tiến độ luyện tập, thuật toán điều chỉnh độ khó/nội dung theo thời gian (adaptive learning)

**Deliverables:**
- Học viên nhận được bộ bài tập cá nhân hóa sau mỗi buổi học, tự động cập nhật theo tiến độ

---

## Phase 5: Learning Dashboard (3–4 tuần)

**Mục tiêu:** Trực quan hóa dữ liệu học tập, giúp học viên và giáo viên theo dõi tiến bộ.

**Công việc:**
- Dashboard hiển thị: độ chính xác phát âm theo thời gian, tỷ lệ lỗi ngữ pháp giảm dần, xu hướng từ vựng hay quên (dùng lại dữ liệu forgetting-score đã có từ Phase 1)
- Biểu đồ xu hướng lỗi (error trends) theo tuần/tháng
- Báo cáo tổng hợp cho giáo viên/phụ huynh, xuất PDF/Excel nếu cần

**Deliverables:**
- Dashboard đầy đủ với biểu đồ trực quan cho học viên và giáo viên

---

## Phase 6: Kiểm Thử, Tối Ưu & Ra Mắt (4–6 tuần)

**Mục tiêu:** Đảm bảo chất lượng, hiệu năng, và sẵn sàng vận hành thực tế.

**Công việc:**
- Kiểm thử toàn diện (unit test, integration test, load test cho pipeline xử lý AI — đặc biệt là core pipeline Phase 1 vốn chạy đầu tiên và chịu tải nhiều nhất)
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
| Phase 1.1 | Xác nhận hạ tầng AI hiện có (Kafka wiring) | 2–3 ngày |
| Phase 1.2 | Recording ingestion tối thiểu (`recording-service`) | 1–1.5 tuần |
| Phase 1.3 | Lưu trữ transcript | 1 tuần |
| Phase 1.4 | Lưu trữ & expose từ vựng hay quên | 1 tuần |
| Phase 1.5 | Kiểm chứng chất lượng & tinh chỉnh | 0.5–1 tuần |
| Phase 1.6 | Demo end-to-end | 2–3 ngày |
| **Phase 1 (tổng)** | **Core Pipeline: Video → Text → Từ vựng hay quên** | **3–5 tuần** |
| Phase 2 | Hạ tầng xung quanh tối thiểu (Auth + Recording UX) | 3–4 tuần |
| Phase 3 | Pronunciation + Grammar Analysis | 7–10 tuần |
| Phase 4 | Personalized Recommendations | 4–5 tuần |
| Phase 5 | Learning Dashboard | 3–4 tuần |
| Phase 6 | Testing & Launch | 4–6 tuần |
| **Tổng** | | **~24–34 tuần (~6–8 tháng)** |

*Lưu ý: Phase 3 (Pronunciation + Grammar) có thể chạy song song với Phase 2 nếu đủ nhân lực, vì cả hai không phụ thuộc lẫn nhau — chỉ đều phụ thuộc vào transcript đã có từ Phase 1.*

## 3. Rủi Ro Cần Lưu Ý

- **Độ tin cậy của core pipeline (Phase 1):** Toàn bộ sản phẩm phụ thuộc vào chất lượng transcript và forgetting-score — cần validate kỹ với dữ liệu thật trước khi mở rộng, tránh xây các phase sau trên một nền chưa chắc chắn.
- **Chi phí AI API:** Speech-to-Text + LLM + Pronunciation scoring chạy trên số lượng lớn video có thể tốn chi phí đáng kể — cần tính toán kỹ trước khi scale, đặc biệt khi thêm Grammar (Phase 3) dùng LLM.
- **Độ chính xác Pronunciation Analysis:** Phần khó nhất về mặt kỹ thuật AI trong Phase 3 — nên làm POC sớm ngay khi bắt đầu phase đó.
- **Độ trễ xử lý:** Video dài → xử lý AI mất thời gian → cần đo đạc ở Phase 1 xem hệ thống bất đồng bộ hiện tại (Kafka consumer) có đáp ứng đủ hay cần thêm cơ chế queue/backpressure.
- **Bảo mật dữ liệu:** Video/audio học viên là dữ liệu cá nhân nhạy cảm, cần chính sách lưu trữ và mã hóa rõ ràng trước khi có người dùng thật ở Phase 2.

## 4. Gợi Ý Bước Tiếp Theo

1. Bắt đầu ngay Phase 1.1: xác nhận `app/main.py` của `ai-service` đã khởi động Kafka consumer thật (`spawn_consumer_task`).
2. Phase 1.2: dựng tối thiểu `recording-service` (upload + publish `recording.uploaded`) để có input thật cho pipeline, thay vì test thủ công qua `/api/v1/upload`.
3. Phase 1.3–1.4: chọn `vocabulary-service` (đề xuất) để consume `transcript.ready`/`learning.gap.analyzed` và lưu kết quả — quyết định sớm để tránh trùng lặp trách nhiệm giữa các service.
4. Phase 1.5–1.6: kiểm chứng chất lượng forgetting-score với dữ liệu thật và demo end-to-end trước khi mở sang Phase 2.
