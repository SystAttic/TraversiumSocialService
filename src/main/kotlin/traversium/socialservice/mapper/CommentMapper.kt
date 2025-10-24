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
            albumId = entity.albumId,
            parentId = entity.parent?.commentId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            replyCount = replyCount
        )
    }

    fun toEntity(dto: CreateCommentDto, authorId: Long, albumId: Long, parent: Comment?): Comment {
        return Comment(
            content = dto.content,
            userId = authorId,
            albumId = albumId,
            parent = parent
        )
    }
}