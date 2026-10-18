/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.qt.widgets.*;

final class SecretsDialog extends QDialog {
    SecretsDialog(QWidget parent, SessionSecrets secrets) {
        super(parent);
        setObjectName("secretsDialog");
        setWindowTitle("Settings - Secrets");
        setMinimumWidth(500);
        var layout = new QVBoxLayout(this);
        var note = new QLabel("Shared secret and custom rules are session only. The masking checkbox is saved between runs. "
                + "The shared secret is used for binary export. Encrypted storage is not implemented.");
        note.setWordWrap(true);
        layout.addWidget(note);
        var form = new QFormLayout();
        var shared = field("sharedSecret", secrets.sharedValue());
        form.addRow("Shared secret", new SharedSecretField(this, shared));
        layout.addLayout(form);
        var obfuscateSnmp = new QCheckBox("Obfuscate SNMP secrets");
        obfuscateSnmp.setObjectName("hashCommunityStrings");
        obfuscateSnmp.setChecked(secrets.obfuscateSnmpSecrets());
        layout.addWidget(obfuscateSnmp);
        var maskingNote = new QLabel("Mask SNMP communities, security identities, and credentials in text and hex displays. "
                + "Saved values remain unchanged. This does not hash or encrypt the configuration.");
        maskingNote.setWordWrap(true);
        layout.addWidget(maskingNote);
        var customRules = new CustomSnmpRulesPane(secrets.customRules());
        layout.addWidget(customRules);
        var clear = new QPushButton("Clear shared secret");
        clear.setObjectName("clearSecrets");
        clear.clicked.connect(shared::clear);
        layout.addWidget(clear);
        var buttons = new QDialogButtonBox();
        var apply = buttons.addButton("Apply", QDialogButtonBox.ButtonRole.AcceptRole);
        apply.setObjectName("applySecrets");
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.accepted.connect(() -> {
            secrets.replace(shared.text(), obfuscateSnmp.isChecked(), customRules.rules());
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
