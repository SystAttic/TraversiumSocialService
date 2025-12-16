package traversium.socialservice.exceptions

/**
 * Thrown when a user attempts to create a comment on a nonexistent media
 * This should map to an HTTP 404 Forbidden response.
 */
class MediaNotFoundException(message: String) : RuntimeException(message)