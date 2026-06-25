# scijava-ui-robot — handoff / roadmap

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
6. **Package** `ch.epfl.biop.scijava.ui.robot` (BIOP-owned, not squatting
   `org.scijava`). Change now if ever, it's cheap.
7. **Mode switch is per-launch, no global flag.** The launcher choice *is* the
   mode: `programmaticLauncher()` runs headless, `searchLauncher(query)` runs
   visibly and drives the dialog. There is no `FORCE_PROGRAMMATIC` global (the
   original toolkit had one); to iterate headlessly you swap the launcher at the
   call site. `CmdExecutor.launch()` stays a one-liner delegating to the launcher,
   which encapsulates its own mode.
8. **IJ1 lives in this repo but quarantined.** The `…robot.ij1` package is the
   *only* code allowed to import `ij.*` / `net.imagej.*`; core never does
   (enforced by convention + a grep check). A future module split is then just
   "move the `ij1` package + the `imagej-legacy` dependency." Multi-module was
   considered and deferred — single module for now.

## Current state (committed)

Core depends on `scijava-common` + `scijava-ui-swing`; the `ij1` package adds
`imagej-legacy` (compile) and the `imagej` gateway (test scope).

Done:
- `robot/` — `CmdExecutor` (type-state builder), `InputResolution` /
  `PreSetResolution` / `DialogResolution`, `Resolutions` (`programmatic`,
  `fromDialog`), `Launcher` / `Launchers` (`programmaticLauncher`),
  `LaunchRequest`, plus the `Gesture` / `GestureContext` capability (a resolution
  *optionally* implements `Gesture` for a visible action).
- `robot/groovy/GroovyRender` — headless `cs.run(...)` snippet projection.
- `robot/core/` — `Ui`, `Timings`, `Inspector` (ported; used to locate the IJ1
  search bar). `Timeline` / `EventRecorder` / `Step` are **no-op placeholders**.
- `robot/widgets/Harvester` — decoupled from `Fiji` (takes a `Context`); drives
  checkbox / number / text / combo / radio / `File` / `File[]`.
- **`robot/ij1/` (the IJ1 binding, quarantined — decision #8):** `Fiji`
  (`searchAndRun`, ported), `Ij1Launchers.searchLauncher(query)` (visible:
  pre-set gestures → search-bar trigger → `Harvester` drives the dialog),
  `Ij1Resolutions.selectActiveImage(title)` (a `PreSetResolution` + `Gesture` —
  `value()` resolves the `ImagePlus` by title for the headless run; the gesture
  activates its window for the visible run).
- `LaunchRequest` now exposes the pre-set vs dialog split
  (`runPreSetGestures()`, `dialogArgs()`, `dialogNarrations()`) so a visible
  launcher drives only the dialog inputs.
- Tests: `CmdExecutorTest` (headless, 4 tests, green here), `HarvesterWidgetsTest`
  (GUI, 7 widgets, run locally — confirmed by the user), and the ij1 tests
  (`ActiveImagePresetTest`, `SearchLauncherTest`, GUI/local; compile here, **not
  yet run** — they boot a real Fiji and drive the screen, so run them locally).

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

### 3. Port the remaining generic (non-BDV) widget/AWT helpers
From `…/docs/videos/`:
- `core/Inspector` (AWT/Swing tree walker) — pure, port as-is.
- `widgets/Tree` (JTree driver) and `widgets/Popup` (JPopupMenu navigator) —
  pure Swing, needed for menu driving and later BDV. Port as-is.
- Optionally re-add `Harvester.fillListByNames` (`String[]`→`JList`) if a
  non-BDV multi-select use appears (currently dropped — only BDV used it).

### 4. Menu-bar driving (a feature the user wants)
`MenuDriver` interface + `widgets/MenuBar` (Swing `JMenuBar`: open top-level
`JMenu`, then reuse `Popup.clickPath`). `SwingMenuDriver` in core; `Ij1MenuDriver`
(AWT menu has no bounds → keyboard mnemonics or route to search) in the IJ1
binding. A `menuLauncher` uses it.

### 5. Recording layer (the video-making half)
Flesh out the placeholders and port the rest of `core/`:
`Timeline` (timeline.json v4), `EventRecorder` (global AWT listener),
`Step` (narration timing, screenshots, intro/outro), plus `Assets`,
`ScreenRecorder`, `Screenshotter`, `GroovyScript` (full, with `#@File`
hoisting), `Layout`, `CommandRef`, `Demo`. This is large; do it as its own
increment once the visible execution path (TODO #1) is proven.

### 6. BDV binding module (`scijava-ui-robot-bdv`)
Deps: core + bdv-core + bigdataviewer-playground. Move/port: `bdv/Bdv` (window
ops), the source-tree `JTree` widgets + sorted-list drag + `BdvHandle[]`/
`BvvHandle[]` `JList` branches (re-add to a `Harvester` extension point or a
binding-side dispatcher), a `treeLauncher` / `launchFromSourcesTree`
(contributes `"sources"` via `Launcher.contributedInputs`), and a
`selectActiveBdv` resolution. Port the BDV widget tests from `WidgetsTest`.

### 7. Groovy rendering: object-valued inputs + File hoisting
`GroovyRender.literal` has a TODO fallback for unsupported types. Add a
per-resolution rendering hook so object-valued resolutions render themselves
(e.g. `selectActiveBdv` → a title lookup, `selectActiveImage` → `IJ.getImage()`).
Port `#@File` hoisting from the original `GroovyScript`.

### 8. Packaging / infra
GitHub repo `BIOP/scijava-ui-robot`; CI runs headless tests only
(`-Dtest=CmdExecutorTest`); publish to `maven.scijava.org`. Decide multi-module
(core + ij1 + bdv under one reactor) vs separate repos — leaning multi-module.

## How to build / test
```bash
mvn clean install                       # compiles all; runs headless test
mvn test -Dtest=CmdExecutorTest         # headless backbone
mvn test -Dtest=HarvesterWidgetsTest    # GUI — local display required
```

## Source of truth for ports
Original toolkit: `ijp-imglib2bdvdemo-ij2/src/test/java/ch/epfl/biop/docs/videos/`
and its `AUTOMATION.md`. The demo scripts (`docs/Generate*DocAssets.java`) are
worked examples of the *old* `CommandExecutor` API — useful to see intent, but
the API here is the new type-state builder, not a 1:1 port.
