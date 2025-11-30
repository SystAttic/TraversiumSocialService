package traversium.socialservice.service

import org.hibernate.annotations.CreationTimestamp
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import traversium.audit.kafka.ActivityType
import traversium.audit.kafka.AuditStreamData
import traversium.audit.kafka.EntityType
import traversium.notification.kafka.NotificationStreamData
import traversium.socialservice.db.model.Comment
import traversium.socialservice.db.repository.CommentRepository
import traversium.socialservice.dto.CommentDto
import traversium.socialservice.dto.CreateCommentDto
import traversium.socialservice.dto.UpdateCommentDto
import traversium.socialservice.exceptions.CommentNotFoundException
import traversium.socialservice.exceptions.UnauthorizedCommentAccessException
import traversium.socialservice.mapper.CommentMapper
import java.time.OffsetDateTime

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val commentMapper: CommentMapper,
    private val eventPublisher: ApplicationEventPublisher
) {
    @Transactional
    fun createComment(mediaIdFromPath: Long, authorIdFromAuth: Long, authorFirebaseId: String, createDto: CreateCommentDto): CommentDto {
        val parentComment: Comment? = createDto.parentId?.let { pid ->
            commentRepository.findById(pid)
                .orElseThrow { CommentNotFoundException("Parent comment with Id $pid was not found") }
        }

        val newCommentEntity = commentMapper.toEntity(
            dto = createDto,
            authorId = authorIdFromAuth,
            mediaId = mediaIdFromPath,
            parent = parentComment
        )

        val savedCommentEntity = commentRepository.save(newCommentEntity)

        // Publish notification event (notify media owner or parent comment author)
        publishCommentNotification(savedCommentEntity, parentComment)

        // Publish audit event
        publishCommentAuditEvent(authorFirebaseId, "COMMENT_CREATED", savedCommentEntity.commentId, mediaIdFromPath)

        return commentMapper.toDto(savedCommentEntity)
    }

    @Transactional
    fun updateComment(commentId: Long, authorIdFromAuth: Long, authorFirebaseId: String, updateDto: UpdateCommentDto): CommentDto {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { CommentNotFoundException("Comment with id $commentId was not found") }

        if(comment.userId != authorIdFromAuth) {
            throw UnauthorizedCommentAccessException("User is not authorized to update this comment")
        }

        comment.content = updateDto.content

        val updatedComment = commentRepository.save(comment)

        // Publish audit event
        publishCommentAuditEvent(authorFirebaseId, "COMMENT_UPDATED", commentId, comment.mediaId)

        return commentMapper.toDto(updatedComment)
    }

    @Transactional
    fun deleteComment(commentId: Long, authorIdFromAuth: Long, authorFirebaseId: String){
        val comment = commentRepository.findById(commentId)
            .orElseThrow { CommentNotFoundException("Comment with id $commentId was not found") }

        if(comment.userId != authorIdFromAuth) {
            throw UnauthorizedCommentAccessException("User is not authorized to delete this comment")
        }

        val mediaId = comment.mediaId
        commentRepository.delete(comment)

        // Publish audit event
        publishCommentAuditEvent(authorFirebaseId, "COMMENT_DELETED", commentId, mediaId)
    }

    private fun publishCommentNotification(comment: Comment, parentComment: Comment?) {
        // For now, we'll notify the parent comment author if it's a reply, otherwise media owner
        // TODO: Get media owner from TripService to send proper notification
        val notification = NotificationStreamData(
            timestamp = OffsetDateTime.now(),
            senderId = comment.userId.toString(), // This should be Firebase UID or username
            receiverIds = emptyList(), // TODO: Get actual receiver IDs (media owner or parent comment author)
            collectionReferenceId = null,
            nodeReferenceId = comment.mediaId,
            commentReferenceId = comment.commentId,
            action = if (parentComment != null) "COMMENT_REPLY" else "COMMENT_CREATED"
        )
        eventPublisher.publishEvent(notification)
    }

    private fun publishCommentAuditEvent(userId: String, action: String, commentId: Long?, mediaId: Long) {
        val auditEvent = AuditStreamData(
            timestamp = OffsetDateTime.now(),
            userId = userId,
            activityType = ActivityType.USER_ACTIVITY,
            action = action,
            entityType = null, // TODO: Add COMMENT to EntityType enum in audit-models
            entityId = commentId,
            tripId = null,
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
        //TODO: preveri če media obstaja na TripService

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