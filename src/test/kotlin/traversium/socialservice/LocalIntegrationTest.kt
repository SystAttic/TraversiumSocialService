package traversium.socialservice

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import traversium.socialservice.client.TripServiceClient
import traversium.socialservice.db.repository.CommentRepository
import traversium.socialservice.dto.CreateCommentDto
import traversium.socialservice.security.TraversiumAuthentication
import traversium.socialservice.security.TraversiumPrincipal
import traversium.socialservice.service.CommentService

// @SpringBootTest loads the REAL application context.
// It will try to connect to Real Postgres, Real Kafka, and Real Moderation Service.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LocalIntegrationTest {

    // 1. Mock ONLY the Trip Service
    // This replaces the real TripServiceClient bean with a MockK version.
    @MockkBean
    lateinit var tripServiceClient: TripServiceClient

    // 2. Autowire the REAL Service
    // This service will talk to the REAL Database and REAL Kafka
    @Autowired
    lateinit var commentService: CommentService

    @Autowired
    lateinit var commentRepository: CommentRepository

    // Setup User Authentication Mocking (Same as Unit Test)
    @BeforeEach
    fun setUp() {
        mockkStatic(SecurityContextHolder::class)
        mockAuthenticatedUser("test-firebase-uid", 12345L)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(SecurityContextHolder::class)
        // Optional: Clean up the REAL database after test so you can run it again
        commentRepository.deleteAll()
    }

    @Test
    fun `createComment should write to REAL DB and check REAL Moderation Service`() {
        // GIVEN
        val mediaId = 100L
        val inputDto = CreateCommentDto(content = "This is a real integration test!")

        // Define behavior for the MOCKED Trip Service
        every { tripServiceClient.doesMediaExist(mediaId) } returns true
        every { tripServiceClient.getMediaOwner(mediaId) } returns "some-owner-uid"

        // WHEN
        // This will:
        // 1. Call TripService (Mocked -> Returns True)
        // 2. Call Moderation Service (Real -> gRPC to localhost:9090)
        // 3. Save to Postgres (Real -> localhost:5432)
        // 4. Send Audit/Notification to Kafka (Real -> localhost:9092)
        val result = commentService.createComment(mediaId, inputDto)

        // THEN
        assertNotNull(result.commentId)
        println("Created Comment ID: ${result.commentId}")

        // Verify it is actually in the database
        val savedComment = commentRepository.findById(result.commentId!!)
        assert(savedComment.isPresent)
        println("Comment found in Real DB: ${savedComment.get().content}")
    }

    // --- Helper for Mocking Login ---
    private fun mockAuthenticatedUser(uid: String, userId: Long) {
        val principal = TraversiumPrincipal(uid, "test@integration.com", null)
        val auth = TraversiumAuthentication(principal, null, emptyList(), "mock-token")
        val securityContext = mockk<SecurityContext>()
        every { securityContext.authentication } returns auth
        every { SecurityContextHolder.getContext() } returns securityContext
    }
}