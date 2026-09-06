package com.elearning.platform.email

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Supplies the `RestClient.Builder` the Mailgun sender needs.
 *
 * Spring Boot 4 splits auto-configuration across modules, and this application
 * depends on `spring-boot-starter-webmvc` - which serves HTTP but does not
 * configure a client for calling out. Rather than pull in another starter for a
 * single outbound call, the builder is declared here.
 *
 * Declaring it also means the timeouts are ours to set, and they have to be:
 * without an auto-configured builder there are no defaults, and a request with
 * no read timeout will hold its thread until the far end answers. Mailgun is
 * called from a request thread during registration and password reset, so an
 * unresponsive provider would otherwise tie up the container's threads rather
 * than failing and letting the caller move on.
 */
@Configuration
@ConditionalOnProperty(name = ["elearning.mail.provider"], havingValue = "mailgun")
class MailgunClientConfig {

    @Bean
    @ConditionalOnMissingBean(RestClient.Builder::class)
    fun mailgunRestClientBuilder(): RestClient.Builder =
        RestClient.builder().requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(5))
                setReadTimeout(Duration.ofSeconds(10))
            },
        )
}
