# Changelog

All notable changes to JustPdf are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning follows [Semantic Versioning](https://semver.org/) — `MAJOR.MINOR.PATCH`.

---

## [Unreleased]

> Changes staged for the next release go here.

---

## [1.0.0] — 2026-05-26

### Added
- Open PDFs via **"Open with"** from any app (`ACTION_VIEW` intent filter)
- Receive PDFs shared from other apps (`ACTION_SEND` intent filter)
- Browse and open PDFs from the device file system via Storage Access Framework (`ACTION_OPEN_DOCUMENT`)
- Continuous vertical scroll through all pages using `RecyclerView` + Android built-in `PdfRenderer`
- Pinch-to-zoom and double-tap zoom on individual pages
- Portrait and landscape support — rotation handled without Activity restart (`configChanges`)
- Minimal floating toolbar (auto-hides after 3 seconds, revealed by tap):
  - **Open file** button
  - **Share** button
  - **Page counter** (`current / total`)
- Share the currently open PDF to any app (Mail, WhatsApp, Drive, etc.) via `FileProvider` + `ACTION_SEND` chooser
- Fully stateless — no history, no bookmarks, no database
- Zero external PDF library dependencies — uses Android's built-in `PdfRenderer` (API 21+)
- Sideload-only — no Play Store distribution

### Technical
- Min SDK: 21 (Android 5.0 Lollipop)
- Target SDK: 34 (Android 14)
- Language: Kotlin
- Build: Gradle 8.6, AGP 8.4, Kotlin DSL
- `versionCode`: 1
- `versionName`: 1.0.0

---

<!-- Link definitions (update URLs when tags are created on GitHub) -->
[Unreleased]: https://github.com/michielhbrand/just-pdf/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/michielhbrand/just-pdf/releases/tag/v1.0.0
