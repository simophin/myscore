# MyScore: product and architecture record

Last updated: 2026-08-19

## Product direction

MyScore is a deliberately small Android sheet-music file browser and reader. Its primary job is to make a user-selected folder of PDF scores easy to manage and read. The main navigation has three destinations:

1. **Scores** — a root-confined folder browser for PDFs and directories.
2. **Find** — browse/search IMSLP in-app and download a selected score into the library.
3. **Settings** — choose/change the library folder or import an existing PDF.

The first milestone is a working local-first reader. Annotation, set lists, metadata editing, accounts, and cloud sync are not in scope yet.

## Decisions made

### Storage

The library is a directory selected with Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`). The app persists the URI grant and stores only that URI in Preferences DataStore. This avoids the broad `MANAGE_EXTERNAL_STORAGE` permission, works with local and compatible cloud document providers, and leaves scores accessible after uninstall.

The Scores browser exposes only folders and PDFs. Navigation begins at the granted tree and every directory and mutation is validated by the repository as a descendant of that root, so the browser cannot navigate or operate outside it. Breadcrumbs and system Back provide hierarchy navigation. Imports use `DocumentFile.createFile()` so downloads land in the selected library regardless of the underlying provider.

Files and folders have copy, move, and delete actions. Copy/move use a visible clipboard and paste into the currently displayed folder. On Android 7.0 and newer the repository first asks the document provider to perform its native `DocumentsContract` operation; unsupported providers and Android 6 use recursive stream-copy fallback. A fallback move deletes its source only after the complete copy succeeds and removes the new copy if source deletion fails. Name collisions receive a numbered suffix. Moving a folder into itself or a descendant is rejected, and deletion always requires UI confirmation.

### PDF reading

The reader uses the platform `PdfRenderer`, not a WebView or an unmaintained third-party PDF SDK. Pages are rendered off the main thread and only around the visible horizontal pager position. It supports horizontal page turns, pinch zoom/pan, and double-tap zoom. Expanded-width windows (840dp and above) show paired pages. Annotation is explicitly out of scope. While the reader is visible it applies Compose's `keepScreenOn` modifier; Android may sleep normally as soon as the reader leaves composition.

The last settled page is persisted per document. The last-opened document URI is also persisted: on a cold start, MyScore reopens it at that page when it is still present in the configured library. If it has moved, been deleted, or lost permission, startup falls back to the gallery. The app accepts PDF `ACTION_VIEW` intents and `myscore://open?uri=<encoded-content-uri>` deep links. A caller must still grant access to a content URI; a deep link cannot bypass Android's URI permission model.

The selected tree is observed with `ContentObserver`, and the gallery refreshes when a provider reports a change. It also refreshes whenever the app resumes because not every Storage Access Framework provider sends reliable descendant notifications.

### Adaptive and desktop layout

Layout decisions use the current app window bounds, not physical device type or rotation, so they update during desktop-window resizing, split screen, and fold/unfold changes. Portrait and square windows use bottom navigation; landscape windows place the three top-level destinations in a navigation rail on the left. Browser items remain row-shaped: compact windows use one list column, windows from 700dp use two columns, and windows from 1200dp use three. Reader windows at least 840dp wide show paired pages.

The reader has an immersive full-screen toggle. It hides app chrome and system bars using `WindowInsetsControllerCompat`, permits transient bars via edge swipe, and keeps a small exit-full-screen control visible. Desktop windowing may retain the system-owned caption bar by platform design.

### IMSLP and copyright

IMSLP's documented API exposes paginated lists of people and works, but not a supported direct file-download API. Its website also applies copyright-region checks and sometimes a download delay. The Find tab therefore hosts IMSLP's website in a WebView. Search and score selection happen on IMSLP itself, preserving its copyright/disclaimer and delay flow. When the user activates the site's download, MyScore handles that user-initiated URL with the WebView's cookies and user agent and writes the response into the selected library folder. The gallery refreshes when it completes.

Normal HTTP(S) navigation remains inside the Find WebView, including links to hosts used during IMSLP's navigation flow; only non-web URI schemes are delegated to Android. Pop-up windows are disabled. Scrolling down the page collapses the native search/status controls and scrolling up (or returning to the top) reveals them.

We do not scrape catalogs or file URLs and do not bypass IMSLP's copyright/disclaimer flow. The UI reminds users that public-domain status varies by country. **Import PDF** remains as a fallback for files acquired elsewhere and lives in Settings with the other library-management actions.

### API-backed score sources

No reviewed API is a drop-in replacement for IMSLP's classical repertoire and work/edition structure. The planned direction is therefore multiple Find providers, keeping IMSLP for breadth and adding API-native sources behind repository interfaces:

1. **Library of Congress JSON API — preferred first provider.** Its public, no-key `notated-music` search endpoint exposes structured search results, item metadata, rights fields, and resource/file records. It is institutionally maintained and straightforward to integrate. Its coverage is strongest for digitized historical collections, particularly American material, and a result is not guaranteed to expose a ready-made PDF, so the client must filter downloadable PDF resources and show item-level rights information.
2. **Wikimedia Commons / MediaWiki API — useful secondary provider.** It offers programmatic search and media-file retrieval for freely licensed and public-domain material. Coverage is large, but score metadata and categorization are community-authored and considerably less consistent than a music-specific catalog; license/attribution data must remain visible per file.
3. **Internet Archive APIs — possible later provider.** Search and item metadata/file APIs make many PDFs discoverable, but uploader-defined metadata and rights statements vary. It should only expose items whose rights and downloadable PDF are sufficiently clear, rather than treating the archive as uniformly public domain.

Mutopia is an excellent public-domain score source but does not expose a documented first-party search/download API, so integrating it would return us to site parsing. CPDL is compelling if a choral-only provider becomes a priority, but it is not a general classical-score replacement. We will not add either through undocumented scraping.

### Android stack

- Kotlin 2.3.21 and its Compose compiler plugin
- Android Gradle Plugin 9.2.0 / Gradle 9.7.0
- `compileSdk` and `targetSdk` 37, `minSdk` 23
- Stable Compose BOM 2026.06.01 and Material 3
- Lifecycle-aware state collection, ViewModel-owned state, coroutines for I/O
- Preferences DataStore for the one persisted setting
- No dependency-injection framework yet; the app is too small to justify one

The code is grouped by responsibility (`data`, `ui`, `ui/theme`). Repositories isolate storage from UI, leaving room to split into feature modules only if the codebase grows enough to benefit.

### MVVM and testing

`MainViewModel` is a plain AndroidX `ViewModel` with no `Application`, `Activity`, `Context`, or storage-provider dependency. It consumes `ScoreLibraryRepository` and `UserSettingsRepository` interfaces and exposes one immutable `MainUiState` `StateFlow`. The UI lifecycle-collects that state and sends user actions back through ViewModel methods, following unidirectional data flow. `MyScoreApplication` is the small composition root; a factory supplies concrete Android repository implementations. A DI framework remains unnecessary at this size.

JVM tests use hand-written fakes rather than mocks and cover folder selection/loading, error state, download preconditions, and reader persistence. Instrumented Compose tests cover gallery selection and bottom navigation. An Activity-level instrumentation test opens a generated PDF through `ACTION_VIEW` and verifies the real reader destination.

### CI and signing

- Pull requests run `testDebugUnitTest` on GitHub Actions.
- Publishing a GitHub Release builds `assembleRelease` and attaches a tag-named APK to that release.
- Release minification and resource shrinking are disabled, so R8 does not run.
- Per the project's low-friction signing decision, `.github/signing/debug.keystore` is a checked-in copy of the development debug key and signs release builds with the public `android` passwords. This provides APK integrity only. It provides **no identity protection or secret custody** and must be replaced before any distribution channel requires durable ownership.

### Dependency/reuse policy

Before implementing a substantial subsystem, research existing solutions and prefer a well-maintained, multi-contributor project with meaningful adoption. Evaluate release recency, issue activity, licensing, Android compatibility, supply-chain source, and whether its architecture actually fits—not stars alone. Use stable dependency releases unless we explicitly record a reason to accept preview software.

For the file browser, Material Files (about 8.4k GitHub stars, 2,300+ commits, and multiple published releases) validated familiar breadcrumb navigation and per-item file operations. Its GPL application code was not embedded or copied. MyScore stays on the platform Storage Access Framework and `DocumentFile`/`DocumentsContract`, avoiding a general file-manager dependency and retaining its deliberately restricted root.

For the PDF reader, this review found:

- The official AndroidX PDF viewer is actively developed and supports gestures and paired pages, but as of 2026-08-19 it has no stable release (latest `1.0.0-alpha19`). That conflicts with the stable-dependencies decision.
- AndroidPdfViewer has roughly 8.5k GitHub stars, but its published line remains beta/JitPack-based after years of inactivity and its native PDFium lineage has had Android 15 16KB-page compatibility and fork fragmentation concerns.
- Smaller Compose viewers have much lower adoption and are principally single-maintainer projects.

The current reader therefore stays on the Android platform's stable `PdfRenderer` API with Compose gesture/paging primitives. Revisit AndroidX PDF when it reaches stable; it is the preferred eventual replacement if its horizontal paging behavior fits sheet music.

## Current milestone

Implemented:

- Three-tab Material 3 Compose shell with edge-to-edge layout and dynamic color
- Persistent score-folder selection
- Root-confined folder-tree browser showing folders and PDFs as rows
- Adaptive one-, two-, or three-column file list based on window width
- Confirmed deletion and clipboard-style copy/move/paste for files and folders
- Horizontal PDF reader with double-tap/pinch/pan, two-page expanded layout, remembered page, and last-score restoration
- PDF `ACTION_VIEW` and `myscore://open?uri=…` deep-link handling
- Automatic library refresh from provider change events plus an on-resume fallback
- Window-responsive navigation: bottom bar in portrait, left rail in landscape/desktop windows
- Immersive reader full-screen toggle with swipe-reveal system bars
- Embedded IMSLP browsing/search with user-initiated downloads saved to the selected folder
- In-WebView HTTP(S) navigation and scroll-to-hide native search controls
- Import of an existing PDF into the selected folder from Settings
- Reader-level screen-awake behavior

Next likely increments:

1. Add create-folder and rename operations if real usage shows they are needed.
2. Add a Library of Congress API-backed Find provider with explicit rights and PDF filtering.
3. Add an explicit "Copy deep link" action for integrations that already hold URI access.
4. Move long downloads to WorkManager with progress, cancellation, and retry.
5. Add repository and UI tests, accessibility checks, and a baseline profile.

## Open product questions

- Is horizontal page turning the preferred default, and is a two-page landscape mode important?
- Which minimum Android version/device class matters most (phone, tablet, e-ink tablet)?
