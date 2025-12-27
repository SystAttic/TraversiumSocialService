package traversium.socialservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.cloud.context.config.annotation.RefreshScope

@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "security.oauth2.client")
data class OAuthClientProperties(
    var tokenUri: String = "",
    var clientId: String = "",
    var clientSecret: String = "",
    var grantType: String = "",
    var refreshSkewSeconds: Long = 30
)