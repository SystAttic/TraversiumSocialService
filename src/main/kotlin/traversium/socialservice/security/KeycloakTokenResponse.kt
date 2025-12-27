package traversium.socialservice.security

import com.fasterxml.jackson.annotation.JsonProperty

data class KeycloakTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,

    @JsonProperty("expires_in")
    val expiresIn: Long,

    @JsonProperty("token_type")
    val tokenType: String,

    @JsonProperty("scope")
    val scope: String? = null
)