# WA Al-othmany — Master UI Specification

هذه الوثيقة تربط كود المرحلة الأولى بالتصميم البصري المعتمد في `master-ui-reference.png`.

## الهوية

- اسم المنتج: **WA Al-othmany**
- الأسلوب: Dark Premium / Modern Automation Dashboard
- الخلفية الأساسية: Navy / Near Black
- Primary: Cyan / Teal
- Success: Green
- Premium accent: Gold
- Pause: Amber
- Destructive/Stop: Red

## Dashboard

يجب أن يحتوي دائمًا على:

1. اسم التطبيق والحالة.
2. Shizuku / Accessibility / Overlay status.
3. اختيار مصدر WhatsApp:
   - Main
   - Business
   - Dual Messenger
   - Work Profile
   - Secure Folder
4. إحصائيات المصادر والقروبات والمجتمعات والروابط.
5. Smart Speed Settings.
6. الأزرار الأساسية:
   - المزامنة
   - الانضمام
   - الفحص
   - الاستخراج
   - النشر
   - الحذف
7. Execution control:
   - بدء التنفيذ
   - إيقاف مؤقت
   - إيقاف نهائي
8. Bottom navigation:
   - الرئيسية
   - القروبات
   - المهام
   - النتائج
   - الإعدادات

## قاعدة التصميم

لا يتم وضع منطق Shizuku أو محركات WhatsApp داخل Composables. الشاشة تعرض state وتصدر actions فقط. هذا شرط ثابت للمراحل التالية.
