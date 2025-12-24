package traversium.socialservice.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import traversium.moderation.textmoderation.TextModerationServiceGrpc

@EnableConfigurationProperties(GrpcProperties::class)
@Configuration
class GrpcClientConfig {
    @Bean
    fun moderationChannel(grpcClientProperties: GrpcProperties): ManagedChannel =
        ManagedChannelBuilder
            .forAddress(grpcClientProperties.host, grpcClientProperties.port)
            .usePlaintext()
            .build()

    @Bean
    fun moderationStub(channel: ManagedChannel): TextModerationServiceGrpc.TextModerationServiceBlockingStub =
        TextModerationServiceGrpc.newBlockingStub(channel)

}