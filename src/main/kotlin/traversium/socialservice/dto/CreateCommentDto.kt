package traversium.socialservice.dto

data class CreateCommentDto(
    val content: String,
    val parentId: Long? = null
)