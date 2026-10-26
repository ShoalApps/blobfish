package io.github.shoalapps.blobfish.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Signing in with Bluesky's own sign-in page (AT Protocol OAuth): your password and two-factor stay
 * with Bluesky, and the app gets tokens bound to a key that never leaves this phone (DPoP).
 * The app's public description lives at [CLIENT_ID]; see atproto.com/specs/oauth.
 */
object BskyOAuth {
    const val CLIENT_ID = "https://shoalapps.github.io/oauth/blobfish.json"
    /** Native apps return on a scheme made from the client ID's host, reversed. */
    const val REDIRECT = "io.github.shoalapps:/oauth/blobfish"
    private const val SCOPE = "atproto transition:generic"
    private const val KEY_ALIAS = "blobfish-dpop"

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    /** A sign-in that's waiting for Bluesky's page to send you back. */
    private data class Pending(val state: String, val verifier: String, val issuer: String, val tokenUrl: String, val did: String?, val handle: String?, val pds: String?)

    // ---- The DPoP key, kept in Android's key store ----

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): Pair<PrivateKey, ECPublicKey> {
        val ks = keyStore()
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKeyPair()
        }
        val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey to (entry.certificate.publicKey as ECPublicKey)
    }

    /** Forgets the key (on sign-out), so old tokens can never be used again. */
    fun forgetKey() { runCatching { keyStore().deleteEntry(KEY_ALIAS) } }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun unsigned32(n: BigInteger): ByteArray { val b = n.toByteArray(); return when { b.size == 32 -> b; b.size > 32 -> b.copyOfRange(b.size - 32, b.size); else -> ByteArray(32 - b.size) + b } }
    private fun randomString(bytes: Int = 32) = b64(ByteArray(bytes).also(random::nextBytes))
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())

    /** The DER signature Android gives, as the 64 raw bytes a JWT wants. */
    private fun derToRaw(der: ByteArray): ByteArray {
        var i = 2
        if (der[1].toInt() and 0x80 != 0) i += der[1].toInt() and 0x7f
        fun int(): BigInteger { i++; val len = der[i++].toInt(); val v = BigInteger(1, der.copyOfRange(i, i + len)); i += len; return v }
        return unsigned32(int()) + unsigned32(int())
    }

    /** A DPoP proof for one request. */
    fun proof(method: String, url: String, nonce: String?, accessToken: String? = null): String {
        val (priv, pub) = key()
        val jwk = buildJsonObject { put("kty", "EC"); put("crv", "P-256"); put("x", b64(unsigned32(pub.w.affineX))); put("y", b64(unsigned32(pub.w.affineY))) }
        val header = buildJsonObject { put("typ", "dpop+jwt"); put("alg", "ES256"); put("jwk", jwk) }
        val payload = buildJsonObject {
            put("jti", randomString(16)); put("htm", method); put("htu", url.substringBefore('?').substringBefore('#'))
            put("iat", System.currentTimeMillis() / 1000)
            nonce?.let { put("nonce", it) }
            accessToken?.let { put("ath", b64(sha256(it))) }
        }
        val signingInput = b64(header.toString().toByteArray()) + "." + b64(payload.toString().toByteArray())
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(priv); update(signingInput.toByteArray()); sign() }
        return signingInput + "." + b64(derToRaw(sig))
    }

    // ---- Nonces: each server hands one out and wants it back ----

    private val nonces = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun origin(url: String) = Uri.parse(url).let { "${it.scheme}://${it.authority}" }
    fun nonceFor(url: String) = nonces[origin(url)]
    fun remember(url: String, r: Response) { r.header("DPoP-Nonce")?.let { nonces[origin(url)] = it } }

    /** POSTs a form with a DPoP proof, retrying once when the server asks for a fresh nonce. */
    private fun postForm(http: OkHttpClient, url: String, form: Map<String, String>): JsonObject {
        repeat(2) { attempt ->
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            val req = Request.Builder().url(url).post(body).header("DPoP", proof("POST", url, nonceFor(url))).build()
            http.newCall(req).execute().use { r ->
                remember(url, r)
                val o = runCatching { json.parseToJsonElement(r.body!!.string()) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
                if (r.isSuccessful) return o
                if (o.s("error") == "use_dpop_nonce" && attempt == 0) return@repeat
                throw AtpException(r.code, o.s("error_description") ?: o.s("error") ?: "Bluesky said ${r.code}")
            }
        }
        throw AtpException(400, "Bluesky didn't accept the sign-in")
    }

    private fun getJson(http: OkHttpClient, url: String): JsonObject =
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw AtpException(r.code, "Couldn't reach $url")
            json.parseToJsonElement(r.body!!.string()) as JsonObject
        }

    private fun JsonObject?.s(k: String) = (this?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content

    // ---- Finding your account's server ----

    private fun didFor(http: OkHttpClient, handle: String): String =
        if (handle.startsWith("did:")) handle
        else getJson(http, "https://public.api.bsky.app/xrpc/com.atproto.identity.resolveHandle?handle=" + Uri.encode(handle)).s("did") ?: throw AtpException(404, "Couldn't find @$handle")

    /** Your PDS and handle, from your DID document. */
    private fun didDoc(http: OkHttpClient, did: String): Pair<String, String?> {
        val doc = if (did.startsWith("did:web:")) getJson(http, "https://${did.removePrefix("did:web:")}/.well-known/did.json") else getJson(http, "https://plc.directory/$did")
        val pds = (doc["service"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { it.s("id")?.endsWith("atproto_pds") == true }.s("serviceEndpoint")
            ?: throw AtpException(404, "Your account has no server listed")
        val handle = (doc["alsoKnownAs"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.firstOrNull { it.startsWith("at://") }?.removePrefix("at://")
        return pds.trimEnd('/') to handle
    }

    private fun authServerFor(http: OkHttpClient, pds: String): String =
        (getJson(http, "$pds/.well-known/oauth-protected-resource")["authorization_servers"] as? JsonArray)?.firstOrNull()?.let { (it as JsonPrimitive).content }
            ?: throw AtpException(404, "Your server doesn't support signing in this way")

    // ---- The flow ----

    /**
     * Starts signing in and returns the address of Bluesky's sign-in page to open.
     * With a handle, it signs in on that account's own server; without one, on Bluesky's.
     */
    suspend fun start(context: Context, http: OkHttpClient, handle: String?): String = withContext(Dispatchers.IO) {
        val h = handle?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
        var did: String? = null; var pds: String? = null; var realHandle: String? = null
        val issuer = if (h != null) {
            did = didFor(http, h)
            val (p, hh) = didDoc(http, did); pds = p; realHandle = hh
            authServerFor(http, p)
        } else "https://bsky.social"
        val meta = getJson(http, "$issuer/.well-known/oauth-authorization-server")
        val par = meta.s("pushed_authorization_request_endpoint") ?: throw AtpException(500, "Bluesky's sign-in isn't available")
        val authorize = meta.s("authorization_endpoint")!!
        val token = meta.s("token_endpoint")!!
        val verifier = randomString(48)
        val state = randomString(16)
        val form = buildMap {
            put("client_id", CLIENT_ID); put("response_type", "code"); put("redirect_uri", REDIRECT); put("scope", SCOPE); put("state", state)
            put("code_challenge", b64(sha256(verifier))); put("code_challenge_method", "S256")
            h?.let { put("login_hint", it) }
        }
        val requestUri = postForm(http, par, form).s("request_uri") ?: throw AtpException(500, "Bluesky didn't start the sign-in")
        save(context, Pending(state, verifier, meta.s("issuer") ?: issuer, token, did, realHandle, pds))
        "$authorize?client_id=${Uri.encode(CLIENT_ID)}&request_uri=${Uri.encode(requestUri)}"
    }

    /** Finishes signing in when Bluesky's page sends you back to the app. */
    suspend fun finish(context: Context, http: OkHttpClient, redirect: Uri): Session = withContext(Dispatchers.IO) {
        val p = load(context) ?: throw AtpException(400, "That sign-in has expired. Please try again.")
        redirect.getQueryParameter("error")?.let { err ->
            clear(context)
            throw AtpException(400, if (err == "access_denied") "Sign-in cancelled" else redirect.getQueryParameter("error_description") ?: "Sign-in didn't finish")
        }
        if (redirect.getQueryParameter("state") != p.state) throw AtpException(400, "That sign-in didn't match. Please try again.")
        redirect.getQueryParameter("iss")?.let { if (it != p.issuer) throw AtpException(400, "Unexpected sign-in server") }
        val code = redirect.getQueryParameter("code") ?: throw AtpException(400, "Bluesky didn't finish the sign-in")
        val t = postForm(http, p.tokenUrl, mapOf("grant_type" to "authorization_code", "code" to code, "redirect_uri" to REDIRECT, "client_id" to CLIENT_ID, "code_verifier" to p.verifier))
        val sub = t.s("sub") ?: throw AtpException(500, "Bluesky didn't say who signed in")
        if (p.did != null && sub != p.did) throw AtpException(400, "A different account signed in than the one you asked for")
        // Check the account really lives on the server that signed it in.
        val (pds, handle) = if (p.pds != null && p.did == sub) p.pds to p.handle else didDoc(http, sub)
        if (p.did == null && authServerFor(http, pds).trimEnd('/') != p.issuer.trimEnd('/')) throw AtpException(400, "That account's server didn't sign you in")
        clear(context)
        Session(sub, handle ?: sub, t.s("access_token")!!, t.s("refresh_token") ?: "", pds, oauth = true, tokenUrl = p.tokenUrl)
    }

    /** New tokens from the refresh token. */
    fun refresh(http: OkHttpClient, s: Session): Session {
        val t = postForm(http, s.tokenUrl!!, mapOf("grant_type" to "refresh_token", "refresh_token" to s.refresh, "client_id" to CLIENT_ID))
        return s.copy(access = t.s("access_token")!!, refresh = t.s("refresh_token") ?: s.refresh)
    }

    // ---- Remembering the pending sign-in across the browser trip ----

    private fun prefs(c: Context) = c.getSharedPreferences("oauth", Context.MODE_PRIVATE)
    private fun save(c: Context, p: Pending) = prefs(c).edit()
        .putString("state", p.state).putString("verifier", p.verifier).putString("issuer", p.issuer).putString("token", p.tokenUrl)
        .putString("did", p.did).putString("handle", p.handle).putString("pds", p.pds).putLong("at", System.currentTimeMillis()).apply()
    private fun load(c: Context): Pending? {
        val pr = prefs(c)
        if (System.currentTimeMillis() - pr.getLong("at", 0) > 15 * 60_000) return null
        return Pending(pr.getString("state", null) ?: return null, pr.getString("verifier", null) ?: return null, pr.getString("issuer", null)!!, pr.getString("token", null)!!,
            pr.getString("did", null), pr.getString("handle", null), pr.getString("pds", null))
    }
    private fun clear(c: Context) = prefs(c).edit().clear().apply()
}
