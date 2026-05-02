# UI Redesign Plan — Modern Design System

This plan implements a modern, IFTTT-inspired design system for the OTP Forwarder Android app. It is structured as **7 self-contained phases** that can each be executed in a fresh Claude Code session (clean context window), driven by a script analogous to `run-maps-phases.sh`.

---

## How to execute

After this plan is approved:

1. Move this file to `/Users/sudhanva/Documents/otp-forwarder/UI_REDESIGN_PLAN.md`.
2. Create `run-ui-phases.sh` (template at the bottom of this file) and `UI_PROGRESS.md` containing `CURRENT_PHASE=0`.
3. Run `bash run-ui-phases.sh` — each iteration reads this file, implements one phase end-to-end, builds, tests, commits, and bumps `UI_PROGRESS.md`.

Each phase below is **standalone**: a fresh Claude session armed with this file + CLAUDE.md has everything needed to complete that phase. No cross-phase references that require prior conversation memory.

---

## Global rules (every phase must follow)

- **Stack**: Kotlin, Jetpack Compose, Material3, min SDK 26, target SDK 35. Compose BOM 2024.12.01 already in place.
- **No new dependencies**. Do not edit `gradle/libs.versions.toml` or `app/build.gradle.kts`.
- **No ViewModel / domain / data changes**. This is a pure UI redesign. All `*ViewModel.kt`, `domain/`, `data/`, `di/`, `receiver/`, `service/`, `worker/`, `util/` are untouched.
- **No font additions**. Use the system FontFamily (Roboto on Android). Only adjust weights/sizes via the Material3 type scale.
- **Build discipline**: at the end of each phase, run `./gradlew assembleDebug` and `./gradlew test` — both must pass before committing.
- **One commit per phase**, using the exact commit message specified in the phase.
- **After commit**: overwrite `UI_PROGRESS.md` so its first line is `CURRENT_PHASE=N` where N is the phase just completed.
- **Branch**: stay on whatever branch the script enforces. Never switch branches.
- **Stay in scope**: a phase implements only the files listed in its Create/Modify sections. No drive-by edits, no refactors of unrelated code, no new abstractions.
- **Preserve all existing behavior**: this is a visual overhaul. ViewModels, navigation routes, click handlers, validation logic — all stay exactly as they are. Only the composable rendering changes.

---

## Frozen design decisions

The user has approved the following. Do not deviate.

- **Brand palette**: cobalt blue `#2563EB` primary, slate neutrals (full spec in §1).
- **Typography**: system FontFamily, Material3 type-scale overrides with bolder weights (full spec in §2).
- **Material You / dynamic color**: disabled by default (param kept on `OtpForwarderTheme` for opt-in).
- **Shapes**: 8/12/16/20/28 dp scale (full spec in §3).
- **Scope**: full overhaul of all 6 screens.
- **EditRule IF/THEN**: differentiated — blue IF block, charcoal (Ink800) THEN block.
- **Bottom nav**: keep 4 tabs; restyle, do not restructure.

---

## §1. Color tokens (referenced by every phase)

Raw palette (used in `Color.kt`):

```
// Brand
BrandBlue50   = #EFF6FF   // tint / banners / primary container
BrandBlue400  = #60A5FA   // dark-mode primary (lifted for AA contrast)
BrandBlue600  = #2563EB   // primary
BrandBlue700  = #1D4FD8   // pressed
BrandBlue800  = #1E40AF   // on-tint text

// Ink (slate)
Ink900 = #0F172A   // primary text
Ink800 = #1E293B   // strong text / THEN block bar
Ink500 = #64748B   // muted text / onSurfaceVariant
Ink300 = #CBD5E1   // disabled
Ink200 = #E2E8F0   // divider / outline

// Surfaces
Surface0 = #FFFFFF        // background, surface
Surface1 = #F8FAFC        // surfaceVariant
Surface2 = #F1F5F9        // tint / hover

// Semantic status
Success = #16A34A
Warning = #F59E0B
Danger  = #DC2626

// Dark mode
DarkBg       = #0B1220
DarkSurface  = #111827
DarkSurface2 = #1F2937
DarkOutline  = #334155
DarkText     = #ECEEF1
DarkTextDim  = #94A3B8
```

Material3 mapping:

`lightColorScheme(...)`:
- primary = BrandBlue600, onPrimary = #FFFFFF
- primaryContainer = BrandBlue50, onPrimaryContainer = BrandBlue800
- secondary = Ink800, onSecondary = #FFFFFF
- secondaryContainer = Surface2, onSecondaryContainer = Ink900
- tertiary = Warning, onTertiary = #1C1004
- tertiaryContainer = #FEF3C7, onTertiaryContainer = #5A3A00
- background = Surface0, onBackground = Ink900
- surface = Surface0, onSurface = Ink900
- surfaceVariant = Surface1, onSurfaceVariant = Ink500
- outline = Ink200, outlineVariant = #F0F2F5
- error = Danger, onError = #FFFFFF
- errorContainer = #FEE2E2, onErrorContainer = #7A1014

`darkColorScheme(...)`:
- primary = BrandBlue400, onPrimary = #0B1A47
- primaryContainer = #1E3A8A, onPrimaryContainer = #DBEAFE
- secondary = #CBD5E1, secondaryContainer = DarkSurface2
- tertiary = #FCD34D, tertiaryContainer = #4A2F00
- background = DarkBg, surface = DarkSurface, surfaceVariant = DarkSurface2
- onSurface = DarkText, onSurfaceVariant = DarkTextDim
- outline = DarkOutline, outlineVariant = #2A2F38
- error = #FF6B6F, errorContainer = #4A0E10

Semantic extras (in `BrandColors.kt`, exposed via `MaterialTheme.brand` extension):
- `success` = Success, `warning` = Warning, `info` = BrandBlue600
- `ifBlock` = BrandBlue600, `thenBlock` = Ink800
- Service-icon palette (used by `ServiceIconCircle`):
  - `iconBlue` = #2563EB
  - `iconViolet` = #7C3AED
  - `iconGreen` = #16A34A
  - `iconRed` = #DC2626
  - `iconAmber` = #F59E0B
  - `iconCyan` = #0EA5E9

---

## §2. Typography (referenced by every phase)

System FontFamily (FontFamily.Default). Override the Material3 type scale in `Type.kt`:

| Style | Size | Line height | Weight | Letter spacing | Use |
|---|---|---|---|---|---|
| displayLarge | 48sp | 56sp | ExtraBold | -1sp | (reserved) |
| displayMedium | 36sp | 44sp | ExtraBold | -0.5sp | hero in onboarding |
| displaySmall | 28sp | 36sp | Bold | 0 | secondary heroes |
| headlineLarge | 24sp | 32sp | Bold | -0.25sp | screen titles in TopAppBar |
| headlineMedium | 22sp | 28sp | Bold | 0 | IfThenBlock "IF"/"THEN" hero label |
| headlineSmall | 18sp | 24sp | SemiBold | 0 | section titles, EmptyState title |
| titleLarge | 18sp | 24sp | SemiBold | 0 | (reserved) |
| titleMedium | 16sp | 22sp | SemiBold | 0.15sp | card titles |
| titleSmall | 14sp | 20sp | SemiBold | 0.1sp | dense card titles |
| bodyLarge | 16sp | 24sp | Normal | 0.5sp | primary body |
| bodyMedium | 14sp | 20sp | Normal | 0.25sp | secondary body |
| bodySmall | 13sp | 18sp | Normal | 0.4sp | captions |
| labelLarge | 14sp | 20sp | SemiBold | 0.1sp | buttons, action chips |
| labelMedium | 12sp | 16sp | Medium | 0.5sp | nav labels, dense labels |
| labelSmall | 11sp | 16sp | Medium | 0.5sp | ALL-CAPS eyebrows ("TODAY", "IF") |

---

## §3. Shapes & Spacing (referenced by every phase)

`Shape.kt`:
```
extraSmall = RoundedCornerShape(8.dp)
small      = RoundedCornerShape(12.dp)
medium     = RoundedCornerShape(16.dp)
large      = RoundedCornerShape(20.dp)
extraLarge = RoundedCornerShape(28.dp)
```

`Spacing.kt`:
```
object Spacing {
  val xxs=2.dp; val xs=4.dp; val sm=8.dp; val md=12.dp
  val lg=16.dp; val xl=20.dp; val xxl=24.dp; val xxxl=32.dp
  val gutterScreen=16.dp; val cardInner=16.dp; val sectionGap=24.dp
  val rowMin=56.dp; val iconCircle=40.dp; val iconCircleLg=56.dp
}
object Elevation { val card=0.dp; val cardElevated=2.dp; val nav=8.dp }
```

---

# Phase 1 — Theme tokens

**Goal**: Establish the design tokens. App still builds and runs; visually re-tints to blue, otherwise unchanged.

**Files to modify**:
- `app/src/main/java/com/otpforwarder/ui/theme/Color.kt` — replace contents with the raw palette in §1 (use `Color(0xFF...)` literals).
- `app/src/main/java/com/otpforwarder/ui/theme/Theme.kt` — replace `LightColorScheme` and `DarkColorScheme` with the Material3 mapping in §1; default `dynamicColor = false` (keep param so callers can opt back in); wire `shapes = AppShapes` and `typography = AppTypography`; provide `LocalBrandColors` via `CompositionLocalProvider`.
- `app/src/main/java/com/otpforwarder/ui/theme/Type.kt` — replace `val Typography = Typography()` with the full override per §2. Name the val `AppTypography`.

**Files to create**:
- `app/src/main/java/com/otpforwarder/ui/theme/Shape.kt` — define `AppShapes = Shapes(extraSmall=…, small=…, medium=…, large=…, extraLarge=…)` per §3.
- `app/src/main/java/com/otpforwarder/ui/theme/Spacing.kt` — define `object Spacing` and `object Elevation` per §3.
- `app/src/main/java/com/otpforwarder/ui/theme/BrandColors.kt` — `data class BrandColors(...)` with all semantic extras from §1; `val LocalBrandColors = staticCompositionLocalOf { lightBrandColors }`; provide `lightBrandColors` and `darkBrandColors` instances; expose `MaterialTheme.brand: BrandColors` extension that reads `LocalBrandColors.current`.

**Verification**:
- `./gradlew assembleDebug` succeeds
- `./gradlew test` succeeds
- App installs and renders — every screen now reads with blue primary (no other layout changes expected this phase)

**Commit message**:
```
feat(ui): introduce design tokens (colors, typography, shapes, spacing, brand colors)
```

**After commit**: overwrite `UI_PROGRESS.md` to `CURRENT_PHASE=1`.

---

# Phase 2 — Foundation components

**Goal**: Add the bedrock of the component library — wrappers for the most common Material3 primitives. No screen migrations yet; existing screens continue to use raw Material3 components.

**Directory to create**: `app/src/main/java/com/otpforwarder/ui/component/`

**Files to create** (each ~30–80 lines, with a `@Preview` at the bottom):

1. **`AppCard.kt`** — wraps `Card`. Signature:
   `fun AppCard(modifier: Modifier = Modifier, tone: CardTone = CardTone.Default, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`
   - `enum class CardTone { Default, Tinted, Brand, Danger }`
   - Default: `containerColor = surfaceVariant`, 1dp border `outline`, 0dp elevation
   - Tinted: `containerColor = Surface2`
   - Brand: `containerColor = primaryContainer`, border `primary`
   - Danger: `containerColor = errorContainer`, border `error`
   - Shape: `MaterialTheme.shapes.medium` (16dp)

2. **`AppButton.kt`** — single entry point covering the 4 variants. Signature:
   `fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, variant: ButtonVariant = ButtonVariant.Primary, leadingIcon: ImageVector? = null, enabled: Boolean = true, fullWidth: Boolean = false, danger: Boolean = false)`
   - `enum class ButtonVariant { Primary, Tonal, Outline, Text }`
   - Primary → `Button` with `primary`/`onPrimary`
   - Tonal → `FilledTonalButton` with `secondaryContainer`
   - Outline → `OutlinedButton` with 1.5dp `primary` stroke
   - Text → `TextButton`
   - All: shape = `MaterialTheme.shapes.small` (12dp), height = 48dp, label = `labelLarge`
   - `danger=true` swaps primary color → `error`

3. **`AppTopBar.kt`** — wraps `TopAppBar`. Signature:
   `fun AppTopBar(title: String, modifier: Modifier = Modifier, navigationIcon: @Composable (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})`
   - Transparent container, 0 elevation
   - Title text uses `headlineSmall` Bold

4. **`BrandSwitch.kt`** — wraps `Switch`. Signature:
   `fun BrandSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, showLabel: Boolean = false)`
   - When `showLabel=true`, render "ON"/"OFF" `labelMedium` SemiBold to the left of the switch
   - Custom `SwitchDefaults.colors`: checked thumb = `onPrimary`, checked track = `primary`, unchecked thumb = `Surface0`, unchecked track = `Ink300`

5. **`BrandFab.kt`** — wraps FAB. Signature:
   `fun BrandFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, extended: Boolean = true)`
   - Extended: `ExtendedFloatingActionButton` with text + leading icon, `containerColor = primary`, `contentColor = onPrimary`
   - Compact: `FloatingActionButton` (icon only)

6. **`AppNavBar.kt`** — wraps `NavigationBar`. Signature:
   `fun AppNavBar(content: @Composable RowScope.() -> Unit)`
   - `containerColor = surface`, hairline 1dp top divider in `outline`
   - 0 elevation
   - Provide a sibling `AppNavBarItem(selected, onClick, icon, label)` that renders `NavigationBarItem` with selected indicator color = `primaryContainer`, icon tint when selected = `primary`, unselected = `Ink500`, label = `labelMedium`

7. **`KeyValueRow.kt`** — Signature:
   `fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null)`
   - 56dp min height, label `bodyMedium` Ink900, value `bodyMedium` Ink500, optional trailing slot right-aligned

**Verification**:
- `./gradlew assembleDebug` succeeds
- `./gradlew test` succeeds
- App installs and behaves identically to Phase 1 (these components are unused by screens yet)

**Commit message**:
```
feat(ui): add foundation component wrappers (AppCard, AppButton, AppTopBar, BrandSwitch, BrandFab, AppNavBar, KeyValueRow)
```

**After commit**: overwrite `UI_PROGRESS.md` to `CURRENT_PHASE=2`.

---

# Phase 3 — Pattern components

**Goal**: Add reusable patterns built on top of the Phase 2 foundation. Still no screen migrations.

**Files to create** under `app/src/main/java/com/otpforwarder/ui/component/`:

1. **`SectionHeader.kt`**:
   `fun SectionHeader(title: String, modifier: Modifier = Modifier, eyebrow: String? = null, subtitle: String? = null)`
   - Eyebrow: `labelSmall` ALL-CAPS Ink500, letterSpacing 0.5sp, 4dp bottom padding
   - Title: `headlineSmall` SemiBold Ink900
   - Subtitle: `bodyMedium` Ink500, 4dp top padding

2. **`EmptyState.kt`**:
   `fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null)`
   - Centered Column, 64dp icon tinted `Ink300`, 16dp gap, headlineSmall title Ink900, 8dp gap, bodyMedium subtitle Ink500 centered, 24dp gap, optional action slot

3. **`StatusPill.kt`**:
   ```
   enum class PillTone { Success, Warning, Danger, Info, Neutral }
   fun StatusPill(label: String, tone: PillTone, modifier: Modifier = Modifier, leadingDot: Boolean = true)
   ```
   - Pill: `extraSmall` (8dp) shape, padding 8dp horizontal × 4dp vertical
   - Background: tone color at 14% alpha (use `Color.copy(alpha=0.14f)`)
   - Foreground (text + dot): tone color
   - Tone → color: Success→Success, Warning→Warning, Danger→Danger, Info→primary, Neutral→Ink500
   - Label: `labelMedium` SemiBold
   - Leading dot: 8dp circle, 6dp right padding

4. **`PermissionBanner.kt`**:
   `fun PermissionBanner(message: String, onGrant: () -> Unit, modifier: Modifier = Modifier)`
   - Wraps `AppCard(tone = CardTone.Tinted)` (use `tertiaryContainer` background for warning feel — pass via direct override since Tinted is Surface2)
   - Actually: build internally as `Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium)` with 1dp border `Warning.copy(alpha=0.3f)`
   - Row: 24dp warning icon + Column { titleSmall "Permission required", bodyMedium message } + `AppButton(text="Grant", variant=Tonal, onClick=onGrant)`

5. **`ServiceIconCircle.kt`**:
   `fun ServiceIconCircle(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp)`
   - Box with `Modifier.size(size).clip(CircleShape).background(tint)`
   - Centered `Icon(icon, contentDescription=null, tint = Color.White, modifier = Modifier.size(size * 0.55f))`

6. **`AppDialog.kt`**:
   `fun AppDialog(onDismiss: () -> Unit, title: String, confirmLabel: String, onConfirm: () -> Unit, modifier: Modifier = Modifier, dismissLabel: String = "Cancel", danger: Boolean = false, content: @Composable () -> Unit)`
   - `AlertDialog` with `shape = MaterialTheme.shapes.extraLarge`
   - title: `headlineSmall`
   - confirm button: `AppButton(text=confirmLabel, variant=Primary, danger=danger, onClick=onConfirm)`
   - dismiss button: `AppButton(text=dismissLabel, variant=Text, onClick=onDismiss)`

7. **`AppBottomSheet.kt`**:
   `fun AppBottomSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit)`
   - `ModalBottomSheet` with `shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))`
   - Standard drag handle
   - 16dp horizontal + 8dp top + 24dp bottom padding
   - If title provided: `headlineSmall` at top, then 16dp gap, then content slot

**Verification**:
- `./gradlew assembleDebug` succeeds
- `./gradlew test` succeeds
- App still behaves identically to Phase 2

**Commit message**:
```
feat(ui): add pattern components (SectionHeader, EmptyState, StatusPill, PermissionBanner, ServiceIconCircle, AppDialog, AppBottomSheet)
```

**After commit**: overwrite `UI_PROGRESS.md` to `CURRENT_PHASE=3`.

---

# Phase 4 — Domain-specific components

**Goal**: Add the four components that give the EditRule screen its applet-builder feel. Still no screen migrations.

**Files to create** under `app/src/main/java/com/otpforwarder/ui/component/`:

1. **`IfThenBlock.kt`**:
   ```
   enum class BlockKind { If, Then }
   fun IfThenBlock(kind: BlockKind, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit)
   ```
   - Wraps a `Surface` with `shape = MaterialTheme.shapes.large` (20dp), `color = surface`, 1dp border `outline`
   - Internal layout: Row with a 6dp wide vertical bar on the left (color = `MaterialTheme.brand.ifBlock` for If, `thenBlock` for Then), then Column with:
     - 16dp inner padding
     - `headlineMedium` "IF" or "THEN" in bar color, ExtraBold
     - Optional `bodyMedium` subtitle Ink500
     - 12dp gap
     - Content slot

2. **`ConditionItem.kt`** — replaces inline `ConditionRow` in EditRuleScreen:
   ```
   fun ConditionItem(
     iconCircle: @Composable () -> Unit,
     title: String,
     onRemove: () -> Unit,
     modifier: Modifier = Modifier,
     content: @Composable ColumnScope.() -> Unit,
   )
   ```
   - `AppCard(tone = CardTone.Tinted)`
   - Row: `iconCircle` slot (call site passes `ServiceIconCircle`) + Column { titleMedium title, content slot } + remove `IconButton(Icons.Outlined.Close)`
   - 16dp inner padding, 12dp gap between leading icon and content

3. **`ActionItem.kt`** — same shape as `ConditionItem` for visual consistency:
   ```
   fun ActionItem(
     iconCircle: @Composable () -> Unit,
     title: String,
     onRemove: () -> Unit,
     modifier: Modifier = Modifier,
     content: @Composable ColumnScope.() -> Unit,
   )
   ```
   - Same internals as `ConditionItem`. (Duplication intentional — call sites are semantically distinct and may diverge later.)

4. **`ConnectorPill.kt`** — replaces inline `ConnectorSelector`:
   ```
   enum class Connector { And, Or }
   fun ConnectorPill(value: Connector, onToggle: () -> Unit, modifier: Modifier = Modifier)
   ```
   - **Important**: do NOT depend on the domain `Connector` enum. This component uses its own UI-only enum. The screen migration in Phase 6 will adapt between domain and UI types.
   - Layout: centered Column, 24dp tall × 1dp wide vertical line in `outline`, then a clickable pill (`extraSmall` shape, `secondaryContainer` background, 1dp `outline` border, 12dp horizontal × 6dp vertical padding) with `labelLarge` text "AND"/"OR", then another 24dp vertical line below
   - Tap toggles between AND ↔ OR

**Verification**:
- `./gradlew assembleDebug` succeeds
- `./gradlew test` succeeds

**Commit message**:
```
feat(ui): add applet-builder components (IfThenBlock, ConditionItem, ActionItem, ConnectorPill)
```

**After commit**: overwrite `UI_PROGRESS.md` to `CURRENT_PHASE=4`.

---

# Phase 5 — Migrate Home, Rules, and bottom navigation

**Goal**: Apply the new component library to the three highest-traffic surfaces. Behavior unchanged; visuals fully redesigned.

**Files to modify**:

### 5.1 `app/src/main/java/com/otpforwarder/ui/OtpForwarderApp.kt`
- Replace inline `NavigationBar { … NavigationBarItem(...) … }` block with `AppNavBar { Screen.entries.forEach { AppNavBarItem(...) } }`.
- Bottom-bar visibility logic (hidden on onboarding) stays exactly as is.

### 5.2 `app/src/main/java/com/otpforwarder/ui/screen/home/HomeScreen.kt`
- TopAppBar → `AppTopBar(title = "OTP Forwarder", actions = { BrandSwitch(checked = state.masterEnabled, onCheckedChange = vm::setMasterEnabled, showLabel = true) })`
- Day-bucket headers (`Today`, `Yesterday`, `Earlier`) → `SectionHeader(eyebrow = "TODAY"/…, title = null)` — actually use a simpler signature: pass eyebrow only, no title. Adjust `SectionHeader` if needed to allow title-less variant, OR just render a `Text` with `labelSmall` ALL-CAPS Ink500 directly here.
- `OtpLogCard` → rebuild with `AppCard(tone = CardTone.Default)`:
  - Row: `ServiceIconCircle(icon = Icons.Outlined.Sms, tint = brand.iconBlue)` for normal forwards; tint = `brand.warning` for partial; tint = `brand.danger` for failed (still tap-to-retry)
  - Column { Row { titleMedium sender, Spacer.weight, labelMedium time Ink500 }, bodyLarge code, bodyMedium summary Ink500 }
  - Footer: `StatusPill(label, tone)` — Sent → Success, Partial → Warning, Failed → Danger ("Failed · tap to retry"), Skipped → Neutral
- Empty state → `EmptyState(icon = Icons.Outlined.Inbox, title = "All clear", subtitle = "Forwarded OTPs will show up here")`
- Snackbar/retry behavior unchanged

### 5.3 `app/src/main/java/com/otpforwarder/ui/screen/rules/RulesScreen.kt`
- TopAppBar → `AppTopBar(title = "Forwarding Rules")`
- FAB → `BrandFab(text = "New rule", icon = Icons.Default.Add, onClick = …, extended = true)`
- `RuleCard` → rebuild as a mini-applet `AppCard(tone = CardTone.Default, onClick = { navigate to edit })`:
  - Top row: titleMedium rule name (weight 1), `BrandSwitch(checked = enabled, onCheckedChange = …)`
  - 12dp gap
  - Inner block: Surface with `Shapes.small` and `Surface2` background, 12dp padding, containing:
    - Row { labelSmall "IF" Ink500, 8dp gap, bodyMedium condition summary Ink900 (single line, ellipsize) }
    - 4dp gap, 1dp divider `outline`, 4dp gap
    - Row { labelSmall "THEN" Ink500, 8dp gap, bodyMedium action summary Ink900 (single line, ellipsize) }
  - 8dp gap
  - Bottom row: Spacer.weight, `StatusPill(label = "PRIORITY $n", tone = Neutral)`
  - Disabled: render whole card at 60% alpha
- Empty → `EmptyState(icon = Icons.Outlined.Bolt, title = "No rules yet", subtitle = "Create a rule to start forwarding OTPs", action = { AppButton(text = "Create your first rule", variant = Primary, onClick = …) })`

**Notes**:
- `Icons.Outlined.Bolt` requires `androidx.compose.material:material-icons-extended` — already on classpath.
- The condition/action *summary text* should use the same string-derivation logic the existing screen uses; do not change that logic, just re-render it.

**Verification**:
- `./gradlew assembleDebug` succeeds
- `./gradlew test` succeeds
- Manual on device:
  - Home: master switch toggles, log items render with status pills and icon circles, retry-on-failed-tap works, empty state appears when no logs
  - Rules: list renders mini-applet cards, switches toggle, FAB opens edit, empty state CTA opens add
  - Bottom nav: 4 tabs, blue selected indicator, hairline divider visible

**Commit message**:
```
feat(ui): redesign Home, Rules, and bottom navigation with new design system
```

**After commit**: overwrite `UI_PROGRESS.md` to `CURRENT_PHASE=5`.

---

# Phase 6 — Migrate EditRuleScreen (the IFTTT moment)

**Goal**: Rebuild the rule editor as an applet-builder. This is the largest screen (~1000 lines) and the most visible payoff. Behavior, validation, and ViewModel calls all unchanged.

**File to modify**: `app/src/main/java/com/otpforwarder/ui/screen/rules/EditRuleScreen.kt`

### 6.1 Top-level layout
- TopAppBar → `AppTopBar(title = if (isNew) "New Rule" else "Edit Rule", navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, …) } }, actions = { if (!isNew) IconButton(onClick = showDeleteDialog) { Icon(Icons.Default.Delete, …) } })`
- Pull "Save Rule" button **out of the scrollable column** into `Scaffold(bottomBar = { … })`:
  - The bottomBar is a Surface (color = surface, hairline top border) with 16dp padding containing `AppButton(text = "Save Rule", variant = Primary, fullWidth = true, enabled = state.canSave, onClick = vm::save)`

### 6.2 Conditions section → `IfThenBlock(kind = If, subtitle = "All conditions must match for the rule to fire")`
- Inside the block, render the conditions list:
  - For each condition (index `i`):
    - If `i > 0`: `ConnectorPill(value = uiConnector(conditions[i-1].connectorToNext), onToggle = { vm.toggleConnector(i-1) })`
    - `ConditionItem(...)` (see 6.3)
  - After the last item: `AppButton(text = "Add condition", variant = Outline, fullWidth = true, leadingIcon = Icons.Default.Add, onClick = { showAddConditionMenu = true })` followed by a `DropdownMenu` containing the existing add-condition options
- Empty conditions list (rule allows zero): show only the "Add condition" button

### 6.3 ConditionItem rendering — for each condition kind
| Kind | iconCircle tint | iconCircle icon | Title text |
|---|---|---|---|
| OtpTypeIs | `brand.iconBlue` | `Icons.Outlined.VerifiedUser` | "Is a {type} OTP" |
| SenderMatches | `brand.iconViolet` | `Icons.Outlined.AlternateEmail` | "Sender matches" |
| BodyContains | `brand.iconGreen` | `Icons.Outlined.TextFields` | "Message contains" |
| ContainsMapsLink | `brand.iconRed` | `Icons.Outlined.Map` | "Contains maps link" |

The `content` slot of `ConditionItem` is the editor for that condition: existing `OutlinedTextField` / `ExposedDropdownMenuBox` / etc. — **render exactly the same fields the screen does today**, just inside the new card. Validation, error states, and supporting text all preserved.

### 6.4 Actions section → `IfThenBlock(kind = Then, subtitle = "Actions run in order; one failure does not stop the others")`
- Same loop pattern as conditions (no connector pills between actions)
- For each action: `ActionItem(...)` (see 6.5)
- Trailing `AppButton(text = "Add action", variant = Outline, fullWidth = true, leadingIcon = Icons.Default.Add, onClick = { showAddActionMenu = true })` + dropdown

### 6.5 ActionItem rendering — for each action kind
| Kind | iconCircle tint | iconCircle icon | Title text |
|---|---|---|---|
| ForwardSms | `brand.iconCyan` | `Icons.AutoMirrored.Outlined.Send` | "Forward SMS" |
| SetRingerLoud | `brand.iconAmber` | `Icons.Outlined.VolumeUp` | "Set ringer to loud" |
| PlaceCall | `brand.iconGreen` | `Icons.Outlined.Call` | "Place a call" |
| OpenMapsNavigation | `brand.iconRed` | `Icons.Outlined.Navigation` | "Open Maps navigation" |

Inside `content`: same recipient pickers, checkboxes, helper text, and permission hints the screen already uses.

### 6.6 PermissionHint → `PermissionBanner`
Replace inline `PermissionHint` composable with `PermissionBanner(message = …, onGrant = …)`. Keep the same trigger conditions.

### 6.7 AdvancedOptionsSection
Wrap in `AppCard(tone = CardTone.Default)`. Header row has `titleMedium "Advanced"` + `IconButton(toggle expand)` with `Icons.Default.ExpandMore`/`ExpandLess`. Body is the existing priority `OutlinedTextField`.

### 6.8 NameRuleDialog and Delete dialog → `AppDialog`
- NameRuleDialog: `AppDialog(title = "Name your rule", confirmLabel = "Save", onConfirm = …, dismissLabel = "Cancel", onDismiss = …) { OutlinedTextField(...) }`
- Delete dialog: `AppDialog(title = "Delete rule?", confirmLabel = "Delete", danger = true, onConfirm = …, ...) { Text("This cannot be undone.") }`

### 6.9 InlineAddRecipientSheet → `AppBottomSheet`
`AppBottomSheet(onDismiss = …, title = "New recipient") { /* existing fields */ }` — keep contact-picker integration intact.

### 6.10 Connector adaptation
Phase 4 specified that `ConnectorPill` uses its own UI-only `Connector` enum. In this screen, write a small private adapter:
```
private fun com.otpforwarder.domain.model.Connector.toUi(): com.otpforwarder.ui.component.Connector =
  when (this) { AND -> Connector.And; OR -> Connector.Or }
private fun com.otpforwarder.ui.component.Connector.toDomain(): com.otpforwarder.domain.model.Connector =
  when (this) { And -> AND; Or -> OR }
```
(Use the actual domain package paths discovered when reading the screen.)

**Verification**:
- `./gradlew assembleDebug` succeeds
- `./gradlew test` succeeds
- Manual on device:
  - Open EditRule from Rules → see two `IfThenBlock`s (blue IF, charcoal THEN), large bold labels
  - Add 2 conditions → connector pill appears between them, toggles AND ↔ OR on tap
  - Each condition row has correct icon circle and color
  - Add a Forward SMS action → cyan icon circle, recipient picker still works
  - Permission-required action → banner shows, Grant button still triggers permission flow
  - Save Rule button is sticky at the bottom and always visible
  - Delete dialog confirms in red; Name dialog accepts input
  - Inline add-recipient sheet still works end-to-end

**Commit message**:
```
feat(ui): redesign EditRule as applet builder with IF/THEN blocks
```

**After commit**: overwrite `UI_PROGRESS.md` to `CURRENT_PHASE=6`.

---

# Phase 7 — Migrate Recipients, Settings, Onboarding

**Goal**: Finish the redesign — apply the new system to the remaining four screens.

### 7.1 `app/src/main/java/com/otpforwarder/ui/screen/recipients/RecipientsScreen.kt`
- TopAppBar → `AppTopBar(title = "Recipients")`
- FAB → `BrandFab(text = "New recipient", icon = Icons.Default.Add, onClick = …, extended = true)`
- `RecipientCard` rebuilt as `AppCard(tone = CardTone.Default, onClick = { navigate to edit })`:
  - Row: `ServiceIconCircle(icon = Icons.Outlined.Person, tint = paletteFor(recipient.name))` where `paletteFor` returns one of the 6 brand-icon colors based on `recipient.name.hashCode().mod(6)` for stability
  - Column { titleMedium name, bodyMedium phone Ink500, FlowRow with attached rule names rendered as `StatusPill(label = ruleName, tone = Neutral, leadingDot = false)` }
  - Trailing: `BrandSwitch(checked = active, onCheckedChange = …)`
- Empty → `EmptyState(icon = Icons.Outlined.PersonAdd, title = "No recipients yet", subtitle = "Add a contact to forward OTPs to", action = { AppButton(text = "Add recipient", variant = Primary, onClick = …) })`

### 7.2 `app/src/main/java/com/otpforwarder/ui/screen/recipients/EditRecipientScreen.kt`
- TopAppBar → `AppTopBar(title = if (isNew) "New Recipient" else "Edit Recipient", navigationIcon = back, actions = { if (!isNew) delete IconButton })`
- Body wrapped in `AppCard(tone = CardTone.Default)` containing existing name/phone fields + contact-picker button (style as `AppButton(variant = Outline)`)
- Rules-assignment section: `SectionHeader(title = "Rules using this recipient")` then a list — each row is a `KeyValueRow(label = ruleName, trailing = { Checkbox(...) })`. Or simpler: render existing rule rows inside `AppCard(tone = CardTone.Tinted)` items in a Column.
- "Add new rule" inline action → `AppButton(variant = Text, leadingIcon = Icons.Default.Add)`
- Bottom bar via `Scaffold.bottomBar`: `AppButton(text = "Save", variant = Primary, fullWidth = true)`
- Delete confirmation → `AppDialog(title = "Delete recipient?", danger = true, …)`

### 7.3 `app/src/main/java/com/otpforwarder/ui/screen/settings/SettingsScreen.kt`
- TopAppBar → `AppTopBar(title = "Settings")`
- Three sections, each `SectionHeader` + a grouped `AppCard`:
  - **Permissions card**:
    - If any permission is denied: render `PermissionBanner(message = "{permission name} is needed for {reason}", onGrant = …)` at the top of the card for the first missing permission
    - For each permission row use `KeyValueRow(label = permissionName, trailing = { StatusPill(label = if (granted) "GRANTED" else "DENIED", tone = if (granted) Success else Danger, leadingDot = false) })`
  - **Classification card**:
    - `KeyValueRow(label = "Active tier", value = state.activeTierLabel)`
    - If downloading: `LinearProgressIndicator(progress = state.progress, modifier = Modifier.fillMaxWidth().height(4.dp).clip(extraSmall))` + `bodySmall` "Downloading model… {percent}%"
    - If download available: `AppButton(text = "Download model", variant = Tonal, onClick = …)`
  - **About card**:
    - `KeyValueRow(label = "Version", value = state.versionName)`
    - `bodySmall` Ink500 "All processing happens on-device"

### 7.4 `app/src/main/java/com/otpforwarder/ui/screen/onboarding/PermissionOnboardingScreen.kt`
- Hero: centered Column at top, 96dp `ServiceIconCircle(icon = Icons.Outlined.MarkEmailRead, tint = brand.iconBlue, size = 96.dp)`, 16dp gap, `displaySmall` "OTP Forwarder", 8dp gap, `bodyLarge` Ink500 "Forward your verification codes — privately, on-device" centered
- 32dp gap
- Three permission rows, each an `AppCard(tone = CardTone.Tinted)` with: leading 32dp `Icon` (Sms, Notifications, Phone), Column { titleMedium permission name, bodyMedium Ink500 "{why we need this}" }
- Spacer.weight(1f)
- Bottom sticky stack:
  - `AppButton(text = "Grant Permissions", variant = Primary, fullWidth = true, onClick = requestPermissions)`
  - When user has denied 2+ times: `AppButton(text = "Open Settings", variant = Outline, fullWidth = true, onClick = openAppSettings)` below

**Verification**:
- `./gradlew assembleDebug` succeeds
- `./gradlew test` succeeds
- Manual on device, walk every screen:
  - Recipients: empty state, FAB, mini-cards with initial-color circles, rule pills, switches
  - EditRecipient: form, contact picker, save, delete dialog (red), rule assignment toggles
  - Settings: each permission row with status pill; deny one in system settings → banner appears at top of card; classification card shows tier and download button if applicable
  - Onboarding (fresh install or after revoking permissions): hero icon circle, 3 permission cards, primary CTA, settings fallback when blocked
- Toggle system dark mode → confirm all four screens read correctly (Ink500 vs DarkTextDim, card outlines visible)
- Rotate device → no layout breakage

**Commit message**:
```
feat(ui): redesign Recipients, EditRecipient, Settings, and Onboarding with new design system
```

**After commit**: overwrite `UI_PROGRESS.md` to `CURRENT_PHASE=7`.

---

## Files affected (cumulative summary)

### Created (24 files)
Theme: `Shape.kt`, `Spacing.kt`, `BrandColors.kt`
Components: `AppCard.kt`, `AppButton.kt`, `AppTopBar.kt`, `BrandSwitch.kt`, `BrandFab.kt`, `AppNavBar.kt`, `KeyValueRow.kt`, `SectionHeader.kt`, `EmptyState.kt`, `StatusPill.kt`, `PermissionBanner.kt`, `ServiceIconCircle.kt`, `AppDialog.kt`, `AppBottomSheet.kt`, `IfThenBlock.kt`, `ConditionItem.kt`, `ActionItem.kt`, `ConnectorPill.kt`

### Modified (11 files)
Theme: `Color.kt`, `Theme.kt`, `Type.kt`
App: `OtpForwarderApp.kt`
Screens: `home/HomeScreen.kt`, `rules/RulesScreen.kt`, `rules/EditRuleScreen.kt`, `recipients/RecipientsScreen.kt`, `recipients/EditRecipientScreen.kt`, `settings/SettingsScreen.kt`, `onboarding/PermissionOnboardingScreen.kt`

### Untouched
All ViewModels, `domain/`, `data/`, `di/`, `receiver/`, `service/`, `worker/`, `util/`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`, `res/`.

---

## `run-ui-phases.sh` template (to be created in project root after plan approval)

```bash
#!/bin/bash
# Unattended loop: implements one phase of UI_REDESIGN_PLAN.md per iteration,
# each in a fresh Claude Code session. Stop anytime with Ctrl+C.

set -euo pipefail
cd "$(dirname "$0")"

PLAN_FILE="UI_REDESIGN_PLAN.md"
PROGRESS_FILE="UI_PROGRESS.md"
LOG_FILE="ui-phase-loop.log"
TOTAL="${TOTAL:-7}"

if [[ ! -f "$PLAN_FILE" ]]; then
  echo "✗ Missing $PLAN_FILE — aborting."
  exit 1
fi

[[ -f "$PROGRESS_FILE" ]] || echo "CURRENT_PHASE=0" > "$PROGRESS_FILE"

while :; do
  CUR=$(grep -oE '[0-9]+' "$PROGRESS_FILE" | head -1)
  NEXT=$((CUR + 1))

  if [[ $NEXT -gt $TOTAL ]]; then
    echo "✓ All $TOTAL phases complete."
    break
  fi

  echo ""
  echo "=========================================="
  echo "  Starting Phase $NEXT of $TOTAL"
  echo "  $(date)"
  echo "=========================================="

  claude -p --dangerously-skip-permissions \
    "Implement Phase $NEXT from $PLAN_FILE end-to-end. Follow the rules in CLAUDE.md and the Global rules at the top of $PLAN_FILE. \
     Read $PLAN_FILE first to find the exact scope, file list, and commit message for Phase $NEXT — do not implement any other phase. \
     Run ./gradlew assembleDebug and ./gradlew test — both must pass before you commit. \
     Commit using the commit message specified for Phase $NEXT in $PLAN_FILE. \
     Finally, overwrite $PROGRESS_FILE so its first line is exactly: CURRENT_PHASE=$NEXT" \
    2>&1 | tee -a "$LOG_FILE"

  NEW=$(grep -oE '[0-9]+' "$PROGRESS_FILE" | head -1)
  if [[ "$NEW" != "$NEXT" ]]; then
    echo ""
    echo "✗ Phase $NEXT did not advance $PROGRESS_FILE. Stopping."
    echo "  Log: $LOG_FILE"
    exit 1
  fi

  echo "✓ Phase $NEXT complete."
done
```

Note: the script intentionally does **not** enforce a specific branch (unlike the maps version). The user can run it on whatever branch they prefer, or add a branch check if desired.
