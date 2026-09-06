package com.elearning.shared.testing

import org.junit.jupiter.api.Tag
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.rabbitmq.RabbitMQContainer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import java.net.URI

/**
 * Base class for tests that need real infrastructure: PostgreSQL, and MinIO
 * standing in for S3. Flyway builds the schema, so migrations are exercised on
 * every run (AGENT.md §26).
 *
 * Singleton containers, started once per JVM and deliberately never stopped by
 * JUnit: `@Testcontainers`/`@Container` tie a container's lifetime to a single
 * test class, but Spring caches and shares one application context across all of
 * them. The second class would then reuse a context still pointing at a stopped
 * container and fail with "connection refused". Ryuk removes them at JVM exit.
 *
 * Every integration test extends this one class so they all share that single
 * cached context - a different property source per class would fragment it and
 * pay the startup cost repeatedly.
 */
@Tag("integration")
abstract class IntegrationTest {

    companion object {
        private const val TEST_BUCKET = "elearning-test"
        private const val TEST_PUBLIC_BUCKET = "elearning-test-public"

        @JvmStatic
        @ServiceConnection
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("elearning")
                .apply { start() }

        @JvmStatic
        val minio: MinIOContainer =
            MinIOContainer("minio/minio:latest")
                .withUserName("testaccess")
                .withPassword("testsecret")
                .apply {
                    start()
                    createBucket(TEST_BUCKET)
                    // Public assets (thumbnails) live in their own bucket, so it
                    // has to exist here too or a PUBLIC upload has nowhere to go.
                    createBucket(TEST_PUBLIC_BUCKET)
                }

        /**
         * MinIO starts empty; the application never creates buckets (that is a
         * deployment concern, handled by `minio-init` in Docker Compose).
         */
        private fun MinIOContainer.createBucket(bucket: String) {
            S3Client.builder()
                .endpointOverride(URI.create(s3URL))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(userName, password)),
                )
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()
                .use { it.createBucket(CreateBucketRequest.builder().bucket(bucket).build()) }
        }

        /**
         * Real broker: the notification dispatch path runs through RabbitMQ, and
         * a stubbed publisher would verify nothing about whether a message is
         * actually produced and consumed.
         */
        @JvmStatic
        @ServiceConnection
        val rabbit: RabbitMQContainer = RabbitMQContainer("rabbitmq:4-alpine").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun storageProperties(registry: DynamicPropertyRegistry) {
            registry.add("elearning.storage.endpoint") { minio.s3URL }
            registry.add("elearning.storage.access-key") { minio.userName }
            registry.add("elearning.storage.secret-key") { minio.password }
            registry.add("elearning.storage.media-bucket") { TEST_BUCKET }
            registry.add("elearning.storage.public-bucket") { TEST_PUBLIC_BUCKET }
            registry.add("elearning.storage.path-style-access") { true }
        }
    }
}
