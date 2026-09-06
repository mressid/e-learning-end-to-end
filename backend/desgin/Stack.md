| Area                | Technology                         | Role                                    |
| ------------------- | ---------------------------------- | --------------------------------------- |
| Web frontend        | **Next.js + React + TypeScript**   | Main web client                         |
| Mobile              | **React Native / Expo**            | Mobile client later                     |
| Backend             | **Spring Boot + Kotlin**           | Main API + business logic               |
| Security            | **Spring Security**                | Authentication/authorization            |
| Authentication      | **Spring Security + JWT/OAuth2**   | Login, tokens, protected APIs           |
| API                 | **REST + OpenAPI**                 | Client/backend contract                 |
| Database            | **PostgreSQL**                     | Main source of truth                    |
| ORM                 | **Hibernate/JPA**                  | Persistence                             |
| Migrations          | **Flyway**                         | Database schema evolution               |
| Cache               | **Redis**                          | Caching, rate limiting, temporary state |
| Object storage      | **S3 / MinIO**                     | Videos, PDFs, images, source files      |
| Async messaging     | **RabbitMQ**                       | Background jobs/events                  |
| Video processing    | **FFmpeg**                         | Transcoding/thumbnails/metadata         |
| Email               | **Resend / SMTP provider**         | Verification, reset, notifications      |
| Search              | **PostgreSQL FTS initially**       | Course/resource search                  |
| API documentation   | **Springdoc OpenAPI**              | Swagger/OpenAPI                         |
| Testing             | **JUnit + MockK + Testcontainers** | Unit/integration tests                  |
| Logging             | **SLF4J + Logback**                | Application logging                     |
| Metrics             | **Micrometer + Prometheus**        | Metrics                                 |
| Visualization       | **Grafana**                        | Monitoring dashboards                   |
| Tracing             | **OpenTelemetry**                  | Distributed tracing later               |
| Containers          | **Docker**                         | Packaging/runtime                       |
| Local orchestration | **Docker Compose**                 | Dev infrastructure                      |
| Reverse proxy       | **Nginx / Traefik**                | HTTPS/routing                           |
| CI/CD               | **GitHub Actions**                 | Build/test/deployment                   |
