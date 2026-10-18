package io.oscarui.ui;

import io.qt.widgets.*;

/** Chooses an export format without starting a save operation. */
final class SaveFormatDialog extends QDialog {
    enum SaveFormat { TEXT, BINARY }

    private SaveFormat selectedFormat = SaveFormat.TEXT;

    SaveFormatDialog(QWidget parent) {
        super(parent);
        setObjectName("saveFormatDialog");
        setWindowTitle("Save configuration");
        var layout = new QVBoxLayout(this);
        layout.addWidget(new QLabel("Choose a save format. Binary exports through Oscar; Text preserves editable source."));
        var buttons = new QDialogButtonBox();
        var textButton = buttons.addButton("Text", QDialogButtonBox.ButtonRole.ActionRole);
        textButton.setObjectName("chooseTextSave");
        var binaryButton = buttons.addButton("Binary", QDialogButtonBox.ButtonRole.ActionRole);
        binaryButton.setObjectName("chooseBinarySave");
        textButton.clicked.connect(this::accept);
        binaryButton.clicked.connect(() -> {
            selectedFormat = SaveFormat.BINARY;
            accept();
        });
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }

    SaveFormat selectedFormat() {
        return selectedFormat;
    }
}
