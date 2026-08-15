# BYSEL — Play / Android improvement tracker

Living backlog from Google Play Console, Play Academy, and Android Developers docs shared in chat.  
**Update rule:** append new items when more docs are reviewed; mark status as we ship.

Last updated: 2026-08-15 (Edge-to-edge theme, FCM token, Data safety, CI tests)

---

## Legend

| Priority | Meaning |
|----------|---------|
| P0 | Compliance / discoverability risk — do soon |
| P1 | Strong UX / quality win |
| P2 | Nice-to-have / featuring polish |
| Skip | Reviewed; no action for BYSEL now |

| Status | Meaning |
|--------|---------|
| Done | Shipped or confirmed OK |
| Next | Recommended soon |
| Later | Backlog |
| Watching | Monitor only (vitals / metrics) |
| Skip | Explicitly not adopting |

---

## Already confirmed OK (no work)

| Source | Item | Notes |
|--------|------|--------|
| Developer verification | Package registered | `com.bysel.trader` Registered (2026-04-11) |
| Target API (Aug 31, 2026) | `targetSdk = 36` | Meets new apps/updates bar |
| Policy (Jul 15, 2026) | Anonymous/random chat | N/A — signed-in stock AI, 18+ |
| Policy (Jul 15, 2026) | SMS / READ_CALL_LOG | Not used |
| Policy (Jul 15, 2026) | Personal loans / EWA | N/A |
| Policy (Jul 15, 2026) | Location Data safety | No location permissions |
| Privacy / User Data + AI | Third-party AI disclosure | Privacy policy mentions Groq/Gemini/ISM |
| Releases | AABs ready to upload | Internal 2.6.165 (213), Closed 3.0.4 (214), Open 4.0.4 (212) — speed + in-app polish |
| Background work guide | API choice | Coroutines + WorkManager alerts + FCM |
| Task scheduling (WorkManager) | Persistent work choice | Alerts use WorkManager; UI refresh stays on coroutines — correct split |
| Keep device awake guide | Wake locks / keepScreenOn | No app wake locks found; correctly lets device suspend — **Skip** adding wake locks |
| Compose UI testing guide | Test infra | `ui-test-junit4` + `ui-test-manifest` already in Gradle; one instrumented test exists |
| About notifications | Basic alerts path | Channel + POST_NOTIFICATIONS + small icon already used in `AlertsManager` |
| Simple data sharing | Send text via Sharesheet | Portfolio share already uses `ACTION_SEND` + chooser |
| Edge-to-edge setup | enableEdgeToEdge + insets | `enableEdgeToEdge()` + `WindowInsets.safeDrawing` kept; `Theme.Material3.DayNight.NoActionBar` + transparent system bars (no enforcement opt-out) |
| Display cutouts | Avoid notch overlap | `safeDrawingPadding()` covers cutout insets — correct Compose approach |
| Predictive back | Platform opt-in | `android:enableOnBackInvokedCallback="true"` already set; custom `BackHandler` used for tabs/exit |
| App shortcuts | Static launcher shortcuts | `@xml/shortcuts` + MainActivity `shortcut_action` routing; wrong conversation categories removed |
| Adaptive icons | FG/BG + monochrome | `mipmap-anydpi-v26` + `v33` include `<monochrome>` for themed icons |
| Connectivity / battery guides | Core patterns | Pause refresh on background; optional Wi‑Fi/charging gates |
| Cronet guide | Adopt Cronet | **Skip** — OkHttp/Retrofit is fine |
| Core value guide | Multi form-factor (Wear/PC) | **Skip** unless tablet featuring becomes a goal |
| Store listings | Package/listing basics | Live; CTR ~82% on tiny sample |
| Design for Safety | Permission minimization | No location/camera/mic/storage; only INTERNET + POST_NOTIFICATIONS; AD_ID removed |
| Design for Safety | Secure networking | HTTPS only, `usesCleartextTraffic=false`, network security config |
| Design for Safety | Auth / encryption | EncryptedSharedPreferences session; Credential Manager + biometrics |
| Design for Safety | In-context notifications | POST_NOTIFICATIONS requested near alert feature, not at cold start |
| Security checklist | Auth stack | Credential Manager + biometrics + autofill + short-lived tokens + refresh |
| Security checklist | Storage / IPC | App-private Room/prefs; FCM service `exported=false`; no ContentProviders; widget export required |
| Security checklist | Identifiers | App UUIDs / trace ids — no IMEI/phone identifiers |
| Privacy checklist | Location / ads IDs | No location; AD_ID removed; app-scoped UUIDs only |
| Privacy checklist | Permission UX | Notifications requested in Settings (feature context), not at startup |
| Identity hub | Credential Manager passwords | Jetpack credentials + play-services-auth; save after login/register; one-tap restore |
| Identity hub | Legacy Google Sign-In / Smart Lock | Not used — already on Credential Manager (no migration debt) |
| About SIWG | Auth coverage today | Password + Firebase phone OTP + biometrics; SIWG not required to ship testers |
| Auth & Onboarding | Friction model | Minimal signup fields; contextual notification permission; no forced walkthrough; Forgot password accessible |
| Live Updates guide | Notification model | Standard auto-cancel price-alert banners on threshold cross — not ongoing ProgressStyle / promoted Live Updates |
| Notifications guide | Template choice | BigText + DEFAULT channel for price alerts; LOW for practice/test; contextual permission already |

---

## Active backlog

### P0 — Watch / verify

| ID | Item | Source | Status | Notes |
|----|------|--------|--------|-------|
| P0-1 | Android vitals: crash & ANR after new AABs | Technical quality | Watching | Filter versionCodes 209–211; fix only if over bad-behavior thresholds |
| P0-2 | Data safety form matches privacy (AI + third-party AI) | Policy clarification Jul 2026 | Next (manual Console paste) | Checklist below matches current app + privacy pages (15 Aug 2026) |
| P0-3 | Content rating questionnaire complete | Content ratings clarification | Next | Confirm not “unrated” |

### P1 — Product / UX quality

| ID | Item | Source | Status | Notes |
|----|------|--------|--------|-------|
| P1-1 | Cache-first wallet/holdings + staged resume | Technical quality / UX (startup) | Done | Shipped in 4.0.3 / 3.0.3 / 2.6.164 builds — aligns with lazy/async content for TTID/TTFD |
| P1-1b | Defer off-screen tab composition at cold start | App startup analysis (Aug 2026) | Done | `beyondBoundsPageCount = 0` — Home no longer precomposes AI/Trade |
| P1-1c | Priority quotes first, then expand universe | App startup analysis (Aug 2026) | Done | Cold start fetches indices+watchlist+holdings first; full universe after 1.2s |
| P1-1d | `reportFullyDrawn` for TTFD | App startup analysis (Aug 2026) | Done | Fired when cached wallet/holdings/quotes ready (2.5s fallback) |
| P1-1e | Defer on-device LLM + Play update check | App startup analysis (Aug 2026) | Done | LLM init +6s; in-app update check +3s |
| P1-1f | StrictMode thread policy (debug) | App startup analysis (Aug 2026) | Done | Disk/network detect + penaltyLog in DEBUG only |
| P1-2 | Redeploy backend custom LLM to Render | Product (not Play policy) | Done | Merged PR #25 to main (query-aware ISM profiles + Products/SGB); smoke after Render finishes |
| P1-3 | Store listing: honest paper-trading + educational AI copy | Core value / marketing assets | Later | Avoid “guaranteed tips”; keep screenshots accurate |
| P1-4 | Grow listing visitors / testers | Store performance | Later | CTR strong; volume is the gap (~17 visitors early) |
| P1-5 | In-app updates for freshness | Technical quality (app freshness) | Later | Optional; increases users on latest binary |

### P2 — Optional polish

| ID | Item | Source | Status | Notes |
|----|------|--------|--------|-------|
| P2-1 | Glance home widget polish (sizes, tap, refresh) | [platform-samples appwidgets](https://github.com/android/platform-samples/tree/main/samples/user-interface/appwidgets) | Partial Done | NIFTY row now uses real index quote (not first heatmap equity); further size/refresh polish Later |
| P2-2 | Offline-first / modular architecture inspiration | [nowinandroid](https://github.com/android/nowinandroid) + Data layer guide | Later | Don’t full-migrate; borrow patterns gradually |
| P2-2b | Split fat `TradingRepository` into typed repos | Data layer guide (Mar/May 2026) | Later | Ideal: QuotesRepository, HoldingsRepository, WalletRepository, MarketRepository — only when touching those areas |
| P2-2c | Explicit Remote/Local data source classes | Data layer guide | Later | Today API+Room live inside repository; extract when adding a second remote source |
| P2-2d | Room/local as source of truth for user data | Data layer guide (offline-first) | Partial Done | Holdings/quotes Room + wallet prefs already cache-first; keep expanding this pattern |
| P2-2e | Prefer DataStore over SharedPreferences for settings | Data layer guide | Later | theme/watchlist/settings still use prefs — migrate when editing those stores |
| P2-2f | Domain models separate from API DTOs | Data layer guide | Later | Many models shared UI↔network; split only where API payloads are bloated |
| P2-3 | Baseline / startup profile | App startup analysis + technical quality | Later | Still valuable after this speed pass for ART ahead-of-time optimization |
| P2-3b | Macrobenchmark startup measurements | App startup analysis (Aug 2026) | Later | Measure cold/warm start to quantify gains; not required to ship |
| P2-3c | StrictMode.ThreadPolicy on debug builds | App startup analysis (Aug 2026) | Done | See P1-1f |
| P2-3d | Jetpack App Startup library for init ordering | App startup analysis (Aug 2026) | Later | Only if Application/onCreate init grows heavy |
| P2-4 | Large screens / foldables guidelines | App quality form factors | Later | Phone-first OK for now |
| P2-5 | Custom store listings / experiments | Store listings | Later | Too early at low traffic |
| P2-6 | Play Academy policy courses | Play Academy | Skip/optional | Training only; not required to ship |
| P2-7 | GWP-ASan / deeper stability tooling | Technical quality (stability) | Later | If vitals show hard-to-debug native issues |
| P2-8 | JankStats / rendering monitoring | Technical quality (rendering) | Later | If jank appears in vitals |
| P2-9 | External keepalive for Render free tier | Cold-start / resume lag | Later | Cron/UptimeRobot → `/warmup` every ~10–14 min |
| P2-10 | Optional periodic local cache sync via WorkManager (Wi‑Fi + charging) | Task scheduling guide | Later | Only for non-urgent prefetch (e.g. watchlist quotes to Room); **not** for live trading ticks |
| P2-11 | Review AlertWorker constraints / unique work / retry | Task scheduling guide | Later | Confirm unique work + backoff already sane when next touching alerts |
| P2-12 | Add Compose UI tests for critical flows | Compose testing guide (Aug 2026) | Partial Done | Added `ShortcutActionsComposeTest` + widget helper unit tests; expand Auth/Home flows Later |
| P2-13 | Semantics / contentDescription on key actions | Compose testing guide | Later | Improves testability + a11y when editing those screens |
| P2-14 | Split notification channels (alerts vs market vs system) | About notifications | Done | `bysel_price_alerts` + `bysel_practice` (+ legacy channel kept) |
| P2-15 | Notification tap deep-link + lock-screen auth for trade actions | About notifications | Partial Done | Tap opens Alerts via `shortcut_action=price_alerts` + `onNewIntent`; trade-action auth Later (no trade actions on notifs today) |
| P2-16 | Update/group price-alert notifications (avoid flood) | About notifications / Notifications guide | Done | Stable ids + group key + InboxStyle summary; BigText; onlyAlertOnce |
| P2-17 | Extend share sheet to AI answers / stock detail (portfolio share exists) | Simple data sharing overview | Partial Done | AI chat replies have Share; stock-detail share Later |
| P2-18 | Receive shared text/links into AI ask (optional) | Simple data sharing overview | Later | `intent-filter` for `ACTION_SEND` text/plain → prefill AI chat; only if product wants it |
| P2-19 | Confirm `windowSoftInputMode=adjustResize` + IME insets on AI/auth | Edge-to-edge setup | Done | Manifest `adjustResize`; AI input + Auth use `imePadding` (+ Auth scroll) |
| P2-20 | Status bar icon contrast per theme (light/dark) | Edge-to-edge setup | Done | `WindowCompat` light status/nav bars from theme surface luminance |
| P2-21 | Spot-check cutout/landscape on a notched device | About cutouts | Watching | Manual QA: Home nav + AI header not under notch; `safeDrawing` should already handle |
| P2-22 | Predictive back polish for nested screens | About predictive back | Later | Opt-in exists; migrate custom `BackHandler` paths (detail → Home) to `PredictiveBackHandler` / system animations where it doesn’t break double-back-to-exit |
| P2-23 | Fix shortcut categories / add dynamic shortcuts | App shortcuts overview | Partial Done | Removed wrong `conversation` categories; dynamic “last viewed stock” Later |
| P2-24 | Add monochrome adaptive icon layer for themed icons | Adaptive icons (Android 13+) | Done | `<monochrome>` on v26/v33 adaptive icons |
| P2-25 | Material3 snackbar for wallet top-up / order result (vs Toast) | Material components in Compose | Done | App `SnackbarHost` shows `productActionMessage` (orders, credit add, SIP/IPO, etc.) |
| P2-26 | Nav bar badge for active price alerts | Material components in Compose (Badges) | Done | Badge on More tab + Price Alerts row when `isActive` count > 0 |
| P2-27 | Segmented button / cleaner tabs for Trade Spot sort or workspace | Material components in Compose | Skip/Later | Trade already uses `ScrollableTabRow` + FilterChips — low incremental value |
| P2-28 | Material SearchBar on Search / watchlist add | Material components in Compose | Later | Custom search works; adopt SearchBar only when next editing Search |
| P2-29 | Compose layout basics adoption | Compose layout basics (Aug 2026) | Done | Already on Column/Row/Box + Lazy lists + Scaffold slots; wide layout via `rememberWindowLayoutInfo` |
| P2-30 | Use BoxWithConstraints for adaptive Trade/Home panes | Compose layout basics | Later | Only if tablet/fold featuring; WindowLayoutInfo already covers content padding |
| P2-31 | Compose modifiers — core usage | Compose modifiers (Aug 2026) | Done | App already chains padding/size/clickable/weight extensively; order/scope patterns in place |
| P2-32 | Accept `modifier:` on shared UI components | Compose modifiers + Styles Do's | Partial Done | `QuoteCard` / `HoldingCard` / `AlertCard` / `TradingQuoteCard` / `InfoChip` take Modifier; expand when editing other rows |
| P2-33 | Hoist reusable Lazy item modifier chains | Compose modifiers (perf) | Later | Watchlist browse / More menu / Search results — extract static chains to cut realloc on scroll |
| P2-34 | FlowRow for chip/filter rows | Flow layouts in Compose (Aug 2026) | Done | Trade sort chips, More quick labs, Search jump chips wrap instead of horizontal scroll-off |
| P2-35 | Wire MaterialTheme typography + shapes | Anatomy / Theming with Styles (Material path) | Done | `ByselTypography` + `ByselShapes` passed into `MaterialTheme` in MainActivity |
| P2-36 | Stable style-layer helpers (ScreenHeader, card colors, PnL) | Styles docs → Material tokens | Done | `ThemeStyles.kt`; applied on Trade/More/Search headers, Home Pulse title, `Cards.kt` |
| P2-37 | Map AppTheme positive → ColorScheme.tertiary | Material theming | Done | `toMaterialColorScheme` sets tertiary/onTertiary from positive; error stays negative |
| P2-38 | Migrate remaining screens off raw `fontSize` / hard-coded corners | Theming consistency | Later | Adopt `MaterialTheme.typography` / `shapes` when editing each screen |
| P2-39 | Experimental Compose `Style` / `styleable` API | Fundamentals / State / Theming with Styles | Later | Await Material Styles support; keep Modifiers + helpers until then |
| P2-40 | Price tick flash + wallet amount animation | State/animations (Material path) | Done | `TickPriceText` / `AnimatedAmountText` on Trade sheet, watchlist, QuoteCard, Home wallet |
| P2-41 | Trade workspace crossfade + fill feedback enter | State/animations | Done | `AnimatedContent` for Spot/Advanced/Derivatives; execution card `AnimatedVisibility` |
| P2-42 | AI typing dots pulse | State/animations | Done | `PulsingDots` replaces static TypingIndicator blobs |
| P2-43 | StyleState / experimental Style `animate {}` | State and animations in Styles | Later | Same as P2-39 — use Compose animation APIs until Material Styles land |
| P2-44 | Apply Styles perf idea (draw-phase updates) without Styles API | Performance benefits with Styles | Done | `TickPriceText` flash via `drawBehind` so ticks redraw without per-frame recomposition |
| P2-45 | Adopt experimental Styles for allocation wins (Compose 1.11+) | Performance benefits with Styles | Later | BOM still on 2024.06; Styles need Material support + BOM bump — revisit then |
| P2-46 | Apply Styles Do's on Material path (visuals vs behavior) | Do's and don'ts with Styles | Done | Theme helpers for visuals; Card `onClick` for behavior; no Style on screens; tokens read at use site |
| P2-47 | Press-depth Buy/Sell CTA (`TradeActionButton`) | Examples with Styles | Done | Scale + translateY on press for Trade sheet / quote Buy-Sell / confirm — Material path, themed colors |
| P2-48 | Fancy hover-shadow / multi-layer Style demo buttons | Examples with Styles | Skip | Decorative desktop-hover chrome; not BYSEL phone trading UX |
| P2-49 | Wait for Material Styles + shape/infinite support | Styles current limitations | Later | Doc: no Material Styles yet; no infinite Style anim; no custom shapes — keep Compose APIs (PulsingDots / TradeActionButton) |
| P2-50 | Marquee long company names + AnnotatedString price/% | Style text | Done | `MarqueeText` / `PriceChangeLine` on Search + watchlist Add sheet; % color no longer paints the whole price |
| P2-51 | HTML `AnnotatedString.fromHtml` for legal / AI | Style text | Later | Legal still asset HTML WebView-style; adopt when editing LegalDocumentDialog |
| P2-52 | Gradient brush / text shadow styling | Style text | Skip | Decorative; hurts trading readability |
| P2-53 | Paragraph LineBreak + body lineHeight in theme | Style paragraph | Done | `ByselTypography` body → `LineBreak.Paragraph`; titles → Heading; AI bubbles + news titles use bodyMedium |
| P2-54 | Auto hyphenation / CJK LineBreak strictness | Style paragraph | Skip | EN trading UI; locale hyphen dictionaries not a product need |
| P2-55 | Digit/decimal filters + number keyboards on trade fields | Configure text fields | Done | Qty digits-only; limit/wallet decimal filter; Number/Decimal IME; Search Characters+Search |
| P2-56 | State-based TextField / rememberTextFieldState | Configure text fields | Later | Needs Material3 ≥1.4 alpha; app on 1.2.1 / BOM 2024.06 — migrate when BOM bumps |
| P2-57 | Rainbow Brush TextField styling | Configure text fields | Skip | Decorative; not for trading inputs |
| P2-58 | SelectionContainer on AI chat bubbles | Enable user interactions (text) | Done | Long-press select/copy on user + AI message body |
| P2-59 | LinkAnnotation for URLs / in-text symbol taps | Enable user interactions (text) | Later | Trade/alert chips already cover intents; add URL links if AI answers start including them |
| P2-60 | Tabular/monospace numeric type for prices & wallet | Work with fonts | Done | `ByselNumericType` / `asNumeric()` on TickPriceText, PriceChangeLine, AnimatedAmountText |
| P2-61 | Downloadable Google Fonts brand typeface | Work with fonts | Later | Needs certs + `ui-text-google-fonts` + fallback; revisit for brand polish after BOM bump |
| P2-62 | Variable font file in APK (Roboto Flex etc.) | Work with fonts | Skip | APK weight + Android O+ only; numeric monospace covers trading readability |
| P2-63 | Modern emoji via Compose Text/TextField | Display emoji | Done | BOM 2024.06 + Material Text/OutlinedTextField — emoji OOTB (incl. API 24–30); StockDetail/AI already use emoji in Text |
| P2-64 | Emoji2 / EmojiTextView for View-system text | Display emoji | Skip | No app TextView UI; legal is WebView; MainActivity is FragmentActivity + Compose |
| P2-65 | Autofill on Auth username/email/password | Autofill in Compose | Done | `byselAutofill` AutofillNode (Compose 1.6) on login/register + forgot-password; CredentialHelper save kept |
| P2-66 | ContentType semantics / LocalAutofillManager.commit | Autofill in Compose | Later | Needs Compose 1.7+ ContentType API; migrate when BOM bumps |
| P2-67 | Exclude auth/biometric prefs from Auto Backup & device transfer | Design for Safety (encrypt data) | Done | `backup_rules.xml` + `data_extraction_rules.xml` exclude `bysel_auth*`, `security_prefs`, crypto keysets |
| P2-68 | Package visibility `<queries>` for Custom Tabs / https / UPI | Design for Safety (minimize data / package visibility) | Done | Manifest queries for VIEW https, UPI, CustomTabsService |
| P2-69 | Play Integrity API for abuse / risky clients | Design for Safety (fraud) | Later | Useful before paid growth / abuse; not blocking paper-trading testers |
| P2-70 | Cert pinning pins (hooks exist, pins often empty) | Design for Safety (secure network) | Later | Fill pins when TLS endpoints stabilize; empty pin set is intentional today |
| P2-71 | Data access auditing APIs | Design for Safety (give users control) | Skip/Later | Debug-oriented; only if auditing SDK permission use becomes a need |
| P2-72 | Self-revoke unused runtime permissions | Design for Safety (permission downgrading) | Skip | Only meaningful runtime permission is notifications; system auto-revoke covers unused apps |
| P2-73 | Release OkHttp logs → NONE (was BASIC) | Security checklist (user data / credentials) | Done | BASIC logged headers incl. Authorization; debug keeps BODY |
| P2-74 | Gate auth-refresh / slow-request logs behind DEBUG | Security checklist (logging) | Done | `AuthTokenRefresher` + `RequestMetadataInterceptor` |
| P2-75 | Cleartext localhost only in debug network security config | Security checklist (networking) | Done | Release base-config cleartext=false; debug source set keeps 10.0.2.2/localhost |
| P2-76 | Harden legal WebView (JS off + asset-only nav) | Security checklist (WebView) | Done | Removed unused SetJavaScriptEnabled suppress; block non-asset URLs |
| P2-77 | Remove unused chart `JavascriptInterface`; block WebView nav | Security checklist (WebView) | Done | Empty `ChartBridge` removed; chart still needs JS for Lightweight Charts |
| P2-78 | Vendor Lightweight Charts JS into APK assets (drop unpkg CDN) | Security checklist (WebView / dynamic code) | Later | Chart still loads script from unpkg over HTTPS; bundling improves integrity |
| P2-79 | secrets-gradle-plugin / env-specific API keys rotation | Security checklist (API keys) | Later | Firebase via google-services; backend keys stay server-side — formalize client secret hygiene when adding Maps/etc. |
| P2-80 | Passkeys via Credential Manager + WebAuthn backend | Passkeys UX / Identity | Later | Needs RP server (create/assert/store credentials). When ready, follow UX moments below — do not ship dead “Create a passkey” UI first |
| P2-80-ux | Passkey adoption UX checklist (post-backend) | User authentication with passkeys | Later | Promote at: signup (default), after password sign-in, password reset, Settings. Settings: list provider + created/last-used + Delete. Copy: “Create a passkey”, lead with benefits, Google passkey icon. Sign-in via CM unified chooser (not separate method buttons) |
| P2-80b | Sign in with Google via Credential Manager | About SIWG / Identity hub | Later | Blocked on prerequisites (see P2-80b-prereq). When unblocked: CM bottom sheet + dedicated SIWG button + backend Google ID token → BYSEL session |
| P2-80b-prereq | SIWG setup checklist | About SIWG (Aug 7, 2026) | Next (manual) | (1) Google Auth Platform / Cloud OAuth web client ID (2) SHA-1/256 in Firebase + `oauth_client` in google-services (today empty) (3) OAuth brand verification for consent screen (4) Backend endpoint to verify Google ID token & issue BYSEL tokens (or Firebase Google provider + exchange) (5) Data safety / privacy copy for Google profile share |
| P2-81 | AccountManager multi-app credential sharing | Security checklist (credentials) | Skip | Single-app session in EncryptedSharedPreferences is correct |
| P2-82 | Notification deny → graceful degrade + soft nudge | Privacy checklist (minimize permissions) | Done | Alerts still save; snackbar hints Settings; deny toast explains in-app alerts still work |
| P2-83 | Respect permanent notification denial (open system settings) | Privacy checklist (permission denials) | Done | After asked + no rationale, open app notification settings instead of ignored re-request |
| P2-84 | In-dialog permission rationale copy for notifications | Privacy checklist (explain why) | Done | Notifications dialog clarifies price-alert-only use + optional banners |
| P2-85 | Android vitals permission-deny rates review | Privacy checklist (minimize permissions) | Watching | Manual Play Console vitals after AAB uptake |
| P2-86 | Data access auditing APIs for SDK permission use | Privacy checklist (handle data safely) | Skip | Same as P2-71 — only POST_NOTIFICATIONS + network; revisit if adding SDKs with dangerous perms |
| P2-87 | App hibernation exemption prompt | Privacy checklist (user-facing privacy) | Skip | Price alerts use WorkManager/FCM when allowed; don’t nag for unused-app exemption |
| P2-88 | Camera/mic capture indicators | Privacy checklist | Skip | No camera/mic features |
| P2-89 | Credential Manager password one-tap polish | Identity hub | Done | `preferImmediatelyAvailableCredentials` on cold start; Activity host; “Use saved password” picker; debug-only CM logs |
| P2-89b | `clearCredentialState` on logout / delete account | About Credential Manager | Done | Clears provider session so next sign-in shows full chooser; saved passwords remain |
| P2-90 | Credential Manager + WebView auth | Identity hub / About CM | Skip | Auth is native Compose, not WebView |
| P2-91 | Become a credential provider / privileged origin calls | Identity hub / About CM | Skip | Consumer app, not a password manager / browser |
| P2-92 | Migrate legacy Google Sign-In / Smart Lock / FIDO2 | Identity hub / About CM | Skip | Never depended on those APIs; already on Credential Manager passwords |
| P2-93 | Restore Credentials (cross-device app restore) | About Credential Manager | Later | Useful with passkeys/SIWG; password managers already sync passwords across devices |
| P2-94 | Digital credentials (mDL / national ID) | About Credential Manager | Skip | Not a BYSEL product surface |
| P2-95 | Credential metadata update APIs | About Credential Manager | Later | Only if display-name / username sync with password managers becomes a need |
| P2-96 | Ship SIWG button before OAuth/backend ready | About SIWG | Skip | Would be a dead control; `google-services.json` has empty `oauth_client`; no `/auth/google` exchange |
| P2-97 | AuthorizationClient (Drive/Calendar/Photos scopes) | About SIWG | Skip | Auth only — BYSEL does not access Google user data APIs |
| P2-98 | Auth copy → “Sign in” / “Create account” (platform wording) | Passkeys UX content style | Done | Primary CTA + mode toggle + guest skip; OTP/password under “Other options” |
| P2-99 | Fake passkey settings / create prompts without WebAuthn | Passkeys UX guide | Skip | Confusing; wait for P2-80 backend |
| P2-100 | Auth form max-width + register value copy + password hint | Authentication & Onboarding | Done | `widthIn(420)`; minimal-info signup blurb; “At least 6 characters” supporting text |
| P2-101 | Assistive OTP / auth error tone | Authentication & Onboarding (UX writing) | Done | Softer Firebase OTP errors focused on next step (retry / password) |
| P2-102 | Full marketing onboarding walkthrough / steppers | Authentication & Onboarding | Skip | Prefer in-context education; signup already short; guest skip is debug-only by design |
| P2-103 | Bulk permission ask at cold start | Authentication & Onboarding | Skip | Notifications already primed in Settings / alert moment |
| P2-104 | Rich first-run tooltips / feature discovery sheets | Authentication & Onboarding | Later | Optional after core flows stabilize; avoid blocking paper-trading entry |
| P2-105 | Live Updates / ProgressStyle for price alerts | Live update notifications | Skip | Alerts are finite events (crossed level), not ongoing rideshare/delivery/nav journeys; no clear in-progress timeline |
| P2-106 | POST_PROMOTED_NOTIFICATIONS + promoted ongoing chip | Live update notifications | Skip | Same — BYSEL should not request Live Update promotion for market ticks or static alerts |
| P2-107 | Alert only on critical changes (not every quote tick) | Live update notifications (alert behavior) | Done | Already: notify only when ABOVE/BELOW threshold triggers; no ETA-style minor updates |
| P2-108 | Notification category + accent color + public lock visibility | Notifications design guide | Done | CATEGORY_STATUS; `notification_accent` tint (not colorized); VISIBILITY_PUBLIC for market levels |
| P2-109 | Concise titles (symbol headline, no app name) | Notifications design guide (style) | Done | Title = symbol; body = crossed above/below; test title without “BYSEL” |
| P2-110 | Reply / messaging / media / call / big-picture templates | Notifications design guide | Skip | Wrong templates for threshold price alerts |
| P2-111 | Inline notification actions that duplicate tap | Notifications design guide | Skip | Tap already deep-links to Alerts; avoid redundant action buttons |
| P2-112 | Promo / rating / re-engagement / holiday notifications | Notifications design guide (when not to use) | Skip | Not used; stay alert-value-only |

### Explicit Skip

| ID | Item | Reason |
|----|------|--------|
| S-1 | Cronet migration | Large rewrite; OkHttp sufficient |
| S-2 | Foreground service for quote refresh | Wrong API; stay async + pause on background |
| S-3 | Aggressive LTE prefetch / TelephonyManager bandwidth logic | Overkill for this app category |
| S-4 | Anonymous chat / Families policy changes | Not in scope |
| S-5 | Full Hilt + multi-module Now in Android rewrite | Large risk vs benefit while AABs work; evolve incrementally |
| S-6 | Immediate DataStore migration of all prefs | No user-facing speed win today; do when touching settings code |
| S-7 | WorkManager for quote/heatmap polling while app open | Wrong API — use coroutines; WorkManager is for persistent/deferred work |
| S-8 | AlarmManager for recurring market sync | Inefficient vs WorkManager; only for exact alarms |
| S-9 | App-held WakeLock / keepScreenOn for quotes or AI | Battery drain; BYSEL should suspend when idle — WorkManager/FCM already wake when needed |
| S-10 | Picture-in-picture (PiP) | For video playback overlays; BYSEL has no video player product surface |
| S-11 | Material catalog wholesale rewrite (FAB/drawer/rail/carousel/date-time pickers) | BYSEL already Material3 on phone; drawer/rail/carousel/FAB not core to paper-trading flows — adopt per-screen when editing |
| S-12 | Custom `Layout` / `layout` modifier for app screens | Custom layouts doc (Aug 2026) — charts use `Canvas`; UI uses Row/Column/FlowRow/Lazy. No product gap that needs hand-rolled measure/place |
| S-13 | Repo-wide “every composable accepts Modifier” retrofit | Compose modifiers — high churn, low user value; adopt when touching a component |
| S-14 | FlowRow grids replacing Lazy grids / quote carousels | Flow layouts — keep LazyRow for long quote/recent lists; FlowRow only for short chip sets |
| S-15 | Custom AlignmentLines for charts / text baselines | Alignment lines doc — price charts already draw axes/labels on Canvas; no parent-layout alignment need |
| S-16 | IntrinsicSize / custom MeasurePolicy intrinsics | Intrinsics doc — no Row+Divider wrap-height bugs; fillMaxHeight uses are intentional bar/chart fills |
| S-17 | onVisibilityChanged / onLayoutRectChanged for impressions or prefetch | Visibility tracking — no video autoplay; no item-impression analytics; lists already load on screen entry |
| S-18 | Parallel non-Material theme CompositionLocals from scratch | Anatomy of a theme — keep extending AppTheme + MaterialTheme (P2-35–37 Done); don’t fork a second theme tree |
| S-19 | Wholesale Compose Styles API (`Style` / `styleable`) migration | Experimental; see P2-39/P2-45 Later — borrow phase-shifting via drawBehind (P2-44) instead |
| S-20 | StyleState / hovered-focused-pressed Style animations | Same experimental stack; Material ripple/indication covers press feedback today |
| S-21 | Replace Modifiers with Styles for all visual props | Styles vs modifiers — coexist; Modifiers stay for behavior; ThemeStyles cover shared look |
| S-22 | Jetsnack-style CompanyTheme built only on experimental Styles | Theming with Styles — await Material Styles; AppTheme + ByselTypography/Shapes is the Material recommendation |
| S-23 | Hover-shadow / multi-layer Style button demos | Examples with Styles — desktop-hover aesthetics; phone Buy/Sell uses P2-47 instead |
| S-24 | Adopt Styles despite current limitations / before Material | Limitations doc — Material not integrated; infinite anim & shapes unsupported; BYSEL already uses recommended workarounds |
| S-25 | Location / background location / approximate location upgrades | Design for Safety — BYSEL has no location features |
| S-26 | Camera / mic / photo picker / scoped storage media flows | Design for Safety — no camera/media product surface; keep using system intents if ever needed |
| S-27 | Companion device / nearby Bluetooth / Wi‑Fi without location | Design for Safety — no nearby-device features |
| S-28 | Privacy Sandbox / advertising privacy APIs | Design for Safety — no ads; AD_ID already removed |
| S-29 | External storage / MODE_WORLD_* / dynamic DexClassLoader | Security checklist — not used; app-private storage only |
| S-30 | SMS as data channel / READ_SMS | Security checklist — use FCM; no SMS data protocol |
| S-31 | Custom dangerous permissions / exported ContentProviders | Security checklist — none declared |
| S-32 | Native NDK / custom crypto protocols | Security checklist — stay on Android SDK + platform TLS / EncryptedSharedPreferences |
| S-33 | Location / coarse / background / companion-device flows | Privacy checklist — BYSEL has no location or nearby-device features |
| S-34 | Advertising ID / SSAID cross-app identity | Privacy checklist — no ads; AD_ID removed; signed-in account is identity |
| S-35 | Scoped storage migration project | Privacy checklist — no shared media/external storage product surface |
| S-36 | Credential provider SDK / privileged CM-on-behalf-of | Identity hub — not a password manager or browser |
| S-37 | Live Updates / ProgressStyle chips for trading | Live update notifications — wrong pattern for threshold price alerts; keep standard notifications |

---

## Doc log (what was reviewed)

| Date | Doc / surface | Outcome |
|------|----------------|---------|
| 2026-08-07 | Play policy announcement (Jul 15, 2026) | Mostly N/A; AI User Data + target API already OK |
| 2026-08-07 | Android developer verification | Registered — Done |
| 2026-08-07 | Store listings performance | CTR good; grow traffic later |
| 2026-08-07 | Play Academy ToS | Optional training |
| 2026-08-07 | Android vitals overview | Watching after AAB uptake |
| 2026-08-07 | App quality pillars (overview) | No blocker |
| 2026-08-07 | Core value guidelines | Marketing accuracy + metrics later |
| 2026-08-07 | Technical quality guidelines | Vitals + optional baseline profile |
| 2026-08-07 | Background tasks overview | Already correct stack |
| 2026-08-07 | Preserving battery (connectivity) | Already aligned |
| 2026-08-07 | Minimize regular updates | Already aligned; Skip Cronet-level rewrites |
| 2026-08-07 | Cronet guide | Skip |
| 2026-08-07 | nowinandroid + appwidgets samples | Optional later polish only |
| 2026-08-07 | App startup analysis and optimization (updated Aug 4, 2026) | Implemented speed pass: pager bounds, priority quotes, reportFullyDrawn, deferred LLM/update check, StrictMode debug; still Later: baseline profile, Macrobenchmark |
| 2026-08-07 | User requested app speed improvements | Shipped code changes above; needs new AAB to reach testers |
| 2026-08-07 | App architecture — Data layer guide | BYSEL already has repository + Room + WorkManager alerts; backlog: split repos, DataStore, stricter offline-first SOT — **Skip** big-bang rewrite |
| 2026-08-07 | Task scheduling (WorkManager) | Already correct: AlertWorker + FCM; Skip WM for live quotes; optional later: constrained periodic cache sync |
| 2026-08-07 | Choose API to keep device awake | No wake locks in app — correct; Skip keepScreenOn/WakeLock for trading |
| 2026-08-07 | Test your Compose layout | Deps already present; expand UI tests later (Home/auth/tabs) — not blocking speed AAB |
| 2026-08-07 | About notifications | Basics OK (channel, permission, icon); Later: multi-channel, deep-link, auth-required actions, grouping |
| 2026-08-07 | Simple data sharing between apps (Sharesheet/Intents) | Optional Later: share AI/stock text out; receive into AI — not required for Play |
| 2026-08-07 | Set up Edge-to-edge | Mostly Done (`enableEdgeToEdge` + safeDrawing); Later: adjustResize/IME + status-bar contrast |
| 2026-08-07 | About cutouts | Covered by `safeDrawingPadding()`; Watching: manual notch/landscape QA |
| 2026-08-07 | About picture-in-picture (PiP) | **Skip** — video-focused; not applicable to paper-trading UI |
| 2026-08-07 | About predictive back | Partial Done (manifest flag); Later: PredictiveBackHandler for detail/back UX |
| 2026-08-07 | App shortcuts overview | Done basics (static shortcuts); Later: category cleanup + optional dynamic shortcuts |
| 2026-08-07 | Adaptive icons | Partial Done (adaptive FG/BG); Later: monochrome layer for themed icons |
| 2026-08-07 | In-app polish pass (UI/notifications/themes/alerts/widget/tests) | Done: IME+adjustResize, notif channels+deep-link, status-bar contrast, shortcut categories, monochrome icon, AI share, theme dialog scroll, widget NIFTY fix, helper/Compose tests — needs new AAB |
| 2026-08-07 | Material components in Compose (catalog) | Mostly already covered (Scaffold, NavBar, sheets, chips, tabs, dialogs, progress). Later: snackbar for wallet/orders, optional alert badge, SearchBar when touching Search. Skip wholesale FAB/drawer/rail/carousel rewrite |
| 2026-08-07 | Material high-value gaps targeted | Done: Scaffold SnackbarHost for actions; More + Price Alerts badges for active alerts. Skip segmented tabs rewrite; SearchBar stays Later |
| 2026-08-07 | Compose layout basics (updated Aug 7, 2026) | Already aligned (Row/Column/Box, Lazy lists, Scaffold, nested layouts OK). Later only: BoxWithConstraints if tablet/fold panes become a goal. Skip custom Layout rewrite |
| 2026-08-07 | Compose modifiers (updated Aug 7, 2026) | Core usage Done. Later when editing lists/cards: Modifier params + hoisted reusable item chains. Skip repo-wide Modifier retrofit |
| 2026-08-07 | Flow layouts in Compose | Done high-value: FlowRow for Trade sort / More labs / Search jumps. Skip FlowRow for quote carousels (stay LazyRow) |
| 2026-08-07 | Custom layouts in Compose | Skip — no BYSEL screen needs custom measure/place; charts already use Canvas; keep standard layouts |
| 2026-08-07 | Alignment lines in Jetpack Compose | Skip — FirstBaseline/custom chart lines only matter for custom Layout parents; not a BYSEL product gap |
| 2026-08-07 | Intrinsic measurements in Compose layouts | Skip — no IntrinsicSize usage/need; existing fillMaxHeight is for charts/bars, not divider-height bugs |
| 2026-08-07 | Visibility tracking in Compose | Skip — no video / impression / visibility-gated prefetch product need; revisit if Firebase item impressions are added |
| 2026-08-07 | Anatomy of a theme in Compose | Skip — educational custom-theme architecture; BYSEL already uses MaterialTheme + AppTheme schemes |
| 2026-08-07 | Fundamentals of Styles | Skip — experimental Style/styleable API; no product gap vs existing Modifier + Material theming |
| 2026-08-07 | State and animations in Styles | Skip — StyleState interaction styling; stick with Material indication until Styles stabilize |
| 2026-08-07 | Styles versus modifiers | Skip — comparison guidance; no migration until Styles stabilize and Material components expose Style params |
| 2026-08-07 | Theming with Styles | Implemented Material path (P2-35–37): typography/shapes/style helpers; experimental Styles → P2-39 Later |
| 2026-08-07 | Anatomy / Fundamentals / State / Styles vs modifiers | Same pass — stable Material theming improved; experimental Style API deferred |
| 2026-08-07 | State and animations (user value) | Done P2-40–42 — tick flash, wallet count-up, workspace crossfade, AI pulsing dots; Style animate{} → P2-43 Later |
| 2026-08-07 | Performance benefits with Styles | Borrowed draw-phase idea (P2-44 Done); full Styles migration Later (P2-45) — BOM/Material not ready |
| 2026-08-07 | Do's and don'ts with Styles | Applied Material-path Do's (P2-46): modifier on cards, visuals from theme, clicks as behavior; experimental Style params Still Later |
| 2026-08-07 | Examples with Styles | Press-depth TradeActionButton Done (P2-47); decorative hover/shadow Style demos Skip (S-23 / P2-48) |
| 2026-08-07 | Styles current limitations | Confirms deferral (P2-49/S-24): Material pending; infinite → rememberInfiniteTransition (PulsingDots); press via TradeActionButton |
| 2026-08-07 | Style text | Done P2-50 marquee + AnnotatedString price/%; HTML Later; gradient/shadow Skip |
| 2026-08-07 | Style paragraph | Done P2-53 theme LineBreak/lineHeight; AI + news body readable wraps; hyphen/CJK Skip |
| 2026-08-07 | Configure text fields | Done P2-55 value-based filters/keyboards; state-based TextField Later (P2-56); gradient Skip |
| 2026-08-07 | Enable user interactions (text) | Done P2-58 AI SelectionContainer; LinkAnnotation Later (P2-59) |
| 2026-08-07 | Work with fonts | Done P2-60 numeric monospace+tnum; Google Fonts Later; variable font Skip |
| 2026-08-07 | Display emoji | Already covered (P2-63) — Compose Text emoji support; no Emoji2 needed without TextViews |
| 2026-08-07 | Autofill in Compose | Done P2-65 Auth AutofillNode bridge; ContentType/commit Later (P2-66) on BOM bump |
| 2026-08-08 | Design for Safety (privacy + security hub) | Already strong (min perms, HTTPS, encrypted auth, biometrics). Done: backup exclude auth prefs (P2-67), package `<queries>` (P2-68). Later: Play Integrity, cert pins. Skip: location/camera/media/nearby/ads APIs |
| 2026-08-08 | Security checklist (updated Aug 4, 2026) | Done: release HTTP logs NONE, debug-only cleartext, auth log gating, WebView harden (P2-73–77). Later: vendor chart JS, passkeys, API-key plugin. Skip: SMS/NDK/external storage/AccountManager |
| 2026-08-08 | Privacy checklist (updated Mar 6, 2026) | Already strong (no location/ads IDs, min perms, package queries, backup exclude). Done: alert notify degrade/nudge + permanent-deny → Settings (P2-82–84). Watching: vitals deny rates. Manual: Data safety form (P0-2). Skip: hibernation nag, data-access audit, cam/mic |
| 2026-08-08 | Identity / Credential Manager hub | Already on CM passwords + autofill + biometrics + Firebase phone OTP. Done: one-tap polish (P2-89). Later: passkeys (P2-80), Sign in with Google (P2-80b). Skip: legacy Sign-In/Smart Lock/FIDO2 migration, WebView CM, credential-provider role |
| 2026-08-08 | About Credential Manager (updated May 13, 2026) | Confirms CM as recommended API. Done: clearCredentialState on logout (P2-89b). Later: Restore Credentials, passkeys/SIWG, metadata updates. Skip: digital credentials, WebView CM, being a credential provider |
| 2026-08-08 | About Sign in with Google (updated Aug 7, 2026) | UX requires CM bottom sheet + dedicated SIWG button. **Not implementing yet** — empty OAuth clients, no backend Google token exchange, brand verification pending. Checklist → P2-80b-prereq (Next/manual). Skip AuthorizationClient / dead SIWG button |
| 2026-08-08 | User authentication with passkeys (UX, Oct 2024) | Design guide for adoption moments + copy. **No passkey feature yet** (needs WebAuthn). Done: Sign in wording polish (P2-98). Later: P2-80 + P2-80-ux. Skip: fake Create-a-passkey / manage-passkeys UI |
| 2026-08-08 | Authentication & Onboarding (updated May 19, 2026) | Already: min fields, Forgot password, CM/autofill/biometrics, contextual notifs. Done: form max-width, password requirements, assistive OTP copy (P2-100–101). Skip: forced walkthrough / bulk perms. Later: optional feature-discovery tooltips |
| 2026-08-08 | Live update notifications (updated Mar 2, 2026) | **Skip** Live Updates/ProgressStyle for BYSEL. Price alerts are event banners (already critical-only on threshold). Rideshare/delivery templates N/A. Confirmed P2-107 Done |
| 2026-08-08 | Notifications (design guide, updated Mar 2, 2026) | Already: channels, contextual permission, BigText, deep-link. Done: grouping summary (P2-16), category/color/visibility (P2-108), concise titles (P2-109). Skip: wrong templates, promo notifs, duplicate actions |
| 2026-08-15 | Android 15 edge-to-edge + FCM token + Data safety + CI tests | Material3 transparent system bars (no e2e opt-out); FCM token POST + honest 15-min alert copy; privacy pages aligned; `testDebugUnitTest` no longer `continue-on-error` |

---

## Play Console Data safety checklist (paste into Console)

Matches in-app / website / backend privacy as of **15 August 2026**. Do not invent extra trackers. Fill Play Console manually.

**Does your app collect or share any of the required user data types?** Yes

**Advertising ID:** No. Manifest removes `AD_ID`; `google_analytics_adid_collection_enabled=false`.

| Data type | Collected? | Shared? | Required? | Purpose | Notes |
|-----------|------------|---------|-----------|---------|-------|
| Name | Optional | No (app functionality only) | No | App functionality | Optional display name |
| Email address | Yes | No | Yes for password accounts | Account management | Also used for password reset |
| Phone number | Yes | Yes — Firebase Auth / SMS OTP | No (OTP path) | Account management | Firebase phone OTP |
| User IDs | Yes | No | Yes | Account management | BYSEL account id / session |
| Other financial info | Yes | No | Yes for paper trading | App functionality | Simulated wallet, holdings, paper trades — **not** live brokerage or bank credentials |
| Photos / video / audio / files / calendar / contacts / location | No | — | — | — | Not used |
| App interactions | Yes | Yes — Firebase Analytics (Google) | No | Analytics | SDK present; AD_ID collection off; no ad SDK |
| Other user-generated content | Yes | Yes — AI providers when you use AI | No | App functionality | Stock notes (server + device); AI chat prompts/answers |
| Crash logs | No | — | — | — | No Crashlytics. Play vitals are collected by Google Play, not by BYSEL code |
| Device or other IDs | Yes | Yes — Firebase Cloud Messaging | No | App functionality | FCM token for price-alert push. No Advertising ID |
| Approximate / precise location | No | — | — | — | No location permission |

**Third-party sharing (ephemeral / service providers, not sold):**

- Firebase Authentication + SMS OTP — phone number
- Firebase Cloud Messaging — device token for alerts
- Firebase Analytics — basic usage events (not ads)
- Groq / Gemini / Indian Stock LLM — AI prompts you send
- Cloud host (API) — account + simulation data
- Market data sources — public quotes only (not personal account data)

**Data handling answers:**

- Encrypted in transit: Yes (HTTPS)
- Encrypted at rest: Yes for auth session on device (EncryptedSharedPreferences); server uses standard cloud storage
- Users can request deletion: Yes (in-app delete account + support email)
- Data used to track users across apps/sites for ads: **No**
- Data used for advertising / remarketing: **No**
- Kids: App is 18+; not designed for children

---

## How this file gets updated

When more Google developer docs are pasted in chat:

1. Classify: **Done / Next / Later / Watching / Skip**
2. Add a row under Active backlog or Skip
3. Append a line under **Doc log**
4. Bump **Last updated**
