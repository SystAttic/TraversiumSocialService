package traversium.socialservice.dto

import traversium.socialservice.db.model.Comment
import java.time.LocalDateTime

data class CommentDto(
    val commentId: Long?,
    val content: String,
    val userId: Long,
    val albumId: Long,
    val parentId: Long?,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,

    val replyCount: Long = 0
)