package traversium.socialservice.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Thrown when comment is not allowed by moderation service
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
class CommentModerationException(message: String) : RuntimeException(message)