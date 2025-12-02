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
import traversium.socialservice.dto.LikeCountDto
import traversium.socialservice.dto.LikeDto
import traversium.socialservice.exceptions.DuplicateLikeException
import traversium.socialservice.exceptions.LikeNotFoundException
import traversium.socialservice.security.TraversiumAuthentication
import traversium.socialservice.security.TraversiumPrincipal
import traversium.socialservice.service.LikeService

@RestController
@RequestMapping("/rest/v1")
@Tag(name = "Likes", description = "Endpoints for managing likes on media")
class LikeController(
    private val likeService: LikeService
) : Logging {

    @PostMapping("/media/{mediaId}/likes")
    @Operation(
        operationId = "likeMedia",
        tags = ["Like"],
        summary = "Like a media item.",
        description = "Creates a like for the specified media item by the authenticated user.",
        responses = [
            ApiResponse(
                responseCode = "201",
                description = "Successfully created like.",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = LikeDto::class)
                )]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Conflict - User has already liked this media item."
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Authentication required."
            )
        ]
    )
    fun likeMedia(@PathVariable mediaId: Long): ResponseEntity<LikeDto> {
        return try {
            val like = likeService.likeMedia(mediaId)
            ResponseEntity.status(HttpStatus.CREATED).body(like)
        } catch (ex: DuplicateLikeException) {
            logger.warn("Attempted to like already liked media $mediaId: ${ex.message}")
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while liking media $mediaId", ex)
            ResponseEntity.badRequest().build()
        }
    }

    @DeleteMapping("/media/{mediaId}/likes")
    @Operation(
        operationId = "unlikeMedia",
        tags = ["Like"],
        summary = "Unlike a media item.",
        description = "Removes the like for the specified media item by the authenticated user.",
        responses = [
            ApiResponse(
                responseCode = "204",
                description = "Successfully removed like."
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found - Like does not exist."
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Authentication required."
            )
        ]
    )
    fun unlikeMedia(@PathVariable mediaId: Long): ResponseEntity<Void> {
        return try {
            likeService.unlikeMedia(mediaId)
            ResponseEntity.status(HttpStatus.NO_CONTENT).build()
        } catch (ex: LikeNotFoundException) {
            logger.warn("Failed to unlike media $mediaId: ${ex.message}")
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while unliking media $mediaId", ex)
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/media/{mediaId}/likes")
    @Operation(
        operationId = "getLikeCount",
        tags = ["Like"],
        summary = "Get like count for a media item.",
        description = "Retrieves the total number of likes for the specified media item and whether the current user has liked it.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved like count.",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = LikeCountDto::class)
                )]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Authentication required."
            )
        ]
    )
    fun getLikeCount(@PathVariable mediaId: Long): ResponseEntity<LikeCountDto> {
        return try {
            val likeCount = likeService.getLikeCount(mediaId)
            ResponseEntity.ok(likeCount)
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while retrieving like count for media $mediaId", ex)
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/media/{mediaId}/likes/check")
    @Operation(
        operationId = "checkIfLiked",
        tags = ["Like"],
        summary = "Check if current user has liked a media item.",
        description = "Checks whether the authenticated user has liked the specified media item.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully checked like status.",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = Boolean::class)
                )]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Authentication required."
            )
        ]
    )
    fun checkIfLiked(@PathVariable mediaId: Long): ResponseEntity<Boolean> {
        return try {
            val hasLiked = likeService.hasUserLiked(mediaId)
            ResponseEntity.ok(hasLiked)
        } catch (ex: Exception) {
            logger.error("An unexpected error occurred while checking like status for media $mediaId", ex)
            ResponseEntity.badRequest().build()
        }
    }
}

