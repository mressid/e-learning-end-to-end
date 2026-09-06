package com.elearning.shared.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.spec.SecretKeySpec

/**
 * Symmetric (HS256) JWT signing.
 *
 * A shared secret is the right trade-off while the monolith both issues and
 * verifies its own tokens. If a second service ever needs to verify them,
 * switch to RSA/EC so the public key can be published - the rest of the
 * security configuration does not change.
 */
@Configuration
class JwtConfig(private val properties: JwtProperties) {

    private fun secretKey() = SecretKeySpec(properties.secret.toByteArray(), "HmacSHA256")

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secretKey()))

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(secretKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
}
