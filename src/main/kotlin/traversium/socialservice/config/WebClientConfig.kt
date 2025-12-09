package traversium.socialservice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    @Bean
    fun tripServiceWebClient(): WebClient {
        val tripServiceUrl = System.getenv("TRIP_SERVICE_URL")

        return WebClient.builder().baseUrl(tripServiceUrl).build()
    }
}