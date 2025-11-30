package traversium.socialservice.db.model

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime

@Entity
@Table(
    name = "likes",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "media_id"])]
)
class Like(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val likeId: Long? = null,

    @Column(nullable = false, name = "user_id")
    val userId: Long,

    @Column(nullable = false, name = "media_id")
    val mediaId: Long,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: OffsetDateTime? = null
)

