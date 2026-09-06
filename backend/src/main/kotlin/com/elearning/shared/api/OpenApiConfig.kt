package com.elearning.shared.api

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Describes the API document itself. Endpoint-level documentation stays on the
 * controllers, so the contract is readable without opening the code (§27).
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun eLearningOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("E-Learning Platform API")
                    .version("v1")
                    .description(
                        """
                        REST API for the E-Learning platform.

                        Authentication uses a bearer JWT. Send it as
                        `Authorization: Bearer <token>`. Endpoints that fail
                        return the `ApiError` shape, whose `requestId` matches
                        the `X-Request-Id` response header.
                        """.trimIndent(),
                    )
                    .license(License().name("Proprietary")),
            )
            .components(
                Components().addSecuritySchemes(
                    BEARER_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT issued by the identity module"),
                ),
            )

    companion object {
        /** Referenced from `@SecurityRequirement(name = ...)` on protected operations. */
        const val BEARER_SCHEME = "bearerAuth"
    }
}
