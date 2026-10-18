package io.oscarui.ui;

import io.qt.widgets.*;

final class SecretsDialog extends QDialog {
    SecretsDialog(QWidget parent, SessionSecrets secrets) {
        super(parent);
        setObjectName("secretsDialog");
        setWindowTitle("Settings - Secrets");
        setMinimumWidth(500);
        var layout = new QVBoxLayout(this);
        var note = new QLabel("Session only: values are forgotten when you close OSCAR-UI. "
                + "The shared secret is used for binary export. Encrypted storage is not implemented.");
        note.setWordWrap(true);
        layout.addWidget(note);
        var form = new QFormLayout();
        var shared = field("sharedSecret", secrets.sharedValue());
        form.addRow("Shared secret", shared);
        layout.addLayout(form);
        var hashCommunity = new QCheckBox("Hash community strings");
        hashCommunity.setObjectName("hashCommunityStrings");
        hashCommunity.setChecked(secrets.hashCommunityStrings());
        layout.addWidget(hashCommunity);
        var hashingNote = new QLabel("Hashing preference only for now; the algorithm and export behavior are not implemented.");
        hashingNote.setWordWrap(true);
        layout.addWidget(hashingNote);
        var clear = new QPushButton("Clear shared secret");
        clear.setObjectName("clearSecrets");
        clear.clicked.connect(shared::clear);
        layout.addWidget(clear);
        var buttons = new QDialogButtonBox();
        var apply = buttons.addButton("Apply for Session", QDialogButtonBox.ButtonRole.AcceptRole);
        apply.setObjectName("applySecrets");
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.accepted.connect(() -> {
            secrets.replace(shared.text(), hashCommunity.isChecked());
            accept();
        });
        buttons.rejected.connect(this::reject);
        finished.connect(result -> shared.clear());
        layout.addWidget(buttons);
    }

    private QLineEdit field(String name, String value) {
        var field = new QLineEdit();
        field.setObjectName(name);
        field.setEchoMode(QLineEdit.EchoMode.Password);
        field.setText(value);
        return field;
    }
}
