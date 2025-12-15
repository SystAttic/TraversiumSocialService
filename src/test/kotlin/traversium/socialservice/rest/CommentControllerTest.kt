package traversium.socialservice.rest

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import traversium.socialservice.config.FirebaseFilterConfig
import traversium.socialservice.dto.CommentDto
import traversium.socialservice.dto.CreateCommentDto
import traversium.socialservice.dto.UpdateCommentDto
import traversium.socialservice.exceptions.CommentNotFoundException
import traversium.socialservice.exceptions.UnauthorizedCommentAccessException
import traversium.socialservice.security.FirebaseAuthenticationFilter
import traversium.socialservice.service.CommentService
import java.time.OffsetDateTime

@WebMvcTest(
    controllers = [CommentController::class],
    excludeFilters = [ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = [FirebaseFilterConfig::class, FirebaseAuthenticationFilter::class]
    )]
)
@AutoConfigureMockMvc(addFilters = false) // Bypass Spring Security for these tests
class CommentControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var commentService: CommentService

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `createComment should return 201 Created`() {
        val mediaId = 123L
        val dto = CreateCommentDto("Nice!")
        val resultDto = CommentDto(1L, "Nice!", 99L, "uid", mediaId, null, OffsetDateTime.now())

        every { commentService.createComment(mediaId, dto) } returns resultDto

        mockMvc.post("/rest/v1/media/$mediaId/comments") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(dto)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.commentId") { value(1L) }
            jsonPath("$.content") { value("Nice!") }
        }
    }

    @Test
    fun `createComment should return 404 if parent not found`() {
        val mediaId = 123L
        val dto = CreateCommentDto("Reply", parentId = 999L)

        // Mock the service throwing the specific exception
        every { commentService.createComment(mediaId, dto) } throws CommentNotFoundException("Parent missing")

        mockMvc.post("/rest/v1/media/$mediaId/comments") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(dto)
        }.andExpect {
            status { isNotFound() } // Verifies the catch block in controller
        }
    }

    @Test
    fun `updateComment should return 403 Forbidden when unauthorized`() {
        val commentId = 5L
        val dto = UpdateCommentDto("Hacked content")

        every { commentService.updateComment(commentId, dto) } throws UnauthorizedCommentAccessException("Not yours")

        mockMvc.put("/rest/v1/comments/$commentId") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(dto)
        }.andExpect {
            status { isForbidden() } // Verifies the catch block in controller
        }
    }

    @Test
    fun `deleteComment should return 204 No Content`() {
        val commentId = 5L

        // Service returns Unit (void)
        every { commentService.deleteComment(commentId) } returns Unit

        mockMvc.delete("/rest/v1/comments/$commentId")
            .andExpect {
                status { isNoContent() }
            }

        verify(exactly = 1) { commentService.deleteComment(commentId) }
    }

    @Test
    fun `getComments should return 200 and Page`() {
        val mediaId = 100L
        val commentDto = CommentDto(1L, "Content", 1L, "uid", mediaId, null)
        val page = PageImpl(listOf(commentDto))

        every { commentService.getCommentsForAlbum(mediaId, any<Pageable>()) } returns page

        mockMvc.get("/rest/v1/media/$mediaId/comments")
            .andExpect {
                status { isOk() }
                jsonPath("$.content[0].content") { value("Content") }
                jsonPath("$.totalElements") { value(1) }
            }
    }
}