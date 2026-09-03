# fiji-robot — handoff / roadmap

Working notes for continuing this project in a fresh session. Read this first,
then `README.md` for the user-facing shape.

## What this project is

A visible, recordable driver for SciJava commands, extracted from the
tutorial-video automation toolkit that lives in the sibling repo
`ijp-imglib2bdvdemo-ij2` under
`src/test/java/ch/epfl/biop/docs/videos/` (see its `AUTOMATION.md`). The goal:
run a command the way a user would (establish context → trigger → fill dialog)
so the run can be **reproduced headlessly** *and* **recorded** (cursor motion,
subtitles, `timeline.json`) for tutorial videos.

Published to Maven only — **not** pushed to a Fiji update site (the Robot is
unsafe by design; it's a video-making tool). bigdataviewer-playground needs
**no** modification; the BDV binding depends only on its existing public API.

## Design decisions already settled (do not relitigate)

1. **Type-state builder.** `CmdExecutor.of(ctx, Cmd).preSet(...).withLauncher(...)
   .postSet(...).launch()`. `withLauncher` returns a different type
   (`PreLaunch` → `PostLaunch`) so order and the single launch point are
   compile-enforced.
2. **Two-phase temporal model: preSet → launch → postSet.** A SciJava
   preprocessor (active image, active BDV) resolves its input *during* the run,
   before the harvester dialog. Establishing ambient UI state *before*
   `CommandService.run` (what `preSet` does) survives into the preprocessor
   chain, so the real preprocessor picks it up. **The "robot preprocessor at
   matching priority" idea was explicitly rejected as over-engineering** — the
   two-bucket model covers the real cases. (The one assumption still to be
   verified by a spike: that pre-launch active-window selection survives into
   the chain — see TODO #2.)
3. **Each resolution owns its row.** value / narration / (later) gesture /
   (later) Groovy rendering live on the resolution. The executor, the Groovy
   renderer and the timeline are thin **projectors** iterating the plan — adding
   an input kind is one new class, not edits across subsystems
   (matrix → diagonal).
4. **The harvester is the one irreducible off-diagonal case**: one dialog
   resolves many inputs in one batched, blocking, async-driven pass. Don't force
   it into the per-input mold.
5. **Recording couples at two layers, only one of which is hard.** Gesture trace
   (mouse/key with timing) already lives at the `core.Ui` primitive layer and is
   decoupled. Semantic content (narration timing, step bodies, command list) is
   the plan projected into timeline shape — another projector, not a separate
   integration.
6. **Package** `ch.unige.biochem.fiji.robot` (not squatting
   `org.scijava`). Change now if ever, it's cheap.
7. **Mode switch is per-launch, no global flag.** The launcher choice *is* the
   mode: `programmaticLauncher()` runs headless, `searchLauncher(query)` runs
   visibly and drives the dialog. There is no `FORCE_PROGRAMMATIC` global (the
   original toolkit had one); to iterate headlessly you swap the launcher at the
   call site. `CmdExecutor.launch()` stays a one-liner delegating to the launcher,
   which encapsulates its own mode.
8. **Bindings live in this repo but quarantined.** Each binding package is the
   *only* code allowed to import its toolkit: `…robot.ij1` → `ij.*` /
   `net.imagej.*`; `…robot.bdv` → `bdv.*` / `sc.fiji.*` / `bvv.*`. Core never
   imports either (enforced by convention + a grep check). A future module split
   is then just "move the binding package + its one dependency"
   (`imagej-legacy`, `bigdataviewer-playground`). Multi-module was considered and
   deferred — single module for now.

## Current state (committed)

Core depends on `scijava-common` + `scijava-ui-swing`; the `ij1` package adds
`imagej-legacy`, the `bdv` package adds `bigdataviewer-playground` 0.21.0, and the
`imagej` gateway is pulled in test-scoped for booting Fiji in GUI tests.

Done:
- `robot/` — `CmdExecutor` (type-state builder), `InputResolution` /
  `PreSetResolution` / `DialogResolution`, `Resolutions` (`programmatic`,
  `fromDialog`), `Launcher` / `Launchers` (`programmaticLauncher`),
  `LaunchRequest`, plus the `Gesture` / `GestureContext` capability (a resolution
  *optionally* implements `Gesture` for a visible action).
- `robot/groovy/GroovyRender` (+ `GroovyRenderContext`) — headless `cs.run(...)`
  projection: snippet form by default, full parameterised script (`#@File` /
  `#@Service` + `#@CommandService cs`) when inputs hoist params. Object-valued
  inputs render via the `GroovyRenderable` capability (root pkg), which renders
  the carried spec instead of `value()` — see TODO #7. `groovy/GroovyScript`
  (implements `Timeline.ScriptSource`) is the adapter that auto-records each
  `CmdExecutor.launch()` body into the timeline, keyed by the open `Step` — see
  TODO #5.
- `robot/core/` — `Ui`, `Timings`, `Inspector` (ported; used to locate the IJ1
  search bar). **Recording layer ported and decoupled (was placeholders):**
  `Timeline` (in-memory recorder + `timeline.json` v4 writer), `EventRecorder`
  (global AWT human-gesture capture), `Step` (narrated step bracketing +
  waitForUser), plus `ScreenRecorder` (ffmpeg gdigrab subprocess),
  `Screenshotter` (Robot PNG capture), `Assets` (per-demo output session),
  `CommandRef` (DTO). The key design change vs the original toolkit:
  **`Timeline` no longer hard-depends on `ScreenRecorder` or `GroovyScript`** —
  it owns its own clip index/name counter (lockstep with `ScreenRecorder` via
  identical `%03d-slug.mp4` naming), takes the ffmpeg first-frame anchor through
  `setFirstFrameAnchor(...)` (fed by `Step.end()`), and gets the embedded
  reproduction script through an optional pluggable `Timeline.ScriptSource`
  (null → script block omitted). That decoupling is what makes a
  `timeline.json` headlessly testable: with `ScreenRecorder.ENABLED=false` +
  `Screenshotter.ENABLED=false` + `Timeline.ENABLED=true`, a demo produces a
  complete deterministic `timeline.json` with no ffmpeg/display/PNGs. `Ui` gained
  the recording-bounds helpers (`recordingBoundsLogical/Physical`,
  `targetScreenPhysicalBounds`, `RECORDING_BOTTOM_INSET_PX`, `logScreens`), all
  headless-guarded.
- `robot/widgets/` — `Harvester` (decoupled from `Fiji`; checkbox / number / text
  / combo / radio / `File` / `File[]`), plus the generic Swing drivers `Tree`
  (JTree navigation), `Popup` (JPopupMenu walk), `Lists` (flat-`JList`
  multi-select) and the `Widgets` component finder, ported from the toolkit and
  stripped of the bdv-specific `"Sources>"` prefix so they stay binding-free.
  `Harvester` exposes a **`WidgetDriver` extension point** (`registerDriver` /
  `unregisterDriver`): each registered driver is consulted before the built-in
  type ladder, so a binding plugs in widgets core can't know about (the BDV
  source widgets) without core importing the binding. Drivers match by
  container *shape* + value type, so registration is harmless for unrelated
  dialogs.
- **`robot/ij1/` (the IJ1 binding, quarantined — decision #8):** `Fiji`
  (`searchAndRun`, ported), `Ij1Launchers.searchLauncher(query)` (visible:
  pre-set gestures → search-bar trigger → `Harvester` drives the dialog),
  `Ij1Resolutions.selectActiveImage(title)` (a `PreSetResolution` + `Gesture` —
  `value()` resolves the `ImagePlus` by title for the headless run; the gesture
  activates its window for the visible run).
- **`robot/bdv/` (the BDV binding, quarantined — decision #8).** Split into two
  sub-packages along the library's two capabilities — *run a command* vs *drive
  UI directly* — the same line core already draws (root pkg / `CmdExecutor` vs
  `widgets/` + `core/Ui`). Both stay under `…robot.bdv`, so the quarantine (only
  the `…robot.bdv` subtree imports `bdv.*` / `sc.fiji.*`) is unaffected.
  - **`bdv/command/` (connects BDV to `CmdExecutor`):**
    `BdvLaunchers.treeLauncher(path)` / `treeLauncher()` / `treeLauncher(path...)`
    (visible: right-click the BDV-Playground source tree → walk the popup to the
    command → `Harvester` drives the dialog; contributes `"sources"` via
    `Launcher.contributedInputs` for the Groovy projection);
    `BdvResolutions.selectActiveBdv(title)` (the BDV mirror of `selectActiveImage`
    — `value()` resolves the `BdvHandle` by title; the gesture activates its
    window so `ActiveBdvPreprocessor` reads it; popup menu path derived from the
    command's `MenuPath`, dropping the `Plugins > BigDataViewer-Playground`
    prefix); and `BdvWidgets`, which registers three `WidgetDriver`s on
    `Harvester` (single-source `JTree`, `style="sorted"` tree→list drag,
    `BdvHandle[]`/`BvvHandle[]` flat-list multi-select). The tree launchers call
    `BdvWidgets.register()` so a launched command's source widgets are driveable;
    a test driving `Harvester.run` directly calls it in setup.
  - **`bdv/view/` (standalone Robot drivers for a BDV window, invoked by the
    demo script *between* command runs — not by the executor):** `Bdv`
    (`setTimepoint`, `setCardPanelExpanded`, `selectSourceInCard`,
    `setDisplayRange`), ported from the toolkit. Future BVV / canvas drivers
    land here.
- `LaunchRequest` now exposes the pre-set vs dialog split
  (`runPreSetGestures()`, `dialogArgs()`, `dialogNarrations()`) so a visible
  launcher drives only the dialog inputs.
- Tests: `CmdExecutorTest` (headless, 4) + `TreeLauncherRenderTest` (headless, 3,
  pins the `"sources"` contribution) + `WidgetDriverDispatchTest` (headless, 4,
  pins the `WidgetDriver` extension-point contract) + `core/RecordingTimelineTest`
  (headless, 3 — pins the `timeline.json` v4 shape: steps/clips, comments,
  mouse/key events, intro/outro, the optional `ScriptSource` block, and the
  disabled-Timeline no-write case) + `GroovyHoistRenderTest` (headless, 2 — pins
  `#@File` hoisting + dedup) + `ij1/ActiveImageRenderTest` (headless, 1 — pins
  the object-valued render with no image open) + `bdv/ActiveBdvRenderTest`
  (headless, 1 — pins the by-title BDV lookup render) + `core/RecordingScriptAdapterTest`
  (headless, 1 — pins the `GroovyScript` adapter: a launch inside a step embeds
  its body + hoists a `#@File` into the shared preamble, command-free step stays
  visualization-only) green here; `core/RecordingLayerDemo` is a runnable `main`
  worked example (no JUnit) that installs a `GroovyScript` recorder and lets two
  `CmdExecutor` launches auto-populate a full sample `timeline.json`;
  `HarvesterWidgetsTest` (GUI, run locally — confirmed by the user); ij1 GUI tests
  (`ActiveImagePresetTest`, `SearchLauncherTest`) and bdv GUI tests
  (`ActiveBdvPresetTest`, `TreeLauncherTest`, `ActiveBdvSelectionTest`,
  `SourceWidgetsTest`) compile here but are **run locally** — they boot a real
  Fiji and drive the screen (the user has confirmed `SourceWidgetsTest` green).
  Run one GUI test class per JVM (each boots its own ImageJ gateway).
  `SourceWidgetsTest` makes its example sources with bdv-playground's procedural
  `VoronoiSourceCreator` (no Bio-Formats, no download — see `BdvTestSources`) and
  derives expected tree paths / leaf counts from the live model instead of
  hardcoding them.
- **Camera focus regions** (`focus.window` / `focus.dialog` / `focus.clear`
  events in `timeline.json`; additive, so still version 4): the driving side
  records *where the interesting content is* — a window's / dialog's logical
  screen rect plus its title — so the downstream renderer frames it with its
  auto-zoom instead of inferring attention from click positions. Emitted
  automatically by `Ui.placeFrame` / `dragFrame` / `resizeFrame` /
  `waitForFrame`, the `Harvester` dialog drive (clear on OK), window
  close/minimize (clear), and the `bdv.view.Bdv` ops; public API
  `Ui.focus(Window)` / `Ui.focusClear()` for demo scripts (e.g. point the
  camera at a viewer during a passive streaming hold). Pinned by
  `RecordingTimelineTest`; sample in `docs/sample-timeline.json`.

## TODO (roughly in priority order)

### 1. Wire visible execution into `CmdExecutor`  ← LARGELY DONE
The visible path now exists via `searchLauncher` (in `ij1`). Landed:
- The **gesture capability** (`Gesture` / `GestureContext`) — a resolution opts
  in; `value()`/`narration()` unchanged.
- The **mode switch**: per-launch, no global flag (settled decision #7). The
  launcher *is* the mode; `searchLauncher.launch()` runs preSet gestures → search
  trigger → `Harvester.runOpenDialog(cmd, dialogNarrations, dialogArgs)`.
- A visible launcher: `searchLauncher(query)` (IJ1). The `dialogResolutions`
  side of the gesture capability (a `DialogResolution` that drives its *own*
  widget rather than going through the value-based `Harvester` path) is **not**
  done — `searchLauncher` still drives dialog inputs by value via `Harvester`.
- Acceptance test: `SearchLauncherTest` (GUI/local) — written, not yet run.

Still open: a core-only `dialogLauncher()` (`cs.run(cmd, true)` then let `postSet`
drive the dialog, no IJ1) for visible tests that don't need the search bar.

### 2. Active-image spike (IJ1 binding)  ← IMPLEMENTED, pending local run
`Ij1Resolutions.selectActiveImage(title)` is a `PreSetResolution` + `Gesture`:
the gesture activates the named window (visible click + `WindowManager
.setCurrentWindow` as a correctness guarantee) so the real
`LegacyImagePreprocessor` resolves the command's `ImagePlus` during the run.
`SearchLauncherTest` is the spike that asserts this survives end to end — **run
it locally to confirm the assumption decision #2 rests on.** If the
`setCurrentWindow` guarantee turns out to be doing the real work and a pure
visible click does not survive, revisit whether the gesture is enough on its own.

### 3. Port the remaining generic (non-BDV) widget/AWT helpers  ← MOSTLY DONE
- `core/Inspector` (AWT/Swing tree walker) — **ported**.
- `widgets/Tree` (JTree driver) and `widgets/Popup` (JPopupMenu navigator) —
  **ported** to core, generic (the bdv-specific `"Sources>"` prefix was stripped;
  callers pass full paths from the root).
- Done: the flat-`JList` multi-select primitive is back as the generic
  `widgets/Lists.selectByNames` (`String[]`→`JList`), used by the BDV handle-list
  driver; available to any future non-BDV multi-select use.

### 4. Menu-bar driving (a feature the user wants)
`MenuDriver` interface + `widgets/MenuBar` (Swing `JMenuBar`: open top-level
`JMenu`, then reuse `Popup.clickPath`). `SwingMenuDriver` in core; `Ij1MenuDriver`
(AWT menu has no bounds → keyboard mnemonics or route to search) in the IJ1
binding. A `menuLauncher` uses it.

### 5. Recording layer (the video-making half)  ← CORE PORTED
Landed (decoupled + headless-testable — see "Current state"):
`Timeline` (timeline.json v4), `EventRecorder` (global AWT listener),
`Step` (narration timing, screenshots, intro/outro, waitForUser), `Assets`,
`ScreenRecorder`, `Screenshotter`, `CommandRef`. `RecordingTimelineTest`
(headless) pins the JSON shape; `RecordingLayerDemo` (`main`) is the worked
example.

Still to port / wire:
- **`GroovyScript` adapter — DONE.** `groovy/GroovyScript` implements
  `Timeline.ScriptSource` and is the adapter between the two halves: a
  `CmdExecutor.launch()` inside an open `Step` auto-records its `cs.run(...)`
  body (rendered via TODO #7's hook) into a shared `GroovyRenderContext`, keyed
  by `Step.currentName()`. The shared context means imports and `#@File` /
  `#@Service` params are hoisted once into the top-level `script.preamble`;
  per-step `script.body` is just the call. A step with no run is left
  visualization-only. Install with `new GroovyScript().install()`; the launch
  hook is a no-op when nothing is installed (same "fire unconditionally" shape
  as `Ui`→`Timeline`). `RecordingScriptAdapterTest` pins it; `RecordingLayerDemo`
  now uses it (no hand-wired `ScriptSource`). Instance-based, not the original's
  static accumulator — nothing to reset between sessions.
- **`Layout`** (multi-window placement presets) and the **`Demo`** authoring
  facade (intro/outro + youtube-description.md emission) — not yet ported;
  port when a real multi-step video demo is assembled.
- A GUI smoke test that records an actual short clip (`ScreenRecorder` +
  `EventRecorder` live) — run locally, like the other GUI tests.

### 6. BDV binding (`…robot.bdv`)  ← SOURCE WIDGETS DONE
Landed (single-module, quarantined package — not a separate repo):
- `BdvLaunchers.treeLauncher(...)` — source-tree right-click launcher with
  single / root / multi-select variants; contributes `"sources"` via
  `Launcher.contributedInputs`. Visible-only (per decision #7), like
  `searchLauncher`.
- `BdvResolutions.selectActiveBdv(title)` — the `selectActiveImage` mirror.
- **`bdv/command/BdvWidgets` — the BDV harvester source widgets** (re-added via the
  `Harvester` `WidgetDriver` extension point, settling the "extension point vs
  binding-side dispatcher" question in TODO #6 in favour of the extension point).
  Three drivers: the single-source `JTree` (`SwingSourceWidget` /
  `SwingSourceListWidget` — a leaf selects one source, a parent selects all
  descendants = the multi-source case), the `style="sorted"` tree→list drag
  (`SwingSourceSortedListWidget`), and the flat-`JList` multi-select for
  `BdvHandle[]` / `BvvHandle[]` (`SwingBdvHandleListWidget` /
  `SwingBvvHandleListWidget`). One driver covers both BDV and BVV handle lists
  (same Swing shape).
- **`bdv/view/Bdv`** — BDV-window source ops ported (`setTimepoint`,
  `setCardPanelExpanded`, `selectSourceInCard`, `setDisplayRange`).
- Tests: `TreeLauncherRenderTest` + `WidgetDriverDispatchTest` (headless),
  `ActiveBdvPresetTest` + `TreeLauncherTest` + `ActiveBdvSelectionTest` +
  `SourceWidgetsTest` (GUI/local). `BdvTestSources` makes example sources with
  `VoronoiSourceCreator` (procedural — no Bio-Formats).

Still to port (from `…/docs/videos/bdv/`):
- A `selectActiveBvv` counterpart if BVV commands need it.
- A GUI test for the `bdv/view/Bdv` window ops themselves (`setDisplayRange`
  etc.); currently `Bdv` is ported but only exercised by the demo, not a unit test.

### 7. Groovy rendering: object-valued inputs + File hoisting  ← DONE
Landed:
- **Per-resolution rendering hook** `GroovyRenderable` (root pkg) — an opt-in
  capability mirroring `Gesture` (visible) / value (programmatic). A resolution
  that implements it renders **its own carried spec**, and the renderer calls
  `renderGroovy(ctx)` *instead of* `value()`. This is the key correctness point
  (settled with the user): an object value like `SourceAndConverter[]` /
  `ImagePlus` / `BdvHandle` is **not** reversible to the selector/title that
  produced it (not bijective), so rendering must carry the original spec from
  construction, never derive it from the resolved object. A consequence:
  rendering a plan no longer forces the live object to exist —
  `ActiveImageRenderTest` renders a `selectActiveImage` plan with **no image
  open** (would throw if `value()` were called).
- **`GroovyRenderContext`** (groovy pkg) — per-render accumulator for `import`s
  and hoisted `#@…` script params, deduped. `GroovyRender.assemble(...)` emits a
  full parameterised script (`#@File` / `#@Service` lines + `#@CommandService cs`
  + imports) when anything was hoisted, else the original `cs`-assuming snippet.
- **`File` hoisting**: `GroovyRender.literal(value, ctx)` lifts `File` / `File[]`
  to `#@File` params (deduped by absolute path) instead of inline `new File(...)`
  — `GroovyHoistRenderTest` pins distinct-vs-dedup.
- Wired resolutions: `selectActiveImage(title)` → `WindowManager.getImage("title")`
  + `import ij.WindowManager` (chose title-faithful over the old `IJ.getImage()`
  sketch, per the carry-the-spec principle — flip in one line if "active image"
  semantics are wanted); `selectActiveBdv(title)` → a by-title `find` over a
  hoisted `#@ObjectService` (`ActiveBdvRenderTest`).
- The launcher-contributed `"sources"` path already carried the spec string (the
  tree path), so it needed no change — it was the precedent this generalises.

Still open (nice-to-haves, not blocking):
- Human-readable `#@File` labels (the original `GroovyScript.nameFile` preferred
  names) — currently the label is the `fileN` var name.
- The adapter that drives `Timeline.scriptSource` from a `CmdExecutor` plan is
  now **done** — see `groovy/GroovyScript` under TODO #5.

### 8. Packaging / infra
GitHub repo `unige-biochem/fiji-robot`; CI runs headless tests only
(`-Dtest=CmdExecutorTest`); publish to `maven.scijava.org`. Decide multi-module
(core + ij1 + bdv under one reactor) vs separate repos — leaning multi-module.

## How to build / test
```bash
mvn clean install                       # compiles all; runs headless test
mvn test -Dtest=CmdExecutorTest         # headless backbone
mvn test -Dtest=HarvesterWidgetsTest    # GUI — local display required

# Regenerate the worked example + committed sample asset (docs/sample-timeline.json):
mvn -q test-compile exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=ch.unige.biochem.fiji.robot.core.RecordingLayerDemo
```

`README.md` carries the user-facing shape (the video pipeline, the extension
points, per-binding usage). `docs/sample-timeline.json` is a committed sample of
the primary output, refreshed by the demo above — keep it text-only (no binary
clips/PNGs in git).

## Source of truth for ports
Original toolkit: `ijp-imglib2bdvdemo-ij2/src/test/java/ch/epfl/biop/docs/videos/`
and its `AUTOMATION.md`. The demo scripts (`docs/Generate*DocAssets.java`) are
worked examples of the *old* `CommandExecutor` API — useful to see intent, but
the API here is the new type-state builder, not a 1:1 port.
