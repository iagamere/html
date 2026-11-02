package com.htmlpublisher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private val LightColors = lightColorScheme(
    primary = Color(0xFF3350E8), onPrimary = Color.White,
    secondaryContainer = Color(0xFFE8ECFB),
    background = Color(0xFFF1F3F8), surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF141824), onSurface = Color(0xFF141824),
    onSurfaceVariant = Color(0xFF5B6279), outline = Color(0xFFD9DDE9),
    error = Color(0xFFC4281C)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DA2FF), onPrimary = Color(0xFF0B0E18),
    secondaryContainer = Color(0xFF1E2540),
    background = Color(0xFF0E1118), surface = Color(0xFF171B26),
    onBackground = Color(0xFFE7EAF5), onSurface = Color(0xFFE7EAF5),
    onSurfaceVariant = Color(0xFF98A0BA), outline = Color(0xFF2A3042),
    error = Color(0xFFFF7A70)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

private val ltrMono = TextStyle(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr, fontSize = 13.sp)

@Composable private fun okColor() = if (isSystemInDarkTheme()) Color(0xFF4CC38A) else Color(0xFF14804A)
@Composable private fun warnColor() = if (isSystemInDarkTheme()) Color(0xFFE0A53A) else Color(0xFF9A5800)

@Composable
private fun Section(num: Int?, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (num != null) {
                Box(
                    Modifier.size(26.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(num.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ErrorText(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.Top) {
        Checkbox(checked, onChange)
        Text(text, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Root(vm: MainViewModel) {
    val uri = LocalUriHandler.current
    val clip = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addFiles(it) }
    var showPaste by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ناشر HTML", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    "ارفع ملف HTML ويتولى التطبيق الباقي: ينشئ مستودعاً على GitHub، ويرفع الملفات في commit واحد، ويفعّل GitHub Pages، ثم يعطيك رابطاً جاهزاً للمشاركة.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1) الحساب
            Section(1, "الحساب") {
                val u = vm.user
                if (u == null) {
                    OutlinedTextField(
                        value = vm.tokenInput, onValueChange = { vm.tokenInput = it },
                        label = { Text("رمز الوصول من GitHub") },
                        placeholder = { Text("ghp_… أو github_pat_…") },
                        singleLine = true, textStyle = ltrMono,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    CheckRow(vm.remember, { vm.remember = it }, "تذكّر الرمز على هذا الجهاز (يُحفظ مشفّراً)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.connect(vm.tokenInput) }, enabled = !vm.connecting) {
                            if (vm.connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text("اتصال")
                        }
                        TextButton(onClick = {
                            uri.openUri("https://github.com/settings/tokens/new?scopes=public_repo&description=HTML%20Publisher")
                        }) { Text("إنشاء رمز جديد") }
                    }
                    Hint("صلاحية public_repo تكفي لأن المستودعات المنشأة عامة دائماً. لا تمنح صلاحية repo الكاملة. يُرسل الرمز إلى api.github.com فقط.")
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u, style = ltrMono.copy(fontSize = 15.sp))
                                Text("متصل", color = okColor(), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { vm.logout() }) { Text("خروج") }
                        }
                    }
                }
                vm.accError?.let { ErrorText(it) }
                vm.accWarn?.let { Text(it, color = warnColor(), style = MaterialTheme.typography.bodySmall) }
            }

            // 2) الملف
            Section(2, "الملف") {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("اختيار ملفات (HTML، صور، CSS، JS)")
                }
                Hint("الملف الرئيسي يصبح index.html. يمكنك أيضاً مشاركة ملف HTML إلى التطبيق من أي تطبيق آخر.")
                vm.entries.forEach { e ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.path, Modifier.weight(1f), style = ltrMono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (e.main) Text(" رئيسي ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            Text(fmtSize(e.file.bytes.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = ltrMono.copy(fontSize = 11.sp))
                            TextButton(onClick = { vm.removeFile(e.file) }) { Text("إزالة") }
                        }
                    }
                }
                vm.fileError?.let { ErrorText(it) }
                TextButton(onClick = { showPaste = !showPaste }) { Text("أو الصق الشيفرة مباشرة") }
                if (showPaste) {
                    OutlinedTextField(
                        value = vm.pasted, onValueChange = { vm.pasted = it },
                        textStyle = ltrMono, minLines = 6, maxLines = 12,
                        placeholder = { Text("<!doctype html>…", style = ltrMono) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (vm.files.isNotEmpty() && vm.pasted.isNotBlank()) Hint("تنبيه: الملفات المختارة لها الأولوية وسيتم تجاهل الشيفرة الملصوقة.")
                }
            }

            // 3) المستودع
            Section(3, "المستودع") {
                OutlinedTextField(
                    value = vm.repoName, onValueChange = { vm.onNameChange(it) },
                    label = { Text("اسم المستودع") }, placeholder = { Text("my-site", style = ltrMono) },
                    singleLine = true, textStyle = ltrMono, isError = vm.nameError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                vm.nameError?.let { ErrorText(it) }
                val info = vm.repoInfo
                when {
                    vm.checking -> Hint("جارٍ التحقق…")
                    info != null && !info.exists -> Text("مستودع جديد", color = okColor(), style = MaterialTheme.typography.bodySmall)
                    info != null && info.exists -> Text("المستودع موجود", color = warnColor(), style = MaterialTheme.typography.bodySmall)
                }
                val u = vm.user
                if (u != null && Names.isValid(vm.repoName.trim())) {
                    Text("الرابط المتوقع: " + Names.siteUrl(u, vm.repoName.trim()), style = ltrMono.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (info != null && info.exists) {
                    if (info.isPrivate) {
                        ErrorText("هذا المستودع خاص، وGitHub Pages المجانية لا تعمل مع المستودعات الخاصة. اختر اسماً آخر.")
                    } else {
                        CheckRow(vm.allowUpdate, { vm.allowUpdate = it }, "أوافق على تحديث هذا المستودع الموجود. سيُستبدل index.html وكل ملف باسم مطابق لملفاتي.")
                        CheckRow(vm.replaceAll, { vm.replaceAll = it }, "حذف كل الملفات الأخرى في المستودع (يبقى ملفاتي فقط)")
                    }
                }
                Hint("الصفحات المجانية تتطلب مستودعاً عاماً، لذلك يُنشأ المستودع بوضع Public.")
            }

            // 4) النشر
            Section(4, "النشر") {
                CheckRow(vm.confirmPublic, { vm.confirmPublic = it }, "أفهم أن محتوى الملفات سيصبح عاماً على الإنترنت، وأنه لا يحتوي على أسرار أو بيانات خاصة.")
                Button(onClick = { vm.publish() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.busy) "جارٍ النشر…" else "انشر الآن", fontSize = 17.sp)
                }
                vm.error?.let { ErrorText(it) }
                if (vm.steps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        vm.steps.forEach { StepRow(it) }
                    }
                }
                vm.result?.let { r ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, okColor())
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("الموقع منشور", color = okColor(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(r.url, style = ltrMono.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.primary)
                            if (!r.built) Hint("الرابط جاهز، لكن GitHub لم يؤكد اكتمال البناء بعد. قد يظهر الموقع خلال دقيقتين إلى خمس دقائق، وبعد التحديث قد تحتاج إلى إعادة تحميل الصفحة.")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { uri.openUri(r.url) }) { Text("فتح الموقع") }
                                OutlinedButton(onClick = { clip.setText(AnnotatedString(r.url)); copied = true }) {
                                    Text(if (copied) "تم النسخ" else "نسخ الرابط")
                                }
                            }
                            TextButton(onClick = { vm.publishAnother() }) { Text("نشر ملف آخر") }
                        }
                    }
                }
            }

            // السجل
            if (vm.history.isNotEmpty()) {
                Section(null, "آخر عمليات النشر") {
                    vm.history.forEach { h ->
                        Column(Modifier.fillMaxWidth().clickable { uri.openUri(h.url) }.padding(vertical = 2.dp)) {
                            Text(h.url, style = ltrMono.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.primary)
                            Text(DateFormat.getDateTimeInstance().format(Date(h.at)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { vm.clearHistory() }) { Text("مسح السجل") }
                }
            }
        }
    }
}

@Composable
private fun StepRow(s: StepState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (s.status) {
                Status.Pending -> Text("○", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Status.Run -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Status.Done -> Text("✓", color = okColor(), fontWeight = FontWeight.Bold)
                Status.Error -> Text("×", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Column {
            Text(
                s.title, fontWeight = FontWeight.Medium,
                color = if (s.status == Status.Pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (s.detail.isNotEmpty()) {
                Text(
                    s.detail, style = MaterialTheme.typography.bodySmall,
                    color = if (s.status == Status.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun fmtSize(n: Int): String = when {
    n < 1024 -> "$n B"
    n < 1048576 -> "%.1f KB".format(n / 1024.0)
    else -> "%.1f MB".format(n / 1048576.0)
}
