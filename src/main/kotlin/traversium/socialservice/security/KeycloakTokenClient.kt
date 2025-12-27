package traversium.socialservice.security

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import traversium.socialservice.config.OAuthClientProperties
import java.time.Instant
import org.apache.logging.log4j.kotlin.Logging

@Component
class KeycloakTokenClient(
    private val webClientBuilder: WebClient.Builder,
    private val props: OAuthClientProperties
): Logging {

    private var cachedToken: String? = null
    private var expiresAt: Instant = Instant.MIN

    private val webClient = webClientBuilder.build()

    fun getToken(): String {
        if (cachedToken != null &&
            Instant.now().isBefore(expiresAt.minusSeconds(props.refreshSkewSeconds))
        ) {
            val secondsRemaining = java.time.Duration.between(Instant.now(), expiresAt).toSeconds()

            logger.info("Cached Keycloak token, expires in ${secondsRemaining}s")
            return cachedToken!!
        }

        val response = webClient.post()
            .uri(props.tokenUri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(
                "grant_type=${props.grantType}" +
                        "&client_id=${props.clientId}" +
                        "&client_secret=${props.clientSecret}"
            )
            .retrieve()
            .bodyToMono(KeycloakTokenResponse::class.java)
            .block()!!

        cachedToken = response.accessToken
        expiresAt = Instant.now().plusSeconds(response.expiresIn)
        logger.info("Obtained Keycloak token, expires in ${response.expiresIn}s")

        return cachedToken!!
    }
}