package traversium.socialservice.dto

import java.time.OffsetDateTime

data class CommentDto(
    val commentId: Long?,
    val content: String,
    val userId: Long,
    val albumId: Long,
    val parentId: Long?,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,

    val replyCount: Long = 0
)