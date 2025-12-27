package traversium.socialservice.security

import io.grpc.*
import org.springframework.stereotype.Component

@Component
class GrpcAuthClientInterceptor(
    private val tokenClient: KeycloakTokenClient
) : ClientInterceptor {

    companion object {
        private val AUTHORIZATION_KEY: Metadata.Key<String> =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {

        val call = next.newCall(method, callOptions)

        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(call) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                val token = tokenClient.getToken()
                headers.put(AUTHORIZATION_KEY, "Bearer $token")
                super.start(responseListener, headers)
            }
        }
    }
}