/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import static org.junit.jupiter.api.Assertions.*;
import io.oscarui.service.OscarDocumentService;
import io.qt.core.*;
import io.qt.gui.QAction;
import io.qt.widgets.*;
import java.nio.file.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "oscar.ui.tests", matches = "true")
class MainWindowTest {
    @Test void upgradeChipOnlyAppearsForNewerReleasesAndOpensUpdates() throws Exception {
        var clicked = new java.util.concurrent.atomic.AtomicBoolean();
        var chip = new UpgradeChip(() -> clicked.set(true));
        try {
            assertTrue(chip.isHidden());
            chip.check(() -> new io.oscarui.service.ReleaseUpdateService.ReleaseCheck("New release", null, true));
            await(() -> !chip.isHidden());
            // The callback must remain owned by the chip after constructor return.
            System.gc();
            chip.click();
            assertTrue(clicked.get());
            chip.setAvailable(false);
            assertTrue(chip.isHidden());
            chip.check(() -> { throw new java.io.IOException("offline"); });
            await(() -> !chip.findChild(io.qt.core.QTimer.class).isActive());
            assertTrue(chip.isHidden());
            chip.check(() -> new io.oscarui.service.ReleaseUpdateService.ReleaseCheck("Current", null, false));
            await(() -> !chip.findChild(io.qt.core.QTimer.class).isActive());
            assertTrue(chip.isHidden());
        } finally {
            chip.stop();
            chip.dispose();
        }
        var window = new MainWindow(false);
        try {
            assertNotNull(window.findChild(QPushButton.class, "upgradeChip"));
            assertTrue(window.findChild(QPushButton.class, "upgradeChip").isHidden());
        } finally {
            window.close();
            window.dispose();
        }
    }

    @Test void certificateAddAndEditUseMultilineValidatedInput() throws Exception {
        var service = new OscarDocumentService();
        var document = service.open(Files.writeString(temporary.resolve("cert.txt"), "Docsis { NetworkAccess 1; }"));
        for (String name : java.util.List.of("ManufacturerCVC", "Co-signerCVC")) {
            var definition = service.choices(document, document.root()).stream()
                    .filter(entry -> entry.name().equals(name)).findFirst().orElseThrow();
            var dialog = new TlvEditorDialog(null, service, document, document.root(), java.util.List.of(definition));
            try {
                dialog.show();
                var input = dialog.findChild(QPlainTextEdit.class, "certificateText");
                var add = dialog.findChild(QPushButton.class, "confirmAddTlv");
                assertFalse(add.isEnabled());
                input.setPlainText("-----BEGIN CERTIFICATE-----\nMAH/\n-----END CERTIFICATE-----");
                assertTrue(add.isEnabled());
                assertEquals("30:01:FF", dialog.selection().value());
                input.setPlainText("broken");
                assertFalse(add.isEnabled());
                input.setPlainText("30 01 FF");
                assertTrue(add.isEnabled());
                document = service.insert(document, document.root(), definition, dialog.selection().value());
            } finally {
                dialog.dispose();
            }
            var node = document.root().childTlvs().stream().filter(entry -> entry.tlvName().equals(name))
                    .findFirst().orElseThrow();
            var edit = new TlvEditorDialog(null, service, document, document.root(), java.util.List.of(definition), node);
            try {
                edit.show();
                assertEquals("30:01:FF", edit.selection().value());
                assertTrue(edit.findChild(QPushButton.class, "confirmEditTlv").isEnabled());
            } finally {
                edit.dispose();
            }
        }
    }

    @Test void ubuntuCancelWaitsForWorkerAndDiscardsCompletedDownload() throws Exception {
        var installer = new io.oscarui.service.UbuntuUpdateService.Installer("0.4.0",
                java.net.URI.create(io.oscarui.service.ReleaseUpdateService.UI_PROJECT
                        + "/releases/download/v0.4.0/oscar-ui_amd64.deb"), 1, "sha256:" + "0".repeat(64));
        Path download = Files.createTempDirectory(temporary, "canceled-").resolve("update.deb");
        Files.write(download, new byte[]{1});
        var releaseWorker = new java.util.concurrent.CountDownLatch(1);
        var dialog = new UbuntuUpdateDialog(null, installer, new io.oscarui.service.UbuntuUpdateService(), () -> {
            releaseWorker.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return download;
        });
        try {
            dialog.show();
            dialog.reject();
            assertTrue(dialog.isVisible());
            releaseWorker.countDown();
            await(() -> !dialog.isVisible());
            assertFalse(Files.exists(download));
        } finally {
            releaseWorker.countDown();
            dialog.dispose();
        }
    }

    @Test void ubuntuDownloadRequiresExplicitInstallAndCleansUpOnCancel() throws Exception {
        var installer = new io.oscarui.service.UbuntuUpdateService.Installer("0.4.0",
                java.net.URI.create(io.oscarui.service.ReleaseUpdateService.UI_PROJECT
                        + "/releases/download/v0.4.0/oscar-ui_amd64.deb"), 1, "sha256:" + "0".repeat(64));
        Path download = Files.createTempDirectory(temporary, "download-").resolve("update.deb");
        Files.write(download, new byte[]{1});
        var dialog = new UbuntuUpdateDialog(null, installer, new io.oscarui.service.UbuntuUpdateService(), () -> download);
        try {
            dialog.show();
            await(() -> dialog.findChild(QPushButton.class, "installUbuntuUpdate").isEnabled());
            assertEquals(download, dialog.downloadedPackage());
            dialog.reject();
            assertFalse(Files.exists(download));
            assertEquals(QDialog.DialogCode.Rejected.value(), dialog.result());
        } finally {
            dialog.dispose();
        }
    }

    @Test void binaryFailureReportIsReadOnlyAndCopyable() {
        String report = "000000  45  9  Unknown [UNKNOWN_TYPE]";
        var dialog = new BinaryFailureDialog(null, report);
        try {
            assertEquals("Binary import failed", dialog.windowTitle());
            var output = dialog.findChild(QPlainTextEdit.class, "binaryFailureReport");
            assertTrue(output.isReadOnly());
            assertEquals(report, output.toPlainText());
            dialog.findChild(QPushButton.class, "copyBinaryFailureReport").click();
            assertEquals(report, QApplication.clipboard().text());
        } finally {
            dialog.dispose();
        }
    }

    @TempDir Path temporary;
    private Throwable callbackFailure;

    @BeforeAll static void initializeQt() {
        QApplication.initialize(new String[0]);
        QApplication.setQuitOnLastWindowClosed(false);
    }
    @AfterAll static void shutdownQt() { QApplication.shutdown(); }

    @Test void revealsSharedSecretsOnlyWhileHeldInBothDialogs() {
        var secrets = new SessionSecrets();
        for (QDialog dialog : new QDialog[]{new SecretsDialog(null, secrets), new SharedSecretCheckDialog(null)}) {
            dialog.show();
            try {
                String fieldName = dialog instanceof SecretsDialog ? "sharedSecret" : "checkSharedSecret";
                var field = dialog.findChild(QLineEdit.class, fieldName);
                var reveal = dialog.findChild(QToolButton.class, fieldName + "Reveal");
                field.setText("synthetic-secret");
                assertEquals(QLineEdit.EchoMode.Password, field.echoMode());
                assertFalse(reveal.icon().isNull());
                reveal.pressed.emit();
                assertEquals(QLineEdit.EchoMode.Normal, field.echoMode());
                reveal.released.emit();
                assertEquals(QLineEdit.EchoMode.Password, field.echoMode());
                reveal.pressed.emit();
                QCoreApplication.sendEvent(dialog, new QEvent(QEvent.Type.WindowDeactivate));
                assertEquals(QLineEdit.EchoMode.Password, field.echoMode());
                reveal.pressed.emit();
                QCoreApplication.sendEvent(reveal, new QEvent(QEvent.Type.Leave));
                assertEquals(QLineEdit.EchoMode.Password, field.echoMode());
                field.setEnabled(false);
                reveal.pressed.emit();
                assertEquals(QLineEdit.EchoMode.Password, field.echoMode());
            } finally {
                dialog.close();
                dialog.dispose();
            }
        }
    }

    @Test void checksSharedSecretInMaskedIndependentDialog() throws Exception {
        var documents = new OscarDocumentService();
        var binary = new io.oscarui.service.OscarBinaryService(documents);
        Path source = Files.writeString(temporary.resolve("source.txt"), "Docsis { NetworkAccess 1; }");
        Path binaryPath = temporary.resolve("check.bin");
        binary.saveDocument(binary.openAuto(source), binaryPath, null, "public-test-key");
        var dialog = new SharedSecretCheckDialog(null);
        dialog.show();
        try {
            var secret = dialog.findChild(QLineEdit.class, "checkSharedSecret");
            var file = dialog.findChild(QLineEdit.class, "checkBinaryPath");
            var check = dialog.findChild(QPushButton.class, "runSharedSecretCheck");
            var result = dialog.findChild(QLabel.class, "sharedSecretCheckResult");
            assertEquals(QLineEdit.EchoMode.Password, secret.echoMode());
            assertFalse(check.isEnabled());
            file.setText(binaryPath.toString());
            secret.setText("public-test-key");
            check.click();
            await(() -> result.text().equals("Correct Shared Secret"));
            assertTrue(secret.text().isEmpty());
            secret.setText("wrong-key");
            check.click();
            await(() -> result.text().equals("Incorrect Shared Secret"));
            file.setText(source.toString());
            secret.setText("public-test-key");
            check.click();
            await(() -> result.text().startsWith("Unable to Check:"));
        } finally {
            dialog.close();
            dialog.dispose();
        }
    }

    @Test void packetCableSnmp64UsesStructuredAddAndEdit() throws Exception {
        var service = new OscarDocumentService();
        var packetCableTypes = service.types().stream()
                .filter(configurationType -> configurationType.family().equals("packetcable")).toList();
        assertFalse(packetCableTypes.isEmpty());
        for (var configurationType : packetCableTypes) {
            var document = service.create(configurationType, false);
            var definition = service.choices(document, document.root()).stream()
                    .filter(candidate -> candidate.name().equals("Snmp64")).findFirst().orElseThrow();
            var addDialog = new TlvEditorDialog(null, service, document, document.root(), java.util.List.of(definition));
            try {
                addDialog.show();
                var oidInput = addDialog.findChild(QLineEdit.class, "snmpOid");
                var typeSelector = addDialog.findChild(QComboBox.class, "snmpType");
                var valueInput = addDialog.findChild(QLineEdit.class, "snmpValue");
                var apply = addDialog.findChild(QPushButton.class, "confirmAddTlv");
                assertTrue(oidInput.isVisible());
                assertTrue(typeSelector.isVisible());
                assertTrue(valueInput.isVisible());
                assertFalse(addDialog.findChild(QLineEdit.class, "tlvValue").isVisible());
                assertEquals(com.comcast.oscar.ber.BERService.getDataTypeStringList(),
                        java.util.stream.IntStream.range(0, typeSelector.count()).mapToObj(typeSelector::itemText).toList());
                assertFalse(apply.isEnabled());
                oidInput.setText("1.3.6.1.2.1.1.5.0");
                typeSelector.setCurrentText("IpAddress");
                valueInput.setText("192.0.2.999");
                assertFalse(apply.isEnabled());
                valueInput.setText("192.0.2.1");
                assertTrue(apply.isEnabled());
                assertTrue(addDialog.findChild(QLabel.class, "snmpValueHint").text().contains("IPv4"));
                document = service.insert(document, document.root(), definition, addDialog.selection().value());
            } finally {
                addDialog.close();
                addDialog.dispose();
            }
            var snmpNode = document.root().childTlvs().stream()
                    .filter(node -> node.tlvName().equals("Snmp64")).findFirst().orElseThrow();
            var editDialog = new TlvEditorDialog(null, service, document, document.root(),
                    java.util.List.of(definition), snmpNode);
            try {
                editDialog.show();
                assertEquals(snmpNode.encodedValueText().split(" ", 2)[0],
                        editDialog.findChild(QLineEdit.class, "snmpOid").text());
                assertEquals("IpAddress", editDialog.findChild(QComboBox.class, "snmpType").currentText());
                var valueInput = editDialog.findChild(QLineEdit.class, "snmpValue");
                assertEquals("192.0.2.1", valueInput.text());
                assertTrue(editDialog.findChild(QPushButton.class, "confirmEditTlv").isEnabled());
                valueInput.setText("192.0.2.2");
                assertTrue(editDialog.findChild(QPushButton.class, "confirmEditTlv").isEnabled());
                var edited = service.replaceValue(document, snmpNode, editDialog.selection().value());
                assertTrue(edited.text().contains("Snmp64 " + editDialog.selection().value() + ";"));
            } finally {
                editDialog.close();
                editDialog.dispose();
            }
        }
    }

    @Test void structuredSnmpAddValidatesFieldsThroughOscar() throws Exception {
        var service = new OscarDocumentService();
        var configurationType = service.types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
        var document = service.create(configurationType, false);
        var definition = com.comcast.oscar.dictionary.TlvRegistry.forFamily(configurationType.family())
                .find(java.util.List.of(11));
        var dialog = new TlvEditorDialog(null, service, document, document.root(), java.util.List.of(definition));
        try {
            dialog.show();
            var oid = dialog.findChild(QLineEdit.class, "snmpOid");
            var type = dialog.findChild(QComboBox.class, "snmpType");
            var value = dialog.findChild(QLineEdit.class, "snmpValue");
            var apply = dialog.findChild(QPushButton.class, "confirmAddTlv");
            assertFalse(dialog.findChild(QLineEdit.class, "tlvValue").isVisible());
            assertEquals(com.comcast.oscar.ber.BERService.getDataTypeStringList(),
                    java.util.stream.IntStream.range(0, type.count()).mapToObj(type::itemText).toList());
            assertFalse(apply.isEnabled());
            oid.setText("1.3.6.1.2.1.1.5.0");
            type.setCurrentText("IpAddress");
            value.setText("192.0.2.999");
            assertFalse(apply.isEnabled());
            value.setText("192.0.2.1");
            assertTrue(apply.isEnabled());
            type.setCurrentText("HexString");
            value.setText("0:FF");
            assertFalse(apply.isEnabled());
            value.setText("00:01:FF");
            assertTrue(apply.isEnabled());
            type.setCurrentText("Integer32");
            value.setText("2147483648");
            assertFalse(apply.isEnabled());
            value.setText("-2147483648");
            assertTrue(apply.isEnabled());
            type.setCurrentText("Counter32");
            value.setText("-1");
            assertFalse(apply.isEnabled());
            value.setText("4294967295");
            assertTrue(apply.isEnabled());
            type.setCurrentText("Counter64");
            value.setText("1");
            assertFalse(apply.isEnabled(), "Pinned OSCAR grammar does not accept Counter64");
            type.setCurrentText("OctetString");
            value.setText("lab name");
            assertTrue(apply.isEnabled());
            assertEquals("1.3.6.1.2.1.1.5.0 OctetString \"lab name\"", dialog.selection().value());
            value.setText("bad\"quote");
            assertFalse(apply.isEnabled());
        } finally {
            dialog.close();
            dialog.dispose();
        }
    }

    @Test void editsSnmp11AfterTextIsSavedAsBinaryWithMasking() throws Exception {
        var documents = new OscarDocumentService();
        var binary = new io.oscarui.service.OscarBinaryService(documents);
        var textPath = Files.writeString(temporary.resolve("snmp.txt"),
                "Docsis { Snmp11 1.3.6.1.2.1.69.1.2.1.4.1 OctetString \"lab-community\"; }");
        var binaryPath = temporary.resolve("snmp.bin");
        binary.saveDocument(binary.openAuto(textPath), binaryPath, null, "public-test-key");
        var window = new MainWindow(false);
        window.show();
        try {
            window.openFile(binaryPath);
            await(() -> window.completed() && window.succeeded());
            var initialTree = window.findChild(QTreeWidget.class, "configurationTree");
            initialTree.setCurrentItem(initialTree.topLevelItem(0).child(0));
            respond(window, dialog -> {
                assertEquals("docsDevNmAccessCommunity.1",
                        dialog.findChild(QLineEdit.class, "snmpOid").text());
                assertEquals("OctetString", dialog.findChild(QComboBox.class, "snmpType").currentText());
                assertEquals("lab-community", dialog.findChild(QLineEdit.class, "snmpValue").text());
                dialog.reject();
            });
            action(window, "editTlv").trigger();
            assertCallback();
            respond(window, dialog -> {
                dialog.findChild(QCheckBox.class, "hashCommunityStrings").setChecked(true);
                dialog.findChild(QPushButton.class, "applySecrets").click();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            var snmp = tree.topLevelItem(0).child(0);
            tree.setCurrentItem(snmp);
            assertTrue(action(window, "editTlv").isEnabled());
            respond(window, dialog -> {
                var oid = dialog.findChild(QLineEdit.class, "snmpOid");
                var value = dialog.findChild(QLineEdit.class, "snmpValue");
                assertEquals("", oid.text());
                assertEquals("", value.text());
                assertEquals(QLineEdit.EchoMode.Password, oid.echoMode());
                assertEquals(QLineEdit.EchoMode.Password, value.echoMode());
                assertEquals(window.currentDocument().root().childTlvs().get(0).encodedValueText(),
                        ((TlvEditorDialog) dialog).selection().value());
                oid.setText("1.3.6.1.2.1.69.1.2.1.4.1");
                value.setText("replacement-lab");
                assertTrue(dialog.findChild(QPushButton.class, "confirmEditTlv").isEnabled());
                dialog.findChild(QPushButton.class, "confirmEditTlv").click();
            });
            action(window, "editTlv").trigger();
            await(() -> window.completed() && window.succeeded());
            assertCallback();
            assertTrue(window.currentDocument().text().contains("replacement-lab"));
            assertFalse(window.findChild(QPlainTextEdit.class, "configurationText")
                    .toPlainText().contains("replacement-lab"));
            binary.saveDocument(window.currentDocument(), binaryPath,
                    window.currentDocument().origin().diskHash(), "public-test-key");
            assertTrue(binary.openAuto(binaryPath).text().contains("replacement-lab"));
        } finally { window.close(); window.dispose(); }
    }

    @Test void snmpMaskingRefreshesTextTreeClipboardHexAndEditorWithoutChangingSource() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            String source = "Docsis { SNMPv1v2cCoexistenceConfig { SNMPv1v2cCommunityName lab-private; } "
                    + "Snmp11 1.3.6.1.4.1.55555.2.1 OctetString \"vendor-password\"; NetworkAccess 1; }";
            Path file = Files.writeString(temporary.resolve("masked.txt"), source);
            window.openFile(file);
            await(window::completed);
            assertTrue(window.succeeded());
            var original = window.currentDocument();
            respond(window, dialog -> {
                dialog.findChild(QCheckBox.class, "hashCommunityStrings").setChecked(true);
                var input = dialog.findChild(QLineEdit.class, "customSnmpObject");
                input.setText("1.3.6.1.4.1.55555.2");
                dialog.findChild(QPushButton.class, "addCustomSnmp").click();
                input.setText(".1.3.6.1.4.1.55555.2");
                dialog.findChild(QPushButton.class, "addCustomSnmp").click();
                assertEquals(1, dialog.findChild(QListWidget.class, "customSnmpRules").count());
                assertTrue(dialog.findChild(QLabel.class, "customSnmpValidation").text().contains("already"));
                dialog.findChild(QPushButton.class, "applySecrets").click();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
            var text = window.findChild(QPlainTextEdit.class, "configurationText");
            assertFalse(text.toPlainText().contains("lab-private"));
            assertFalse(text.toPlainText().contains("vendor-password"));
            text.selectAll();
            text.copy();
            assertFalse(QApplication.clipboard().text().contains("lab-private"));
            assertFalse(QApplication.clipboard().text().contains("vendor-password"));
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            var parent = tree.topLevelItem(0).child(0);
            assertEquals("********", parent.child(0).text(2));
            assertEquals("CustomSnmpSecret ********", tree.topLevelItem(0).child(1).text(2));
            tree.setCurrentItem(parent.child(0));
            assertEquals("SNMPv1v2cCommunityName ********;", text.textCursor().selectedText());
            respond(window, dialog -> {
                var value = dialog.findChild(QLineEdit.class, "tlvValue");
                assertEquals("", value.text());
                assertTrue(value.placeholderText().startsWith("********"));
                dialog.findChild(QPushButton.class, "confirmEditTlv").click();
            });
            tree.itemDoubleClicked.emit(parent.child(0), 0);
            await(window::completed);
            assertCallback();
            assertSame(original, window.currentDocument());
            tree.setCurrentItem(parent);
            var closed = new java.util.concurrent.atomic.AtomicBoolean();
            respond(window, dialog -> {
                var hex = dialog.findChild(QTreeWidget.class, "tlvHexTree").topLevelItem(0);
                assertEquals("FF FF FF FF", hex.text(1));
                assertEquals("FF FF FF FF", hex.child(0).text(1));
                dialog.reject();
                closed.set(true);
            });
            action(window, "viewTlvHex").trigger();
            await(closed::get);
            assertCallback();
            window.grab().save("target/snmp-masked-preview.png");
            respond(window, dialog -> {
                var list = dialog.findChild(QListWidget.class, "customSnmpRules");
                list.setCurrentRow(0);
                dialog.findChild(QPushButton.class, "removeCustomSnmp").click();
                assertEquals(0, list.count());
                dialog.reject();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
            respond(window, dialog -> {
                assertEquals(1, dialog.findChild(QListWidget.class, "customSnmpRules").count());
                dialog.findChild(QCheckBox.class, "hashCommunityStrings").setChecked(false);
                dialog.findChild(QPushButton.class, "applySecrets").click();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
            assertEquals(source, text.toPlainText());
            assertSame(original, window.currentDocument());
            assertEquals(source, Files.readString(file));
        } finally {
            window.close();
            window.dispose();
        }
    }

    @Test void maskedEditorAcceptsReplacementWithoutSavingThePlaceholder() throws Exception {
        var service = new OscarDocumentService();
        var document = service.open(Files.writeString(temporary.resolve("editor-secret.txt"),
                "Docsis { SNMPv1v2cCoexistenceConfig { SNMPv1v2cCommunityName lab-old; } }"));
        var parent = document.root().childTlvs().getFirst();
        var selected = parent.childTlvs().getFirst();
        var dialog = new TlvEditorDialog(null, service, document, parent,
                java.util.List.of(service.definition(document, selected)), selected);
        try {
            dialog.applyMask(new io.oscarui.service.SnmpSecretMask(true, java.util.List.of()));
            var field = dialog.findChild(QLineEdit.class, "tlvValue");
            assertEquals(QLineEdit.EchoMode.Password, field.echoMode());
            assertEquals("", field.text());
            assertEquals(selected.encodedValueText(), dialog.selection().value());
            field.setText("lab-new");
            assertEquals("lab-new", dialog.selection().value());
            var changed = service.replaceValue(document, selected, dialog.selection().value());
            assertTrue(changed.text().contains("lab-new"));
            assertFalse(changed.text().contains("********"));
        } finally {
            dialog.dispose();
        }
    }

    @Test void binaryHexRequiresAcknowledgmentAndBreakdownRemainsMasked() throws Exception {
        var service = new OscarDocumentService();
        var binary = new io.oscarui.service.OscarBinaryService(service);
        var source = service.open(Files.writeString(temporary.resolve("inspect-secret.txt"),
                "Docsis { SNMPv1v2cCoexistenceConfig { SNMPv1v2cCommunityName lab-private; } NetworkAccess 1; }"));
        Path file = Files.write(temporary.resolve("inspect-secret.bin"), binary.encode(source, "lab-test-key"));
        var window = new MainWindow(false);
        window.show();
        try {
            window.openFile(file);
            await(window::completed);
            assertTrue(window.succeeded());
            respond(window, dialog -> {
                dialog.findChild(QCheckBox.class, "hashCommunityStrings").setChecked(true);
                dialog.findChild(QPushButton.class, "applySecrets").click();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
            respond(window, dialog -> {
                var prompt = (QMessageBox) dialog;
                assertEquals(prompt.button(QMessageBox.StandardButton.Cancel), prompt.defaultButton());
                assertNull(dialog.findChild(QPlainTextEdit.class, "binaryInspectionText"));
                prompt.button(QMessageBox.StandardButton.Cancel).click();
            });
            action(window, "inspectHexDump").trigger();
            assertCallback();
            QApplication.processEvents();
            assertTrue(window.findChildren(BinaryInspectionDialog.class).isEmpty());
            String expectedHex = com.comcast.oscar.utilities.HexDump.dumpHexString(Files.readAllBytes(file));
            for (String actionName : java.util.List.of("inspectHexDump", "inspectTlvBreakdown")) {
                var closed = new java.util.concurrent.atomic.AtomicBoolean();
                respondToInspection(window, actionName, dialog -> {
                    String listing = dialog.findChild(QPlainTextEdit.class, "binaryInspectionText").toPlainText();
                    if (actionName.equals("inspectHexDump")) {
                        assertEquals(expectedHex, listing);
                    } else {
                        assertTrue(listing.contains("SNMPv1v2cCoexistenceConfig | FF FF FF FF"));
                        assertFalse(listing.contains("6C:61:62"));
                        assertFalse(listing.contains("lab-private"));
                    }
                    dialog.reject();
                    closed.set(true);
                });
                action(window, actionName).trigger();
                await(closed::get);
                assertCallback();
            }
        } finally {
            window.close();
            window.dispose();
        }
    }

    @Test void customSnmpRulesValidateNamesAndRemainSessionOnly() {
        var secrets = new SessionSecrets();
        var dialog = new SecretsDialog(null, secrets);
        try {
            var input = dialog.findChild(QLineEdit.class, "customSnmpObject");
            var add = dialog.findChild(QPushButton.class, "addCustomSnmp");
            input.setText("bad;command");
            add.click();
            assertFalse(dialog.findChild(QLabel.class, "customSnmpValidation").text().isEmpty());
            input.setText("docsDevNmAccessIp");
            add.click();
            assertEquals(1, dialog.findChild(QListWidget.class, "customSnmpRules").count());
            dialog.findChild(QPushButton.class, "applySecrets").click();
            assertEquals(1, secrets.customRules().size());
            secrets.close();
            assertTrue(secrets.customRules().isEmpty());
        } finally {
            dialog.dispose();
        }
    }

    @Test void selectsAppendedSourceChildWhenBinaryOrderDiffers() throws Exception {
        var documents = new OscarDocumentService();
        var document = documents.open(Files.writeString(temporary.resolve("child-order.txt"),
                "Docsis { DownstreamServiceFlow { MaxTrafficBurst 6; ServiceFlowReference 2; } }"));
        var pane = new ConfigurationTreePane();
        try {
            pane.displayRoot(document.root(), true);
            pane.selectLastChild(document.root().childTlvs().getFirst());
            assertEquals("ServiceFlowReference", pane.selectedTlv().tlvName());
        } finally {
            pane.dispose();
        }
    }

    @Test void displaysCompiledCommunityRowsAndHighlightsOnlySelectedStatement() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            window.openFile(Path.of("cm-config/examples/docsis/1gbps-ds-100mbps-us.txt"));
            await(window::completed);
            assertTrue(window.succeeded());
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            var root = tree.topLevelItem(0);
            assertEquals(20, root.childCount());
            for (int index = 1; index <= 16; index++) {
                assertEquals("Snmp11", root.child(index).text(0));
                assertEquals("11", root.child(index).text(1));
                assertFalse(root.child(index).text(2).contains("/*"));
                assertFalse(root.child(index).text(2).contains("Snmp11"));
            }
            tree.setCurrentItem(root.child(9));
            var text = window.findChild(QPlainTextEdit.class, "configurationText");
            String selected = text.textCursor().selectedText();
            assertTrue(selected.startsWith("Snmp11 "));
            assertTrue(selected.contains("private"));
            assertFalse(selected.contains("/*"));
            window.resize(1200, 700);
            window.grab().save("target/binary-tlv-tree.png");
        } finally {
            window.close();
            window.dispose();
        }
    }

    @Test void failedTextCompilationKeepsTheDisplayedDocument() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            window.openFile(Files.writeString(temporary.resolve("good.txt"), "Docsis { NetworkAccess 1; }"));
            await(window::completed);
            var original = window.currentDocument();
            String displayed = window.findChild(QPlainTextEdit.class, "configurationText").toPlainText();
            window.openFile(Files.writeString(temporary.resolve("bad.txt"),
                    "Docsis { UpstreamServiceFlow { ServiceFlowReference 1; QualityOfServiceParameterSetType 7; } }"));
            await(window::completed);
            assertFalse(window.succeeded());
            assertSame(original, window.currentDocument());
            assertEquals(displayed, window.findChild(QPlainTextEdit.class, "configurationText").toPlainText());
            assertTrue(window.findChild(QLabel.class, "operationStatus").text().startsWith("OSCAR text compilation failed:"));
        } finally {
            window.close();
            window.dispose();
        }
    }

    @Test void selectedTlvHexShowsOnlyTheChosenSubtree() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            assertFalse(action(window, "viewTlvHex").isEnabled());
            window.openFile(Files.writeString(temporary.resolve("hex.txt"),
                    "Docsis { DownstreamServiceFlow { ServiceFlowReference 1; } "
                    + "DownstreamServiceFlow { ServiceFlowReference 2; } }"));
            await(window::completed);
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            tree.setCurrentItem(tree.topLevelItem(0).child(1));
            assertTrue(action(window, "viewTlvHex").isEnabled());
            var closed = new java.util.concurrent.atomic.AtomicBoolean();
            respond(window, dialog -> {
                var hexTree = dialog.findChild(QTreeWidget.class, "tlvHexTree");
                assertEquals("Name", hexTree.headerItem().text(0));
                assertEquals("Hex", hexTree.headerItem().text(1));
                assertEquals(1, hexTree.topLevelItemCount());
                var parent = hexTree.topLevelItem(0);
                assertEquals("19:04:01:02:00:02", parent.text(1));
                assertEquals("01:02:00:02", parent.child(0).text(1));
                assertTrue(parent.isExpanded());
                dialog.grab().save("target/selected-tlv-hex.png");
                respond(window, descriptionDialog -> {
                    assertEquals("tlvDescriptionDialog", descriptionDialog.objectName());
                    assertEquals("Description - ServiceFlowReference", descriptionDialog.windowTitle());
                    var description = descriptionDialog.findChild(QPlainTextEdit.class, "tlvDescriptionText");
                    assertTrue(description.isReadOnly());
                    assertFalse(description.toPlainText().isBlank());
                    assertFalse(description.toPlainText().contains("No description is available"));
                    descriptionDialog.reject();
                });
                hexTree.itemDoubleClicked.emit(parent.child(0), 0);
                assertTrue(parent.isExpanded());
                dialog.reject();
                closed.set(true);
            });
            action(window, "viewTlvHex").trigger();
            assertFalse(action(window, "viewTlvHex").isEnabled());
            await(closed::get);
            assertCallback();
        } finally {
            window.close();
            window.dispose();
        }
    }

    @Test void oscarCommentsDefaultOnAndGlobalTogglePreservesSource() throws Exception {
        var window = new MainWindow(false);
        window.show();

        try {
            window.openFile(Files.write(temporary.resolve("comments.bin"), java.util.HexFormat.of().parseHex("030101ff00")));
            await(window::completed);
            var text = window.findChild(QPlainTextEdit.class, "configurationText");
            String source = window.currentDocument().text();
            assertTrue(text.toPlainText().contains("/* TLV: [3]"));
            assertEquals(source.replace("\r\n", "\n"), text.toPlainText());
            window.grab().save("target/text-tlv-numbers.png");
            respond(window, dialog -> {
                var checkbox = dialog.findChild(QCheckBox.class, "showTextTlvNumbersCheckbox");
                assertTrue(checkbox.isChecked());
                checkbox.setChecked(false);
                dialog.reject();
            });
            action(window, "globalSettings").trigger();
            assertCallback();
            assertTrue(text.toPlainText().contains("/* TLV: [3]"));
            respond(window, dialog -> {
                dialog.findChild(QCheckBox.class, "showTextTlvNumbersCheckbox").setChecked(false);
                dialog.accept();
            });
            action(window, "globalSettings").trigger();
            assertCallback();
            assertFalse(text.toPlainText().contains("/* TLV: [3]"));
            assertEquals(source, window.currentDocument().text());
            assertTrue(text.toPlainText().contains("NetworkAccess 1;"));
            respond(window, dialog -> {
                dialog.findChild(QCheckBox.class, "showTextTlvNumbersCheckbox").setChecked(true);
                dialog.accept();
            });
            action(window, "globalSettings").trigger();
            assertCallback();
            assertTrue(text.toPlainText().contains("/* TLV: [3]"));
        } finally {
            window.close();
            window.dispose();
        }
    }

    @Test void aboutShowsLinksAndChecksWithoutBlockingUi() throws Exception {
        var result = new java.util.concurrent.CompletableFuture<String>();
        var dialog = new AboutDialog(null, result::get);
        dialog.show();
        try {
            var uninstall = dialog.findChild(QPushButton.class, "uninstallButton");
            assertFalse(uninstall.isVisible());
            assertFalse(dialog.uninstallRequested());
            var button = dialog.findChild(QPushButton.class, "checkUpdatesButton");
            var status = dialog.findChild(QLabel.class, "updateStatus");
            assertTrue(dialog.findChildren(QLabel.class).stream()
                    .anyMatch(label -> label.text().contains("https://github.com/Comcast/Oscar")));
            assertTrue(dialog.findChildren(QLabel.class).stream()
                    .anyMatch(label -> label.text().contains("https://github.com/mgarcia01752/oscar-ui")));
            var licenseLink = dialog.findChild(QLabel.class, "applicationLicenseLink");
            assertTrue(licenseLink.openExternalLinks());
            assertTrue(licenseLink.text().contains("https://github.com/mgarcia01752/oscar-ui/blob/main/LICENSE"));
            button.click();
            assertFalse(button.isEnabled());
            assertEquals("Checking GitHub…", status.text());
            result.complete("Update available: v0.2.0. Installed: 0.1.0.");
            await(button::isEnabled);
            assertTrue(status.text().startsWith("Update available"));
            dialog.grab().save("target/about-dialog.png");
        } finally {
            dialog.close();
            dialog.stopChecking();
            dialog.dispose();
        }
        var failed = new AboutDialog(null, () -> { throw new java.io.IOException("offline"); });
        failed.show();
        try {
            failed.checkUpdates();
            await(() -> failed.findChild(QPushButton.class, "checkUpdatesButton").isEnabled());
            assertTrue(failed.findChild(QLabel.class, "updateStatus").text().startsWith("Unable"));
        } finally {
            failed.close();
            failed.stopChecking();
            failed.dispose();
        }
    }

    @Test void appearancePersistsAndSystemChangesRespectOverrides() throws Exception {
        var store = new io.oscarui.service.AppearanceSettingsStore(temporary.resolve("settings.json"));
        var window = new MainWindow(false, store);
        window.show();
        try {
            respond(window, dialog -> {
                var choices = dialog.findChild(QComboBox.class, "appearanceMode");
                assertEquals("System", choices.currentText());
                assertEquals(3, choices.count());
                choices.setCurrentIndex(2);
                dialog.reject();
            });
            action(window, "globalSettings").trigger();
            assertCallback();
            assertFalse(Files.exists(temporary.resolve("settings.json")));
            respond(window, dialog -> {
                dialog.findChild(QComboBox.class, "appearanceMode").setCurrentIndex(2);
                dialog.accept();
            });
            action(window, "globalSettings").trigger();
            assertCallback();
            assertEquals(io.oscarui.service.Appearance.DARK, store.load());
            QApplication.styleHints().colorSchemeChanged.emit(Qt.ColorScheme.Light);
            QApplication.processEvents();
            assertTrue(QApplication.palette().color(io.qt.gui.QPalette.ColorRole.Window).lightness() < 128);
        } finally {
            window.close();
            window.dispose();
        }
        var reopened = new MainWindow(false, store);
        reopened.show();
        try {
            assertTrue(QApplication.palette().color(io.qt.gui.QPalette.ColorRole.Window).lightness() < 128);
            respond(reopened, dialog -> {
                var choices = dialog.findChild(QComboBox.class, "appearanceMode");
                assertEquals("Dark", choices.currentText());
                choices.setCurrentIndex(0);
                dialog.accept();
            });
            action(reopened, "globalSettings").trigger();
            assertCallback();
            assertEquals(io.oscarui.service.Appearance.SYSTEM, store.load());
            QApplication.styleHints().colorSchemeChanged.emit(Qt.ColorScheme.Dark);
            QApplication.processEvents();
            assertTrue(QApplication.palette().color(io.qt.gui.QPalette.ColorRole.Window).lightness() < 128);
            QApplication.styleHints().colorSchemeChanged.emit(Qt.ColorScheme.Light);
            QApplication.processEvents();
            assertTrue(QApplication.palette().color(io.qt.gui.QPalette.ColorRole.Window).lightness() > 128);
        } finally {
            reopened.close();
            reopened.dispose();
        }
    }

    @Test void inspectMenuFollowsActiveFileAndDisplaysOscarListings() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            assertEquals(java.util.List.of("&File", "&Edit", "&Settings", "&Inspect", "&Tools", "&About"),
                    window.menuBar().actions().stream().map(QAction::text).toList());
            var inspectMenu = window.findChild(QMenu.class, "inspectMenu");
            assertFalse(inspectMenu.isEnabled());
            assertFalse(inspectMenu.menuAction().isEnabled());
            var hoverPosition = window.menuBar().actionGeometry(inspectMenu.menuAction()).center();
            for (QEvent.Type eventType : java.util.List.of(QEvent.Type.MouseMove,
                    QEvent.Type.MouseButtonPress, QEvent.Type.MouseButtonRelease)) {
                boolean moving = eventType == QEvent.Type.MouseMove;
                var mouseEvent = new io.qt.gui.QMouseEvent(eventType, new QPointF(hoverPosition),
                        new QPointF(window.menuBar().mapToGlobal(hoverPosition)),
                        moving ? Qt.MouseButton.NoButton : Qt.MouseButton.LeftButton,
                        new Qt.MouseButtons(eventType == QEvent.Type.MouseButtonPress
                                ? Qt.MouseButton.LeftButton : Qt.MouseButton.NoButton),
                        new Qt.KeyboardModifiers());
                try {
                    QApplication.sendEvent(window.menuBar(), mouseEvent);
                    if (moving) {
                        assertEquals("A binary file must be selected to inspect the file.", QToolTip.text());
                    }
                    assertFalse(inspectMenu.isVisible());
                } finally {
                    mouseEvent.dispose();
                }
            }
            QToolTip.hideText();
            assertFalse(action(window, "inspectHexDump").isEnabled());
            assertFalse(action(window, "inspectTlvBreakdown").isEnabled());
            byte[] bytes = java.util.HexFormat.of().parseHex("030101ff00");
            window.openFile(Files.write(temporary.resolve("inspect.bin"), bytes));
            await(() -> window.completed());
            assertTrue(inspectMenu.isEnabled());
            assertTrue(inspectMenu.menuAction().isEnabled());
            assertTrue(action(window, "inspectHexDump").isEnabled());
            assertTrue(action(window, "inspectTlvBreakdown").isEnabled());
            for (String actionName : java.util.List.of("inspectHexDump", "inspectTlvBreakdown")) {
                var closed = new java.util.concurrent.atomic.AtomicBoolean();
                respondToInspection(window, actionName, dialog -> {
                    try {
                        assertEquals("binaryInspectionDialog", dialog.objectName());
                        var output = dialog.findChild(QPlainTextEdit.class, "binaryInspectionText");
                        assertTrue(output.isReadOnly());
                        dialog.grab().save("target/ui-" + actionName + ".png");
                        assertEquals(actionName.equals("inspectHexDump")
                                ? com.comcast.oscar.utilities.HexDump.dumpHexString(bytes)
                                : "[Type:3] 03:01:01\n", output.toPlainText());
                    } finally {
                        dialog.reject();
                        closed.set(true);
                    }
                });
                action(window, actionName).trigger();
                assertFalse(action(window, actionName).isEnabled());
                await(closed::get);
            }
            window.openFile(Files.writeString(temporary.resolve("inspect.txt"), "Docsis { NetworkAccess 1; }"));
            await(() -> window.completed());
            assertFalse(inspectMenu.isEnabled());
            assertFalse(inspectMenu.menuAction().isEnabled());
            assertFalse(action(window, "inspectHexDump").isEnabled());
            assertFalse(action(window, "inspectTlvBreakdown").isEnabled());
        } finally {
            window.close();
            window.dispose();
        }
    }

    @Test void duplicatesServiceFlowAndSelectsCopy() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path file = Files.writeString(temporary.resolve("duplicate.txt"),
                    "Docsis { DownstreamServiceFlow { ServiceFlowReference 1; QualityOfServiceParameterSetType 07; } }");
            window.openFile(file);
            await(() -> window.completed() && window.succeeded());
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            assertFalse(action(window, "duplicateTlv").isEnabled());
            tree.setCurrentItem(tree.topLevelItem(0).child(0));
            assertTrue(action(window, "duplicateTlv").isEnabled());
            var original = window.currentDocument();
            action(window, "duplicateTlv").trigger();
            await(() -> window.completed() && window.currentDocument() != original);
            assertEquals(2, tree.topLevelItem(0).childCount());
            assertSame(tree.topLevelItem(0).child(1), tree.currentItem());
            assertEquals(2, tree.currentItem().childCount());
            assertTrue(tree.currentItem().isExpanded());
            assertTrue(action(window, "fileSave").isEnabled());
            tree.setCurrentItem(tree.currentItem().child(0));
            assertFalse(action(window, "duplicateTlv").isEnabled());
        } finally { window.close(); window.dispose(); }
    }

    @Test void embeddedParentsAppearInAddForTheirSupportedFamilies() throws Exception {
        var service = new OscarDocumentService();
        for (String header : java.util.List.of("Docsis", "Docsis-4.0")) {
            var type = service.types().stream().filter(entry -> entry.header().equals(header)).findFirst().orElseThrow();
            var document = service.create(type, false);
            var dialog = new TlvEditorDialog(null, service, document, document.root(), service.choices(document, document.root()));
            try {
                dialog.show();
                var table = dialog.findChild(QTreeWidget.class, "tlvChoices");
                var names = java.util.stream.IntStream.range(0, table.topLevelItemCount())
                        .mapToObj(i -> table.topLevelItem(i).text(1)).toList();
                assertTrue(names.contains("eRouterConfig"), header);
                assertEquals(header.equals("Docsis"), names.contains("eSTBConfig"));
                assertNotNull(dialog.findChild(QLabel.class, "tlvAvailabilityHint"));
            } finally {
                dialog.close();
                dialog.dispose();
            }
        }
    }

    @Test void tlvColumnsSortNumericallyAndAlphabeticallyWithoutChangingSelection() throws Exception {
        var service = new OscarDocumentService();
        var configurationType = service.types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
        var document = service.create(configurationType, false);
        var registry = com.comcast.oscar.dictionary.TlvRegistry.forFamily(configurationType.family());
        var choices = java.util.List.of(registry.find(java.util.List.of(18)),
                registry.find(java.util.List.of(2)), registry.find(java.util.List.of(1)));
        var dialog = new TlvEditorDialog(null, service, document, document.root(), choices);
        try {
            dialog.show();
            var table = dialog.findChild(QTreeWidget.class, "tlvChoices");
            assertEquals("TLV", table.headerItem().text(0));
            assertEquals("Name", table.headerItem().text(1));
            assertEquals(java.util.List.of("1", "2", "18"),
                    java.util.stream.IntStream.range(0, 3).mapToObj(i -> table.topLevelItem(i).text(0)).toList());
            table.setCurrentItem(table.topLevelItem(1));
            var selected = dialog.selection().definition();
            dialog.findChild(QLineEdit.class, "tlvValue").setText("1");
            table.header().setSortIndicator(0, Qt.SortOrder.DescendingOrder);
            assertEquals("18", table.topLevelItem(0).text(0));
            table.header().setSortIndicator(1, Qt.SortOrder.AscendingOrder);
            assertEquals("DownstreamFrequency", table.topLevelItem(0).text(1));
            table.header().setSortIndicator(1, Qt.SortOrder.DescendingOrder);
            assertEquals("UpstreamChannelID", table.topLevelItem(0).text(1));
            assertEquals(selected, dialog.selection().definition());
            assertEquals("1", dialog.selection().value());
            dialog.findChild(QLineEdit.class, "tlvSearch").setText("MaxCPE");
            assertEquals(1, table.topLevelItemCount());
            assertEquals("18", table.topLevelItem(0).text(0));
            assertEquals("MaxCPEAllowed", dialog.selection().definition().name());
            dialog.grab().save("target/ui-add-tlv-columns.png");
        } finally { dialog.close(); dialog.dispose(); }
    }

    @Test void deletesScalarAndWholeContainerFromEditorOnlyAfterConfirmation() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path file = Files.writeString(temporary.resolve("delete-editor.txt"),
                    "Docsis-4.0 { UpstreamServiceFlow { ServiceFlowReference 1; QosParameterSetType 7; } NetworkAccess 1; }");
            window.openFile(file);
            await(() -> window.completed() && window.succeeded());
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            for (int remaining = 2; remaining > 0; remaining--) {
                var original = window.currentDocument();
                boolean container = remaining == 2;
                tree.setCurrentItem(tree.topLevelItem(0).child(0));
                assertTrue(action(window, "editTlv").isEnabled());
                respond(window, editor -> {
                    assertEquals("editTlvDialog", editor.objectName());
                    assertEquals(!container, editor.findChild(QPushButton.class, "confirmEditTlv").isEnabled());
                    respond(window, confirmation -> {
                        var question = (QMessageBox) confirmation;
                        assertEquals(container, question.text().contains("All child TLVs"));
                        question.button(QMessageBox.StandardButton.Cancel).click();
                        assertSame(original, window.currentDocument());
                        respond(window, sameEditor -> {
                            assertSame(editor, sameEditor);
                            respond(window, accepted -> ((QMessageBox) accepted)
                                    .button(QMessageBox.StandardButton.Yes).click());
                            sameEditor.findChild(QPushButton.class, "deleteEditedTlv").click();
                        });
                    });
                    editor.findChild(QPushButton.class, "deleteEditedTlv").click();
                });
                action(window, "editTlv").trigger();
                await(() -> window.completed() && window.currentDocument() != original);
                assertCallback();
                assertEquals(remaining - 1, window.currentDocument().root().childTlvs().size());
                assertTrue(action(window, "fileSave").isEnabled());
            }
            assertFalse(window.currentDocument().text().contains("ServiceFlowReference"));
            assertTrue(Files.readString(file).contains("ServiceFlowReference"));
        } finally { window.close(); window.dispose(); }
    }

    @Test void editsClassifierErrorMessageWithOscar311Names() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path source = Files.writeString(temporary.resolve("error-message.txt"),
                    "Docsis { UpstreamPacketClassification { ClassifierError { ErrorMessage initial; } } }");
            window.openFile(source);
            await(() -> window.completed() && window.succeeded());
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            var entry = tree.topLevelItem(0).child(0).child(0).child(0);
            assertEquals("ErrorMessage", entry.text(0));
            respond(window, dialog -> {
                assertEquals("editTlvDialog", dialog.objectName());
                dialog.findChild(QLineEdit.class, "tlvValue").setText("example");
                assertTrue(dialog.findChild(QPushButton.class, "confirmEditTlv").isEnabled());
                dialog.findChild(QPushButton.class, "confirmEditTlv").click();
            });
            tree.itemDoubleClicked.emit(entry, 0);
            await(() -> window.completed() && window.currentDocument().text().contains("ErrorMessage example;"));
            assertCallback();
            action(window, "fileSave").trigger();
            await(() -> window.completed() && !action(window, "fileSave").isEnabled());
            assertTrue(Files.readString(source).contains("ErrorMessage example;"));
            window.openFile(source);
            await(() -> window.completed() && window.succeeded());
            respond(window, dialog -> {
                assertEquals("example", dialog.findChild(QLineEdit.class, "tlvValue").text());
                dialog.reject();
            });
            tree.itemDoubleClicked.emit(tree.topLevelItem(0).child(0).child(0).child(0), 0);
            assertCallback();
        } finally { window.close(); window.dispose(); }
    }

    @Test void hidesGeneratedMicsUntilEnabledAndKeepsThemReadOnly() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            String source = "Docsis { NetworkAccess 1; CmMic [00]; CmtsMic [00]; }";
            window.openFile(Files.writeString(temporary.resolve("mic.txt"), source));
            await(() -> window.completed() && window.currentDocument() != null);
            var original = window.currentDocument();
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            var root = tree.topLevelItem(0);
            var toggle = action(window, "showMicTlvs");
            assertTrue(toggle.isCheckable());
            assertFalse(toggle.isChecked());
            assertFalse(root.child(0).isHidden());
            assertTrue(root.child(1).isHidden());
            assertTrue(root.child(2).isHidden());
            toggle.trigger();
            for (int index = 1; index <= 2; index++) {
                var mic = root.child(index);
                assertFalse(mic.isHidden());
                tree.setCurrentItem(mic);
                assertFalse(action(window, "editTlv").isEnabled());
                assertFalse(action(window, "deleteTlv").isEnabled());
                tree.itemDoubleClicked.emit(mic, 0);
                assertSame(original, window.currentDocument());
            }
            toggle.trigger();
            assertSame(root, tree.currentItem());
            assertSame(original, window.currentDocument());
            assertEquals(source, window.currentDocument().text());
            var service = new OscarDocumentService();
            service.types();
            assertTrue(service.choices(original, original.root()).stream()
                    .noneMatch(entry -> service.isGeneratedMic(original.configurationType(), entry.path())));
            assertFalse(service.isGeneratedMic(original.configurationType(), java.util.List.of(24, 6)));
            assertFalse(window.windowTitle().contains("*"));
        } finally { window.close(); window.dispose(); }
    }

    @Test void addsImmediateChildrenAndPopulatesNestedContainersOnlyOnExplicitAction() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            var service = new OscarDocumentService();
            var configurationType = service.types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
            var registry = com.comcast.oscar.dictionary.TlvRegistry.forFamily(configurationType.family());
            var definition = registry.find(java.util.List.of(22));
            window.createDocument(configurationType, false);
            await(() -> window.completed() && window.currentDocument() != null);
            respond(window, dialog -> {
                dialog.findChild(QLineEdit.class, "tlvSearch").setText(definition.name());
                assertTrue(dialog.findChild(QPushButton.class, "confirmAddTlv").isEnabled());
                dialog.findChild(QPushButton.class, "confirmAddTlv").click();
            });
            action(window, "addTlv").trigger();
            await(() -> window.completed() && !window.currentDocument().root().childTlvs().isEmpty());
            assertCallback();
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            var classifier = tree.topLevelItem(0).child(0);
            assertEquals(registry.children(definition.path()).size(), classifier.childCount());
            assertTrue(classifier.isExpanded());
            assertSame(classifier, tree.currentItem());
            int nestedIndex = java.util.stream.IntStream.range(0, classifier.childCount())
                    .filter(index -> classifier.child(index).text(0).equals(registry.find(java.util.List.of(22, 8)).name()))
                    .findFirst().orElseThrow();
            var nested = classifier.child(nestedIndex);
            var original = window.currentDocument();
            tree.setCurrentItem(nested);
            nested.setExpanded(true);
            QApplication.processEvents();
            assertSame(original, window.currentDocument());
            assertEquals(0, nested.childCount());
            tree.itemDoubleClicked.emit(nested, 0);
            await(() -> window.completed() && window.currentDocument() != original);
            int childCount = registry.children(java.util.List.of(22, 8)).size();
            assertEquals(childCount, tree.topLevelItem(0).child(0).child(nestedIndex).childCount());
            assertTrue(tree.currentItem().isExpanded());
            window.grab().save("target/ui-container-scaffold.png");
            for (int remaining = childCount; remaining > 0; remaining--) {
                var current = window.currentDocument();
                tree.setCurrentItem(tree.topLevelItem(0).child(0).child(nestedIndex).child(0));
                assertTrue(action(window, "editTlv").isEnabled());
                assertTrue(action(window, "deleteTlv").isEnabled());
                respond(window, dialog -> ((QMessageBox) dialog).button(QMessageBox.StandardButton.Yes).click());
                action(window, "deleteTlv").trigger();
                await(() -> window.completed() && window.currentDocument() != current);
                assertCallback();
            }
            assertEquals(0, tree.topLevelItem(0).child(0).child(nestedIndex).childCount());
            var emptied = window.currentDocument();
            QApplication.processEvents();
            assertSame(emptied, window.currentDocument());
            action(window, "addTlv").trigger();
            await(() -> window.completed() && window.currentDocument() != emptied);
            assertEquals(childCount, tree.topLevelItem(0).child(0).child(nestedIndex).childCount());
            assertEquals(window.currentDocument().text(), window.findChild(QPlainTextEdit.class, "configurationText").toPlainText());
        } finally { window.close(); window.dispose(); }
    }

    @Test void opensBinaryThroughMenuAndSavesDecodedTextSeparately() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path input = Files.write(temporary.resolve("input.cfg"), java.util.HexFormat.of().parseHex("030101ff00"));
            respond(window, chooser -> {
                assertInstanceOf(QFileDialog.class, chooser);
                chooser.findChild(QLineEdit.class, "fileNameEdit").setText(input.toString());
                chooser.accept();
            });
            action(window, "fileOpen").trigger();
            await(() -> window.completed() && window.currentDocument() != null);
            assertCallback();
            var imported = window.currentDocument();
            assertNull(imported.path());
            assertEquals(input, imported.origin().path());
            assertEquals("OSCAR-UI", window.windowTitle());
            assertNull(window.findChild(QLabel.class, "documentName"));
            assertEquals("Binary", window.findChild(QLabel.class, "documentType").text());
            assertFalse(window.windowTitle().contains("Untitled"));
            assertTrue(window.findChild(QLabel.class, "operationStatus").isHidden());
            assertEquals("NetworkAccess", window.findChild(QTreeWidget.class, "configurationTree").topLevelItem(0).child(0).text(0));
            assertEquals(imported.text(), window.findChild(QPlainTextEdit.class, "configurationText").toPlainText());
            assertFalse(action(window, "fileSave").isEnabled());
            window.grab().save("target/ui-binary-open.png");
            respond(window, QDialog::reject);
            action(window, "fileOpen").trigger();
            assertCallback();
            assertSame(imported, window.currentDocument());
            Path text = temporary.resolve("decoded.txt");
            respond(window, chooser -> {
                assertInstanceOf(QFileDialog.class, chooser);
                chooser.findChild(QLineEdit.class, "fileNameEdit").setText(text.toString());
                chooser.accept();
            });
            action(window, "fileSaveAs").trigger();
            await(() -> window.completed() && text.equals(window.currentDocument().path()));
            assertCallback();
            assertEquals(imported.text(), Files.readString(text));
            assertEquals("OSCAR-UI", window.windowTitle());
            assertNull(window.findChild(QLabel.class, "documentName"));
            assertEquals("Text", window.findChild(QLabel.class, "documentType").text());
            assertTrue(window.findChild(QLabel.class, "operationStatus").isHidden());
            assertFalse(action(window, "fileSave").isEnabled());
            assertArrayEquals(java.util.HexFormat.of().parseHex("030101ff00"), Files.readAllBytes(input));
        } finally { window.close(); window.dispose(); }
    }

    @Test void openCancellationAndFailedDetectionPreserveDocument() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path text = Files.writeString(temporary.resolve("text.bin"), "Docsis { NetworkAccess 1; }");
            respond(window, chooser -> {
                assertInstanceOf(QFileDialog.class, chooser);
                chooser.findChild(QLineEdit.class, "fileNameEdit").setText(text.toString());
                chooser.accept();
            });
            action(window, "fileOpen").trigger();
            await(() -> window.completed() && window.currentDocument() != null);
            assertCallback();
            var original = window.currentDocument();
            assertEquals(text, original.path());
            respond(window, QDialog::reject);
            action(window, "fileOpen").trigger();
            assertCallback();
            assertSame(original, window.currentDocument());
            Path malformed = Files.write(temporary.resolve("broken.bin"), new byte[]{3, 10});
            window.openFile(malformed);
            await(() -> window.completed() && !window.succeeded());
            assertSame(original, window.currentDocument());
            assertFalse(action(window, "fileSave").isEnabled());
        } finally { window.close(); window.dispose(); }
    }

    @Test void createsInsertsSavesAndPreservesDocumentOnCancellation() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            var types = new OscarDocumentService().types();
            int index = java.util.stream.IntStream.range(0, types.size())
                    .filter(position -> types.get(position).header().equals("Docsis")).findFirst().orElseThrow();
            respond(window, dialog -> {
                assertEquals("newDocumentDialog", dialog.objectName());
                assertFalse(dialog.findChild(QPushButton.class, "createDocument").isEnabled());
                dialog.findChild(QComboBox.class, "newFileType").setCurrentIndex(index);
                dialog.grab().save("target/ui-new.png");
                dialog.findChild(QPushButton.class, "createDocument").click();
            });
            action(window, "fileNew").trigger();
            await(() -> window.completed() && window.currentDocument() != null);
            assertCallback();
            assertTrue(window.currentDocument().root().childTlvs().isEmpty());
            assertTrue(action(window, "fileSave").isEnabled());
            respond(window, dialog -> {
                assertEquals("addTlvDialog", dialog.objectName());
                dialog.findChild(QLineEdit.class, "tlvSearch").setText("NetworkAccess");
                assertFalse(dialog.findChild(QPushButton.class, "confirmAddTlv").isEnabled());
                dialog.findChild(QLineEdit.class, "tlvValue").setText("bad");
                assertFalse(dialog.findChild(QPushButton.class, "confirmAddTlv").isEnabled());
                dialog.findChild(QLineEdit.class, "tlvValue").setText("1");
                dialog.grab().save("target/ui-add-tlv.png");
                dialog.findChild(QPushButton.class, "confirmAddTlv").click();
            });
            action(window, "addTlv").trigger();
            await(() -> window.completed() && window.currentDocument().root().childTlvs().size() == 1);
            assertCallback();
            assertTrue(window.currentDocument().text().contains("NetworkAccess 1;"));
            assertEquals("NetworkAccess", window.findChild(QTreeWidget.class, "configurationTree").currentItem().text(0));
            Path destination = temporary.resolve("saved.txt");
            respond(window, dialog -> {
                assertInstanceOf(QFileDialog.class, dialog);
                dialog.findChild(QLineEdit.class, "fileNameEdit").setText(destination.toString());
                assertEquals(destination.toString(), ((QFileDialog) dialog).selectedFiles().get(0));
                dialog.accept();
            });
            action(window, "fileSave").trigger();
            await(() -> window.completed() && destination.equals(window.currentDocument().path()));
            assertCallback();
            assertEquals(window.currentDocument().text(), Files.readString(destination));
            assertFalse(action(window, "fileSave").isEnabled());
            var saved = window.currentDocument();
            respond(window, QDialog::reject);
            action(window, "fileOpen").trigger();
            assertSame(saved, window.currentDocument());
            respond(window, QDialog::reject);
            action(window, "fileNew").trigger();
            await(window::completed);
            assertCallback();
            assertSame(saved, window.currentDocument());
            respond(window, QDialog::reject);
            action(window, "fileSaveAs").trigger();
            assertSame(saved, window.currentDocument());
        } finally { window.close(); window.dispose(); }
    }

    @Test void opensPopupAfterLoadAndHonorsUnsavedCloseCancellation() throws Exception {
        var window = new MainWindow(true);
        window.show();
        try {
            Path file = temporary.resolve("input.txt");
            Files.writeString(file, "Docsis { NetworkAccess 1; }");
            respond(window, dialog -> {
                assertEquals("addTlvDialog", dialog.objectName());
                dialog.reject();
            });
            window.openFile(file);
            await(() -> window.completed() && window.currentDocument() != null && QApplication.activeModalWidget() == null);
            assertCallback();
            assertEquals(file, window.currentDocument().path());
            respond(window, QDialog::reject);
            window.createDocument(window.currentDocument().configurationType(), false);
            await(() -> window.completed() && window.currentDocument().path() == null && QApplication.activeModalWidget() == null);
            respond(window, dialog -> ((QMessageBox) dialog).button(QMessageBox.StandardButton.Cancel).click());
            window.close();
            assertCallback();
            assertTrue(window.isVisible());
            assertTrue(action(window, "fileSave").isEnabled());
            respond(window, dialog -> ((QMessageBox) dialog).button(QMessageBox.StandardButton.Discard).click());
            window.close();
            assertCallback();
            await(() -> !window.isVisible());
        } finally { window.dispose(); }
    }

    @Test void protectsExistingFilesAcrossOverwriteConfirmation() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            var configurationType = new OscarDocumentService().types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
            window.createDocument(configurationType, false);
            await(() -> window.completed() && window.currentDocument() != null);
            var original = window.currentDocument();
            Path destination = temporary.resolve("existing.txt");
            Files.writeString(destination, "original file");
            boolean[] answered = {false};
            saveAs(window, destination, dialog -> {
                ((QMessageBox) dialog).button(QMessageBox.StandardButton.No).click();
                answered[0] = true;
            });
            await(() -> answered[0] && window.completed());
            assertSame(original, window.currentDocument());
            assertEquals("original file", Files.readString(destination));
            assertTrue(action(window, "fileSave").isEnabled());
            saveAs(window, destination, dialog -> {
                try { Files.writeString(destination, "changed during confirmation"); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                ((QMessageBox) dialog).button(QMessageBox.StandardButton.Yes).click();
            });
            await(() -> window.completed() && !window.succeeded());
            assertEquals("changed during confirmation", Files.readString(destination));
            assertSame(original, window.currentDocument());
            assertTrue(action(window, "fileSave").isEnabled());
            saveAs(window, destination, dialog -> ((QMessageBox) dialog).button(QMessageBox.StandardButton.Yes).click());
            await(() -> window.completed() && destination.equals(window.currentDocument().path()));
            assertEquals(original.text(), Files.readString(destination));
            assertFalse(action(window, "fileSave").isEnabled());
        } finally { window.close(); window.dispose(); }
    }

    @Test void createsAllTlvsAndPreservesLoadedDocumentOnOpenFailure() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path file = temporary.resolve("sample.txt");
            Files.writeString(file, "Docsis { NetworkAccess 1; }");
            window.openFile(file);
            await(() -> window.completed() && window.succeeded());
            var loaded = window.currentDocument();
            window.openFile(temporary.resolve("missing.txt"));
            await(window::completed);
            assertFalse(window.succeeded());
            assertSame(loaded, window.currentDocument());
            assertEquals("NetworkAccess", window.findChild(QTreeWidget.class, "configurationTree").topLevelItem(0).child(0).text(0));
            respond(window, dialog -> {
                var combo = dialog.findChild(QComboBox.class, "newFileType");
                for (int index = 0; index < combo.count(); index++) {
                    if (combo.itemText(index).equals("Docsis-4.0")) combo.setCurrentIndex(index);
                }
                dialog.findChild(QRadioButton.class, "allTlvsMode").click();
                dialog.findChild(QPushButton.class, "createDocument").click();
            });
            action(window, "fileNew").trigger();
            await(() -> window.completed() && window.currentDocument().reference());
            assertEquals("Docsis-4.0", window.currentDocument().configurationType().header());
            assertNull(window.currentDocument().path());
            var template = window.currentDocument();
            respond(window, QDialog::reject);
            action(window, "addTlv").trigger();
            await(window::completed);
            assertSame(template, window.currentDocument());
        } finally { window.close(); window.dispose(); }
    }

    @Test void doubleClickEditsCurrentValueAndDeleteHonorsCancellation() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            String source = "Docsis {\r\n MaxCPEAllowed 3;\r\n MaxCPEAllowed 004;\r\n}";
            Path file = Files.writeString(temporary.resolve("edit.txt"), source);
            window.openFile(file);
            await(() -> window.completed() && window.succeeded());
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            var view = window.findChild(QPlainTextEdit.class, "configurationText");
            var item = tree.topLevelItem(0).child(1);
            tree.setCurrentItem(item);
            assertEquals("MaxCPEAllowed 004;", view.textCursor().selectedText());
            var original = window.currentDocument();
            respond(window, dialog -> {
                assertEquals("editTlvDialog", dialog.objectName());
                assertEquals("4", dialog.findChild(QLineEdit.class, "tlvValue").text());
                dialog.reject();
            });
            tree.itemDoubleClicked.emit(item, 0);
            assertCallback();
            assertSame(original, window.currentDocument());
            respond(window, dialog -> {
                var value = dialog.findChild(QLineEdit.class, "tlvValue");
                assertEquals("4", value.text());
                value.setText("5");
                dialog.grab().save("target/ui-edit-tlv.png");
                dialog.findChild(QPushButton.class, "confirmEditTlv").click();
            });
            tree.itemDoubleClicked.emit(item, 0);
            await(() -> window.completed() && window.currentDocument() != original);
            assertEquals(source.replace("004", "5"), window.currentDocument().text());
            assertEquals("5", tree.currentItem().text(2));
            assertTrue(action(window, "fileSave").isEnabled());
            var edited = window.currentDocument();
            respond(window, dialog -> ((QMessageBox) dialog).button(QMessageBox.StandardButton.Cancel).click());
            action(window, "deleteTlv").trigger();
            assertCallback();
            assertSame(edited, window.currentDocument());
            respond(window, dialog -> ((QMessageBox) dialog).button(QMessageBox.StandardButton.Yes).click());
            action(window, "deleteTlv").trigger();
            await(() -> window.completed() && window.currentDocument() != edited);
            assertEquals(1, tree.topLevelItem(0).childCount());
            assertSame(tree.topLevelItem(0), tree.currentItem());
            assertFalse(action(window, "deleteTlv").isEnabled());
            assertEquals(source, Files.readString(file));
        } finally { window.close(); window.dispose(); }
    }

    @Test void maskingPreferenceSurvivesRestartAndUpdatesStatus() throws Exception {
        var store = new io.oscarui.service.AppearanceSettingsStore(temporary.resolve("settings.json"));
        var window = new MainWindow(false, store);
        window.show();
        try {
            assertFalse(window.findChild(QLabel.class, "maskingStatus").isVisible());
            respond(window, dialog -> {
                dialog.findChild(QCheckBox.class, "hashCommunityStrings").setChecked(true);
                dialog.findChild(QLineEdit.class, "sharedSecret").setText("synthetic-session-key");
                dialog.findChild(QPushButton.class, "applySecrets").click();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
            assertTrue(store.loadObfuscateSnmpSecrets());
            assertTrue(window.findChild(QLabel.class, "maskingStatus").isVisible());
            assertTrue(window.findChild(QFrame.class, "maskingDivider").isVisible());
            assertFalse(Files.readString(temporary.resolve("settings.json")).contains("synthetic-session-key"));
        } finally { window.close(); window.dispose(); }
        var next = new MainWindow(false, store);
        next.show();
        try {
            assertTrue(next.findChild(QLabel.class, "maskingStatus").isVisible());
            respond(next, dialog -> {
                assertTrue(dialog.findChild(QCheckBox.class, "hashCommunityStrings").isChecked());
                assertEquals("", dialog.findChild(QLineEdit.class, "sharedSecret").text());
                dialog.findChild(QCheckBox.class, "hashCommunityStrings").setChecked(false);
                dialog.reject();
            });
            action(next, "settingsSecrets").trigger();
            assertCallback();
            assertTrue(store.loadObfuscateSnmpSecrets());
            respond(next, dialog -> {
                dialog.findChild(QCheckBox.class, "hashCommunityStrings").setChecked(false);
                dialog.findChild(QPushButton.class, "applySecrets").click();
            });
            action(next, "settingsSecrets").trigger();
            assertCallback();
            assertFalse(store.loadObfuscateSnmpSecrets());
            assertFalse(next.findChild(QLabel.class, "maskingStatus").isVisible());
            assertFalse(next.findChild(QFrame.class, "maskingDivider").isVisible());
        } finally { next.close(); next.dispose(); }
    }

    @Test void secretsAreMaskedAndOptionalChecksPreserveDocument() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            respond(window, dialog -> {
                assertEquals("secretsDialog", dialog.objectName());
                assertNull(dialog.findChild(QLineEdit.class, "communityString"));
                var shared = dialog.findChild(QLineEdit.class, "sharedSecret");
                assertEquals(QLineEdit.EchoMode.Password, shared.echoMode());
                shared.setText("public-test-key");
                dialog.findChild(QPushButton.class, "applySecrets").click();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
            respond(window, dialog -> {
                assertEquals("public-test-key", dialog.findChild(QLineEdit.class, "sharedSecret").text());
                dialog.findChild(QPushButton.class, "clearSecrets").click();
                dialog.reject();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
            window.openFile(Files.writeString(temporary.resolve("binary.txt"), "Docsis { NetworkAccess 1; }"));
            await(() -> window.completed() && window.succeeded());
            var document = window.currentDocument();
            respond(window, dialog -> {
                assertEquals("binaryCheckDialog", dialog.objectName());
                assertNull(dialog.findChild(QPushButton.class, "saveBinary"));
                dialog.findChild(QPushButton.class, "checkConfig").click();
                var checks = dialog.findChild(QTreeWidget.class, "configChecks");
                boolean sharedSet = false;
                for (int i = 0; i < checks.topLevelItemCount(); i++) {
                    if (checks.topLevelItem(i).text(1).equals("shared-secret"))
                        sharedSet = checks.topLevelItem(i).text(0).equals("SET");
                }
                assertTrue(sharedSet);
                dialog.grab().save("target/ui-check-config.png");
                dialog.reject();
            });
            action(window, "configChecks").trigger();
            assertCallback();
            assertSame(document, window.currentDocument());
            assertFalse(action(window, "fileSave").isEnabled());
            respond(window, dialog -> {
                dialog.findChild(QPushButton.class, "clearSecrets").click();
                dialog.findChild(QPushButton.class, "applySecrets").click();
            });
            action(window, "settingsSecrets").trigger();
            respond(window, dialog -> {
                assertTrue(dialog.findChild(QLineEdit.class, "sharedSecret").text().isEmpty());
                dialog.grab().save("target/ui-secrets.png");
                dialog.reject();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
        } finally { window.close(); window.dispose(); }
        var next = new MainWindow(false);
        try {
            respond(next, dialog -> {
                assertNull(dialog.findChild(QLineEdit.class, "communityString"));
                assertTrue(dialog.findChild(QLineEdit.class, "sharedSecret").text().isEmpty());
                dialog.reject();
            });
            action(next, "settingsSecrets").trigger();
            assertCallback();
        } finally { next.close(); next.dispose(); }
    }

    @Test void saveShortcutUsesTextWithoutFormatPrompt() throws Exception {
        var window = new MainWindow(false);
        window.show();
        window.activateWindow();
        QApplication.processEvents();
        try {
            var configurationType = new OscarDocumentService().types().stream().filter(t -> t.header().equals("Docsis")).findFirst().orElseThrow();
            window.createDocument(configurationType, false);
            await(() -> window.completed() && window.succeeded());
            Path destination = temporary.resolve("shortcut.txt");
            respond(window, chooser -> {
                assertInstanceOf(QFileDialog.class, chooser);
                chooser.findChild(QLineEdit.class, "fileNameEdit").setText(destination.toString());
                chooser.accept();
            });
            assertEquals(new io.qt.gui.QKeySequence(io.qt.gui.QKeySequence.StandardKey.Save),
                    action(window, "fileSave").shortcut());
            var editor = window.findChild(QPlainTextEdit.class, "configurationText");
            editor.setFocus();
            var keyPress = new io.qt.gui.QKeyEvent(QEvent.Type.KeyPress, Qt.Key.Key_S.value(),
                    new Qt.KeyboardModifiers(Qt.KeyboardModifier.ControlModifier));
            QApplication.sendEvent(editor, keyPress);
            keyPress.dispose();
            await(() -> window.completed() && destination.equals(window.currentDocument().path()));
            assertEquals(window.currentDocument().text(), Files.readString(destination));
        } finally { window.close(); window.dispose(); }
    }

    @Test void saveAsBinarySwitchesFormatAndSaveWritesBackWithoutPrompts() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path source = Files.writeString(temporary.resolve("source.txt"), "Docsis { NetworkAccess 1; }");
            window.openFile(source);
            await(() -> window.completed() && window.succeeded());
            Path output = temporary.resolve("output.bin");
            respond(window, dialog -> {
                assertEquals("secretsDialog", dialog.objectName());
                dialog.findChild(QLineEdit.class, "sharedSecret").setText("public-test-key");
                dialog.findChild(QPushButton.class, "applySecrets").click();
                respond(window, chooser -> {
                    assertInstanceOf(QFileDialog.class, chooser);
                    chooser.findChild(QLineEdit.class, "fileNameEdit").setText(output.toString());
                    chooser.accept();
                });
            });
            action(window, "fileSaveAsBinary").trigger();
            await(() -> window.completed() && Files.exists(output));
            assertCallback();
            assertNull(window.currentDocument().path());
            assertEquals(output, window.currentDocument().origin().path());
            assertEquals("Binary", window.findChild(QLabel.class, "documentType").text());
            assertFalse(action(window, "fileSave").isEnabled());
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            tree.setCurrentItem(tree.topLevelItem(0).child(0));
            respond(window, dialog -> {
                dialog.findChild(QLineEdit.class, "tlvValue").setText("0");
                dialog.findChild(QPushButton.class, "confirmEditTlv").click();
            });
            action(window, "editTlv").trigger();
            await(() -> window.completed() && window.currentDocument().text().contains("NetworkAccess 0"));
            action(window, "fileSave").trigger();
            await(() -> window.completed() && !action(window, "fileSave").isEnabled());
            assertArrayEquals(new byte[]{3, 1, 0}, java.util.Arrays.copyOf(Files.readAllBytes(output), 3));
            assertEquals("Docsis { NetworkAccess 1; }", Files.readString(source));
            var saved = window.currentDocument();
            respond(window, QDialog::reject);
            action(window, "fileSaveAsBinary").trigger();
            assertCallback();
            assertSame(saved, window.currentDocument());
            // Reopen the binary and save edits through the unsaved-change prompt.
            window.openFile(output);
            await(() -> window.completed() && window.succeeded());
            tree.setCurrentItem(tree.topLevelItem(0).child(0));
            respond(window, dialog -> {
                dialog.findChild(QLineEdit.class, "tlvValue").setText("1");
                dialog.findChild(QPushButton.class, "confirmEditTlv").click();
            });
            action(window, "editTlv").trigger();
            await(() -> window.completed() && window.currentDocument().text().contains("NetworkAccess 1"));
            respond(window, prompt -> {
                assertInstanceOf(QMessageBox.class, prompt);
                ((QMessageBox) prompt).button(QMessageBox.StandardButton.Save).click();
                respond(window, next -> {
                    assertInstanceOf(NewDocumentDialog.class, next);
                    next.reject();
                });
            });
            action(window, "fileNew").trigger();
            await(() -> window.completed() && !action(window, "fileSave").isEnabled());
            await(() -> QApplication.activeModalWidget() == null);
            assertCallback();
            assertArrayEquals(new byte[]{3, 1, 1}, java.util.Arrays.copyOf(Files.readAllBytes(output), 3));
            saved = window.currentDocument();
            Path other = Files.write(temporary.resolve("other.bin"), new byte[]{1});
            chooseBinaryDestination(window, other, dialog -> {
                try { Files.writeString(other, "changed during confirmation"); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                ((QMessageBox) dialog).button(QMessageBox.StandardButton.Yes).click();
            });
            await(() -> window.completed() && !window.succeeded());
            assertEquals("changed during confirmation", Files.readString(other));
            assertSame(saved, window.currentDocument());
        } finally { window.close(); window.dispose(); }
    }

    private void chooseBinaryDestination(MainWindow window, Path destination, Consumer<QDialog> confirmation) {
        respond(window, dialog -> {
            assertInstanceOf(QFileDialog.class, dialog);
            dialog.findChild(QLineEdit.class, "fileNameEdit").setText(destination.toString());
            dialog.accept();
            respond(window, confirmation);
        });
        action(window, "fileSaveAsBinary").trigger();
    }

    @Test void applicationIconsFollowThemeAndReachDialogs() {
        var originalPalette = QApplication.palette();
        var window = new MainWindow(false);
        try {
            DesktopTheme.apply(io.oscarui.service.Appearance.LIGHT);
            assertFalse(window.windowIcon().isNull());
            long lightIconCacheKey = window.windowIcon().cacheKey();
            var lightImage = window.windowIcon().pixmap(32, 32).toImage();
            DesktopTheme.apply(io.oscarui.service.Appearance.DARK);
            assertFalse(window.windowIcon().isNull());
            var darkImage = window.windowIcon().pixmap(32, 32).toImage();
            assertFalse(darkImage.isNull());
            assertNotEquals(lightIconCacheKey, window.windowIcon().cacheKey());
            var dialog = new QDialog(window);
            try {
                assertEquals(window.windowIcon().cacheKey(), dialog.windowIcon().cacheKey());
            } finally {
                dialog.dispose();
            }
            DesktopTheme.apply(io.oscarui.service.Appearance.LIGHT);
            assertEquals(lightImage, window.windowIcon().pixmap(32, 32).toImage());
            lightImage.dispose();
            darkImage.dispose();
        } finally {
            window.close();
            window.dispose();
            QApplication.setPalette(originalPalette);
            DesktopTheme.updateIcon();
            originalPalette.dispose();
        }
    }

    @Test void globalSettingsRetainHashCheckboxAndSwitchThemes() throws Exception {
        var originalPalette = QApplication.palette();
        var window = new MainWindow(false);
        window.show();
        try {
            var global = window.findChild(QMenu.class, "settingsMenu");
            assertNotNull(global);
            assertTrue(global.actions().contains(action(window, "settingsSecrets")));
            respond(window, dialog -> {
                dialog.findChild(QComboBox.class, "appearanceMode").setCurrentIndex(2);
                dialog.accept();
            });
            action(window, "globalSettings").trigger();
            assertCallback();
            assertTrue(QApplication.palette().color(io.qt.gui.QPalette.ColorRole.Window).lightness() < 128);
            respond(window, dialog -> {
                var hash = dialog.findChild(QCheckBox.class, "hashCommunityStrings");
                assertFalse(hash.isChecked());
                hash.setChecked(true);
                dialog.findChild(QLineEdit.class, "sharedSecret").setText("public-key");
                assertTrue(dialog.palette().color(io.qt.gui.QPalette.ColorRole.Window).lightness() < 128);
                dialog.grab().save("target/ui-global-secrets-dark.png");
                dialog.findChild(QPushButton.class, "applySecrets").click();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
            respond(window, dialog -> {
                dialog.findChild(QComboBox.class, "appearanceMode").setCurrentIndex(1);
                dialog.accept();
            });
            action(window, "globalSettings").trigger();
            assertCallback();
            assertTrue(QApplication.palette().color(io.qt.gui.QPalette.ColorRole.Window).lightness() > 128);
            respond(window, dialog -> {
                var hash = dialog.findChild(QCheckBox.class, "hashCommunityStrings");
                assertTrue(hash.isChecked());
                assertNull(dialog.findChild(QLineEdit.class, "communityString"));
                assertEquals("public-key", dialog.findChild(QLineEdit.class, "sharedSecret").text());
                dialog.grab().save("target/ui-global-secrets-light.png");
                hash.setChecked(false);
                dialog.reject();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
            respond(window, dialog -> {
                assertTrue(dialog.findChild(QCheckBox.class, "hashCommunityStrings").isChecked());
                dialog.reject();
            });
            action(window, "settingsSecrets").trigger();
            assertCallback();
        } finally {
            window.close();
            window.dispose();
            QApplication.setPalette(originalPalette);
            originalPalette.dispose();
        }
    }

    private void saveAs(MainWindow window, Path destination, Consumer<QDialog> confirmation) {
        respond(window, dialog -> {
            dialog.findChild(QLineEdit.class, "fileNameEdit").setText(destination.toString());
            dialog.accept();
            respond(window, confirmation);
        });
        action(window, "fileSaveAs").trigger();
    }

    private QAction action(MainWindow window, String name) { return window.findChild(QAction.class, name); }

    private void respondToInspection(MainWindow window, String actionName, Consumer<QDialog> response) {
        if (actionName.equals("inspectHexDump")) {
            respond(window, dialog -> {
                var prompt = (QMessageBox) dialog;
                assertTrue(prompt.text().contains("clear text"));
                assertNull(prompt.findChild(QPlainTextEdit.class, "binaryInspectionText"));
                prompt.button(QMessageBox.StandardButton.Yes).click();
                respond(window, response);
            });
        } else {
            respond(window, response);
        }
    }

    private void respond(MainWindow window, Consumer<QDialog> response) {
        var timer = new QTimer(window);
        timer.timeout.connect(() -> {
            if (QApplication.activeModalWidget() instanceof QDialog dialog) {
                timer.stop();
                try { response.accept(dialog); }
                catch (Throwable failure) { callbackFailure = failure; dialog.reject(); }
                timer.dispose();
            }
        });
        timer.start(20);
    }

    private void assertCallback() {
        if (callbackFailure != null) fail("Dialog interaction failed", callbackFailure);
    }

    private void await(BooleanSupplier ready) {
        var loop = new QEventLoop();
        var poll = new QTimer(loop);
        var deadline = new QTimer(loop);
        poll.timeout.connect(() -> { if (ready.getAsBoolean() || callbackFailure != null) loop.quit(); });
        deadline.setSingleShot(true);
        deadline.timeout.connect(() -> {
            if (QApplication.activeModalWidget() instanceof QDialog dialog) dialog.reject();
            loop.quit();
        });
        poll.start(20);
        deadline.start(10000);
        loop.exec();
        loop.dispose();
        assertCallback();
        assertTrue(ready.getAsBoolean(), "Qt workflow did not reach the expected state");
    }
}