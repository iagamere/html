# ناشر HTML — تطبيق أندرويد (Kotlin + Jetpack Compose)

ينشر ملفات HTML على GitHub Pages: ينشئ المستودع، يرفع الملفات في commit واحد، يفعّل Pages، ثم يعرض الرابط.

## التشغيل
1. افتح المجلد في Android Studio (Koala 2024.1 أو أحدث، JDK 17).
2. انتظر مزامنة Gradle (يُنشأ gradle wrapper تلقائياً).
3. شغّل التطبيق على جهاز أو محاكي (Android 7.0 / API 24 فأعلى).

لبناء APK من سطر الأوامر بعد المزامنة: `./gradlew assembleDebug`
والناتج في `app/build/outputs/apk/debug/`.

## الرمز (Token)
أنشئ Personal access token بصلاحية `public_repo` فقط (لا حاجة لـ `repo` الكاملة).

## الفروقات عن نسخة الويب
- الرمز يُحفظ مشفّراً (EncryptedSharedPreferences) وخيار التذكر معطّل افتراضياً.
- كل الملفات تُرفع في commit واحد عبر Git Data API بدل commit لكل ملف.
- إشعار إلزامي بأن المحتوى سيصبح عاماً قبل النشر.
- خيار حذف الملفات الأخرى في مستودع موجود.
- منع النشر إلى مستودع خاص بدل الاكتفاء بتحذير.
- سقف 25MB لكل ملف و60MB للمجموع.
- استقبال ملف HTML عبر «مشاركة» من تطبيقات أخرى.
- `allowBackup=false` حتى لا يدخل الرمز في النسخ الاحتياطي.

## البنية
- `GitHubApi.kt` — عميل REST (OkHttp) وترجمة الأخطاء
- `Publisher.kt` — مراحل النشر الأربع
- `Store.kt` — التخزين المشفّر وسجل النشر
- `MainViewModel.kt` — الحالة والمنطق
- `UI.kt` / `MainActivity.kt` — الواجهة (RTL)
