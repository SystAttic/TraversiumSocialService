package traversium.socialservice.service

import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import traversium.socialservice.db.model.Comment
import traversium.socialservice.db.repository.CommentRepository
import traversium.socialservice.dto.CommentDto
import traversium.socialservice.dto.CreateCommentDto
import traversium.socialservice.dto.UpdateCommentDto
import traversium.socialservice.exceptions.CommentNotFoundException
import traversium.socialservice.exceptions.UnauthorizedCommentAccessException
import traversium.socialservice.mapper.CommentMapper

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val commentMapper: CommentMapper
) {
    @Transactional
    fun createComment(mediaIdFromPath: Long, authorIdFromAuth: Long, createDto: CreateCommentDto): CommentDto {
        val parentComment: Comment? = createDto.parentId?.let { pid ->
            commentRepository.findById(pid)
                .orElseThrow { CommentNotFoundException("Parent comment with Id $pid was not found") }
        }

        val newCommentEntity = commentMapper.toEntity(
            dto = createDto,
            authorId = authorIdFromAuth,
            mediaId = mediaIdFromPath,
            parent = parentComment
        )

        val savedCommentEntity = commentRepository.save(newCommentEntity)

        return commentMapper.toDto(savedCommentEntity)
    }

    @Transactional
    fun updateComment(commentId: Long, authorIdFromAuth: Long, updateDto: UpdateCommentDto): CommentDto {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { CommentNotFoundException("Comment with id $commentId was not found") }

        if(comment.userId != authorIdFromAuth) {
            throw UnauthorizedCommentAccessException("User is not authorized to update this comment")
        }

        comment.content = updateDto.content

        return commentMapper.toDto(commentRepository.save(comment))
    }

    @Transactional
    fun deleteComment(commentId: Long, authorIdFromAuth: Long){
        val comment = commentRepository.findById(commentId)
            .orElseThrow { CommentNotFoundException("Comment with id $commentId was not found") }

        if(comment.userId != authorIdFromAuth) {
            throw UnauthorizedCommentAccessException("User is not authorized to delete this comment")
        }

        commentRepository.delete(comment)
    }

    fun getCommentById(commentId: Long): CommentDto {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { CommentNotFoundException("Comment with id $commentId was not found") }

        return commentMapper.toDto(comment)
    }

    fun getCommentsForAlbum(mediaId: Long, pageable: Pageable): Page<CommentDto> {
        //TODO: preveri če media obstaja na TripService

        val commentPage: Page<Comment> = commentRepository.findByMediaIdAndParentIsNull(mediaId, pageable)
        return commentPage.map { commentMapper.toDto(it) }
    }

    fun getRepliesForComment(parentId: Long, pageable: Pageable): Page<CommentDto> {
        if(!commentRepository.existsById(parentId)) {
            throw CommentNotFoundException("Parent comment with ID $parentId does not exist")
        }
        val replyPage: Page<Comment> = commentRepository.findByParent_CommentId(parentId, pageable)
        return replyPage.map { commentMapper.toDto(it) }
    }

}