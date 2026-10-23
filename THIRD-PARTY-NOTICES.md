# Third-party software

The application's Apache-2.0 license does not replace third-party licenses.

| Component | Selected license / source |
| --- | --- |
| Comcast/Oscar 3.7.1, commit d6f3e93c7695908bc56a80890b91ad62014561a5 | Apache-2.0; https://github.com/Comcast/Oscar/releases/tag/v3.7.1 |
| Qt Jambi 6.12.0 | LGPL-2.1 option; https://github.com/OmixVisualization/qtjambi/tree/v6.12.0 |
| Qt 6.12.0 Core, GUI, Widgets, DBus and their platform plugins | LGPL-3.0 option; https://download.qt.io/archive/qt/6.12/6.12.0/submodules/qtbase-everywhere-src-6.12.0.tar.xz |
| ICU 73.2 (Linux Qt dependency) | ICU/Unicode license; https://github.com/unicode-org/icu/releases/tag/release-73-2 |
| Bundled OpenJDK runtime | GPL-2.0 with Classpath Exception and its included third-party notices; see runtime/legal/ and the JDK vendor's source distribution |
| Oscar transitive dependencies | Original JAR license/notice files retained; see generated dependency inventory and bundled JARs |

Qt Jambi's published POM specifies LGPL-2.1 and GPL-3 alternatives. Qt's Core,
GUI, Widgets, and DBus documentation permits LGPL-3.0. We select the LGPL
options and do not use GPL-only Qt modules, Qt Quick, WebEngine, Charts, or
static linking. Qt's own third-party attributions must accompany distribution.

## Replacement and source availability

All application, Oscar, and Qt Jambi JARs remain separate; Qt shared libraries
and plugins are in `app/qt/` (Linux: `lib/app/qt/`). You may replace those JARs,
shared libraries, and plugins with ABI-compatible builds, including modified
builds. No signature/hash check prevents replacement. Qt Jambi's native JAR
can be replaced or its libraries supplied with `-Dio.qt.library-path-override`.
Reverse engineering for debugging modifications to LGPL components is permitted.
Keep the Qt minor version and compiler ABI compatible when replacing libraries.

For each redistributed binary release, distribute the exact corresponding
Qt/Qt Jambi/ICU source archives (including patches, if any) alongside the binary,
with required build instructions and license texts. Upstream source links alone
are not a permanent source-delivery guarantee: mirror the corresponding source
with each release. Release packages include corresponding Qt, Qt Jambi, and JDK
source archives inside app/sources/ (Linux: lib/app/sources/), along with a
SHA-256 manifest. Linux also includes ICU sources. Our binaries use unmodified
Qt/Qt Jambi.
The release checklist in docs/development/build/packaging.md includes source collection.

Qt sources contain additional third-party license material under LICENSES/ and
src/3rdparty/. Preserve these when distributing corresponding source. The bundled
Java runtime's `legal/` directory must remain intact. The complete corresponding
Temurin source archive (including its build scripts and license files) accompanies
the runtime in `app/sources/` (Linux: `lib/app/sources/`). Release checks validate
the source checksum and retained Java notice content against the build JDK. Windows bundles include app-local x64 Visual C++ runtime DLLs from the build host's Visual Studio
redistributable directory. Redistribution is subject to Microsoft's terms;
these DLLs are not relicensed by this project. Qt DLLs also appear beside the
Windows executable for native dependency lookup; replace both copies when
substituting compatible Qt builds.

Official references:
- https://raw.githubusercontent.com/OmixVisualization/qtjambi/v6.12.0/LICENSE.LGPLv21
- https://doc.qt.io/qt-6.12/qtcore-index.html#licenses-and-attributions
- https://doc.qt.io/qt-6.12/qtgui-index.html#licenses
- https://doc.qt.io/qt-6.12/qtwidgets-index.html#licenses
- https://doc.qt.io/qt-6.12/qtdbus-index.html#licenses
- https://doc.qt.io/qt-6.12/lgpl.html
