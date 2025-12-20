package traversium.socialservice.service

import org.apache.logging.log4j.kotlin.Logging
import org.hibernate.annotations.CreationTimestamp
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import traversium.audit.kafka.ActivityType
import traversium.audit.kafka.AuditStreamData
import traversium.audit.kafka.EntityType
import traversium.notification.kafka.ActionType
import traversium.notification.kafka.NotificationStreamData
import traversium.socialservice.client.TripServiceClient
import traversium.socialservice.db.model.Comment
import traversium.socialservice.db.repository.CommentRepository
import traversium.socialservice.dto.CommentDto
import traversium.socialservice.dto.CreateCommentDto
import traversium.socialservice.dto.UpdateCommentDto
import traversium.socialservice.exceptions.CommentNotFoundException
import traversium.socialservice.exceptions.MediaNotFoundException
import traversium.socialservice.exceptions.UnauthorizedCommentAccessException
import traversium.socialservice.mapper.CommentMapper
import java.time.OffsetDateTime

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val commentMapper: CommentMapper,
    private val eventPublisher: ApplicationEventPublisher,
    private val tripServiceClient: TripServiceClient
) : SocialService(), Logging {
    @Transactional
    fun createComment(mediaIdFromPath: Long, createDto: CreateCommentDto): CommentDto {
        val authorIdFromAuth = getCurrentUserId()
        val authorFirebaseId = getCurrentUserFirebaseId()
        val authHeader = getAuthorizationHeader()

        if (!tripServiceClient.doesMediaExist(mediaIdFromPath, authHeader)) {
            throw MediaNotFoundException("Media with ID $mediaIdFromPath does not exist")
        }

        val parentComment: Comment? = createDto.parentId?.let { pid ->
            commentRepository.findById(pid)
                .orElseThrow { CommentNotFoundException("Parent comment with Id $pid was not found") }
        }

        val newCommentEntity = commentMapper.toEntity(
            dto = createDto,
            authorId = authorIdFromAuth,
            firebaseId = authorFirebaseId,
            mediaId = mediaIdFromPath,
            parent = parentComment
        )

        val savedCommentEntity = commentRepository.save(newCommentEntity)

        // Publish notification event (notify media owner or parent comment author)
        publishCommentNotification(savedCommentEntity, parentComment, authHeader)

        // Publish audit event
        publishCommentAuditEvent(authorFirebaseId, "COMMENT_CREATED", savedCommentEntity.commentId, mediaIdFromPath)

        logger.info("Comment with ID ${savedCommentEntity.commentId} created on media $mediaIdFromPath by user $authorIdFromAuth")

        return commentMapper.toDto(savedCommentEntity)
    }

    @Transactional
    fun updateComment(commentId: Long, updateDto: UpdateCommentDto): CommentDto {
        val authorFirebaseId = getCurrentUserFirebaseId()
        val authorIdFromAuth = getCurrentUserId()

        val comment = commentRepository.findById(commentId)
            .orElseThrow { CommentNotFoundException("Comment with id $commentId was not found") }

        if(comment.userId != authorIdFromAuth) {
            throw UnauthorizedCommentAccessException("User is not authorized to update this comment")
        }

        comment.content = updateDto.content

        val updatedComment = commentRepository.save(comment)

        // Publish audit event
        publishCommentAuditEvent(authorFirebaseId, "COMMENT_UPDATED", commentId, comment.mediaId!!)
        logger.info("Comment with ID $commentId updated by user $authorIdFromAuth")

        return commentMapper.toDto(updatedComment)
    }

    @Transactional
    fun deleteComment(commentId: Long){
        val authorFirebaseId = getCurrentUserFirebaseId()
        val authorIdFromAuth = getCurrentUserId()

        val comment = commentRepository.findById(commentId)
            .orElseThrow { CommentNotFoundException("Comment with id $commentId was not found") }

        if(comment.userId != authorIdFromAuth) {
            throw UnauthorizedCommentAccessException("User is not authorized to delete this comment")
        }

        val mediaId = comment.mediaId
        commentRepository.delete(comment)
        logger.info("Comment with ID $commentId deleted by user $authorIdFromAuth")

        // Publish audit event
        publishCommentAuditEvent(authorFirebaseId, "COMMENT_DELETED", commentId, mediaId!!)
    }

    private fun publishCommentNotification(comment: Comment, parentComment: Comment?, authHeader: String?) {
        // For now, we'll notify the parent comment author if it's a reply, otherwise media owner
        val ownerId = if (parentComment != null) parentComment.firebaseId else tripServiceClient.getMediaOwner(comment.mediaId!!, authHeader)

        val notification = NotificationStreamData(
            timestamp = OffsetDateTime.now(),
            senderId = comment.userId.toString(), // This should be Firebase UID or username
            receiverIds = listOf<String>(ownerId!!),
            collectionReferenceId = null,
            nodeReferenceId = null,
            commentReferenceId = comment.commentId,
            action = if (parentComment != null) ActionType.REPLY else ActionType.ADD,
            mediaReferenceId = comment.mediaId,
            mediaCount = null
        )
        eventPublisher.publishEvent(notification)
    }

    private fun publishCommentAuditEvent(userId: String, action: String, commentId: Long?, mediaId: Long) {
        val auditEvent = AuditStreamData(
            timestamp = OffsetDateTime.now(),
            userId = userId,
            activityType = ActivityType.SOCIAL_ACTIVITY,
            action = action,
            entityType = EntityType.COMMENT,
            entityId = commentId,
            tripId = null, // TODO: Extract tripId from mediaId if available
            metadata = mapOf(
                "commentId" to (commentId ?: ""),
                "mediaId" to mediaId,
                "entityType" to "COMMENT"
            )
        )
        eventPublisher.publishEvent(auditEvent)
    }

    fun getCommentById(commentId: Long): CommentDto {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { CommentNotFoundException("Comment with id $commentId was not found") }

        return commentMapper.toDto(comment)
    }

    fun getCommentsForAlbum(mediaId: Long, pageable: Pageable): Page<CommentDto> {
        val authHeader = getAuthorizationHeader()

        if (!tripServiceClient.doesMediaExist(mediaId, authHeader)) {
            throw MediaNotFoundException("Media with ID $mediaId does not exist")
        }

        val commentPage: Page<Comment> = commentRepository.findByMediaIdAndParentIsNull(mediaId, pageable)
        return commentPage.map { commentMapper.toDto(it) }
    }

    fun getRepliesForComment(parentId: Long, pageable: Pageable): Page<CommentDto> {
        if(!commentRepository.existsById(parentId)) {
            throw CommentNotFoundException("Parent comment with ID $parentId does not exist")
        }
        val replyPage: Page<Comment> = commentRepository.findByParent_CommentId(parentId, pageable)
        return replyPage.map { commentMapper.toDto(it) }
    }

}