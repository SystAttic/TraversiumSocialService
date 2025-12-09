package traversium.socialservice.client

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import traversium.socialservice.dto.MediaDto
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class TripServiceClient(
    private val tripServiceWebClient: WebClient
) {
    fun getMediaOwner(mediaId: Long): String? {
        return tripServiceWebClient.get()
            .uri("/rest/v1/media/{mediaId}", mediaId)
            .retrieve()
            .bodyToMono<MediaDto>()
            .map {it.uploader}
            .onErrorResume {
                Mono.empty()
            }
            .block()
    }
}