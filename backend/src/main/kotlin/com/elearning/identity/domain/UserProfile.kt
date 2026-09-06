package com.elearning.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID

/** Display information, separated so the auth table stays narrow. */
@Entity
@Table(name = "user_profiles")
class UserProfile(

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    var user: User,

    @Column(name = "first_name")
    var firstName: String? = null,

    @Column(name = "last_name")
    var lastName: String? = null,

    @Column(nullable = false)
    var timezone: String = "UTC",

    @Column(nullable = false)
    var language: String = "en",
) {
    @Id
    @Column(name = "user_id")
    var userId: UUID? = null

    @Column
    var bio: String? = null

    @Column(name = "avatar_media_id")
    var avatarMediaId: UUID? = null

    val displayName: String?
        get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null }
}
