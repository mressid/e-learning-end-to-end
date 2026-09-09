package com.elearning.platform.media

import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Media's answer about its own derivatives.
 *
 * An HLS manifest and a poster frame are media objects referred to by the media
 * object they were made from. Deleting one would leave a video that claims to
 * be streamable and is not, so they are held the same way as anything else in
 * use. This check used to belong to learning, back when the pointers lived on a
 * lesson; it belongs here now that they live on the file.
 */
@Component
class MediaRenditionReferenceProbe(private val media: MediaObjectRepository) : MediaReferenceProbe {

    override fun isReferenced(mediaId: UUID): Boolean =
        media.existsByHlsManifestMediaId(mediaId) || media.existsByPosterMediaId(mediaId)

    override fun describe(): String = "a streaming rendition or a poster frame"
}
