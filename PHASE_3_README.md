# WA Al-othmany — Smart Sync v0.3.0

## What this patch activates

- A real dedicated Smart Sync screen instead of the previous placeholder.
- Search over locally synchronized groups.
- Four-group preview by default; **Show more** adds 20 rows at a time to keep Compose fast with large lists.
- Smart selections: all, unread, active, locked, deleted, communities.
- Local selection hand-off to Extract / Publish / Join / Delete.
- Contact-sync mode selector prepared in the same workflow.
- Accessibility UI snapshot bridge for WhatsApp / WhatsApp Business.
- Event-driven group scanner with semantic Groups-filter navigation.
- Rewinds the Groups list to the beginning with two no-change confirmations before collecting rows, so a restored WhatsApp scroll position does not skip earlier groups.
- Adaptive UI wait after scrolling instead of a fixed long sleep.
- De-duplication and local group fingerprinting.
- Strict two-pass end verification.
- Checkpoint + sync-run tables.
- Missing groups are not treated as deleted after one scan; deletion requires two fully completed scans where the group stays missing.
- Dedicated dataSync foreground service so Samsung is less likely to kill a running scan after WhatsApp becomes foreground.
- Overlay bubble shows `SYNC <count>` and its pause/stop actions control Smart Sync when a sync is active.
- Room migration 1 -> 2 preserves the v0.2.1 database.

## v0.3.0 validation target

The first on-device validation intentionally targets the selected launchable WhatsApp source and its normal Groups filter. This establishes the exact Samsung + WhatsApp UI structure before archived/community/contact passes are enabled. The database and UI are already structured for those passes.

## Important correctness rules

- No direct access to WhatsApp private databases.
- No Knox / Secure Folder lock bypass.
- A non-launchable secondary Android profile is reported as unsupported by this scanner rather than silently opening the wrong WhatsApp instance.
- Group identity in v0.3.0 is local and name-based; operations that can modify data should re-verify the target group before acting, especially when duplicate group names exist.
- Contact mode controls are wired into selection hand-off, but number collection is deliberately not claimed as active until the group scanner is validated on the real device.
