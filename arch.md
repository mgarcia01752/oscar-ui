# Oscar Desktop Architecture

## Starter Scope

A small Qt Jambi desktop wrapper around the unmodified Oscar 3.1.0 Java
library. Use Oscar's API in-process rather than spawning its CLI. Oscar's
release thin JAR and embedded Maven POM provide the library and dependency
metadata until the artifact is available through Maven Central.

The initial window has a native menu bar, a selected-file label, a read-only
configuration view, and inspection status. No web server is required.

| Menu | Initial actions |
| --- | --- |
| File | Open configuration text, Exit |
| Edit | Copy, Select All |
| About | About Oscar Desktop, About Qt |

Use Qt standard keyboard shortcuts. Copy is available only with a selection;
Select All is available only when content is present. Opening is disabled
while an inspection is running. File-dialog cancellation leaves state intact.

## Responsibilities

- `DesktopApplication`: initialize Qt, run the event loop, and provide the
  headless smoke-test entry point.
- `ui/MainWindow`: own widgets, menus, dialogs, and display state. Widget
  operations stay on the Qt event thread.
- `service/OscarInspectionService`: read bounded UTF-8 input and call Oscar's
  configuration import API. Return `Inspection` or `InspectionException`.
- The single inspection worker keeps parsing off the UI thread. A Qt timer
  receives completed results; closing cancels pending work and stops polling.
- Bootstrap scripts pin and checksum-verify the release. The Maven wrapper
  resolves dependencies in `.m2/`; Qt and Python tooling live in `.tools/`.
- `install.sh --development` checks system prerequisites, creates ignored
  `development/` scratch space, prepares dependencies, and builds the app.

## Boundaries

The starter is read-only. Do not expose Save, Cut, Paste, signing, binary
encoding, or full validation until the corresponding service exists. Syntax
inspection is not proof of value validity, integrity, or interoperability.
SNMP statements remain unsupported because the current upstream integration
can create a working-directory cache. Do not log configuration contents.

## Incremental Plan

1. Deliver the starter window, functional menus, and Oscar text inspection.
2. Verify service tests and a real Qt headless smoke run, including menus,
   selection, clipboard behavior, and file display.
3. Add binary decoding and structured inspection through separate service
   operations after agreeing their supported configuration families.
4. Add editing, validation, and save workflows only after defining format,
   integrity, error handling, and round-trip test requirements.

## Verification

Run `./scripts/desktop.sh build` followed by `./scripts/desktop.sh smoke`.
The build runs JUnit service tests; smoke launches Qt offscreen against a
synthetic public configuration. Also check normal file dialogs, menu placement,
and window closing on Linux and Windows desktops before distribution.
