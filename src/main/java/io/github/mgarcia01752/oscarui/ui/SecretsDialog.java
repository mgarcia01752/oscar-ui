package io.github.mgarcia01752.oscarui.ui;

import io.qt.widgets.*;

final class SecretsDialog extends QDialog {
    SecretsDialog(QWidget parent, SessionSecrets secrets) {
        super(parent);
        setObjectName("secretsDialog");
        setWindowTitle("Settings - Secrets");
        setMinimumWidth(500);
        var layout = new QVBoxLayout(this);
        var note = new QLabel("Session only: values are forgotten when you close Oscar Desktop. "
                + "The shared secret is used for binary export. The community string is not yet used. Encrypted storage is not implemented.");
        note.setWordWrap(true);
        layout.addWidget(note);
        var form = new QFormLayout();
        var community = field("communityString", secrets.communityValue());
        var shared = field("sharedSecret", secrets.sharedValue());
        form.addRow("Community string (commstring)", community);
        form.addRow("Shared secret", shared);
        layout.addLayout(form);
        var clear = new QPushButton("Clear both fields");
        clear.setObjectName("clearSecrets");
        clear.clicked.connect(() -> { community.clear(); shared.clear(); });
        layout.addWidget(clear);
        var buttons = new QDialogButtonBox();
        var apply = buttons.addButton("Apply for Session", QDialogButtonBox.ButtonRole.AcceptRole);
        apply.setObjectName("applySecrets");
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.accepted.connect(() -> {
            secrets.replace(community.text(), shared.text());
            accept();
        });
        buttons.rejected.connect(this::reject);
        finished.connect(result -> { community.clear(); shared.clear(); });
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
