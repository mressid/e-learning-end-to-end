package com.elearning.platform.media

import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.NoSuchUploadException
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.time.Duration

/**
 * S3-compatible implementation. The same code drives MinIO locally and S3 in
 * production; only [com.elearning.platform.media.StorageProperties] differs.
 */
@Component
class S3ObjectStorage(
    private val s3: S3Client,
    private val presigner: S3Presigner,
) : ObjectStorage {

    override fun presignedUpload(bucket: String, key: String, contentType: String, ttl: Duration): URI {
        // contentType is signed into the URL, so the client cannot upload a
        // different type than the one recorded in the database.
        val put = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .build()

        return presigner.presignPutObject(
            PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(put).build(),
        ).url().toURI()
    }

    override fun presignedDownload(bucket: String, key: String, ttl: Duration): URI {
        val get = GetObjectRequest.builder().bucket(bucket).key(key).build()
        return presigner.presignGetObject(
            GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(get).build(),
        ).url().toURI()
    }

    override fun put(bucket: String, key: String, bytes: ByteArray, contentType: String) {
        s3.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
            RequestBody.fromBytes(bytes),
        )
    }

    override fun publicUrl(bucket: String, key: String): URI =
        s3.utilities().getUrl { it.bucket(bucket).key(key) }.toURI()

    // ---- resumable uploads ----------------------------------------------

    override fun createMultipartUpload(bucket: String, key: String, contentType: String): String =
        s3.createMultipartUpload {
            it.bucket(bucket).key(key).contentType(contentType)
        }.uploadId()

    override fun presignedUploadPart(
        bucket: String,
        key: String,
        uploadId: String,
        partNumber: Int,
        ttl: Duration,
    ): URI = presigner.presignUploadPart(
        UploadPartPresignRequest.builder()
            .signatureDuration(ttl)
            .uploadPartRequest { req ->
                req.bucket(bucket).key(key).uploadId(uploadId).partNumber(partNumber)
            }
            .build(),
    ).url().toURI()

    override fun listParts(bucket: String, key: String, uploadId: String): List<UploadedPart> = try {
        s3.listParts { it.bucket(bucket).key(key).uploadId(uploadId) }
            .parts()
            .map { UploadedPart(it.partNumber(), it.eTag(), it.size() ?: 0) }
            .sortedBy(UploadedPart::partNumber)
    } catch (ex: NoSuchUploadException) {
        // Already completed or aborted. An empty list is the honest answer -
        // there is nothing left to resume - and is what the caller can act on.
        emptyList()
    }

    override fun completeMultipartUpload(
        bucket: String,
        key: String,
        uploadId: String,
        parts: List<UploadedPart>,
    ) {
        s3.completeMultipartUpload { req ->
            req.bucket(bucket).key(key).uploadId(uploadId).multipartUpload { upload ->
                upload.parts(
                    // S3 requires ascending part numbers; a client that sent
                    // its parts out of order would otherwise be rejected for
                    // something it has no reason to know about.
                    parts.sortedBy(UploadedPart::partNumber).map { part ->
                        CompletedPart.builder().partNumber(part.partNumber).eTag(part.etag).build()
                    },
                )
            }
        }
    }

    override fun abortMultipartUpload(bucket: String, key: String, uploadId: String) {
        // Idempotent: aborting something already gone is the outcome the caller
        // wanted, and a sweep should not fail on work another sweep finished.
        runCatching { s3.abortMultipartUpload { it.bucket(bucket).key(key).uploadId(uploadId) } }
    }

    override fun get(bucket: String, key: String): ByteArray =
        s3.getObjectAsBytes { it.bucket(bucket).key(key) }.asByteArray()

    override fun statOf(bucket: String, key: String): StoredObject? = try {
        val head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
        StoredObject(head.contentLength(), head.eTag()?.trim('"'))
    } catch (ex: NoSuchKeyException) {
        null
    } catch (ex: software.amazon.awssdk.services.s3.model.S3Exception) {
        // MinIO answers HEAD on a missing key with a bare 404 and no error code,
        // which the SDK surfaces as S3Exception rather than NoSuchKeyException.
        if (ex.statusCode() == 404) null else throw ex
    }

    override fun delete(bucket: String, key: String) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
    }
}
