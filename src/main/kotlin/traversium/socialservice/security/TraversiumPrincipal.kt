package traversium.socialservice.security

import java.security.Principal

data class TraversiumPrincipal(
    val uid: String,
    val email: String?,
    val photoUrl: String?
) : Principal {

    override fun getName(): String = email ?: uid
}

