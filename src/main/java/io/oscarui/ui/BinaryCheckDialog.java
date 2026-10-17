package io.oscarui.ui;

import io.oscarui.service.*;
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
        var note = new QLabel("Optional draft checks; complete configuration validation is unfinished. "
            + "Binary saving exports directly through Oscar without this check gate.");
        note.setWordWrap(true);
        layout.addWidget(note);
        var results = new QTreeWidget();
        results.setObjectName("configChecks");
        results.setHeaderLabels(List.of("Status", "Check", "Details"));
        results.setColumnWidth(0, 145);
        results.setColumnWidth(1, 150);
        layout.addWidget(results, 1);
        var buttons = new QDialogButtonBox();
        Runnable check = () -> {
            results.clear();
            var checks = DraftChecks.inspect(service, document, sharedSecretSet.getAsBoolean());
            checks.forEach(entry ->
                    results.addTopLevelItem(new QTreeWidgetItem(List.of(entry.status(), entry.id(), entry.description()))));
        };
        var recheck = buttons.addButton("Check Config", QDialogButtonBox.ButtonRole.ActionRole);
        recheck.setObjectName("checkConfig");
        recheck.clicked.connect(check::run);
        var secrets = buttons.addButton("Global Secrets", QDialogButtonBox.ButtonRole.ActionRole);
        secrets.setObjectName("binarySecrets");
        secrets.clicked.connect(() -> { settings.run(); check.run(); });
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
        check.run();
    }
}
