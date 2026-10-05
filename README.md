[![Build Status](https://github.com/unige-biochem/fiji-robot/actions/workflows/build.yml/badge.svg)](https://github.com/unige-biochem/fiji-robot/actions/workflows/build.yml)

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
  for manual ones), **camera focus regions** (`focus.window` / `focus.dialog` /
  `focus.clear` — the screen rect the downstream renderer should frame with its
  auto-zoom; emitted automatically by the frame-placement helpers, the
  harvester dialog drive and the BDV view drivers, or explicitly via
  `Ui.focus(window)` / `Ui.focusClear()` for passive holds), an optional
  intro/outro card, and the **embedded headless-equivalent Groovy script**
  (top-level `script.preamble` + per-step `script.body`, contributed
  automatically by each `launch()` via `groovy.GroovyScript`).
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

## Painted pictures and reproducible runs

For pictures that must not depend on the screen (documentation figures taken
over remote desktop, with the window covered or the screen locked),
`widgets.Painter` paints Swing components into images as on screen, on the
event dispatch thread, instead of grabbing the screen like `Screenshotter`:

```java
BufferedImage window = Painter.paintWindowContentWhenStable(frame, 1, 3000, 60_000); // menu bar + content, no title bar
BufferedImage card   = Painter.paint(panel, 1);                                     // any component
BufferedImage menu   = Painter.paintMenu(frame.getJMenuBar(), 1, "File", "Save");   // a closed menu, item highlighted
Painter.write(window, new File("window.png"));
```

`paintWhenStable` repaints until the picture stops changing, for components
that render progressively (a BigDataViewer viewer). `paintMenu` lays out and
paints the popup of a menu without opening it. A scale above 1 suits viewers
and overlays, not Swing text: labels laid out at 1x get cut when painted
bigger. `bdv.view.BdvCards` expands, collapses and paints the cards of a
BigDataViewer card panel, a card whole even when it is scrolled out of view.
`widgets.CommandDialogPainter` paints the dialog of a command without
running it, in a live context, some inputs preset: its choices are the real ones.

Animations are filmed the same way, frame by frame (a stop-motion film), never
recorded from the screen:

```java
Film film = new Film(new File("target/frames"));
StopMotion camera = new StopMotion(film, viewer, region, 1)
        .setScene(() -> Painter.paintWhenStable(viewer, region, 1, 300, 60_000));
VirtualPointer pointer = camera.pointer(viewer, new Point(500, 400))
        .addKeyEventDispatcher(BdvViewer.keyEventDispatcher());  // BigDataViewer tracks keys globally
pointer.holdKeys(InputEvent.SHIFT_DOWN_MASK, "Shift");
pointer.drag(MouseEvent.BUTTON1, 700, 600, 900);                // a frame every 40 ms of the film
pointer.releaseKeys();
pointer.click(MouseEvent.BUTTON3);                               // a context menu: the camera paints it
pointer.choose("Set as Key Slice", 700);                         // moves to the item, highlights it, clicks it
film.writeGif(new File("drag.gif"));                             // or writeMp4(file, fps), through ffmpeg
```

`VirtualPointer` sends synthetic mouse events to one component and key events
to its key listeners (and to the dispatchers given), so the real pointer, the
focus and the visible screen do not matter. `StopMotion` draws the pointer, a
ring while a button is pressed, the keys held, and the popup menu opened on the
component. Each frame lasts the time the script gives it, whatever the time the
program took to reach it.

A **painted video** (the clips of a tutorial, for fiji-auto-tuto) films whole
windows the same way, on a desktop of a chroma-key color, and writes the
gestures to `timeline.json` on the clock of the film:

```java
StopMotion camera = new StopMotion(film, frame.getRootPane(), new Rectangle(-160, -80, 1920, 1080), 1)
        .onDesktop(new Color(0x8C16DB), frame.getTitle());       // the window under a drawn title bar
Timeline.clock = camera::time;                                     // times of the film, not of the wall
Timeline.recordingBounds = new Rectangle(0, 0, 1920, 1080);        // positions in pixels of the frames
VirtualPointer pointer = camera.pointer(frame.getRootPane(), new Point(800, 500));
pointer.chooseInMenuBar(frame.getJMenuBar(), "Import", "Open Demo Slices From ZENODO");
camera.showWindow(dialog, new Point(600, 300));                    // a dialog it opened, where the film puts it
new PaintedDialog(dialog, pointer).set("Slice channels", "0").set("Ensemble", false).ok();
film.writeMp4(new File("001-import.mp4"), 30);                     // a clip lasting the film exactly
```

The camera paints the menu cascades where they open, the list of a combo box
next to it, and AWT windows (an ImageJ image) with their own title bar;
`VirtualPointer` records its clicks, drags and keys with `Timeline.recordClick`,
`recordDrag` and `recordKey`. `PaintedDialog` fills a SciJava dialog by its
labels, as `Harvester` does with the real mouse.

`core.InMemoryPreferences` keeps `java.util.prefs` in memory, empty at start:
a Fiji started with
`-Djava.util.prefs.PreferencesFactory=ch.unige.biochem.fiji.robot.core.InMemoryPreferences`
shows every remembered input (SciJava persisted parameters) at its default, and
leaves the user's preferences untouched.

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
mvn test -Dtest=PainterTest                  # painting components and closed menus offscreen
mvn test -Dtest=StopMotionTest              # synthetic events, filmed frames, GIF and MP4 (ffmpeg if on the PATH)
mvn test -Dtest=PaintedVideoTest            # a menu cascade and a dialog filmed on a desktop (needs a display, not the mouse)
mvn test -Dtest=InMemoryPreferencesTest      # the in-memory preference nodes

# GUI (run locally; each boots its own ImageJ gateway and drives the screen):
mvn test -Dtest=HarvesterWidgetsTest         # the harvester widgets
mvn test -Dtest=SearchLauncherTest           # ij1 search-bar launch
mvn test -Dtest=TreeLauncherTest             # bdv source-tree right-click → dialog
mvn test -Dtest=SourceWidgetsTest            # bdv source widgets, end to end
mvn test -Dtest=CommandDialogPainterTest     # a command dialog painted offscreen
```

The GUI tests synthesize real mouse / keyboard events (and boot a full Fiji), so
they are meant to be run locally, not in headless CI. Run one GUI test class per
JVM — each boots its own ImageJ gateway. They are listed under
`maven-surefire-plugin/excludes` in `pom.xml`, so a bare `mvn test` runs the
headless set only; the explicit `-Dtest=` above overrides that exclusion. GitHub
Actions builds `main` and every PR with the standard SciJava CI scripts
(`.github/build.sh`), which is exactly that headless set.

## Links

- [`HANDOFF.md`](HANDOFF.md) — design decisions, current state, and roadmap.
- [`docs/sample-timeline.json`](docs/sample-timeline.json) — a sample of the
  primary output artifact.
- [`RecordingLayerDemo`](src/test/java/ch/unige/biochem/fiji/robot/core/RecordingLayerDemo.java)
  — the self-contained headless demo that produces it.
