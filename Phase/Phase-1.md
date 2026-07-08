# Phase 1: Hạ Tầng Cốt Lõi & Lesson Recording

**Thời gian ước tính:** 4–6 tuần

**Mục tiêu:** Xây dựng nền tảng cơ bản: đăng ký/đăng nhập, tạo lớp học, ghi hình, lưu trữ và phát lại video.

**Tech stack liên quan:**
- Spring Boot + Spring Security + JWT (authentication & phân quyền)
- MyBatis + PostgreSQL (lesson, class, user management), Flyway để quản lý migration schema
- Spring Boot tích hợp S3 SDK (AWS SDK for Java) lưu trữ video/audio
- Trình duyệt: `MediaRecorder` API + `getDisplayMedia`/`getUserMedia` để ghi màn hình/webcam realtime ngay trên web app (không phụ thuộc Zoom/Google Meet)
- FFmpeg (gọi qua ProcessBuilder hoặc wrapper) để chuẩn hóa định dạng (từ file upload local hoặc từ recorder) và tách audio từ video
- Apache Kafka (spring-kafka) — publisher điểm khởi đầu của toàn bộ pipeline xử lý AI (xem [[Phase-2]])

**Công việc:**
- Authentication & phân quyền (học viên / giáo viên / admin) với Spring Security + JWT/OAuth2
- Module quản lý lớp học (tạo lớp, lịch học, danh sách học viên) — REST API bằng Spring Web MVC
- Hai nguồn ghi nhận buổi học, cùng đổ về một pipeline upload/xử lý chung:
  - **Ghi màn hình/webcam realtime**: dùng `MediaRecorder` + `getDisplayMedia`/`getUserMedia` ngay trên web app, chia nhỏ theo chunk (ví dụ mỗi 5–10s) và upload dần lên backend (resumable/chunked upload) để tránh mất dữ liệu khi mất mạng hoặc đóng tab giữa chừng
  - **Tải file có sẵn từ local**: form upload file video/audio (mp4, webm, mov, mp3, wav...) từ máy người dùng
- Upload & lưu trữ video (cloud storage S3 + CDN) — dùng multipart upload trong Spring, ghép các chunk realtime lại thành 1 file hoàn chỉnh trước khi lưu S3
- Chuẩn hóa định dạng/codec bằng FFmpeg cho cả 2 nguồn (đảm bảo output đồng nhất trước khi vào pipeline AI)
- Sau khi upload S3 thành công, publish event Kafka `recording.uploaded` (payload: recordingId, classId, studentId, s3Url, source: REALTIME/UPLOAD) để kích hoạt pipeline xử lý AI ở Phase 2 — dùng `KafkaTemplate` trong `RecordingService`
- Trang phát lại (playback) video buổi học đã ghi (frontend React/Next.js gọi API Spring)
- Xử lý video cơ bản: tách audio từ video bằng FFmpeg (chuẩn bị cho bước Speech-to-Text)

**Deliverables:**
- Người dùng có thể đăng nhập, vào lớp học, ghi màn hình/webcam realtime hoặc tải file có sẵn từ local, và xem lại bản ghi
- Pipeline lưu trữ video/audio hoạt động ổn định với cả 2 nguồn đầu vào, tự động publish event Kafka khi có video mới
