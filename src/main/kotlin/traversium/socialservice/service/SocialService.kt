package traversium.socialservice.service

import org.springframework.security.core.context.SecurityContextHolder
import traversium.socialservice.security.TraversiumAuthentication
import traversium.socialservice.security.TraversiumPrincipal

/**
 * @author Maja Razinger
 */
abstract class SocialService {
    protected fun getCurrentUserId(): Long {
        val authentication = SecurityContextHolder.getContext().authentication as? TraversiumAuthentication
            ?: throw IllegalStateException("Authentication not found")

        val principal = authentication.principal as? TraversiumPrincipal
            ?: throw IllegalStateException("Principal not found")

        return kotlin.math.abs(principal.uid.hashCode().toLong())
    }

    protected fun getCurrentUserFirebaseId(): String {
        val authentication = SecurityContextHolder.getContext().authentication as? TraversiumAuthentication
            ?: throw IllegalStateException("Authentication not found")

        val principal = authentication.principal as? TraversiumPrincipal
            ?: throw IllegalStateException("Principal not found")

        return principal.uid
    }

    protected fun getAuthorizationHeader(): String? {
        val authentication = SecurityContextHolder.getContext().authentication as? TraversiumAuthentication
            ?: return null

        return authentication.token?.let { "Bearer $it" }
    }
}