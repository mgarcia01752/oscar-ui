/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.settings;

import io.oscarui.service.SnmpSecretMask;
import io.qt.widgets.*;
import java.util.ArrayList;
import java.util.List;

/** Edits a local copy of session SNMP rules; Cancel never changes the session. */
final class CustomSnmpRulesPane extends QGroupBox {
    private final List<SnmpSecretMask.Rule> rules;
    private final QListWidget entries = new QListWidget();
    private final QLineEdit objectName = new QLineEdit();
    private final QLabel validation = new QLabel();

    CustomSnmpRulesPane(List<SnmpSecretMask.Rule> initial) {
        super("Custom SNMP secrets");
        rules = new ArrayList<>(initial);
        var layout = new QVBoxLayout(this);
        var note = new QLabel("Add an SNMP object name or numeric OID. Its value and indexed instances will be masked.");
        note.setWordWrap(true);
        layout.addWidget(note);
        entries.setObjectName("customSnmpRules");
        entries.setMaximumHeight(110);
        rules.forEach(rule -> entries.addItem(label(rule)));
        layout.addWidget(entries);
        var controls = new QHBoxLayout();
        objectName.setObjectName("customSnmpObject");
        objectName.setPlaceholderText("Object name or 1.3.6.1.4.1.…");
        controls.addWidget(objectName, 1);
        var add = new QPushButton("Add");
        add.setObjectName("addCustomSnmp");
        add.clicked.connect(this::addRule);
        controls.addWidget(add);
        var remove = new QPushButton("Remove");
        remove.setObjectName("removeCustomSnmp");
        remove.setEnabled(false);
        entries.currentRowChanged.connect(row -> remove.setEnabled(row >= 0));
        remove.clicked.connect(() -> {
            int row = entries.currentRow();
            if (row >= 0) {
                rules.remove(row);
                entries.takeItem(row).dispose();
                validation.clear();
            }
        });
        controls.addWidget(remove);
        layout.addLayout(controls);
        validation.setObjectName("customSnmpValidation");
        validation.setWordWrap(true);
        layout.addWidget(validation);
    }

    List<SnmpSecretMask.Rule> rules() {
        return List.copyOf(rules);
    }

    private void addRule() {
        try {
            var rule = SnmpSecretMask.resolveRule(objectName.text());
            if (rules.stream().anyMatch(existing -> existing.oid().equals(rule.oid()))) {
                validation.setText("That SNMP object is already listed.");
                return;
            }
            rules.add(rule);
            entries.addItem(label(rule));
            objectName.clear();
            validation.clear();
        } catch (IllegalArgumentException exception) {
            validation.setText(exception.getMessage());
        }
    }

    private String label(SnmpSecretMask.Rule rule) {
        return rule.name().equals(rule.oid()) ? rule.oid() : rule.name() + "  →  " + rule.oid();
    }
}
