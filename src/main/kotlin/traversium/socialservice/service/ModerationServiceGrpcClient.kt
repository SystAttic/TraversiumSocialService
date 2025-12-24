package traversium.socialservice.service

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.apache.logging.log4j.kotlin.logger
import org.springframework.stereotype.Service
import traversium.moderation.textmoderation.ModerateTextRequest
import traversium.moderation.textmoderation.TextModerationServiceGrpc
import java.util.concurrent.TimeUnit

@Service
class ModerationServiceGrpcClient(
    private val moderationStub: TextModerationServiceGrpc.TextModerationServiceBlockingStub
){

    @CircuitBreaker(name = "moderation-service", fallbackMethod = "fallback")
    @Retry(name = "moderation-service")
    fun isTextAllowed(text: String): Boolean {
        logger.info("Calling ModerationService via gRPC")

        val request = ModerateTextRequest.newBuilder()
            .setText(text)
            .build()

        val response = moderationStub
            .withDeadlineAfter(2, TimeUnit.SECONDS)
            .moderateText(request)

        if(response.allowed)
            logger.info("Content allowed by ModerationService. Continue.")
        else
            logger.info("Content blocked by ModerationService. Moderation result: $response")
        return response.allowed
    }

    @Suppress("unused")
    fun fallback(text: String, ex: Throwable): Boolean {
        logger.error("Moderation service not available. Comment not moderated.", ex)
        return true
    }
}