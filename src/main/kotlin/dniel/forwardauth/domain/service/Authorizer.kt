package dniel.forwardauth.domain.service

import dniel.forwardauth.AuthProperties.Application
import dniel.forwardauth.domain.*
import org.slf4j.LoggerFactory

class Authorizer(val accessToken: Token, val idToken: Token, val app: Application, val nonce: Nonce,
                 val originUrl: OriginUrl, val state: State, val authUrl: AuthorizeUrl, val authDomain: String) : AuthorizerStateMachine.Delegate {

    private var fsm: AuthorizerStateMachine
    private var lastError: Error? = null

    companion object Factory {
        val LOGGER = LoggerFactory.getLogger(this::class.java)

        fun create(accessToken: Token, idToken: Token, app: Application, nonce: Nonce,
                   originUrl: OriginUrl, state: State, authUrl: AuthorizeUrl, authDomain: String):
                Authorizer = Authorizer(accessToken, idToken, app, nonce, originUrl, state, authUrl, authDomain)
    }

    init {
        fsm = AuthorizerStateMachine(this)
    }

    data class Error(val message: String)

    override val hasError: Boolean
        get() = lastError != null

    override fun onStartAuthorizing() {
        trace("onStartAuthorizing")
        fsm.post(AuthorizerStateMachine.Event.VALIDATE_REQUESTED_URL)
    }

    override fun onValidateProtectedUrl() {
        trace("onValidateProtectedUrl")
        fsm.post(AuthorizerStateMachine.Event.VALIDATE_WHITELISTED_URL)
    }

    override fun onValidateWhitelistedUrl() {
        trace("onValidateWhitelistedUrl")
        fun isSigninUrl(originUrl: OriginUrl, app: Application) =
                originUrl.startsWith(app.redirectUri)

        if (isSigninUrl(originUrl, app)) {
            fsm.post(AuthorizerStateMachine.Event.WHITELISTED_URL)
        } else {
            fsm.post(AuthorizerStateMachine.Event.RESTRICTED_URL)
        }
    }

    override fun onValidateRestrictedMethod() {
        trace("onValidateRestrictedMethod")
        val method = originUrl.method
        fun isRestrictedMethod(app: Application, method: String) =
                app.restrictedMethods.any() { t -> t.equals(method, true) }

        when {
            isRestrictedMethod(app, method) -> fsm.post(AuthorizerStateMachine.Event.RESTRICTED_METHOD)
            else -> fsm.post(AuthorizerStateMachine.Event.UNRESTRICTED_METHOD)
        }
    }

    override fun onStartValidateTokens() {
        trace("onStartValidateTokens")
        fsm.post(AuthorizerStateMachine.Event.VALIDATE_ACCESS_TOKEN)
    }

    override fun onValidateAccessToken() {
        trace("onValidateAccessToken")
        when {
            accessToken is OpaqueToken -> {
                lastError = Error("Opaque Access Tokens is not supported.")
                fsm.post(AuthorizerStateMachine.Event.ERROR)
            }
            accessToken is JwtToken -> fsm.post(AuthorizerStateMachine.Event.VALID_ACCESS_TOKEN)
            else -> fsm.post(AuthorizerStateMachine.Event.INVALID_ACCESS_TOKEN)
        }
    }

    override fun onValidateIdToken() {
        trace("onValidateIdToken")
        when {
            idToken is JwtToken -> fsm.post(AuthorizerStateMachine.Event.VALID_ID_TOKEN)
            else -> fsm.post(AuthorizerStateMachine.Event.INVALID_ID_TOKEN)
        }
    }

    override fun onValidatePermissions() {
        trace("onValidatePermissions")
        when {
            (accessToken as JwtToken).hasPermission(app.requiredPermissions) -> fsm.post(AuthorizerStateMachine.Event.VALID_PERMISSIONS)
            else -> fsm.post(AuthorizerStateMachine.Event.INVALID_PERMISSIONS)
        }
    }

    override fun onValidateSameSubs() {
        trace("onValidateSameSubs")
        fun hasSameSubs(accessToken: Token, idToken: Token) =
                accessToken is JwtToken && idToken is JwtToken && idToken.subject()  == accessToken.subject()

        // check if both tokens have the same subject
        if (hasSameSubs(accessToken, idToken)) {
            fsm.post(AuthorizerStateMachine.Event.VALID_SAME_SUBS)
        } else {
            fsm.post(AuthorizerStateMachine.Event.INVALID_SAME_SUBS)
        }
    }

    override fun onNeedRedirect() {
        trace("onNeedRedirect")
    }

    override fun onInvalidToken() {
        trace("onInvalidToken")
    }

    override fun onError() {
        trace("onError")
        trace(lastError!!.message)
    }

    override fun onAccessGranted() {
        trace("onAccessGranted")
    }

    override fun onAccessDenied() {
        trace("onAccessDenied")
    }

    /*
     */
    fun authorize(): AuthorizerStateMachine.State {
        return fsm.authorize()
    }

    fun trace(message: String) {
        LOGGER.debug(message)
    }

    fun state(): AuthorizerStateMachine.State {
        return fsm.state
    }
}

/*
fun main(args: Array<String>) {
    val app = Application()
    val aToken = InvalidToken("just for testing")
    val idToken = InvalidToken("just for testing")
    val nonce = Nonce.generate()
    val originUrl = OriginUrl("https", "www.exampple.com", "/", "GET")
    val state = State.create(originUrl, nonce)
    val authUrl = AuthorizeUrl("auth0autorhizeurl", app, state)
    val authDomain = "authdomain"

    val authorizer = Authorizer.create(aToken, idToken, app, nonce, originUrl, state, authUrl, authDomain)
    val output = authorizer.authorize()
    println(output)
}
 */