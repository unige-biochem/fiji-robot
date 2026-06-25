# SciJava UI Robot

A visible, recordable driver for SciJava commands. The goal is to run a command
the way a user would — establishing context, triggering it, filling its dialog —
so the run can be both **reproduced headlessly** and **recorded** (cursor
motion, subtitles, timeline) for tutorial videos.

> Status: early. The type-state builder backbone is in place, plus the first
> slice of the `java.awt.Robot` widget layer (driving SciJava harvester dialogs)
> ported from the original tutorial-video toolkit. The recording layer
> (`timeline.json`, screenshots) and the BDV / IJ1 bindings are not here yet.

## The design in one builder

A command run is described as an ordered recipe whose grammar is enforced by the
compiler — pre-set inputs, then exactly one launcher, then dialog inputs, then a
single terminal `launch()`:

```java
import static ch.epfl.biop.scijava.ui.robot.Resolutions.*;
import static ch.epfl.biop.scijava.ui.robot.Launchers.*;

CmdExecutor.of(context, MyCommand.class)
    .preSet("a", programmatic(2))                  // resolved before launch
    .withLauncher(programmaticLauncher())          // the one trigger
    .postSet("min", fromDialog(0.0, "lower bound"))// harvested from the dialog
    .launch();                                      // execute  (or .renderGroovy())
```

`withLauncher(...)` returns a different builder type, so out-of-order calls
(dialog before launch, two launchers, forgetting to launch) simply don't
compile. The same plan feeds two **projections** today — `launch()` runs it,
`renderGroovy()` renders the headless reproduction — and is designed so further
projections (timeline, visible Robot execution) are added as new readers of the
same resolutions, not as edits to the builder.

## Concepts

- **`InputResolution`** — one `@Parameter`, owning its own value / narration /
  (later) gesture. `PreSetResolution` (before launch) and `DialogResolution`
  (harvested after launch) are distinguished only so the grammar can keep the
  two phases apart at compile time.
- **`Launcher`** — the single point at which the command fires. Today:
  `programmaticLauncher()`. Later: search-bar, menu, source-tree right-click —
  the visible launchers that drive the harvester dialog afterwards.
- **`Resolutions` / `Launchers`** — static factories, meant to be star-imported.

## Why "before launch" needs no in-chain machinery

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
flows. The BDV-specific widgets (source-tree, `BdvHandle[]`/`BvvHandle[]`) live
in the BDV binding module, not here.

The recording hooks (`core.Timeline`, `core.EventRecorder`, `core.Step`) are
present as no-op placeholders so the gesture primitives call them exactly as in
the original toolkit — fleshed out when the recording layer is ported.

## ImageJ1 binding (`…robot.ij1`)

The IJ1 binding lives in this repo but is quarantined in the `…robot.ij1`
package — the only code allowed to import `ij.*` / `net.imagej.*`. It adds two
factories that mirror the core ones:

```java
import static ch.epfl.biop.scijava.ui.robot.ij1.Ij1Launchers.searchLauncher;
import static ch.epfl.biop.scijava.ui.robot.ij1.Ij1Resolutions.selectActiveImage;

CmdExecutor.of(context, MyImageCommand.class)
    .preSet("imp", selectActiveImage("blobs.gif"))   // active-image preset
    .withLauncher(searchLauncher("my command"))      // launch via the search bar
    .postSet("radius", fromDialog(3.0, "We set the radius."))
    .launch();
```

`searchLauncher(query)` is a *visible* launcher: it runs the pre-set gestures,
types the query into Fiji's legacy search bar, then drives the dialog with the
harvester. `selectActiveImage(title)` resolves the `ImagePlus` by title for the
headless run, and activates that window for the visible run so ImageJ's
`LegacyImagePreprocessor` picks it up. Choosing the launcher *is* choosing the
mode — there is no global visible/programmatic switch.

Keeping this package free-standing is what makes a future split into a separate
module a matter of moving the package and the `imagej-legacy` dependency.

## Scope / dependencies

Core depends on `scijava-common` and `scijava-ui-swing` (the harvester it
drives). The `ij1` package adds `imagej-legacy`. No BDV, no
bigdataviewer-playground — those belong to binding modules layered on top.

## Build

```bash
mvn clean install      # compiles everything; runs the headless test
mvn test -Dtest=CmdExecutorTest        # headless backbone only
mvn test -Dtest=HarvesterWidgetsTest   # GUI widget tests — needs a local display
mvn test -Dtest=SearchLauncherTest     # GUI ij1 test — boots Fiji, needs a display
mvn test -Dtest=ActiveImagePresetTest  # GUI ij1 test — boots Fiji, needs a display
```

`HarvesterWidgetsTest` and the `ij1` tests synthesize real mouse / keyboard
events (and the ij1 ones boot a full Fiji), so they are meant to be run locally,
not in headless CI.
