package traversium.socialservice

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@EmbeddedKafka(partitions = 1, brokerProperties = ["listeners=PLAINTEXT://localhost:9092", "port=9092"])
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "trip-service.url=http://trip-service.url:8080",
])
class SocialServiceApplicationTests {

    @MockkBean(relaxed = true)
    lateinit var firebaseApp: FirebaseApp

    @MockkBean(relaxed = true)
    lateinit var firebaseAuth: FirebaseAuth

    @MockkBean
    lateinit var commentRepository: traversium.socialservice.db.repository.CommentRepository

    @MockkBean
    lateinit var likeRepository: traversium.socialservice.db.repository.LikeRepository

    @Test
    fun contextLoads() {
    }
}