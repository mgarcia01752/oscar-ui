package io.oscarui.ui;

import io.qt.gui.QPalette;
import io.qt.widgets.*;

final class GlobalSettingsDialog extends QDialog {
    private final QCheckBox showMicTlvsCheckbox = new QCheckBox("Show CM/CMTS MIC TLVs");
    private final QComboBox appearanceSelector = new QComboBox();

    GlobalSettingsDialog(QWidget parent, boolean showMic) {
        super(parent);
        setObjectName("globalSettingsDialog");
        setWindowTitle("Settings - Global");
        setMinimumWidth(360);
        var layout = new QVBoxLayout(this);
        showMicTlvsCheckbox.setObjectName("showMicCheckbox");
        showMicTlvsCheckbox.setChecked(showMic);
        layout.addWidget(showMicTlvsCheckbox);
        appearanceSelector.setObjectName("appearanceMode");
        appearanceSelector.addItem("Light");
        appearanceSelector.addItem("Dark");
        appearanceSelector.setCurrentIndex(QApplication.palette().color(QPalette.ColorRole.Window).lightness() < 128 ? 1 : 0);
        var form = new QFormLayout();
        form.addRow("Appearance", appearanceSelector);
        layout.addLayout(form);
        var buttons = new QDialogButtonBox();
        buttons.addButton(QDialogButtonBox.StandardButton.Ok);
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.accepted.connect(this::accept);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }

    boolean showMicTlvs() { return showMicTlvsCheckbox.isChecked(); }
    boolean darkMode() { return appearanceSelector.currentIndex() == 1; }
}
