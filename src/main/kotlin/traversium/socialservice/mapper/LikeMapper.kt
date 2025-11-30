package traversium.socialservice.mapper

import org.springframework.stereotype.Component
import traversium.socialservice.db.model.Like
import traversium.socialservice.dto.LikeDto

@Component
class LikeMapper {

    fun toDto(entity: Like): LikeDto {
        return LikeDto(
            likeId = entity.likeId,
            userId = entity.userId,
            mediaId = entity.mediaId,
            createdAt = entity.createdAt
        )
    }

    fun toEntity(userId: Long, mediaId: Long): Like {
        return Like(
            userId = userId,
            mediaId = mediaId
        )
    }
}

