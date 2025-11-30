package traversium.socialservice.db.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import traversium.socialservice.db.model.Like
import java.util.*

@Repository
interface LikeRepository : JpaRepository<Like, Long> {

    fun existsByUserIdAndMediaId(userId: Long, mediaId: Long): Boolean

    fun findByUserIdAndMediaId(userId: Long, mediaId: Long): Optional<Like>

    fun countByMediaId(mediaId: Long): Long

    fun deleteByUserIdAndMediaId(userId: Long, mediaId: Long)
}

