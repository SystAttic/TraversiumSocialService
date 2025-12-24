package traversium.socialservice.grpc

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "grpc.client.moderation-service")
class GrpcProperties(
    val host: String = "localhost",
    val port: Int = 9090
)