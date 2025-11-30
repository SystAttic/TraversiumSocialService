package traversium.socialservice.exceptions

/**
 * Thrown when a user attempts to like a media item they have already liked.
 * This should map to an HTTP 409 Conflict response.
 */
class DuplicateLikeException(message: String) : RuntimeException(message)

