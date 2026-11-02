package com.htmlpublisher

import android.util.Base64
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

enum class Status { Pending, Run, Done, Error }

class StepState(val title: String, val status: Status, val detail: String = "")
class SourceFile(val path: String, val bytes: ByteArray)
class RepoInfo(val exists: Boolean, val isPrivate: Boolean)
class PublishResult(val url: String, val built: Boolean)

object Names {
    private val valid = Regex("^[A-Za-z0-9._-]{1,100}$")

    fun isValid(n: String): Boolean =
        valid.matches(n) && n != "." && n != ".." && !n.startsWith(".") && !n.endsWith(".git")

    fun slug(fileName: String): String {
        var s = fileName.substringBeforeLast('.', fileName).lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
        if (s.isEmpty() || s == "index") s = "site-" + (1000..9999).random()
        return s.take(60)
    }

    fun siteUrl(login: String, name: String): String {
        val l = login.lowercase()
        return if (name.lowercase() == "$l.github.io") "https://$l.github.io/" else "https://$l.github.io/$name/"
    }
}

class Publisher(private val api: GitHubApi, private val login: String) {

    private val statusAr = mapOf("queued" to "في الانتظار", "building" to "قيد البناء", "built" to "اكتمل", "errored" to "فشل")

    suspend fun checkRepo(name: String): RepoInfo {
        val r = api.call("GET", "/repos/$login/$name")
        return when (r.status) {
            200 -> RepoInfo(true, r.json?.optBoolean("private") ?: false)
            404 -> RepoInfo(false, false)
            else -> throw apiError(r, "فحص المستودع")
        }
    }

    suspend fun publish(
        name: String,
        files: List<SourceFile>,
        replaceAll: Boolean,
        onStep: (Int, Status, String) -> Unit
    ): PublishResult {
        val base = "/repos/$login/$name"

        // 1) المستودع
        onStep(0, Status.Run, "")
        val g = api.call("GET", base)
        val repo: JSONObject = when (g.status) {
            200 -> {
                onStep(0, Status.Done, "استخدام المستودع الموجود")
                g.json ?: throw PublishException("ردّ غير متوقع من GitHub.")
            }
            404 -> {
                val c = api.call(
                    "POST", "/user/repos",
                    JSONObject()
                        .put("name", name)
                        .put("description", "Published with HTML Publisher")
                        .put("private", false)
                        .put("auto_init", true)
                )
                if (!c.ok) throw apiError(c, "إنشاء المستودع")
                onStep(0, Status.Done, "تم إنشاء مستودع عام جديد")
                c.json ?: throw PublishException("ردّ غير متوقع من GitHub.")
            }
            else -> throw apiError(g, "فحص المستودع")
        }
        val branch = repo.optString("default_branch").ifEmpty { "main" }

        // 2) رفع كل الملفات في commit واحد (Git Data API)
        onStep(1, Status.Run, "تجهيز الفرع…")
        var headSha: String
        var tries = 0
        while (true) {
            val ref = api.call("GET", "$base/git/ref/heads/$branch")
            if (ref.ok) {
                headSha = ref.json!!.getJSONObject("object").getString("sha")
                break
            }
            if ((ref.status == 404 || ref.status == 409) && tries < 8) {
                tries++
                delay(1500)
                continue
            }
            throw apiError(ref, "قراءة الفرع")
        }
        val cm = api.call("GET", "$base/git/commits/$headSha")
        if (!cm.ok) throw apiError(cm, "قراءة آخر commit")
        val baseTree = cm.json!!.getJSONObject("tree").getString("sha")

        val entries = JSONArray()
        files.forEachIndexed { i, f ->
            onStep(1, Status.Run, "رفع ${i + 1} من ${files.size}: ${f.path}")
            val b = api.call(
                "POST", "$base/git/blobs",
                JSONObject()
                    .put("content", Base64.encodeToString(f.bytes, Base64.NO_WRAP))
                    .put("encoding", "base64")
            )
            if (!b.ok) throw apiError(b, "رفع ${f.path}")
            entries.put(
                JSONObject()
                    .put("path", f.path)
                    .put("mode", "100644")
                    .put("type", "blob")
                    .put("sha", b.json!!.getString("sha"))
            )
        }
        onStep(1, Status.Run, "إنشاء commit…")
        val treeBody = JSONObject().put("tree", entries)
        if (!replaceAll) treeBody.put("base_tree", baseTree)
        val t = api.call("POST", "$base/git/trees", treeBody)
        if (!t.ok) throw apiError(t, "إنشاء شجرة الملفات")
        val cmt = api.call(
            "POST", "$base/git/commits",
            JSONObject()
                .put("message", "Publish via HTML Publisher")
                .put("tree", t.json!!.getString("sha"))
                .put("parents", JSONArray().put(headSha))
        )
        if (!cmt.ok) throw apiError(cmt, "إنشاء commit")
        val newSha = cmt.json!!.getString("sha")
        val upd = api.call("PATCH", "$base/git/refs/heads/$branch", JSONObject().put("sha", newSha))
        if (!upd.ok) throw apiError(upd, "تحديث الفرع")
        onStep(1, Status.Done, if (files.size == 1) "تم رفع index.html" else "تم رفع ${files.size} ملفات في commit واحد")

        // 3) GitHub Pages
        onStep(2, Status.Run, "")
        val source = JSONObject().put("branch", branch).put("path", "/")
        var pg = api.call("GET", "$base/pages")
        if (pg.status == 404) {
            val cr = api.call("POST", "$base/pages", JSONObject().put("build_type", "legacy").put("source", source))
            pg = when {
                cr.status == 409 || (cr.status == 422 && cr.message.contains("already", ignoreCase = true)) ->
                    api.call("GET", "$base/pages")
                !cr.ok -> throw apiError(cr, "تفعيل Pages")
                else -> cr
            }
            onStep(2, Status.Done, "تم تفعيل Pages")
        } else if (pg.ok) {
            val s = pg.json?.optJSONObject("source")
            if (s == null || s.optString("branch") != branch || s.optString("path") != "/") {
                val up = api.call("PUT", "$base/pages", JSONObject().put("source", source))
                if (!up.ok) throw apiError(up, "ضبط مصدر Pages")
            }
            onStep(2, Status.Done, "Pages مفعّلة")
        } else {
            throw apiError(pg, "فحص Pages")
        }
        val siteUrl = pg.json?.optString("html_url")?.takeIf { it.isNotEmpty() } ?: Names.siteUrl(login, name)

        // 4) انتظار البناء
        onStep(3, Status.Run, "بانتظار بدء البناء…")
        val t0 = System.currentTimeMillis()
        var built = false
        while (System.currentTimeMillis() - t0 < 150_000) {
            val b = api.call("GET", "$base/pages/builds/latest")
            val secs = (System.currentTimeMillis() - t0) / 1000
            val j = if (b.ok) b.json else null
            if (j != null) {
                val same = j.optString("commit") == newSha
                val st = j.optString("status")
                if (same && st == "errored") {
                    val em = j.optJSONObject("error")?.optString("message").orEmpty()
                    throw PublishException("فشل بناء الصفحة" + if (em.isNotEmpty()) ": $em" else ".")
                }
                if (same && st == "built") { built = true; break }
                onStep(3, Status.Run, "الحالة: " + (if (same) statusAr[st] ?: st else "في الانتظار") + " ($secs ث)")
            } else {
                onStep(3, Status.Run, "بانتظار بدء البناء ($secs ث)")
            }
            delay(3000)
        }
        onStep(3, Status.Done, if (built) "اكتمل البناء" else "لم يكتمل البناء بعد")
        return PublishResult(siteUrl, built)
    }
}
