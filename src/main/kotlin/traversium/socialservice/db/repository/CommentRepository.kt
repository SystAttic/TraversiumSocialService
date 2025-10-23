package traversium.socialservice.db.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import traversium.socialservice.db.model.Comment

@Repository
interface CommentRepository : JpaRepository<Comment, Long> {

    /**
     * Finds a paginated list of top-level comments for a specific node.
     * Top-level comments are identified by having a null parent.
     *
     * Spring Data JPA automatically generates the query from the method name:
     * "SELECT c FROM Comment c WHERE c.nodeId = :nodeId AND c.parent IS NULL"
     *
     * @param nodeId The ID of the node to fetch comments for.
     * @param pageable The pagination information (page number, size, sorting).
     * @return A Page of top-level comments.
     */
    fun findByNodeIdAndParentIsNull(nodeId: Long, pageable: Pageable): Page<Comment>

    /**
     * Finds a paginated list of replies for a specific parent comment.
     * The underscore in "Parent_CommentId" tells Spring Data to look at the
     * 'commentId' property of the 'parent' field.
     *
     * @param parentId The ID of the parent comment.
     * @param pageable The pagination information.
     * @return A Page of reply comments.
     */
    fun findByParent_CommentId(parentId: Long, pageable: Pageable): Page<Comment>

    /**
     * Efficiently counts the number of direct replies for a given parent comment
     * without fetching the actual comment entities.
     *
     * @param parentId The ID of the parent comment.
     * @return The total number of replies.
     */
    fun countByParent_CommentId(parentId: Long): Long
}