package com.htmlpublisher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class PublishException(message: String) : Exception(message)

class ApiResult(
    val status: Int,
    val body: String,
    val rateRemaining: String?,
    val scopes: String?
) {
    val ok: Boolean get() = status in 200..299

    val json: JSONObject? by lazy {
        try { JSONObject(body) } catch (e: Exception) { null }
    }

    val message: String
        get() {
            val j = json ?: return ""
            var m = j.optString("message")
            val errs = j.optJSONArray("errors")
            if (errs != null && errs.length() > 0) {
                val e0 = errs.opt(0)
                m += " " + if (e0 is JSONObject) e0.optString("message").ifEmpty { e0.optString("code") } else e0.toString()
            }
            return m.trim()
        }
}

class GitHubApi(private val token: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    suspend fun call(method: String, path: String, body: JSONObject? = null): ApiResult =
        withContext(Dispatchers.IO) {
            try {
                val reqBody = when {
                    body != null -> body.toString().toRequestBody(jsonType)
                    method == "GET" -> null
                    else -> "".toRequestBody(jsonType)
                }
                val req = Request.Builder()
                    .url("https://api.github.com$path")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Authorization", "Bearer $token")
                    .method(method, reqBody)
                    .build()
                client.newCall(req).execute().use { r ->
                    ApiResult(
                        r.code,
                        r.body?.string().orEmpty(),
                        r.header("x-ratelimit-remaining"),
                        r.header("x-oauth-scopes")
                    )
                }
            } catch (e: IOException) {
                throw PublishException("تعذّر الاتصال بـ GitHub. تحقق من اتصال الإنترنت وحاول مجدداً.")
            } catch (e: IllegalArgumentException) {
                throw PublishException("الرمز يحتوي على أحرف غير صالحة.")
            }
        }
}

fun apiError(r: ApiResult, what: String): PublishException {
    val m = r.message
    val out = when {
        r.status == 401 -> "الرمز غير صالح أو منتهي الصلاحية. أنشئ رمزاً جديداً."
        r.status == 403 ->
            if (r.rateRemaining == "0") "تجاوزت حد الطلبات المسموح به من GitHub. انتظر قليلاً ثم أعد المحاولة."
            else "الرمز لا يملك الصلاحية المطلوبة. تأكد من تفعيل صلاحية public_repo. $m"
        r.status == 404 -> "غير موجود أو لا تملك صلاحية الوصول إليه. $m"
        r.status == 422 && m.contains("already exists", ignoreCase = true) -> "اسم المستودع مستخدم مسبقاً في حسابك."
        else -> "خطأ ${r.status}" + if (m.isNotEmpty()) ": $m" else ""
    }
    return PublishException(if (what.isEmpty()) out else "$what — $out")
}
