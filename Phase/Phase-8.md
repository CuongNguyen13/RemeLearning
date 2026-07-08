# Phase 8: Kiểm Thử, Tối Ưu & Ra Mắt

**Thời gian ước tính:** 4–6 tuần

**Mục tiêu:** Đảm bảo chất lượng, hiệu năng, và sẵn sàng vận hành thực tế.

**Tech stack liên quan:**
- JUnit 5 + Mockito (unit test), Testcontainers (integration test với PostgreSQL/Redis thật)
- Gatling/JMeter (load test cho pipeline xử lý AI)
- Spring Boot Actuator + Micrometer + Prometheus/Grafana (monitoring), Spring Security best practices (mã hóa dữ liệu nhạy cảm)
- Docker + GitHub Actions (CI/CD), triển khai lên AWS/GCP/Azure với auto-scaling

**Công việc:**
- Kiểm thử toàn diện (unit test với JUnit/Mockito, integration test với Testcontainers, load test cho pipeline xử lý AI bằng Gatling/JMeter)
- Tối ưu chi phí AI (giảm gọi API dư thừa, cache kết quả bằng Redis, xử lý song song với @Async/CompletableFuture)
- Kiểm thử bảo mật dữ liệu (video/audio học viên là dữ liệu nhạy cảm — cần mã hóa tại rest/in transit, tuân thủ quy định bảo mật) — dùng Spring Security, mã hóa S3 server-side
- Beta test với nhóm người dùng thật, thu thập phản hồi
- Chuẩn bị hạ tầng production (auto-scaling, monitoring qua Spring Boot Actuator + Prometheus/Grafana, alerting)
- Ra mắt chính thức (soft launch → full launch)

**Deliverables:**
- Sản phẩm ổn định, sẵn sàng phục vụ người dùng thật với khả năng mở rộng
