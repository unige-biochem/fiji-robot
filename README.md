# SciJava UI Robot

A visible, recordable driver for SciJava commands. The goal is to run a command
the way a user would — establishing context, triggering it, filling its dialog —
so the run can be both **reproduced headlessly** and **recorded** (cursor
motion, subtitles, timeline) for tutorial videos.

> Status: early. This first slice establishes the design backbone and is
> deliberately tiny. The `java.awt.Robot` gesture layer and the BDV / IJ1
> bindings are not here yet.

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

## Scope / dependencies

Core depends only on `scijava-common`. No BDV, no bigdataviewer-playground, no
imagej-legacy — those belong to binding modules layered on top, so a consumer
that only needs harvester-dialog driving never pulls them in.

## Build

```bash
mvn clean install
mvn test
```

GUI-driven tests (the Robot layer) are a known hard problem for CI and are
deferred; everything here runs headless.
