package com.elearning.shared.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Baseline HTTP security.
 *
 * Deny by default: everything requires authentication except the few paths
 * listed below. Authentication itself (JWT resource server, login) is added by
 * the identity module once it exists.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val authenticationEntryPoint: ApiAuthenticationEntryPoint,
    private val accessDeniedHandler: ApiAccessDeniedHandler,
) {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        configuration.allowedOrigins = listOf(
            "http://localhost:8080" // hard-coded for now
        )
        configuration.allowedMethods = listOf(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        )
        configuration.allowedHeaders = listOf(
            "Authorization",
            "Content-Type"
        )
        configuration.allowCredentials = false

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)

        return source
    }

    @Bean
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            // The API is stateless and token-based, so there is no session
            .cors {  }

            // cookie for CSRF to protect.
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Return 401 instead of redirecting to a login page or prompting
            // for browser basic-auth credentials.
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            // Rejections happen in the filter chain, before @RestControllerAdvice,
            // so they need their own wiring to return the standard ApiError body.
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers(*PUBLIC_PATHS).permitAll()
                    // Course discovery is public by design: anonymous callers
                    // can browse published courses. Reads still go through
                    // CourseAuthorization, which hides anything unpublished.
                    // Writes fall through to authenticated().
                    .requestMatchers(HttpMethod.GET, *PUBLIC_READ_PATHS).permitAll()
                    .anyRequest().authenticated()
            }
            // Bearer JWTs, verified with the key from JwtConfig. The resource
            // server installs its own entry point, so it needs the handlers set
            // here too or a malformed token returns an empty 401 body.
            .oauth2ResourceServer {
                it.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .jwt { }
            }
            .build()

    /**
     * Delegating encoder: hashes with bcrypt but can still verify older formats,
     * so the hashing algorithm can be changed without invalidating passwords.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    private companion object {
        /**
         * Readable without authentication. Kept deliberately short: the API
         * contract and liveness checks, nothing that exposes data or internals.
         */
        val PUBLIC_PATHS = arrayOf(
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            // Registration and login must be reachable without a token.
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            // Refresh is reached precisely when the access token has expired,
            // so requiring one here would make it useless. Its own credential
            // is the refresh token in the body.
            "/api/v1/auth/refresh",
            // Logout likewise: a session whose access token already lapsed must
            // still be endable, and the refresh token authenticates the call.
            "/api/v1/auth/logout",
            // All reached by someone who cannot sign in yet, or at all: an
            // unverified account, or one whose password is lost. Each carries
            // its own single-use credential in the body.
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend",
            "/api/v1/auth/password-reset",
            "/api/v1/auth/password-reset/confirm",
            // The dashboard's own sign-in surface. Separate from the learner
            // one because administrators are a separate table; the tokens carry
            // typ=admin and are rejected wherever a learner identity is meant.
            "/api/v1/admin/auth/login",
            "/api/v1/admin/auth/refresh",
            "/api/v1/admin/auth/logout",
        )

        /** Readable anonymously, but only via GET. */
        val PUBLIC_READ_PATHS = arrayOf(
            "/api/v1/courses",
            "/api/v1/courses/**",
            "/api/v1/sections/**",
            // The catalogue's shape is part of discovery: a visitor deciding
            // whether to sign up needs to see how courses are organized.
            "/api/v1/categories",
            "/api/v1/tags",
            // Anyone holding a printed certificate can check it.
            "/api/v1/certificates/verify/**",
        )
    }
}
