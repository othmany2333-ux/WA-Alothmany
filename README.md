# WA Al-othmany — Foundation v0.1

المرحلة الأولى من تطبيق **WA Al-othmany**. هذه النسخة تبني الأساس الذي ستعتمد عليه محركات Shizuku والمزامنة والاستخراج والانضمام والفحص والنشر لاحقًا.

## الموجود في v0.1

- واجهة Jetpack Compose بتصميم Dark Premium / Teal / Gold.
- دعم العربية RTL والإنجليزية LTR.
- Dark / Light / System theme.
- Dashboard مطابق لاتجاه التصميم المعتمد.
- اختيار مصدر WhatsApp: Main / Business / Dual / Work / Secure.
- إعدادات السرعة والانتظار وTurbo flags محفوظة في DataStore.
- شاشة القروبات مع تحديد الكل / إلغاء التحديد / المؤرشف / المجتمعات.
- شاشة المهام.
- شاشة النتائج.
- شاشة الإعدادات.
- سجل نظام دائم في Room.
- شاشة Diagnostics.
- Room schema جاهز لمصادر WhatsApp والقروبات والروابط والمهام والسجلات.
- Hilt + Coroutines-ready architecture.

> ملاحظة: Shizuku وAccessibility وOverlay معروضة في الواجهة كحالات `غير مهيأ` في v0.1. الربط الفعلي يبدأ في المرحلة التالية، حتى نفصل الأساس عن محركات الأتمتة ونحافظ على مشروع قابل للاختبار.

## Toolchain

- Android Gradle Plugin: 9.3.0
- Gradle: 9.5 (wrapper)
- Kotlin: 2.3.21 (مُثبّت عمدًا لتوافق KSP/Hilt؛ يمكن تقييم 2.4.x بعد أول Build ناجح)
- Compose BOM: 2026.08.00
- Activity Compose: 1.12.4
- AndroidX Core: 1.19.0
- compileSdk: 37
- targetSdk: 36 (آخر Target مستقر؛ compileSdk 37 مطلوب لتقنيات Compose الحديثة)
- minSdk: 26
- Room: 2.8.4
- DataStore: 1.2.1
- Navigation Compose: 2.10.0
- Dagger Hilt: 2.60.1

## التشغيل للمبتدئ

1. ثبّت **Android Studio Quail 3 / 2026.1.3 أو أحدث مستقر**.
2. فك ضغط المشروع.
3. من Android Studio اختر **Open** وافتح مجلد `WA-Al-othmany-Foundation-v0.1`.
4. انتظر `Gradle Sync` حتى ينتهي.
5. إذا طلب Android Studio تنزيل Android SDK 37 أو Build Tools، وافق على التثبيت.
6. على هاتفك فعّل **Developer options > USB debugging**.
7. وصّل الهاتف بالكمبيوتر واضغط **Run ▶** ثم اختر الهاتف.

## GitHub

من داخل مجلد المشروع:

```bash
git init
git add .
git commit -m "Foundation v0.1"
git branch -M main
git remote add origin YOUR_GITHUB_REPOSITORY_URL
git push -u origin main
```

## هيكل المشروع

```text
app/src/main/java/com/alothmany/wa/
├── core/
│   ├── logging/
│   ├── model/
│   ├── navigation/
│   └── ui/
├── data/
│   ├── local/
│   └── repository/
├── di/
└── feature/
    ├── dashboard/
    ├── diagnostics/
    ├── groups/
    ├── logs/
    ├── placeholder/
    ├── results/
    ├── settings/
    └── tasks/
```

## المرحلة التالية

**Core Integration v0.2**:

1. Shizuku Manager الحقيقي.
2. Accessibility status / service.
3. Overlay permission/controller.
4. Capability Matrix.
5. WhatsApp Source Detector.
6. System Log events لكل فشل ونجاح.
7. ثم Global Sync Engine كأول محرك WhatsApp حقيقي.

## GitHub Actions

تمت إضافة Workflow جاهز في `.github/workflows/android.yml`. بعد رفع المشروع إلى GitHub سيحاول GitHub تلقائيًا بناء Debug APK وتشغيل اختبارات الوحدة، ثم يضع الـAPK داخل **Actions > Artifacts**.

## ملاحظة Gradle Wrapper

المشروع يتضمن bootstrap wrapper صغيرًا متوافقًا مع مدخل Gradle Wrapper ويحمّل Gradle 9.5 في أول تشغيل. بعد أول مزامنة ناجحة يمكنك استبداله بالـWrapper الرسمي عبر:

```bash
./gradlew wrapper --gradle-version 9.5.0
```

## التصميم المعتمد

مرجع التصميم الرسمي موجود في:

`docs/master-ui-reference.png`

ومواصفات تحويله إلى Compose في:

`docs/MASTER_UI_SPEC_AR.md`
