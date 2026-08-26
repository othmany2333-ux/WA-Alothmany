# WA Al-othmany Architecture — Foundation v0.1

## Layers

- `core/model`: نماذج التطبيق العامة والحالات.
- `core/logging`: سجل دائم للأحداث والأخطاء.
- `core/navigation`: Routes والتنقل.
- `core/ui`: الجذر، الثيم، ومكونات الواجهة المشتركة.
- `data/local`: Room database + DAOs + entities.
- `data/repository`: DataStore وواجهات الوصول للبيانات.
- `di`: Hilt dependency graph.
- `feature/*`: كل شاشة في Feature مستقلة.

## Foundation rule

محركات WhatsApp لا تدخل داخل UI. كل محرك مستقبلي (Sync / Extract / Check / Join / Publish / Delete) سيُبنى كطبقة domain/engine مستقلة ويصدر `StateFlow` للواجهة. بهذه الطريقة يمكن إيقاف واستكمال المهمة دون ربط منطق العمل بالشاشة.

## Phase 2 targets

1. ShizukuManager.
2. AccessibilityManager.
3. OverlayManager.
4. CapabilityMatrix.
5. WhatsAppSourceDetector.
6. Structured error events.
7. Global Sync Engine.
