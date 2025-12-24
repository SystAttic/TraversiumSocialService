package traversium.socialservice.service

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import traversium.audit.kafka.AuditStreamData
import traversium.notification.kafka.NotificationStreamData
import traversium.socialservice.client.TripServiceClient
import traversium.socialservice.db.model.Comment
import traversium.socialservice.db.repository.CommentRepository
import traversium.socialservice.dto.CommentDto
import traversium.socialservice.dto.CreateCommentDto
import traversium.socialservice.dto.UpdateCommentDto
import traversium.socialservice.exceptions.CommentModerationException
import traversium.socialservice.exceptions.CommentNotFoundException
import traversium.socialservice.exceptions.MediaNotFoundException
import traversium.socialservice.exceptions.UnauthorizedCommentAccessException
import traversium.socialservice.mapper.CommentMapper
import traversium.socialservice.security.TraversiumAuthentication
import traversium.socialservice.security.TraversiumPrincipal
import java.util.*

@ExtendWith(MockKExtension::class)
class CommentServiceTest {

    @MockK
    lateinit var commentRepository: CommentRepository

    @MockK
    lateinit var commentMapper: CommentMapper

    @MockK
    lateinit var eventPublisher: ApplicationEventPublisher

    @MockK
    lateinit var tripServiceClient: TripServiceClient

    @MockK
    lateinit var moderationClient: ModerationServiceGrpcClient

    @InjectMockKs
    lateinit var commentService: CommentService

    @BeforeEach
    fun setUp() {
        mockkStatic(SecurityContextHolder::class)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(SecurityContextHolder::class)
    }

    private fun mockAuthenticatedUser(uid: String, userId: Long) {
        val principal = TraversiumPrincipal(uid, "test@test.com", null)

        val auth = TraversiumAuthentication(
            principal = principal,
            details = null,
            authorities = emptyList(),
            token = "mock-token"
        )

        val securityContext = mockk<SecurityContext>()

        every { securityContext.authentication } returns auth
        every { SecurityContextHolder.getContext() } returns securityContext
    }

    @Test
    fun `createComment should save comment and publish events`() {
        // GIVEN
        val firebaseId = "user-uid-123"
        val userId = kotlin.math.abs(firebaseId.hashCode().toLong())
        val mediaId = 100L
        val dto = CreateCommentDto(content = "Nice photo!")
        val mediaOwnerId = "owner-uid-999"

        val commentEntity = Comment(
            commentId = 1L,
            content = "Nice photo!",
            userId = userId,
            firebaseId = firebaseId,
            mediaId = mediaId
        )
        val expectedDto = CommentDto(
            commentId = 1L, content = "Nice photo!", userId = userId,
            firebaseId = firebaseId, mediaId = mediaId, parentId = null
        )

        mockAuthenticatedUser(firebaseId, userId)

        every { tripServiceClient.getMediaOwner(mediaId, any()) } returns mediaOwnerId
        every { commentMapper.toEntity(dto, any(), firebaseId, mediaId, null) } returns commentEntity
        every { commentMapper.toDto(commentEntity) } returns expectedDto
        every { commentRepository.save(commentEntity) } returns commentEntity
        every { tripServiceClient.doesMediaExist(mediaId, any()) } returns true
        every { moderationClient.isTextAllowed(any()) } returns true

        justRun { eventPublisher.publishEvent(any<NotificationStreamData>()) }
        justRun { eventPublisher.publishEvent(any<AuditStreamData>()) }

        // WHEN
        val result = commentService.createComment(mediaId, dto)

        // THEN
        assertEquals(expectedDto, result)

        verify(exactly = 1) { commentRepository.save(commentEntity) }

        verify(exactly = 1) {
            eventPublisher.publishEvent(withArg<NotificationStreamData> {
                assertEquals(mediaOwnerId, it.receiverIds[0])
                assertEquals(userId.toString(), it.senderId)
            })
        }

        verify(exactly = 1) {
            eventPublisher.publishEvent(withArg<AuditStreamData> {
                assertEquals("COMMENT_CREATED", it.action)
                assertEquals(firebaseId, it.userId)
            })
        }
    }

    @Test
    fun `createComment should create a REPLY and notify parent author`() {
        // GIVEN
        val currentUid = "replier-uid"
        val currentUserId = kotlin.math.abs(currentUid.hashCode().toLong())
        val parentId = 50L
        val mediaId = 100L
        val parentAuthorUid = "parent-author-uid"

        val dto = CreateCommentDto(content = "This is a reply", parentId = parentId)

        // The parent comment exists
        val parentComment = Comment(
            commentId = parentId,
            content = "Original",
            userId = 999L,
            firebaseId = parentAuthorUid, // This user should get the notification
            mediaId = 100L
        )

        val newComment = Comment(
            commentId = 51L, content = "This is a reply", userId = currentUserId,
            firebaseId = currentUid, mediaId = 100L, parent = parentComment
        )

        val expectedDto = CommentDto(
            commentId = 51L, content = "This is a reply", userId = currentUserId,
            firebaseId = currentUid, mediaId = 100L, parentId = parentId
        )

        mockAuthenticatedUser(currentUid, currentUserId)

        // Mock finding the parent
        every { commentRepository.findById(parentId) } returns Optional.of(parentComment)
        // Mock saving
        every { commentMapper.toEntity(dto, currentUserId, currentUid, 100L, parentComment) } returns newComment
        every { commentRepository.save(newComment) } returns newComment
        every { commentMapper.toDto(newComment) } returns expectedDto

        every { tripServiceClient.doesMediaExist(mediaId, any()) } returns true
        every { moderationClient.isTextAllowed(any()) } returns true

        // Mock publishers
        justRun { eventPublisher.publishEvent(any<NotificationStreamData>()) }
        justRun { eventPublisher.publishEvent(any<AuditStreamData>()) }

        // WHEN
        val result = commentService.createComment(100L, dto)

        // THEN
        assertEquals(expectedDto, result)

        // Verify Notification was sent to PARENT AUTHOR, not media owner
        verify {
            eventPublisher.publishEvent(withArg<NotificationStreamData> {
                assertEquals(parentAuthorUid, it.receiverIds[0])
                assertEquals("REPLY", it.action.name) // Assuming ActionType.REPLY enum
            })
        }

        // TripService should NOT be called for a reply (based on your service logic optimization)
        verify(exactly = 0) { tripServiceClient.getMediaOwner(any(), any()) }
    }

    @Test
    fun `createComment should throw 404 when parent comment does not exist`() {
        // GIVEN
        val currentUid = "user-1"
        val mediaId = 100L
        val currentUserId = kotlin.math.abs(currentUid.hashCode().toLong())
        val missingParentId = 999L
        val dto = CreateCommentDto(content = "Reply to ghost", parentId = missingParentId)

        mockAuthenticatedUser(currentUid, currentUserId)

        every { commentRepository.findById(missingParentId) } returns Optional.empty()
        every { tripServiceClient.doesMediaExist(mediaId, any()) } returns true
        every { moderationClient.isTextAllowed(any()) } returns true

        // WHEN & THEN
        val exception = assertThrows<CommentNotFoundException> {
            commentService.createComment(100L, dto)
        }
        assertEquals("Parent comment with Id $missingParentId was not found", exception.message)

        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `createComment should throw MediaNotFoundException if media does not exist`() {
        // GIVEN
        val mediaId = 999L
        val dto = CreateCommentDto("Test")

        mockAuthenticatedUser("some-uid", 123L)

        // Mock the check returning FALSE
        every { tripServiceClient.doesMediaExist(mediaId, any()) } returns false
        every { moderationClient.isTextAllowed(any()) } returns true

        // WHEN & THEN
        assertThrows<MediaNotFoundException> {
            commentService.createComment(mediaId, dto)
        }

        // Verify we didn't save anything
        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `createComment should throw OffensiveContentException if content is offensive`() {
        // GIVEN
        val mediaId = 100L
        val dto = CreateCommentDto("You are stupid")

        mockAuthenticatedUser("uid", 1L)
        every { tripServiceClient.doesMediaExist(mediaId, any()) } returns true

        every { moderationClient.isTextAllowed(dto.content) } returns false

        // WHEN & THEN
        assertThrows<CommentModerationException> {
            commentService.createComment(mediaId, dto)
        }

        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `updateComment should update content and publish audit when user is authorized`() {
        // GIVEN
        val currentUid = "author-uid"
        val currentUserId = kotlin.math.abs(currentUid.hashCode().toLong())
        val commentId = 123L
        val mediaId = 555L
        val originalContent = "Old text"
        val newContent = "Updated text"

        val updateDto = UpdateCommentDto(content = newContent)

        val existingComment = Comment(
            commentId = commentId,
            content = originalContent,
            userId = currentUserId,
            firebaseId = currentUid,
            mediaId = mediaId
        )

        val updatedComment = Comment(
            commentId = commentId,
            content = newContent,
            userId = currentUserId,
            firebaseId = currentUid,
            mediaId = mediaId
        )

        val expectedDto = CommentDto(
            commentId = commentId, content = newContent, userId = currentUserId,
            firebaseId = currentUid, mediaId = mediaId, parentId = null
        )

        mockAuthenticatedUser(currentUid, currentUserId)

        every { commentRepository.findById(commentId) } returns Optional.of(existingComment)

        every { commentRepository.save(any()) } returns updatedComment

        every { commentMapper.toDto(updatedComment) } returns expectedDto
        every { moderationClient.isTextAllowed(any()) } returns true

        justRun { eventPublisher.publishEvent(any<AuditStreamData>()) }

        // WHEN
        val result = commentService.updateComment(commentId, updateDto)

        // THEN
        assertEquals(newContent, result.content)

        // Verify that the save method was called with an entity having the NEW content
        verify(exactly = 1) {
            commentRepository.save(withArg { savedEntity ->
                assertEquals(newContent, savedEntity.content)
                assertEquals(commentId, savedEntity.commentId)
            })
        }

        // Verify Audit Event
        verify(exactly = 1) {
            eventPublisher.publishEvent(withArg<AuditStreamData> {
                assertEquals("COMMENT_UPDATED", it.action)
                assertEquals(commentId, it.entityId)
                assertEquals(currentUid, it.userId)
            })
        }
    }

    @Test
    fun `updateComment should throw exception if user is not owner`() {
        // GIVEN
        val currentUserFirebaseId = "attacker-uid"
        val currentUserId = kotlin.math.abs(currentUserFirebaseId.hashCode().toLong())

        val originalOwnerId = 99999L
        val commentId = 5L
        val updateDto = UpdateCommentDto(content = "Hacked content")

        val existingComment = Comment(
            commentId = commentId,
            content = "Original Content",
            userId = originalOwnerId,
            firebaseId = "original-firebase-id",
            mediaId = 50L
        )

        mockAuthenticatedUser(currentUserFirebaseId, currentUserId)

        every { commentRepository.findById(commentId) } returns Optional.of(existingComment)
        every { moderationClient.isTextAllowed(any()) } returns true

        // WHEN & THEN
        assertThrows<UnauthorizedCommentAccessException> {
            commentService.updateComment(commentId, updateDto)
        }

        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `updateComment should throw 404 if comment not found`() {
        // GIVEN
        mockAuthenticatedUser("user", 1L)
        val dto = UpdateCommentDto("New content")

        every { commentRepository.findById(999L) } returns Optional.empty()
        every { moderationClient.isTextAllowed(any()) } returns true

        // WHEN & THEN
        assertThrows<CommentNotFoundException> {
            commentService.updateComment(999L, dto)
        }
    }

    @Test
    fun `deleteComment should delete comment and publish audit if user is owner`() {
        // GIVEN
        val currentUid = "owner-uid"
        val currentUserId = kotlin.math.abs(currentUid.hashCode().toLong())
        val commentId = 10L
        val mediaId = 100L

        val commentToDelete = Comment(
            commentId = commentId,
            content = "To be deleted",
            userId = currentUserId,
            firebaseId = currentUid,
            mediaId = mediaId
        )

        mockAuthenticatedUser(currentUid, currentUserId)

        every { commentRepository.findById(commentId) } returns Optional.of(commentToDelete)
        justRun { commentRepository.delete(commentToDelete) }
        justRun { eventPublisher.publishEvent(any<AuditStreamData>()) }

        // WHEN
        commentService.deleteComment(commentId)

        // THEN
        verify(exactly = 1) { commentRepository.delete(commentToDelete) }
        verify {
            eventPublisher.publishEvent(withArg<AuditStreamData> {
                assertEquals("COMMENT_DELETED", it.action)
                assertEquals(commentId, it.entityId)
            })
        }
    }

    @Test
    fun `deleteComment should throw Forbidden if user is not owner`() {
        // GIVEN
        val attackerUid = "attacker"
        val attackerId = kotlin.math.abs(attackerUid.hashCode().toLong())
        val commentId = 10L

        val comment = Comment(
            commentId = commentId,
            content = "Other user's comment",
            userId = 9999L, // Different user
            firebaseId = "victim",
            mediaId = 100L
        )

        mockAuthenticatedUser(attackerUid, attackerId)
        every { commentRepository.findById(commentId) } returns Optional.of(comment)

        // WHEN & THEN
        assertThrows<UnauthorizedCommentAccessException> {
            commentService.deleteComment(commentId)
        }

        verify(exactly = 0) { commentRepository.delete(any()) }
    }

    @Test
    fun `deleteComment should throw 404 if comment not found`() {
        // GIVEN
        mockAuthenticatedUser("user", 1L)
        every { commentRepository.findById(999L) } returns Optional.empty()

        // WHEN & THEN
        assertThrows<CommentNotFoundException> {
            commentService.deleteComment(999L)
        }
    }

    @Test
    fun `getCommentsForAlbum should return paged dtos`() {
        // GIVEN
        val mediaId = 100L
        val pageable = org.springframework.data.domain.Pageable.unpaged()

        val commentEntity = Comment(commentId = 1, content = "Top level", userId = 1, firebaseId = "u1", mediaId = mediaId)
        val commentDto = CommentDto(commentId = 1, content = "Top level", userId = 1, firebaseId = "u1", mediaId = mediaId, parentId = null)

        val page = org.springframework.data.domain.PageImpl(listOf(commentEntity))

        mockAuthenticatedUser("test-uid", 1L)

        every { commentRepository.findByMediaIdAndParentIsNull(mediaId, pageable) } returns page
        every { commentMapper.toDto(commentEntity) } returns commentDto
        every { tripServiceClient.doesMediaExist(mediaId, any()) } returns true

        // WHEN
        val result = commentService.getCommentsForAlbum(mediaId, pageable)

        // THEN
        assertEquals(1, result.content.size)
        assertEquals("Top level", result.content[0].content)
    }

    @Test
    fun `getRepliesForComment should throw 404 if parent does not exist`() {
        // GIVEN
        val parentId = 999L
        val pageable = org.springframework.data.domain.Pageable.unpaged()

        every { commentRepository.existsById(parentId) } returns false

        // WHEN & THEN
        assertThrows<CommentNotFoundException> {
            commentService.getRepliesForComment(parentId, pageable)
        }

        verify(exactly = 0) { commentRepository.findByParent_CommentId(any(), any()) }
    }

    @Test
    fun `getCommentsForAlbum should throw 404 if media missing`() {
        val mediaId = 999L
        val pageable = org.springframework.data.domain.Pageable.unpaged()

        mockAuthenticatedUser("test-uid", 1L)

        every { tripServiceClient.doesMediaExist(mediaId, any()) } returns false

        assertThrows<MediaNotFoundException> {
            commentService.getCommentsForAlbum(mediaId, pageable)
        }
    }

    @Test
    fun `getRepliesForComment should return paged replies if parent exists`() {
        // GIVEN
        val parentId = 50L
        val pageable = org.springframework.data.domain.Pageable.unpaged()

        val replyEntity = Comment(commentId = 2, content = "Reply", userId = 1, firebaseId = "u1", mediaId = 100L)
        val replyDto = CommentDto(commentId = 2, content = "Reply", userId = 1, firebaseId = "u1", mediaId = 100L, parentId = parentId)

        every { commentRepository.existsById(parentId) } returns true
        every { commentRepository.findByParent_CommentId(parentId, pageable) } returns org.springframework.data.domain.PageImpl(listOf(replyEntity))
        every { commentMapper.toDto(replyEntity) } returns replyDto

        // WHEN
        val result = commentService.getRepliesForComment(parentId, pageable)

        // THEN
        assertEquals(1, result.content.size)
        assertEquals(parentId, result.content[0].parentId)
    }
}