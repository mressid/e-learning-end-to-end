package com.elearning.platform.transcode

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface TranscodeRepository : JpaRepository<TranscodeJob, UUID> {

    fun findByMediaId(mediaId: UUID): Optional<TranscodeJob>

}
