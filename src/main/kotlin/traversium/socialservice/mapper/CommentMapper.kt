package traversium.socialservice.mapper

import org.springframework.stereotype.Component
import traversium.socialservice.db.model.Comment
import traversium.socialservice.db.repository.CommentRepository
import traversium.socialservice.dto.CommentDto
import traversium.socialservice.dto.CreateCommentDto
import traversium.socialservice.exceptions.InvalidCommentDataException

@Component
class CommentMapper(
    private val commentRepository: CommentRepository
) {

    fun toDto(entity: Comment): CommentDto {
        val replyCount = commentRepository.countByParent_CommentId(entity.commentId!!)

        return CommentDto(
            commentId = entity.commentId,
            content = entity.content ?: throw InvalidCommentDataException("Comment content cannot be null"),
            userId = entity.userId ?: throw InvalidCommentDataException("Comment userId cannot be null"),
            firebaseId = entity.firebaseId ?: throw InvalidCommentDataException("Comment firebaseId cannot be null"),
            mediaId = entity.mediaId ?: throw InvalidCommentDataException("Comment mediaId cannot be null"),
            parentId = entity.parent?.commentId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            replyCount = replyCount
        )
    }

    fun toEntity(dto: CreateCommentDto, authorId: Long, firebaseId: String, mediaId: Long, parent: Comment?): Comment {
        return Comment(
            content = dto.content,
            userId = authorId,
            firebaseId = firebaseId,
            mediaId = mediaId,
            parent = parent
        )
    }
}