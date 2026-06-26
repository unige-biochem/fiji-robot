# Fiji Robot

A visible, recordable driver for SciJava/Fiji commands. The goal is to run a command
the way a user would — establishing context, triggering it, filling its dialog —
so the run can be both **reproduced headlessly** and **recorded** (cursor
motion, subtitles, a machine-readable timeline, screenshots, screen-capture
clips) for tutorial videos.

> Status: the type-state builder backbone, the `java.awt.Robot` widget layer
> (driving SciJava harvester dialogs), the IJ1 and BDV bindings, and the
> **recording layer** (`timeline.json`, screen recording, screenshots, embedded
> reproduction script) are all in place. See [`HANDOFF.md`](HANDOFF.md) for the
> detailed state and roadmap.

## The design in one builder

A command run is described as an ordered recipe whose grammar is enforced by the
compiler — pre-set inputs, then exactly one launcher, then dialog inputs, then a
single terminal `launch()`:

```java
import static ch.unige.biochem.fiji.robot.Resolutions.*;
import static ch.unige.biochem.fiji.robot.Launchers.*;

CmdExecutor.of(context, MyCommand.class)
    .preSet("a", programmatic(2))                   // resolved before launch
    .withLauncher(programmaticLauncher())           // the one trigger
    .postSet("min", fromDialog(0.0, "lower bound")) // harvested from the dialog
    .launch();                                       // execute  (or .renderGroovy())
```

`withLauncher(...)` returns a different builder type, so out-of-order calls
(dialog before launch, two launchers, forgetting to launch) simply don't
compile. The same plan feeds several **projections**: `launch()` runs it,
`renderGroovy()` renders the headless reproduction, and — during a recorded demo
— the launch also contributes that reproduction to the timeline (see below).
Adding a projection is writing a new reader of the same resolutions, not editing
the builder.

## How a recorded demo produces a video

A demo is an ordinary `main` that brackets its actions in narrated **steps** and
runs commands through visible launchers on a real Fiji:

```java
GroovyScript.uninstall();
new GroovyScript().install();          // embed the headless script in the timeline
Assets.session("MyTutorial");
Timeline.setIntro("My tutorial", "What we'll do", List.of("highlight one"));

Step.begin("apply-blur", "We run Gaussian Blur from the search bar.");
Step.say("Typing the command name.");
CmdExecutor.of(context, GaussianBlur.class)
    .preSet("imp", selectActiveImage("blobs.gif"))
    .withLauncher(searchLauncher("gaussian blur"))   // visible: drives the real UI
    .postSet("sigma", fromDialog(2.0, "A 2-pixel radius."))
    .launch();
Step.end();

Timeline.setOutro("Thanks!", commands, links);
```

Each `Step.begin … end` pair writes three artifacts into
`target/video-assets/<session>/`:

- **`NNN-step.mp4`** — a screen-capture clip of the step
  (`core.ScreenRecorder`, ffmpeg `gdigrab`).
- **`timeline.json`** — the machine-readable record (`core.Timeline`): chapter
  titles, narration sub-steps (`Step.say`), every cursor/key gesture with timing
  (emitted by `core.Ui` for Robot actions and captured by `core.EventRecorder`
  for manual ones), an optional intro/outro card, and the **embedded
  headless-equivalent Groovy script** (top-level `script.preamble` +
  per-step `script.body`, contributed automatically by each `launch()` via
  `groovy.GroovyScript`).
- **`NNN-step.png`** — key-moment screenshots (`core.Screenshotter`).

A downstream video pipeline (outside this repo) consumes `timeline.json` + the
clips to render the final tutorial: click highlights, cursor overlays, subtitles
from the narration, and title/closing cards from the intro/outro.

Three switches gate the heavy parts for fast iteration — and for headless tests:
`ScreenRecorder.ENABLED`, `Screenshotter.ENABLED`, `Timeline.ENABLED`. With only
`Timeline` enabled, a demo produces a complete, deterministic `timeline.json`
with **no ffmpeg, no display capture and no PNGs**.

### Try it (no display, no Fiji)

[`RecordingLayerDemo`](src/test/java/ch/unige/biochem/fiji/robot/core/RecordingLayerDemo.java)
is a self-contained, headless worked example: it installs a `GroovyScript`,
brackets two narrated steps, runs a command through the programmatic launcher
(which auto-records its script body), simulates the gestures a visible run would
emit, and writes a `timeline.json`.

```bash
mvn -q test-compile exec:java -Dexec.classpathScope=test \
    -Dexec.mainClass=ch.unige.biochem.fiji.robot.core.RecordingLayerDemo
```

The output it produces is committed as a sample asset:
[`docs/sample-timeline.json`](docs/sample-timeline.json). (We keep the small text
artifact rather than binary clips/PNGs — it is the format the pipeline actually
consumes, and stays diffable in git.)

## Concepts

- **`InputResolution`** — one `@Parameter`, owning its own value / narration and
  its optional projections. `PreSetResolution` (before launch) and
  `DialogResolution` (harvested after launch) are distinguished only so the
  grammar can keep the two phases apart at compile time. A resolution opts into
  the **visible** projection by implementing `Gesture` and into the **script**
  projection by implementing `GroovyRenderable`.
- **`Launcher`** — the single point at which the command fires:
  `programmaticLauncher()` (headless), or a visible launcher (search bar, menu,
  source-tree right-click) that drives the harvester dialog afterwards. A
  launcher may contribute inputs it resolves as a side effect of its gesture
  (e.g. a tree right-click contributing `"sources"`).
- **`Resolutions` / `Launchers`** — static factories, meant to be star-imported.

### Why "before launch" needs no in-chain machinery

A SciJava preprocessor (active image, active BDV) resolves its input *during*
the run, before the harvester dialog appears. If the ambient UI state is
established *before* `CommandService.run` is called (what `preSet` does), it
survives into the preprocessor chain and the real preprocessor picks it up. So
the temporal model is just **pre-set → launch → dialog** — two phases and a
pivot — which is exactly the builder grammar.

## Visible widget layer

`widgets.Harvester` drives a SciJava command's input-harvester dialog with
`java.awt.Robot`, located by reflecting each `@Parameter`'s label:

```java
Harvester.run(context, MyCommand.class, "doIt", true, "name", "hello")
         .get().getOutput("result");
```

Supported widgets: checkbox (`boolean`), spinner/slider/scrollbar (`Number`),
text field / combo / radio group (`String`), and the `File` / `File[]` chooser
flows. Widgets core can't know about plug in through the `WidgetDriver`
extension point (see *Extending it*); the BDV source-tree / handle-list widgets
register that way from the BDV binding.

## ImageJ1 binding (`…robot.ij1`)

The IJ1 binding lives in this repo but is quarantined in the `…robot.ij1`
package — the only code allowed to import `ij.*` / `net.imagej.*`. It adds two
factories that mirror the core ones:

```java
import static ch.unige.biochem.fiji.robot.ij1.Ij1Resolutions.selectActiveImage;
import static ch.unige.biochem.fiji.robot.ij1.Ij1Launchers.searchLauncher;

CmdExecutor.of(context, MyImageCommand.class)
    .preSet("imp", selectActiveImage("blobs.gif"))   // active-image preset
    .withLauncher(searchLauncher("my command"))       // launch via the search bar
    .postSet("radius", fromDialog(3.0, "We set the radius."))
    .launch();
```

`searchLauncher(query)` is a *visible* launcher: it runs the pre-set gestures,
types the query into Fiji's legacy search bar, then drives the dialog with the
harvester. `selectActiveImage(title)` resolves the `ImagePlus` by title for the
headless run, activates that window for the visible run so ImageJ's
`LegacyImagePreprocessor` picks it up, and renders as `WindowManager.getImage(title)`
in the script projection. Choosing the launcher *is* choosing the mode — there is
no global visible/programmatic switch.

## BigDataViewer binding (`…robot.bdv`)

The BDV binding is quarantined the same way — the only subtree that imports
`bdv.*` / `sc.fiji.*`. It splits along the two capabilities the library offers:
`…robot.bdv.command` connects BDV to the command executor (`BdvLaunchers`,
`BdvResolutions`, and the harvester source-widget drivers `BdvWidgets`), while
`…robot.bdv.view` holds standalone Robot drivers for a BDV window (`Bdv` —
expand the card panel, pick a source, set a display range, drag the timepoint
slider), the kind of thing a demo script does *between* command runs.

```java
import static ch.unige.biochem.fiji.robot.bdv.command.BdvLaunchers.treeLauncher;
import static ch.unige.biochem.fiji.robot.bdv.command.BdvResolutions.selectActiveBdv;

CmdExecutor.of(context, MyBdvCommand.class)
    .preSet("bdvh", selectActiveBdv("BDV alpha"))       // active-BDV preset
    .withLauncher(treeLauncher("my-dataset>channel 0")) // launch from the source tree
    .postSet("adjust", fromDialog(true, "We re-center the view."))
    .launch();
```

`treeLauncher(path)` right-clicks the BigDataViewer-Playground "BDV Sources"
tree, walks the context menu to the command, then drives the dialog — and
contributes the `"sources"` input (the tree path) so `renderGroovy()` reproduces
it headlessly. Variants: `treeLauncher()` (right-click the root, for commands
that take no sources) and `treeLauncher(path...)` (Ctrl+click multi-select).
`selectActiveBdv(title)` is the exact mirror of `selectActiveImage`.

## Extending it

The architecture is "matrix → diagonal": each capability is a small interface a
class opts into, so a new feature is one new class rather than edits across the
executor, the renderer and the recorder.

- **A new input kind** — implement `InputResolution` (`PreSetResolution` or
  `DialogResolution`). Add `Gesture` if it can be driven visibly, and
  `GroovyRenderable` if its value isn't a faithful literal (an object resolved
  from a selector/title is not reversible, so it must carry and render its
  original spec — see `selectActiveImage` / `selectActiveBdv`). Each projection
  reads it; nothing else changes.
- **A new dialog widget** — implement `widgets.WidgetDriver` and
  `Harvester.registerDriver(...)`. Drivers match by container *shape* + value
  type, so a binding plugs in widgets core can't know about (this is how
  `bdv.command.BdvWidgets` adds the source-tree / handle-list widgets without
  core importing `bdv.*`).
- **A new launch gesture** — implement `Launcher` (and `contributedInputs(...)`
  if the gesture resolves an input as a side effect, like the tree launcher's
  `"sources"`).
- **A new projection of a run** — read the same ordered resolutions
  (`LaunchRequest`). `launch()`, `renderGroovy()` and the timeline-script
  embedding are the existing three.

Not yet implemented (contributions welcome — details in [`HANDOFF.md`](HANDOFF.md)):
a menu-bar launcher (`MenuDriver` / `widgets.MenuBar`), multi-window placement
presets (`Layout`), a `Demo` authoring facade (intro/outro + a generated
`youtube-description.md`), a `selectActiveBvv` counterpart, and human-readable
`#@File` labels in the rendered script.

## Scope / dependencies

Core depends on `scijava-common` and `scijava-ui-swing` (the harvester it
drives). The `ij1` package adds `imagej-legacy`; the `bdv` package adds
`bigdataviewer-playground`. Each binding's dependency is used only by its own
package, so the core stays toolkit-free. The recording layer
(`core.ScreenRecorder`) shells out to `ffmpeg` (on `PATH`) only when recording is
enabled — it is pure JDK otherwise.

## Build

```bash
mvn clean install                            # compiles everything; runs headless tests

# Headless (CI-safe) — no display, no Fiji:
mvn test -Dtest=CmdExecutorTest              # the type-state backbone + renderGroovy
mvn test -Dtest=RecordingTimelineTest        # the timeline.json v4 shape
mvn test -Dtest=RecordingScriptAdapterTest   # the GroovyScript -> timeline adapter
mvn test -Dtest=GroovyHoistRenderTest        # #@File hoisting + dedup
mvn test -Dtest=ActiveImageRenderTest        # object-valued render with no image open
mvn test -Dtest=TreeLauncherRenderTest       # the bdv "sources" contribution

# GUI (run locally; each boots its own ImageJ gateway and drives the screen):
mvn test -Dtest=HarvesterWidgetsTest         # the harvester widgets
mvn test -Dtest=SearchLauncherTest           # ij1 search-bar launch
mvn test -Dtest=TreeLauncherTest             # bdv source-tree right-click → dialog
mvn test -Dtest=SourceWidgetsTest            # bdv source widgets, end to end
```

The GUI tests synthesize real mouse / keyboard events (and boot a full Fiji), so
they are meant to be run locally, not in headless CI. Run one GUI test class per
JVM — each boots its own ImageJ gateway.

## Links

- [`HANDOFF.md`](HANDOFF.md) — design decisions, current state, and roadmap.
- [`docs/sample-timeline.json`](docs/sample-timeline.json) — a sample of the
  primary output artifact.
- [`RecordingLayerDemo`](src/test/java/ch/unige/biochem/fiji/robot/core/RecordingLayerDemo.java)
  — the self-contained headless demo that produces it.
