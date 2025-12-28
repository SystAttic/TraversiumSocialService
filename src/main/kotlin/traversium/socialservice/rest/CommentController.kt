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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import traversium.socialservice.dto.CommentDto
import traversium.socialservice.dto.CreateCommentDto
import traversium.socialservice.dto.UpdateCommentDto
import traversium.socialservice.exceptions.*
import traversium.socialservice.security.TraversiumAuthentication
import traversium.socialservice.security.TraversiumPrincipal
import traversium.socialservice.service.CommentService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

@RestController
@RequestMapping("/rest/v1")
@Tag(name = "Comments", description = "Endpoints for managing comments")
class CommentController(
    private val commentService: CommentService
): Logging {

    @PostMapping("/media/{mediaId}/comments")
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
                description = "Bad request - Moderation service rejected comment or invalid comment data provided"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found - The specified media or parent comment does not exist."
            )
        ]
    )
    fun createComment(
        @PathVariable mediaId: Long,
        @RequestBody createDto: CreateCommentDto
    ): ResponseEntity<CommentDto> {
        return try {
            val savedComment = commentService.createComment(mediaId, createDto)
            ResponseEntity.status(HttpStatus.CREATED).body(savedComment)
        } catch(ex: MediaNotFoundException) {
            logger.warn("Failed to create comment: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch(ex: CommentNotFoundException) {
            logger.warn("Failed to create comment: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while creating a comment on media $mediaId", ex)
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
                responseCode = "400",
                description = "Moderation service rejected comment."
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
        return try {
            val updatedComment = commentService.updateComment(commentId,  updateDto)
            ResponseEntity.ok(updatedComment)
        } catch (ex: CommentNotFoundException) {
            logger.warn("Failed to update comment: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (ex: UnauthorizedCommentAccessException) {
            logger.warn("Unauthorized attempt to update comment $commentId")
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

        return try {
            commentService.deleteComment(commentId)
            ResponseEntity.status(HttpStatus.NO_CONTENT).build()
        } catch (ex: CommentNotFoundException) {
            logger.warn("Failed to delete comment: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (ex: UnauthorizedCommentAccessException) {
            logger.warn("Unauthorized attempt to delete comment $commentId")
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while deleting comment $commentId", ex)
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/media/{mediaId}/comments")
    @Operation(
        operationId = "getComments",
        summary = "Get top-level comments for a media item.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved a paginated list of comments. The list will be empty if the the media has no comments or if the media does not exist.",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = Page::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found - The specified media does not exist."
            )
        ]
    )
    fun getComments(
        @PathVariable mediaId: Long,
        pageable: Pageable
    ): ResponseEntity<Page<CommentDto>> {
        return try {
            val commentsPage = commentService.getCommentsForAlbum(mediaId, pageable)
            ResponseEntity.ok(commentsPage)
        } catch (ex: MediaNotFoundException) {
            logger.warn("Failed to get comments: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
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

    //TODO: add delete operation for all comments of an album
    //TODO: check for blocked users
}