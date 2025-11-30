package traversium.socialservice.service

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import traversium.audit.kafka.ActivityType
import traversium.audit.kafka.AuditStreamData
import traversium.audit.kafka.EntityType
import traversium.notification.kafka.NotificationStreamData
import traversium.socialservice.db.repository.LikeRepository
import traversium.socialservice.dto.LikeCountDto
import traversium.socialservice.dto.LikeDto
import traversium.socialservice.exceptions.DuplicateLikeException
import traversium.socialservice.exceptions.LikeNotFoundException
import traversium.socialservice.mapper.LikeMapper
import java.time.OffsetDateTime

@Service
class LikeService(
    private val likeRepository: LikeRepository,
    private val likeMapper: LikeMapper,
    private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun likeMedia(mediaId: Long, userId: Long, userFirebaseId: String): LikeDto {
        // Check if user has already liked this media
        if (likeRepository.existsByUserIdAndMediaId(userId, mediaId)) {
            throw DuplicateLikeException("User has already liked this media")
        }

        val newLike = likeMapper.toEntity(userId, mediaId)
        val savedLike = likeRepository.save(newLike)

        // Publish notification event (notify media owner)
        publishLikeNotification(userId.toString(), mediaId)

        // Publish audit event
        publishLikeAuditEvent(userFirebaseId, "LIKE_CREATED", savedLike.likeId, mediaId)

        return likeMapper.toDto(savedLike)
    }

    @Transactional
    fun unlikeMedia(mediaId: Long, userId: Long, userFirebaseId: String) {
        val like = likeRepository.findByUserIdAndMediaId(userId, mediaId)
            .orElseThrow { 
                LikeNotFoundException("Like not found for user $userId on media $mediaId")
            }

        val likeId = like.likeId
        likeRepository.delete(like)

        // Publish audit event
        publishLikeAuditEvent(userFirebaseId, "LIKE_DELETED", likeId, mediaId)
    }

    private fun publishLikeNotification(userId: String, mediaId: Long) {
        // TODO: Get media owner from TripService to send proper notification
        val notification = NotificationStreamData(
            timestamp = OffsetDateTime.now(),
            senderId = userId,
            receiverIds = emptyList(), // TODO: Get actual receiver IDs (media owner)
            collectionReferenceId = null,
            nodeReferenceId = mediaId,
            commentReferenceId = null,
            action = "LIKE_CREATED"
        )
        eventPublisher.publishEvent(notification)
    }

    private fun publishLikeAuditEvent(userId: String, action: String, likeId: Long?, mediaId: Long) {
        val auditEvent = AuditStreamData(
            timestamp = OffsetDateTime.now(),
            userId = userId,
            activityType = ActivityType.SOCIAL_ACTIVITY,
            action = action,
            entityType = EntityType.LIKE,
            entityId = likeId,
            tripId = null, // TODO: Extract tripId from mediaId if available
            metadata = mapOf(
                "likeId" to (likeId ?: ""),
                "mediaId" to mediaId,
                "entityType" to "LIKE"
            )
        )
        eventPublisher.publishEvent(auditEvent)
    }

    fun getLikeCount(mediaId: Long, currentUserId: Long?): LikeCountDto {
        val likeCount = likeRepository.countByMediaId(mediaId)
        val isLiked = currentUserId?.let { 
            likeRepository.existsByUserIdAndMediaId(it, mediaId) 
        } ?: false

        return LikeCountDto(
            mediaId = mediaId,
            likeCount = likeCount,
            isLiked = isLiked
        )
    }

    fun hasUserLiked(mediaId: Long, userId: Long): Boolean {
        return likeRepository.existsByUserIdAndMediaId(userId, mediaId)
    }
}

