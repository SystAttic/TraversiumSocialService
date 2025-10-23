package traversium.socialservice.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import traversium.socialservice.dto.CommentDto
import traversium.socialservice.dto.CreateCommentDto
import traversium.socialservice.dto.UpdateCommentDto
import traversium.socialservice.exceptions.*
import traversium.socialservice.service.CommentService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

@RestController
@RequestMapping("/rest/v1")
@Tag(name = "Comments", description = "Endpoints for managing comments")
class CommentController(
    private val commentService: CommentService
): Logging {

    @PostMapping("/nodes/{nodeId}/comments")
    @Operation(
        operationId = "createComment",
        tags = ["Comment"],
        summary = "Create a new comment.",
        description = "Creates a new comment.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully created new comment.",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = CommentDto::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Bad request - Invalid comment data provided"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found - The specified parent comment does not exist."
            )
        ]
    )
    fun createComment(
        @PathVariable nodeId: Long,
        @RequestBody createDto: CreateCommentDto
    ): ResponseEntity<CommentDto> {

        val authorId = 1L //TODO: Replace with real userId from authentication

        return try {
            val savedComment = commentService.createComment(nodeId, authorId, createDto)
            logger.info("Comment with ID ${savedComment.commentId} created on node $nodeId by user $authorId")
            ResponseEntity.status(HttpStatus.CREATED).body(savedComment)
        } catch(ex: CommentNotFoundException) {
            logger.warn("Failed to create comment: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while creating a comment for node $nodeId", ex)
            ResponseEntity.badRequest().build()
        }
    }

    @PutMapping("/comments/{commentId}")
    @Operation(
        operationId = "updateComment",
        summary = "Update an existing comment.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully updated the comment.",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = CommentDto::class)
                )]
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User is not author of the comment."
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found - The specified comment does not exist."
            )
        ]
    )
    fun updateComment(
        @PathVariable commentId: Long,
        @RequestBody updateDto: UpdateCommentDto
    ): ResponseEntity<CommentDto> {

        val authorId = 1L //TODO: Replace with real userId from authentication

        return try {
            val updatedComment = commentService.updateComment(commentId, authorId, updateDto)
            logger.info("Comment with ID $commentId updated by user $authorId")
            ResponseEntity.ok(updatedComment)
        } catch (ex: CommentNotFoundException) {
            logger.warn("Failed to update comment: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (ex: UnauthorizedCommentAccessException) {
            logger.warn("Unauthorized attempt to update comment $commentId by user $authorId")
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while updating comment $commentId", ex)
            ResponseEntity.badRequest().build()
        }
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(
        operationId = "deleteComment",
        summary = "Delete an existing comment.",
        description = "Deletes a comment with the specified id.",
        responses = [
            ApiResponse(
                responseCode = "204",
                description = "Successfully deleted the comment."
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User is not author of the comment."
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found - The specified comment does not exist."
            )
        ]
    )
    fun deleteComment(@PathVariable commentId: Long): ResponseEntity<Void> {

        val authorId = 1L //TODO: Replace with real userId from authentication

        return try {
            commentService.deleteComment(commentId, authorId)
            logger.info("Comment with ID $commentId deleted by user $authorId")
            ResponseEntity.status(HttpStatus.NO_CONTENT).build()
        } catch (ex: CommentNotFoundException) {
            logger.warn("Failed to delete comment: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (ex: UnauthorizedCommentAccessException) {
            logger.warn("Unauthorized attempt to delete comment $commentId by user $authorId")
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while deleting comment $commentId", ex)
            ResponseEntity.badRequest().build()
        }
    }

    //TODO: add a 404 response if node does not exist
    @GetMapping("/nodes/{nodeId}/comments")
    @Operation(
        operationId = "getComments",
        summary = "Get top-level comments for a node.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved a paginated list of comments. The list will be empty if the node has no comments or if the node does not exist.",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = Page::class)
                )]
            )
        ]
    )
    fun getComments(
        @PathVariable nodeId: Long,
        pageable: Pageable
    ): ResponseEntity<Page<CommentDto>> {
        return try {
            val commentsPage = commentService.getCommentsForNode(nodeId, pageable)
            ResponseEntity.ok(commentsPage)
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while retrieving top-level comments.", ex)
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/comments/{commentId}/replies")
    @Operation(
        operationId = "getCommentReplies",
        summary = "Get replies for a specific comment.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved replies.",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = Page::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found - The specified parent comment does not exist."
            )
        ]
    )
    fun getRepliesForComment(@PathVariable commentId: Long, pageable: Pageable): ResponseEntity<Page<CommentDto>> {
        return try {
            val repliesPage = commentService.getRepliesForComment(commentId, pageable)
            ResponseEntity.ok(repliesPage)
        } catch (ex: CommentNotFoundException) {
            logger.warn("Failed to get replies: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while retrieving top-level comments.", ex)
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/comments/{commentId}")
    @Operation(
        operationId = "getComment",
        summary = "Get a specific comment.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved comment.",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = CommentDto::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found - The specified comment does not exist."
            )
        ]
    )
    fun getComment(@PathVariable commentId: Long): ResponseEntity<CommentDto> {
        return try {
            val comment = commentService.getCommentById(commentId)
            ResponseEntity.ok(comment)
        } catch (ex: CommentNotFoundException) {
            logger.warn("Failed to get comment: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while retrieving top-level comments.", ex)
            ResponseEntity.badRequest().build()
        }
    }

    //TODO: add delete operation for all comments of a node
    //TODO: check for blocked users
}