package io.github.mgarcia01752.oscarui.ui;

import io.github.mgarcia01752.oscarui.service.*;
import io.qt.widgets.*;
import java.util.List;
import java.util.function.BooleanSupplier;

final class BinaryCheckDialog extends QDialog {
    BinaryCheckDialog(QWidget parent, OscarDocumentService service, OscarDocumentService.Document document,
                      BooleanSupplier sharedSecretSet, Runnable settings) {
        super(parent);
        setObjectName("binaryCheckDialog");
        setWindowTitle("Check Config - " + document.type().header());
        resize(760, 450);
        var layout = new QVBoxLayout(this);
        var note = new QLabel("Binary save is under development. These draft checks do not establish a valid "
                + "deployable configuration. Save Binary remains unavailable until the required checks and encoder are implemented.");
        note.setWordWrap(true);
        layout.addWidget(note);
        var results = new QTreeWidget();
        results.setObjectName("configChecks");
        results.setHeaderLabels(List.of("Status", "Check", "Details"));
        results.setColumnWidth(0, 145);
        results.setColumnWidth(1, 150);
        layout.addWidget(results, 1);
        Runnable check = () -> {
            results.clear();
            DraftChecks.inspect(service, document, sharedSecretSet.getAsBoolean()).forEach(entry ->
                    results.addTopLevelItem(new QTreeWidgetItem(List.of(entry.status(), entry.id(), entry.description()))));
        };
        var buttons = new QDialogButtonBox();
        var recheck = buttons.addButton("Check Config", QDialogButtonBox.ButtonRole.ActionRole);
        recheck.setObjectName("checkConfig");
        recheck.clicked.connect(check::run);
        var secrets = buttons.addButton("Settings / Secrets", QDialogButtonBox.ButtonRole.ActionRole);
        secrets.clicked.connect(() -> { settings.run(); check.run(); });
        var save = buttons.addButton("Save Binary", QDialogButtonBox.ButtonRole.AcceptRole);
        save.setObjectName("saveBinary");
        save.setEnabled(false);
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
        check.run();
    }
}
