package traversium.socialservice.client

import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import traversium.socialservice.dto.MediaDto
import org.springframework.web.reactive.function.client.bodyToMono
import traversium.commonmultitenancy.TenantContext
import traversium.commonmultitenancy.TenantUtils

@Component
class TripServiceClient(
    private val tripServiceWebClient: WebClient
) {
    fun getMediaOwner(mediaId: Long, token: String?): String? {
        return tripServiceWebClient.get()
            .uri("/rest/v1/media/{mediaId}", mediaId)
            .header(HttpHeaders.AUTHORIZATION, token)
            .header("X-Tenant-Id", TenantUtils.desanitizeTenantIdFromSchema(TenantContext.getTenant()))
            .retrieve()
            .bodyToMono<MediaDto>()
            .map {it.uploader}
            .onErrorResume {
                Mono.empty()
            }
            .block()
    }

    fun doesMediaExist(mediaId: Long, token: String?): Boolean {
        return tripServiceWebClient.get()
            .uri("/rest/v1/media/{mediaId}", mediaId)
            .header(HttpHeaders.AUTHORIZATION, token)
            .header("X-Tenant-Id", TenantUtils.desanitizeTenantIdFromSchema(TenantContext.getTenant()))
            .retrieve()
            .toBodilessEntity()
            .map { it.statusCode.is2xxSuccessful }
            .onErrorResume {
                // If 404 or service down, assume false
                Mono.just(false)
            }
            .block() ?: false
    }
}