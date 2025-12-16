package traversium.socialservice.service

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
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
import traversium.socialservice.db.model.Like
import traversium.socialservice.db.repository.LikeRepository
import traversium.socialservice.dto.LikeDto
import traversium.socialservice.exceptions.DuplicateLikeException
import traversium.socialservice.exceptions.LikeNotFoundException
import traversium.socialservice.exceptions.MediaNotFoundException
import traversium.socialservice.mapper.LikeMapper
import traversium.socialservice.security.TraversiumAuthentication
import traversium.socialservice.security.TraversiumPrincipal
import java.util.*

@ExtendWith(MockKExtension::class)
class LikeServiceTest {

    @MockK
    lateinit var likeRepository: LikeRepository

    @MockK
    lateinit var likeMapper: LikeMapper

    @MockK
    lateinit var eventPublisher: ApplicationEventPublisher

    @MockK
    lateinit var tripServiceClient: TripServiceClient

    @InjectMockKs
    lateinit var likeService: LikeService

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
    fun `likeMedia should save like and publish events when not already liked`() {
        // GIVEN
        val firebaseId = "user-1"
        val userId = kotlin.math.abs(firebaseId.hashCode().toLong())
        val mediaId = 100L
        val ownerId = "media-owner-uid"

        val likeEntity = Like(likeId = 1L, userId = userId, mediaId = mediaId)
        val likeDto = LikeDto(likeId = 1L, userId = userId, mediaId = mediaId, createdAt = null)

        mockAuthenticatedUser(firebaseId, userId)

        every { likeRepository.existsByUserIdAndMediaId(userId, mediaId) } returns false

        every { likeMapper.toEntity(userId, mediaId) } returns likeEntity
        every { likeRepository.save(likeEntity) } returns likeEntity
        every { likeMapper.toDto(likeEntity) } returns likeDto

        every { tripServiceClient.getMediaOwner(mediaId) } returns ownerId
        every { tripServiceClient.doesMediaExist(mediaId) } returns true

        justRun { eventPublisher.publishEvent(any<NotificationStreamData>()) }
        justRun { eventPublisher.publishEvent(any<AuditStreamData>()) }

        // WHEN
        val result = likeService.likeMedia(mediaId)

        // THEN
        assertEquals(likeDto, result)

        verify(exactly = 1) { likeRepository.save(likeEntity) }

        // Verify Notification (To Owner)
        verify {
            eventPublisher.publishEvent(withArg<NotificationStreamData> {
                assertEquals(ownerId, it.receiverIds[0])
                assertEquals("LIKE", it.action.name)
                assertEquals(mediaId, it.mediaReferenceId)
            })
        }

        // Verify Audit (By Liker)
        verify {
            eventPublisher.publishEvent(withArg<AuditStreamData> {
                assertEquals("LIKE_CREATED", it.action)
                assertEquals(firebaseId, it.userId)
            })
        }
    }

    @Test
    fun `likeMedia should throw DuplicateLikeException if already liked`() {
        // GIVEN
        val firebaseId = "user-1"
        val userId = kotlin.math.abs(firebaseId.hashCode().toLong())
        val mediaId = 100L

        mockAuthenticatedUser(firebaseId, userId)

        every { likeRepository.existsByUserIdAndMediaId(userId, mediaId) } returns true

        every { tripServiceClient.doesMediaExist(mediaId) } returns true

        // WHEN & THEN
        val ex = assertThrows<DuplicateLikeException> {
            likeService.likeMedia(mediaId)
        }
        assertEquals("User has already liked this media", ex.message)

        // Verify we never attempted to save or call external services
        verify(exactly = 0) { likeRepository.save(any()) }
        verify(exactly = 0) { tripServiceClient.getMediaOwner(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `likeMedia should throw MediaNotFoundException if media does not exist`() {
        // GIVEN
        val mediaId = 999L
        val firebaseId = "user-1"
        val userId = kotlin.math.abs(firebaseId.hashCode().toLong())

        mockAuthenticatedUser(firebaseId, userId) // Helper from previous steps

        // Mock the check returning FALSE
        every { tripServiceClient.doesMediaExist(mediaId) } returns false

        // WHEN & THEN
        assertThrows<MediaNotFoundException> {
            likeService.likeMedia(mediaId)
        }

        verify(exactly = 0) { likeRepository.save(any()) }
    }

    @Test
    fun `unlikeMedia should delete like and audit if like exists`() {
        // GIVEN
        val firebaseId = "user-1"
        val userId = kotlin.math.abs(firebaseId.hashCode().toLong())
        val mediaId = 100L
        val likeId = 55L

        val existingLike = Like(likeId = likeId, userId = userId, mediaId = mediaId)

        mockAuthenticatedUser(firebaseId, userId)

        every { likeRepository.findByUserIdAndMediaId(userId, mediaId) } returns Optional.of(existingLike)
        justRun { likeRepository.delete(existingLike) }
        justRun { eventPublisher.publishEvent(any<AuditStreamData>()) }

        // WHEN
        likeService.unlikeMedia(mediaId)

        // THEN
        verify(exactly = 1) { likeRepository.delete(existingLike) }

        verify {
            eventPublisher.publishEvent(withArg<AuditStreamData> {
                assertEquals("LIKE_DELETED", it.action)
                assertEquals(likeId, it.entityId)
            })
        }
    }

    @Test
    fun `unlikeMedia should throw LikeNotFoundException if like does not exist`() {
        // GIVEN
        val firebaseId = "user-1"
        val userId = kotlin.math.abs(firebaseId.hashCode().toLong())
        val mediaId = 100L

        mockAuthenticatedUser(firebaseId, userId)

        every { likeRepository.findByUserIdAndMediaId(userId, mediaId) } returns Optional.empty()

        // WHEN & THEN
        val ex = assertThrows<LikeNotFoundException> {
            likeService.unlikeMedia(mediaId)
        }
        assertEquals("Like not found for user $userId on media $mediaId", ex.message)

        verify(exactly = 0) { likeRepository.delete(any()) }
    }

    @Test
    fun `getLikeCount should return count and true when user has liked`() {
        // GIVEN
        val firebaseId = "user-1"
        val userId = kotlin.math.abs(firebaseId.hashCode().toLong())
        val mediaId = 100L

        mockAuthenticatedUser(firebaseId, userId)

        every { likeRepository.countByMediaId(mediaId) } returns 42L
        every { likeRepository.existsByUserIdAndMediaId(userId, mediaId) } returns true

        // WHEN
        val result = likeService.getLikeCount(mediaId)

        // THEN
        assertEquals(mediaId, result.mediaId)
        assertEquals(42L, result.likeCount)
        assertTrue(result.isLiked)
    }

    @Test
    fun `getLikeCount should return count and false when user has NOT liked`() {
        // GIVEN
        val firebaseId = "user-1"
        val userId = kotlin.math.abs(firebaseId.hashCode().toLong())
        val mediaId = 100L

        mockAuthenticatedUser(firebaseId, userId)

        every { likeRepository.countByMediaId(mediaId) } returns 10L
        every { likeRepository.existsByUserIdAndMediaId(userId, mediaId) } returns false

        // WHEN
        val result = likeService.getLikeCount(mediaId)

        // THEN
        assertEquals(10L, result.likeCount)
        assertFalse(result.isLiked)
    }

    @Test
    fun `checkIfLiked should return true when exists`() {
        // GIVEN
        val firebaseId = "user-1"
        val userId = kotlin.math.abs(firebaseId.hashCode().toLong())
        val mediaId = 100L

        mockAuthenticatedUser(firebaseId, userId)
        every { likeRepository.existsByUserIdAndMediaId(userId, mediaId) } returns true

        // WHEN
        val result = likeService.hasUserLiked(mediaId)

        // THEN
        assertTrue(result)
    }
}