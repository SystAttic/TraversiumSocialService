package traversium.socialservice.exceptions

/**
 * Thrown when a user attempts to modify or delete a comment they do not own.
 * This should map to an HTTP 403 Forbidden response.
 */
class UnauthorizedCommentAccessException(message: String) : RuntimeException(message)