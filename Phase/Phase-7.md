# Phase 7: Learning Dashboard

**Thời gian ước tính:** 3–4 tuần

**Mục tiêu:** Trực quan hóa dữ liệu học tập, giúp học viên và giáo viên theo dõi tiến bộ.

**Tech stack liên quan:**
- Spring Boot cung cấp REST API tổng hợp dữ liệu (aggregation queries qua JPA/Native SQL) cho dashboard
- Frontend React/Next.js + thư viện chart (Recharts/Chart.js) gọi API Spring
- Apache PDFBox hoặc iText (Java) để xuất báo cáo PDF; Apache POI để xuất Excel

**Công việc:**
- Thiết kế dashboard hiển thị: độ chính xác phát âm theo thời gian, tỷ lệ lỗi ngữ pháp giảm dần, vốn từ vựng tăng trưởng — API Spring trả dữ liệu theo dạng aggregate/time-series
- Biểu đồ xu hướng lỗi (error trends) theo tuần/tháng
- Báo cáo tổng hợp cho giáo viên/phụ huynh (nếu có)
- Xuất báo cáo (PDF/Excel) bằng PDFBox/iText và Apache POI ở phía backend Spring

**Deliverables:**
- Dashboard đầy đủ với biểu đồ trực quan cho học viên và giáo viên
