package traversium.socialservice.dto

import java.time.OffsetDateTime

data class LikeDto(
    val likeId: Long?,
    val userId: Long,
    val mediaId: Long,
    val createdAt: OffsetDateTime?
)

