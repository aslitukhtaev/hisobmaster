package uz.kassiron.app.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import uz.kassiron.app.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The server's JSON API (app/Controllers/ApiController.php), the way the
 * desktop client calls it (DesktopSync::requestRaw): POST JSON, answer JSON;
 * errors are an HTTP status plus {"error": "<code>"}.
 *
 * Throws [SyncException]: the server's code (login_failed, device_revoked,
 * shop_blocked, ...), "network" when the server can't be reached at all, or
 * "server_error" for an answer that isn't the JSON it should be.
 */
internal class Api(private val server: String = BuildConfig.SERVER_URL.trimEnd('/')) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    /** [auth]: "Device <uuid>:<secret>", or null for activation. */
    fun post(path: String, body: JSONObject, auth: String?): JSONObject {
        val request = Request.Builder()
            .url(server + path)
            .post(body.toString().toRequestBody(json))
            .header("Accept", "application/json")
            .header("X-App-Version", BuildConfig.VERSION_NAME)
            .apply {
                if (auth != null) {
                    // Some Apache/CGI setups drop Authorization; the server reads X-Device-Auth first.
                    header("X-Device-Auth", auth)
                    header("Authorization", auth)
                }
            }
            .build()

        val (status, text) = try {
            client.newCall(request).execute().use { it.code to (it.body?.string() ?: "") }
        } catch (e: IOException) {
            throw SyncException("network", e.message)
        }

        val data = try {
            JSONObject(text)
        } catch (e: JSONException) {
            null
        }

        if (status != 200) {
            val code = data?.optString("error")?.takeIf { it.isNotBlank() } ?: "server_error"
            throw SyncException(code, "HTTP $status")
        }
        return data ?: throw SyncException("server_error", "not JSON")
    }
}
