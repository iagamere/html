package com.htmlpublisher

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PickedFile(val name: String, val bytes: ByteArray)
class Entry(val file: PickedFile, val main: Boolean) {
    val path: String get() = if (main) "index.html" else file.name
}

private const val MAX_FILE = 25L * 1024 * 1024
private const val MAX_TOTAL = 60L * 1024 * 1024
private val STEP_TITLES = listOf("المستودع", "رفع الملفات", "تفعيل Pages", "انتظار النشر")

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private var api: GitHubApi? = null

    // الحساب
    var tokenInput by mutableStateOf("")
    var remember by mutableStateOf(false)
    var user by mutableStateOf<String?>(null)
    var connecting by mutableStateOf(false)
    var accError by mutableStateOf<String?>(null)
    var accWarn by mutableStateOf<String?>(null)

    // الملفات
    val files = mutableStateListOf<PickedFile>()
    var pasted by mutableStateOf("")
    var fileError by mutableStateOf<String?>(null)

    // المستودع
    var repoName by mutableStateOf("")
    var nameError by mutableStateOf<String?>(null)
    var repoInfo by mutableStateOf<RepoInfo?>(null)
    var checking by mutableStateOf(false)
    var allowUpdate by mutableStateOf(false)
    var replaceAll by mutableStateOf(false)
    private var nameDirty = false
    private var checkJob: Job? = null

    // النشر
    var confirmPublic by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val steps = mutableStateListOf<StepState>()
    var result by mutableStateOf<PublishResult?>(null)
    var history by mutableStateOf(store.history())

    val entries: List<Entry>
        get() {
            val htmls = files.filter { it.name.endsWith(".html", true) || it.name.endsWith(".htm", true) }
            val main = htmls.firstOrNull { it.name.equals("index.html", true) } ?: htmls.firstOrNull()
            return files.map { Entry(it, it === main) }
        }

    init {
        store.token?.let { remember = true; connect(it, silent = true) }
    }

    // ---------- الحساب ----------
    fun connect(tok: String, silent: Boolean = false) {
        val t = tok.trim()
        accError = null; accWarn = null
        if (t.isEmpty()) {
            if (!silent) accError = "الصق رمز الوصول أولاً."
            return
        }
        viewModelScope.launch {
            connecting = true
            try {
                val client = GitHubApi(t)
                val r = client.call("GET", "/user")
                if (r.status == 200) {
                    api = client
                    user = r.json!!.getString("login")
                    store.token = if (remember) t else null
                    val sc = r.scopes
                    if (sc != null && !Regex("(^|,\\s*)(repo|public_repo)(\\s*,|$)").containsMatchIn(sc)) {
                        accWarn = "هذا الرمز لا يحمل صلاحية public_repo، وقد يفشل إنشاء المستودع أو تفعيل Pages."
                    }
                    tokenInput = ""
                    scheduleCheck()
                } else if (!silent) {
                    accError = apiError(r, "").message
                } else {
                    store.token = null
                }
            } catch (e: PublishException) {
                if (!silent) accError = e.message
            } finally {
                connecting = false
            }
        }
    }

    fun logout() {
        api = null; user = null; store.token = null
        repoInfo = null; allowUpdate = false
    }

    // ---------- الملفات ----------
    fun addFiles(uris: List<Uri>) {
        val app = getApplication<Application>()
        fileError = null
        viewModelScope.launch {
            for (uri in uris) {
                val (pf, err) = withContext(Dispatchers.IO) { readPicked(app, uri) }
                if (err != null) { fileError = err; continue }
                if (pf != null) {
                    files.removeAll { it.name == pf.name }
                    files.add(pf)
                }
            }
            afterFilesChanged()
        }
    }

    private fun readPicked(app: Application, uri: Uri): Pair<PickedFile?, String?> {
        return try {
            var name = "file"
            var size = -1L
            app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
            if (size > MAX_FILE) return null to "الملف $name أكبر من 25 ميغابايت."
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null to "تعذّرت قراءة $name."
            if (bytes.size > MAX_FILE) return null to "الملف $name أكبر من 25 ميغابايت."
            PickedFile(name, bytes) to null
        } catch (e: Exception) {
            null to "تعذّرت قراءة الملف."
        }
    }

    fun removeFile(f: PickedFile) {
        files.remove(f)
        afterFilesChanged()
    }

    private fun afterFilesChanged() {
        val main = entries.firstOrNull { it.main }
        if (main != null && !nameDirty) {
            val n = main.file.name
            repoName = Names.slug(if (n.equals("index.html", true)) "" else n)
            scheduleCheck()
        }
    }

    // ---------- اسم المستودع ----------
    fun onNameChange(v: String) {
        repoName = v
        nameDirty = true
        scheduleCheck()
    }

    private fun scheduleCheck() {
        checkJob?.cancel()
        repoInfo = null; allowUpdate = false; checking = false
        val n = repoName.trim()
        nameError = if (n.isNotEmpty() && !Names.isValid(n)) "الاسم يقبل أحرفاً إنجليزية وأرقاماً و - و _ و . فقط (ولا يبدأ بنقطة)." else null
        val u = user
        val client = api
        if (n.isEmpty() || nameError != null || u == null || client == null) return
        checkJob = viewModelScope.launch {
            delay(500)
            checking = true
            try {
                repoInfo = Publisher(client, u).checkRepo(n)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                repoInfo = null
            }
            checking = false
        }
    }

    // ---------- النشر ----------
    fun publish() {
        if (busy) return
        error = null; result = null
        val u = user
        val client = api
        if (u == null || client == null) { error = "اتصل بحسابك في الخطوة 1 أولاً."; return }

        val sources: List<SourceFile> = when {
            files.isNotEmpty() -> entries.map { SourceFile(it.path, it.file.bytes) }
            pasted.isNotBlank() -> listOf(SourceFile("index.html", pasted.toByteArray(Charsets.UTF_8)))
            else -> emptyList()
        }
        if (sources.isEmpty()) { error = "اختر ملف HTML أو الصق الشيفرة في الخطوة 2."; return }
        if (sources.sumOf { it.bytes.size.toLong() } > MAX_TOTAL) { error = "الحجم الكلي للملفات يتجاوز 60 ميغابايت."; return }
        val name = repoName.trim()
        if (!Names.isValid(name)) { error = "اسم المستودع غير صالح."; return }
        if (!confirmPublic) { error = "أكّد أنك تفهم أن الموقع سيكون عاماً قبل المتابعة."; return }

        viewModelScope.launch {
            busy = true
            steps.clear()
            STEP_TITLES.forEach { steps.add(StepState(it, Status.Pending)) }
            var cur = 0
            try {
                val pub = Publisher(client, u)
                val info = repoInfo ?: pub.checkRepo(name).also { repoInfo = it }
                if (info.exists && info.isPrivate) {
                    throw PublishException("هذا المستودع خاص، وGitHub Pages المجانية لا تعمل مع المستودعات الخاصة.")
                }
                if (info.exists && !allowUpdate) {
                    throw PublishException("المستودع موجود مسبقاً. فعّل خيار الموافقة على التحديث في الخطوة 3 للمتابعة.")
                }
                val r = pub.publish(name, sources, replaceAll && info.exists) { i, s, d ->
                    cur = i
                    steps[i] = StepState(STEP_TITLES[i], s, d)
                }
                result = r
                history = store.addHistory(name, r.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "حدث خطأ غير متوقع."
                if (steps.isNotEmpty()) steps[cur] = StepState(STEP_TITLES[cur], Status.Error, msg)
                error = msg
            } finally {
                busy = false
            }
        }
    }

    fun publishAnother() {
        files.clear(); pasted = ""; repoName = ""; nameDirty = false
        result = null; steps.clear(); error = null; confirmPublic = false; replaceAll = false
        scheduleCheck()
    }

    fun clearHistory() {
        store.clearHistory()
        history = emptyList()
    }
}
