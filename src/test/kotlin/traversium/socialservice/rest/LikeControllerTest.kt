package traversium.socialservice.rest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import traversium.socialservice.config.FirebaseFilterConfig
import traversium.socialservice.dto.LikeCountDto
import traversium.socialservice.dto.LikeDto
import traversium.socialservice.exceptions.DuplicateLikeException
import traversium.socialservice.exceptions.LikeNotFoundException
import traversium.socialservice.security.FirebaseAuthenticationFilter
import traversium.socialservice.service.LikeService
import java.time.OffsetDateTime

@WebMvcTest(
    controllers = [LikeController::class],
    excludeFilters = [ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = [FirebaseFilterConfig::class, FirebaseAuthenticationFilter::class]
    )]
)
@AutoConfigureMockMvc(addFilters = false) // Disable Spring Security filters
class LikeControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var likeService: LikeService

    @Test
    fun `likeMedia should return 201 Created on success`() {
        val mediaId = 100L
        val likeDto = LikeDto(
            likeId = 1L,
            userId = 10L,
            mediaId = mediaId,
            createdAt = OffsetDateTime.now()
        )

        every { likeService.likeMedia(mediaId) } returns likeDto

        mockMvc.post("/rest/v1/media/$mediaId/likes") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isCreated() }
            jsonPath("$.likeId") { value(1L) }
            jsonPath("$.mediaId") { value(100L) }
        }

        verify(exactly = 1) { likeService.likeMedia(mediaId) }
    }

    @Test
    fun `likeMedia should return 409 Conflict if already liked`() {
        val mediaId = 100L

        every { likeService.likeMedia(mediaId) } throws DuplicateLikeException("Already liked")

        mockMvc.post("/rest/v1/media/$mediaId/likes")
            .andExpect {
                status { isConflict() }
            }
    }

    @Test
    fun `unlikeMedia should return 204 No Content`() {
        val mediaId = 100L

        every { likeService.unlikeMedia(mediaId) } returns Unit

        mockMvc.delete("/rest/v1/media/$mediaId/likes")
            .andExpect {
                status { isNoContent() }
            }

        verify(exactly = 1) { likeService.unlikeMedia(mediaId) }
    }

    @Test
    fun `unlikeMedia should return 404 Not Found if like does not exist`() {
        val mediaId = 100L

        every { likeService.unlikeMedia(mediaId) } throws LikeNotFoundException("Like not found")

        mockMvc.delete("/rest/v1/media/$mediaId/likes")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `getLikeCount should return 200 OK with count`() {
        val mediaId = 100L
        val countDto = LikeCountDto(
            mediaId = mediaId,
            likeCount = 42,
            isLiked = true
        )

        every { likeService.getLikeCount(mediaId) } returns countDto

        mockMvc.get("/rest/v1/media/$mediaId/likes")
            .andExpect {
                status { isOk() }
                jsonPath("$.likeCount") { value(42) }
                jsonPath("$.isLiked") { value(true) }
            }
    }

    @Test
    fun `checkIfLiked should return 200 OK with boolean`() {
        val mediaId = 100L

        every { likeService.hasUserLiked(mediaId) } returns true

        mockMvc.get("/rest/v1/media/$mediaId/likes/check")
            .andExpect {
                status { isOk() }
                content { string("true") }
            }
    }
}