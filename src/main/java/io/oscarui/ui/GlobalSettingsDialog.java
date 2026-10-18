package io.oscarui.ui;

import io.qt.gui.QPalette;
import io.qt.widgets.*;

final class GlobalSettingsDialog extends QDialog {
    private final QCheckBox mic = new QCheckBox("Show CM/CMTS MIC TLVs");
    private final QComboBox appearance = new QComboBox();

    GlobalSettingsDialog(QWidget parent, boolean showMic) {
        super(parent);
        setObjectName("globalSettingsDialog");
        setWindowTitle("Settings - Global");
        setMinimumWidth(360);
        var layout = new QVBoxLayout(this);
        mic.setObjectName("showMicCheckbox");
        mic.setChecked(showMic);
        layout.addWidget(mic);
        appearance.setObjectName("appearanceMode");
        appearance.addItem("Light");
        appearance.addItem("Dark");
        appearance.setCurrentIndex(QApplication.palette().color(QPalette.ColorRole.Window).lightness() < 128 ? 1 : 0);
        var form = new QFormLayout();
        form.addRow("Appearance", appearance);
        layout.addLayout(form);
        var buttons = new QDialogButtonBox();
        buttons.addButton(QDialogButtonBox.StandardButton.Ok);
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.accepted.connect(this::accept);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }

    boolean showMicTlvs() { return mic.isChecked(); }
    boolean darkMode() { return appearance.currentIndex() == 1; }
}
