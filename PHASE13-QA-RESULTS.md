# Phase 13 source-package checks

These checks are performed when preparing the Phase 13 archive, not Android runtime tests:

- [x] Phase 12 ZIP extracted successfully.
- [x] Existing source tree retained in the project archive.
- [x] Release signing is optional and reads secrets only from environment variables.
- [x] No keystore or credential values are included.
- [x] Release workflow includes tests, lint, APK existence check, and checksum generation.
- [ ] Android Gradle build executed in this workspace (not available: Gradle/Android SDK are not installed).
- [ ] Signed APK generated and signature verified (requires the owner's private keystore).
- [ ] Real-device recording and compatibility tests completed (requires physical Android devices).

This document deliberately distinguishes source/configuration checks from tests that have not been run.
