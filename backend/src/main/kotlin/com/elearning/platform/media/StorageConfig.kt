package com.elearning.platform.media

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
class StorageConfig(private val properties: StorageProperties) {

    private fun credentials() = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
    )

    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .region(Region.of(properties.region))
        .credentialsProvider(credentials())
        // MinIO serves buckets as a path segment; virtual-host style would
        // resolve to a hostname that does not exist locally.
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess).build())
        .build()

    @Bean
    fun s3Presigner(): S3Presigner = S3Presigner.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .region(Region.of(properties.region))
        .credentialsProvider(credentials())
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess).build())
        .build()
}
