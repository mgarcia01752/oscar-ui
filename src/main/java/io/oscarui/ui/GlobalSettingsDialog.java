/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.oscarui.service.Appearance;
import io.oscarui.service.OscarDocumentService;
import io.oscarui.service.InspectionException;
import io.qt.widgets.*;

final class GlobalSettingsDialog extends QDialog {
    private final QCheckBox showMicTlvsCheckbox = new QCheckBox("Show CM/CMTS MIC TLVs");
    private final QCheckBox showTextTlvNumbersCheckbox = new QCheckBox("Show TLV comments in configuration text");
    private final QComboBox appearanceSelector = new QComboBox();

    GlobalSettingsDialog(QWidget parent, boolean showMic, boolean showTextTlvNumbers, Appearance appearance) {
        this(parent, showMic, showTextTlvNumbers, appearance, new OscarDocumentService());
    }

    GlobalSettingsDialog(QWidget parent, boolean showMic, boolean showTextTlvNumbers, Appearance appearance,
                         OscarDocumentService service) {
        this(parent, showMic, showTextTlvNumbers, appearance, service, null);
    }

    GlobalSettingsDialog(QWidget parent, boolean showMic, boolean showTextTlvNumbers, Appearance appearance,
                         OscarDocumentService service, OscarDocumentService.FileType activeType) {
        super(parent);
        setObjectName("globalSettingsDialog");
        setWindowTitle("Settings - Global");
        setMinimumWidth(360);
        var layout = new QVBoxLayout(this);
        showMicTlvsCheckbox.setObjectName("showMicCheckbox");
        showMicTlvsCheckbox.setChecked(showMic);
        layout.addWidget(showMicTlvsCheckbox);
        showTextTlvNumbersCheckbox.setObjectName("showTextTlvNumbersCheckbox");
        showTextTlvNumbersCheckbox.setChecked(showTextTlvNumbers);
        layout.addWidget(showTextTlvNumbersCheckbox);
        appearanceSelector.setObjectName("appearanceMode");
        appearanceSelector.addItem("System");
        appearanceSelector.addItem("Light");
        appearanceSelector.addItem("Dark");
        appearanceSelector.setCurrentIndex(appearance.ordinal());
        var form = new QFormLayout();
        form.addRow("Appearance", appearanceSelector);
        layout.addLayout(form);
        var common = new QPushButton("User Defined Common TLVs...");
        common.setObjectName("userDefinedCommonTlvs");
        layout.addWidget(common);
        common.clicked.connect(() -> {
            try {
                var dialog = new CommonTlvSettingsDialog(this, service, activeType);
                try {
                    dialog.exec();
                } finally {
                    dialog.dispose();
                }
            } catch (InspectionException exception) {
                QMessageBox.warning(this, "Catalog unavailable", "Could not load the TLV registry.");
            }
        });
        var buttons = new QDialogButtonBox();
        buttons.addButton(QDialogButtonBox.StandardButton.Ok);
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.accepted.connect(this::accept);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }

    boolean showMicTlvs() { return showMicTlvsCheckbox.isChecked(); }
    boolean showTextTlvNumbers() { return showTextTlvNumbersCheckbox.isChecked(); }
    Appearance appearance() { return Appearance.values()[appearanceSelector.currentIndex()]; }
}
