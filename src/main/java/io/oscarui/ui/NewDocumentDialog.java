package io.oscarui.ui;

import io.oscarui.service.OscarDocumentService.FileType;
import io.qt.widgets.*;
import java.util.List;

final class NewDocumentDialog extends QDialog {
    record Selection(FileType type, boolean allTlvs) {}
    private final List<FileType> types;
    private final QComboBox type = new QComboBox();
    private final QRadioButton all = new QRadioButton("All TLVs");

    NewDocumentDialog(QWidget parent, List<FileType> types) {
        super(parent);
        this.types = types;
        setObjectName("newDocumentDialog");
        setWindowTitle("New Configuration");
        setMinimumWidth(480);
        var layout = new QVBoxLayout(this);
        var form = new QFormLayout();
        type.setObjectName("newFileType");
        types.forEach(entry -> type.addItem(entry.toString()));
        type.setCurrentIndex(-1);
        type.setPlaceholderText("Select type");
        form.addRow("Configuration type", type);
        layout.addLayout(form);
        var modes = new QHBoxLayout();
        var empty = new QRadioButton("Empty");
        empty.setChecked(true);
        all.setObjectName("allTlvsMode");
        var group = new QButtonGroup(this);
        group.addButton(empty);
        group.addButton(all);
        modes.addWidget(empty);
        modes.addWidget(all);
        modes.addStretch();
        layout.addLayout(modes);
        var buttons = new QDialogButtonBox();
        var create = buttons.addButton("Create", QDialogButtonBox.ButtonRole.AcceptRole);
        create.setObjectName("createDocument");
        create.setEnabled(false);
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.accepted.connect(this::accept);
        buttons.rejected.connect(this::reject);
        type.currentIndexChanged.connect(index -> create.setEnabled(index >= 0));
        layout.addWidget(buttons);
    }

    Selection selection() { return new Selection(types.get(type.currentIndex()), all.isChecked()); }
}