package io.github.mgarcia01752.oscarui.ui;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService;
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
    @TempDir Path temporary;
    private Throwable callbackFailure;

    @BeforeAll static void initializeQt() {
        QApplication.initialize(new String[0]);
        QApplication.setQuitOnLastWindowClosed(false);
    }
    @AfterAll static void shutdownQt() { QApplication.shutdown(); }

    @Test void opensBinaryThroughMenuAndSavesDecodedTextSeparately() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path input = Files.write(temporary.resolve("input.cfg"), java.util.HexFormat.of().parseHex("030101ff00"));
            var types = new OscarDocumentService().types();
            int index = java.util.stream.IntStream.range(0, types.size())
                    .filter(position -> types.get(position).header().equals("Docsis")).findFirst().orElseThrow();
            respond(window, chooser -> {
                assertInstanceOf(QFileDialog.class, chooser);
                chooser.findChild(QLineEdit.class, "fileNameEdit").setText(input.toString());
                chooser.accept();
                respond(window, dialog -> {
                    assertEquals("binaryTypeDialog", dialog.objectName());
                    assertFalse(dialog.findChild(QPushButton.class, "confirmBinaryOpen").isEnabled());
                    dialog.findChild(QComboBox.class, "binaryFileType").setCurrentIndex(index);
                    dialog.findChild(QPushButton.class, "confirmBinaryOpen").click();
                });
            });
            action(window, "fileOpenBinary").trigger();
            await(() -> window.completed() && window.currentDocument() != null);
            assertCallback();
            var imported = window.currentDocument();
            assertNull(imported.path());
            assertEquals(input, imported.origin().path());
            assertEquals("NetworkAccess", window.findChild(QTreeWidget.class, "configurationTree").topLevelItem(0).child(0).text(0));
            assertEquals(imported.text(), window.findChild(QPlainTextEdit.class, "configurationText").toPlainText());
            assertTrue(action(window, "fileSave").isEnabled());
            window.grab().save("target/ui-binary-open.png");
            respond(window, prompt -> ((QMessageBox) prompt).button(QMessageBox.StandardButton.Cancel).click());
            action(window, "fileOpenText").trigger();
            assertCallback();
            assertSame(imported, window.currentDocument());
            assertTrue(action(window, "fileSave").isEnabled());
            Path text = temporary.resolve("decoded.txt");
            respond(window, chooser -> {
                assertInstanceOf(QFileDialog.class, chooser);
                chooser.findChild(QLineEdit.class, "fileNameEdit").setText(text.toString());
                chooser.accept();
            });
            action(window, "fileSave").trigger();
            await(() -> window.completed() && text.equals(window.currentDocument().path()));
            assertCallback();
            assertEquals(imported.text(), Files.readString(text));
            assertFalse(action(window, "fileSave").isEnabled());
            assertArrayEquals(java.util.HexFormat.of().parseHex("030101ff00"), Files.readAllBytes(input));
        } finally { window.close(); window.dispose(); }
    }

    @Test void openFormatsCancellationAndFailedBinaryImportPreserveDocument() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path text = Files.writeString(temporary.resolve("text.bin"), "Docsis { NetworkAccess 1; }");
            respond(window, chooser -> {
                chooser.findChild(QLineEdit.class, "fileNameEdit").setText(text.toString());
                chooser.accept();
            });
            action(window, "fileOpenText").trigger();
            await(() -> window.completed() && window.currentDocument() != null);
            assertCallback();
            var original = window.currentDocument();
            assertEquals(text, original.path());
            assertNull(original.origin());
            respond(window, dialog -> {
                assertEquals("openFormatDialog", dialog.objectName());
                dialog.reject();
            });
            action(window, "fileOpen").trigger();
            assertCallback();
            assertSame(original, window.currentDocument());
            respond(window, dialog -> {
                assertEquals("openFormatDialog", dialog.objectName());
                respond(window, QDialog::reject);
                dialog.findChild(QPushButton.class, "chooseTextOpen").click();
            });
            action(window, "fileOpen").trigger();
            assertCallback();
            assertSame(original, window.currentDocument());
            respond(window, QDialog::reject);
            action(window, "fileOpenBinary").trigger();
            assertCallback();
            assertSame(original, window.currentDocument());
            respond(window, chooser -> {
                chooser.findChild(QLineEdit.class, "fileNameEdit").setText(text.toString());
                chooser.accept();
                respond(window, QDialog::reject);
            });
            action(window, "fileOpenBinary").trigger();
            await(window::completed);
            assertCallback();
            assertSame(original, window.currentDocument());
            Path malformed = Files.write(temporary.resolve("broken.bin"), new byte[]{3, 10});
            window.openBinaryFile(malformed, original.type());
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
            assertTrue(window.currentDocument().root().children().isEmpty());
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
            await(() -> window.completed() && window.currentDocument().root().children().size() == 1);
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
            window.createDocument(window.currentDocument().type(), false);
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
            var type = new OscarDocumentService().types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
            window.createDocument(type, false);
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
                    if (combo.itemText(index).startsWith("Docsis-4.0 ")) combo.setCurrentIndex(index);
                }
                dialog.findChild(QRadioButton.class, "allTlvsMode").click();
                dialog.findChild(QPushButton.class, "createDocument").click();
            });
            action(window, "fileNew").trigger();
            await(() -> window.completed() && window.currentDocument().reference());
            assertEquals("Docsis-4.0", window.currentDocument().type().header());
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
                assertEquals("004", dialog.findChild(QLineEdit.class, "tlvValue").text());
                dialog.reject();
            });
            tree.itemDoubleClicked.emit(item, 0);
            assertCallback();
            assertSame(original, window.currentDocument());
            respond(window, dialog -> {
                var value = dialog.findChild(QLineEdit.class, "tlvValue");
                assertEquals("004", value.text());
                value.setText("5");
                dialog.grab().save("target/ui-edit-tlv.png");
                dialog.findChild(QPushButton.class, "confirmEditTlv").click();
            });
            tree.itemDoubleClicked.emit(item, 0);
            await(() -> window.completed() && window.currentDocument() != original);
            assertEquals(source.replace("004", "5"), window.currentDocument().text());
            assertEquals("5", tree.currentItem().text(1));
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

    @Test void secretsAreMaskedAndOptionalChecksPreserveDocument() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            respond(window, dialog -> {
                assertEquals("secretsDialog", dialog.objectName());
                var community = dialog.findChild(QLineEdit.class, "communityString");
                var shared = dialog.findChild(QLineEdit.class, "sharedSecret");
                assertEquals(QLineEdit.EchoMode.Password, community.echoMode());
                assertEquals(QLineEdit.EchoMode.Password, shared.echoMode());
                community.setText("public-test-community");
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
                assertTrue(dialog.findChild(QLineEdit.class, "communityString").text().isEmpty());
                assertTrue(dialog.findChild(QLineEdit.class, "sharedSecret").text().isEmpty());
                dialog.reject();
            });
            action(next, "settingsSecrets").trigger();
            assertCallback();
        } finally { next.close(); next.dispose(); }
    }

    @Test void saveShortcutOffersFormatsAndRoutesTextSave() throws Exception {
        var window = new MainWindow(false);
        try {
            var type = new OscarDocumentService().types().stream().filter(t -> t.header().equals("Docsis")).findFirst().orElseThrow();
            window.createDocument(type, false);
            await(() -> window.completed() && window.succeeded());
            Path destination = temporary.resolve("shortcut.txt");
            respond(window, dialog -> {
                assertEquals("saveFormatDialog", dialog.objectName());
                dialog.findChild(QPushButton.class, "chooseTextSave").click();
                respond(window, chooser -> {
                    chooser.findChild(QLineEdit.class, "fileNameEdit").setText(destination.toString());
                    chooser.accept();
                });
            });
            action(window, "saveShortcut").trigger();
            await(() -> window.completed() && destination.equals(window.currentDocument().path()));
            assertEquals(window.currentDocument().text(), Files.readString(destination));
        } finally { window.close(); window.dispose(); }
    }

    @Test void binarySaveSkipsChecksAndPreservesUnsavedText() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path source = Files.writeString(temporary.resolve("binary-source.txt"), "Docsis { NetworkAccess 1; }");
            window.openFile(source);
            await(() -> window.completed() && window.succeeded());
            var tree = window.findChild(QTreeWidget.class, "configurationTree");
            tree.setCurrentItem(tree.topLevelItem(0).child(0));
            respond(window, dialog -> {
                dialog.findChild(QLineEdit.class, "tlvValue").setText("0");
                dialog.findChild(QPushButton.class, "confirmEditTlv").click();
            });
            action(window, "editTlv").trigger();
            await(() -> window.completed() && window.currentDocument().text().contains("NetworkAccess 0"));
            var edited = window.currentDocument();
            respond(window, QDialog::reject);
            action(window, "fileSaveAsBinary").trigger();
            assertCallback();
            assertSame(edited, window.currentDocument());
            Path output = temporary.resolve("export.bin");
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
            byte[] firstOutput = Files.readAllBytes(output);
            assertArrayEquals(new byte[]{3, 1, 0}, java.util.Arrays.copyOf(firstOutput, 3));
            assertSame(edited, window.currentDocument());
            assertTrue(action(window, "fileSave").isEnabled());
            assertEquals("Docsis { NetworkAccess 1; }", Files.readString(source));
            window.grab().save("target/ui-binary-saved.png");
            respond(window, QDialog::reject);
            action(window, "fileSaveBinary").trigger();
            assertCallback();
            assertArrayEquals(firstOutput, Files.readAllBytes(output));
            boolean[] refused = {false};
            chooseBinaryDestination(window, output, dialog -> {
                ((QMessageBox) dialog).button(QMessageBox.StandardButton.No).click();
                refused[0] = true;
            });
            await(() -> refused[0] && window.completed());
            assertArrayEquals(firstOutput, Files.readAllBytes(output));
            chooseBinaryDestination(window, output, dialog -> {
                try { Files.writeString(output, "changed during confirmation"); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                ((QMessageBox) dialog).button(QMessageBox.StandardButton.Yes).click();
            });
            await(() -> window.completed() && !window.succeeded());
            assertEquals("changed during confirmation", Files.readString(output));
            Path pendingExport = temporary.resolve("unsaved.bin");
            respond(window, prompt -> {
                respond(window, format -> {
                    assertEquals("saveFormatDialog", format.objectName());
                    respond(window, chooser -> {
                        assertInstanceOf(QFileDialog.class, chooser);
                        chooser.findChild(QLineEdit.class, "fileNameEdit").setText(pendingExport.toString());
                        chooser.accept();
                    });
                    format.findChild(QPushButton.class, "chooseBinarySave").click();
                });
                ((QMessageBox) prompt).button(QMessageBox.StandardButton.Save).click();
            });
            action(window, "fileNew").trigger();
            await(() -> window.completed() && Files.exists(pendingExport));
            assertCallback();
            assertSame(edited, window.currentDocument());
            assertTrue(action(window, "fileSave").isEnabled());
            assertEquals("Docsis { NetworkAccess 1; }", Files.readString(source));
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

    private void saveAs(MainWindow window, Path destination, Consumer<QDialog> confirmation) {
        respond(window, dialog -> {
            dialog.findChild(QLineEdit.class, "fileNameEdit").setText(destination.toString());
            dialog.accept();
            respond(window, confirmation);
        });
        action(window, "fileSaveAs").trigger();
    }

    private QAction action(MainWindow window, String name) { return window.findChild(QAction.class, name); }

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