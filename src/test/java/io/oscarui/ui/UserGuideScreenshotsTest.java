/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.oscarui.service.Appearance;
import io.oscarui.service.OscarDocumentService;
import io.qt.core.QEventLoop;
import io.qt.core.QTimer;
import io.qt.gui.QAction;
import io.qt.widgets.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in documentation capture using real widgets and synthetic configuration data. */
@EnabledIfSystemProperty(named = "oscar.ui.screenshots", matches = "true")
class UserGuideScreenshotsTest {
    @TempDir Path temporary;
    private Throwable captureFailure;

    @Test void captureDarkUserGuide() throws Exception {
        QApplication.initialize(new String[0]);
        QApplication.setQuitOnLastWindowClosed(false);
        Path images = Path.of("target/user-guide-images");
        Files.createDirectories(images);
        var window = new MainWindow(false);
        try {
            DesktopTheme.apply(Appearance.DARK);
            window.resize(1200, 580);
            window.show();
            Path source = Files.writeString(temporary.resolve("example.txt"), """
                    Docsis {
                        NetworkAccess 1;
                        MaxCPEAllowed 3;
                        DownstreamServiceFlow {
                            ServiceFlowReference 1;
                            MaxSubstainedTrafficRate 1000000000;
                            MaxTrafficBurst 6000000;
                        }
                        UpstreamServiceFlow {
                            ServiceFlowReference 2;
                            MaxSubstainedTrafficRate 100000000;
                        }
                    }
                    """);
            window.openFile(source);
            waitForDocument(window);
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            tree.expandAll();
            window.findChild(QSplitter.class).setSizes(java.util.List.of(600, 600));
            tree.setColumnWidth(0, 300);
            tree.setColumnWidth(1, 90);
            capture(window, images.resolve("workspace-dark.png"));
            var service = new OscarDocumentService();
            var types = service.types();
            var create = new NewDocumentDialog(window, types);
            int docsisIndex = java.util.stream.IntStream.range(0, types.size())
                    .filter(index -> types.get(index).header().equals("Docsis"))
                    .findFirst().orElseThrow();
            create.findChild(QComboBox.class, "newFileType").setCurrentIndex(docsisIndex);
            captureDialog(create, images.resolve("new-file-dark.png"));
            captureDialog(new SaveFormatDialog(window), images.resolve("save-format-dark.png"));
            tree.setCurrentItem(tree.topLevelItem(0).child(0));
            var timer = new QTimer(window);
            timer.timeout.connect(() -> {
                if (QApplication.activeModalWidget() instanceof QDialog dialog) {
                    timer.stop();
                    try {
                        capture(dialog, images.resolve("edit-tlv-dark.png"));
                    } catch (Throwable failure) {
                        captureFailure = failure;
                    } finally {
                        dialog.reject();
                    }
                }
            });
            timer.start(30);
            window.findChild(QAction.class, "editTlv").trigger();
            timer.dispose();
            assertNull(captureFailure);
            var selected = window.currentDocument().root().childTlvs().get(2);
            var hex = new io.oscarui.service.SelectedTlvHexService().inspect(window.currentDocument(), selected);
            captureDialog(new TlvHexDialog(window, hex, false), images.resolve("selected-tlv-hex-dark.png"));
            captureDialog(new TlvDescriptionDialog(window, hex.name(), hex.description()), images.resolve("tlv-description-dark.png"));
            captureDialog(new GlobalSettingsDialog(window, false, true, Appearance.DARK), images.resolve("global-settings-dark.png"));
            captureDialog(new SecretsDialog(window, new SessionSecrets()), images.resolve("secrets-dark.png"));
            captureDialog(new SharedSecretCheckDialog(window), images.resolve("shared-secret-check-dark.png"));
            captureDialog(new AboutDialog(window), images.resolve("about-dark.png"));
            byte[] binary = java.util.HexFormat.of().parseHex("030101120103190a0102000108043b9aca00ff00");
            captureDialog(new BinaryInspectionDialog(window, BinaryInspectionDialog.View.HEX_DUMP,
                    com.comcast.oscar.utilities.HexDump.dumpHexString(binary), false), images.resolve("hexdump-dark.png"));
            // The listing comes from OSCAR's decoder, not a hand-written report.
            Path binaryFile = Files.write(temporary.resolve("example.bin"), binary);
            window.openFile(binaryFile);
            waitForDocument(window);
            captureInspection(window, images.resolve("tlv-breakdown-dark.png"));
            captureEditingSections(window, source, images);
        } finally {
            window.close();
            window.dispose();
            QApplication.shutdown();
        }
    }

    private void captureEditingSections(MainWindow window, Path source, Path images) throws Exception {
        window.openFile(source);
        waitForDocument(window);
        var tree = window.findChild(QTreeWidget.class, "configurationTree");
        tree.setCurrentItem(tree.topLevelItem(0));
        captureAction(window, "addTlv", images.resolve("add-tlv-dark.png"), dialog -> {
            dialog.findChild(QLineEdit.class, "tlvSearch").setText("DownstreamFrequency");
            dialog.findChild(QLineEdit.class, "tlvValue").setText("500000000");
        });
        tree.setCurrentItem(tree.topLevelItem(0).child(2));
        window.findChild(QAction.class, "duplicateTlv").trigger();
        waitForDocument(window);
        tree.expandAll();
        assertEquals(5, window.currentDocument().root().childTlvs().size());
        capture(window, images.resolve("duplicate-tlv-dark.png"));
        captureAction(window, "deleteTlv", images.resolve("delete-tlv-dark.png"), dialog -> {});
        Path snmpSource = Files.writeString(temporary.resolve("snmp-example.txt"),
                "Docsis { NetworkAccess 1; Snmp11 1.3.6.1.2.1.1.5.0 OctetString \"lab-modem\"; }");
        window.openFile(snmpSource);
        waitForDocument(window);
        tree.setCurrentItem(tree.topLevelItem(0).child(1));
        captureAction(window, "editTlv", images.resolve("snmp-fields-dark.png"), dialog -> {
            dialog.resize(680, 520);
            assertNotNull(dialog.findChild(QLineEdit.class, "snmpOid"));
            assertNotNull(dialog.findChild(QComboBox.class, "snmpType"));
            assertNotNull(dialog.findChild(QLineEdit.class, "snmpValue"));
        });
    }

    private void captureAction(MainWindow window, String actionName, Path destination,
                               java.util.function.Consumer<QDialog> prepare) {
        var loop = new QEventLoop();
        var finished = new java.util.concurrent.atomic.AtomicBoolean();
        var timer = new QTimer(window);
        var deadline = new QTimer(window);
        deadline.setSingleShot(true);
        deadline.timeout.connect(() -> {
            captureFailure = new AssertionError("Timed out capturing " + actionName);
            finished.set(true);
            loop.quit();
            if (QApplication.activeModalWidget() instanceof QDialog dialog) {
                dialog.reject();
            }
        });
        timer.timeout.connect(() -> {
            if (QApplication.activeModalWidget() instanceof QDialog dialog) {
                timer.stop();
                deadline.stop();
                try {
                    prepare.accept(dialog);
                    capture(dialog, destination);
                } catch (Throwable failure) {
                    captureFailure = failure;
                } finally {
                    dialog.reject();
                    finished.set(true);
                    loop.quit();
                }
            }
        });
        try {
            var action = window.findChild(QAction.class, actionName);
            assertTrue(action.isEnabled(), actionName);
            timer.start(30);
            deadline.start(10000);
            action.trigger();
            if (!finished.get()) {
                loop.exec();
            }
            assertNull(captureFailure);
            assertTrue(Files.exists(destination));
        } finally {
            timer.dispose();
            deadline.dispose();
            loop.dispose();
        }
    }

    private void captureInspection(MainWindow window, Path destination) {
        var timer = new QTimer(window);
        timer.timeout.connect(() -> {
            if (QApplication.activeModalWidget() instanceof QDialog dialog) {
                timer.stop();
                try {
                    capture(dialog, destination);
                } catch (Throwable failure) {
                    captureFailure = failure;
                } finally {
                    dialog.reject();
                }
            }
        });
        timer.start(30);
        window.findChild(QAction.class, "inspectTlvBreakdown").trigger();
        var loop = new QEventLoop();
        var deadline = new QTimer(loop);
        deadline.setSingleShot(true);
        deadline.timeout.connect(loop::quit);
        timer.timeout.connect(() -> {
            if (!timer.isActive()) {
                loop.quit();
            }
        });
        deadline.start(10000);
        loop.exec();
        loop.dispose();
        timer.dispose();
        assertNull(captureFailure);
        assertTrue(Files.exists(destination));
    }

    private void waitForDocument(MainWindow window) {
        var loop = new QEventLoop();
        var poll = new QTimer(loop);
        var deadline = new QTimer(loop);
        poll.timeout.connect(() -> {
            if (window.completed()) {
                loop.quit();
            }
        });
        deadline.setSingleShot(true);
        deadline.timeout.connect(loop::quit);
        poll.start(20);
        deadline.start(10000);
        loop.exec();
        loop.dispose();
        assertTrue(window.completed() && window.succeeded(), "Synthetic document must load successfully");
    }

    private void captureDialog(QDialog dialog, Path destination) {
        try {
            dialog.show();
            capture(dialog, destination);
        } finally {
            dialog.close();
            dialog.dispose();
        }
    }

    private void capture(QWidget widget, Path destination) {
        QApplication.processEvents();
        var pixmap = widget.grab();
        try {
            assertFalse(pixmap.isNull());
            assertTrue(pixmap.save(destination.toString()));
        } finally {
            pixmap.dispose();
        }
    }
}
