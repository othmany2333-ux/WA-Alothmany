# WA Al-othmany v0.2.0 — System Integration

This patch upgrades the working Foundation v0.1 to a real Android system-integration core.

## Included

- Shizuku API 13.1.5 integration.
- Shizuku permission lifecycle and Binder monitoring.
- Shizuku UserService running with shell/root identity when available.
- Privileged capability probe for Android users/profiles and WhatsApp packages.
- Real AccessibilityService restricted to WhatsApp and WhatsApp Business.
- Fast, throttled UI-node counting for diagnostics without persisting message text.
- Real SYSTEM_ALERT_WINDOW permission flow.
- Draggable floating WA control overlay with pause/stop control bus.
- Real detection of WhatsApp / WhatsApp Business and best-effort detection across accessible Android users/profiles.
- Room source table synchronized with detected WhatsApp sources.
- Dashboard, Settings and Diagnostics wired to live system state.
- System state transitions written to the existing local Log database.

## Important boundaries

Shizuku started in ADB mode normally gives the UserService shell UID 2000, not root. It does not automatically bypass Android app sandboxes or Samsung Knox. Secure Folder and enterprise Work Profile access are capability-probed; the app does not bypass their lock or policy controls.

## After applying

Build the debug APK in GitHub Actions, install it, then test in this order:

1. Start Shizuku on the phone.
2. Open WA Al-othmany > Settings.
3. Tap Shizuku and grant permission.
4. Confirm Diagnostics shows Binder=true, permission=true, UserService=true, privileged UID=2000 (ADB mode) or 0 (root mode).
5. Open Accessibility settings and enable WA Al-othmany Accessibility Core.
6. Open WhatsApp, return to Diagnostics, confirm Last WhatsApp UI event and UI node count update.
7. Grant Overlay permission and tap Start overlay; confirm the draggable WA bubble appears.
8. Tap Probe sources and confirm installed WhatsApp sources are listed and the dashboard count changes.

Sync/Join/Check/Extract/Publish/Delete remain disabled as execution engines until Phase 3. Phase 2 only establishes the real system-control substrate they will use.

## CI signing stability

Phase 2 also caches a dedicated development debug keystore in GitHub Actions so APKs built from v0.2 onward can normally update each other without uninstalling. The already-installed v0.1 APK may have been signed with an earlier ephemeral CI key, so a one-time uninstall can be required when moving from v0.1 to v0.2.
