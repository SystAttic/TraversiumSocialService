package traversium.socialservice.exceptions

/**
 * Thrown when a comment with a specific ID cannot be found in the database.
 * This should map to an HTTP 404 Not Found response.
 */
class CommentNotFoundException(message: String) : RuntimeException(message)