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

## Current state (committed)

Two commits on the default branch. Core depends on `scijava-common` +
`scijava-ui-swing` only.

Done:
- `robot/` — `CmdExecutor` (type-state builder), `InputResolution` /
  `PreSetResolution` / `DialogResolution`, `Resolutions` (`programmatic`,
  `fromDialog`), `Launcher` / `Launchers` (`programmaticLauncher`),
  `LaunchRequest`.
- `robot/groovy/GroovyRender` — headless `cs.run(...)` snippet projection.
- `robot/core/` — `Ui` (Robot wrapper, ported, trimmed of recording-crop
  helpers), `Timings` (ported). `Timeline` / `EventRecorder` / `Step` are
  **no-op placeholders** so the gesture primitives call them exactly as in the
  original.
- `robot/widgets/Harvester` — decoupled from `Fiji` (takes a `Context`); drives
  checkbox / number / text / combo / radio / `File` / `File[]`. BDV-coupled
  branches removed.
- Tests: `CmdExecutorTest` (headless, 4 tests, green here), `HarvesterWidgetsTest`
  (GUI, 7 widgets, run locally — confirmed working by the user).

## TODO (roughly in priority order)

### 1. Wire visible execution into `CmdExecutor`  ← the core gap
Right now the builder only has `programmaticLauncher`; the visible path doesn't
exist yet. Needed:
- A **gesture capability** on resolutions (a small interface a resolution
  *optionally* implements, e.g. `Gesture { void perform(GestureContext); }`),
  so `DialogResolution` can drive its widget and `PreSetResolution` can do a
  pre-launch gesture. Keep `value()`/`narration()` as-is.
- A **mode switch** (visible vs programmatic). The original used a global
  `CommandExecutor.FORCE_PROGRAMMATIC` + `Ui.FAST_MODE`. Decide: global flag, or
  per-launch. In visible mode, `launch()` should: run preSet gestures → trigger
  via launcher → drive only the `postSet` (dialog) inputs through
  `Harvester.runOpenDialog(cmd, narrations, dialogArgs)`.
- A visible launcher: start with `searchLauncher(query)` or a simple
  `dialogLauncher()` that just does `cs.run(cmd, true)` (no pre-set dialog args)
  and lets `postSet` drive the dialog. (`Fiji.searchAndRun` is IJ1 — a
  `searchLauncher` belongs in an IJ1 binding; a plain `dialogLauncher` can live
  in core.)
- Acceptance: a GUI test that builds a plan with `preSet(programmatic(..))` +
  `postSet(fromDialog(..))` + visible launcher and asserts the command ran with
  the dialog driven.

### 2. Active-image spike (IJ1 binding)  ← validates the whole model
Create module/repo `scijava-ui-robot-ij1` (deps: core + imagej-legacy). Add
`selectActiveImage(name)` as a `PreSetResolution` whose gesture activates the
named image window. Verify that pre-launch selection survives into the real
`LegacyImagePreprocessor` (the assumption decision #2 rests on). If it holds,
the two-phase model is sound; if not, that's where the design needs another
joint. Do a minimal standalone spike first, then integrate with the builder.

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
