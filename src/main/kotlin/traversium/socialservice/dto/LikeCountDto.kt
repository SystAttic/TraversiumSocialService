package traversium.socialservice.dto

data class LikeCountDto(
    val mediaId: Long,
    val likeCount: Long,
    val isLiked: Boolean
)

