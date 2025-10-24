package traversium.socialservice.db.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import traversium.socialservice.db.model.Comment

@Repository
interface CommentRepository : JpaRepository<Comment, Long> {

    fun findByAlbumIdAndParentIsNull(albumId: Long, pageable: Pageable): Page<Comment>

    fun findByParent_CommentId(parentId: Long, pageable: Pageable): Page<Comment>

    fun countByParent_CommentId(parentId: Long): Long
}