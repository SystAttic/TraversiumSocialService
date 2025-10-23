package traversium.socialservice.mapper

import org.springframework.stereotype.Component
import traversium.socialservice.db.model.Comment
import traversium.socialservice.db.repository.CommentRepository
import traversium.socialservice.dto.CommentDto
import traversium.socialservice.dto.CreateCommentDto

@Component
class CommentMapper(
    private val commentRepository: CommentRepository
) {

    fun toDto(entity: Comment): CommentDto {
        // Calculate the reply count using the repository.
        val replyCount = commentRepository.countByParent_CommentId(entity.commentId!!)

        return CommentDto(
            commentId = entity.commentId,
            content = entity.content,
            userId = entity.userId,
            nodeId = entity.nodeId,
            parentId = entity.parent?.commentId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            replyCount = replyCount
        )
    }

    fun toEntity(dto: CreateCommentDto, authorId: Long, nodeId: Long, parent: Comment?): Comment {
        return Comment(
            content = dto.content,
            userId = authorId,
            nodeId = nodeId,
            parent = parent
        )
    }
}