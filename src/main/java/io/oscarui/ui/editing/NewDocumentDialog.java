/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.editing;

import io.oscarui.service.OscarDocumentService.FileType;
import io.qt.widgets.*;
import java.util.List;

public final class NewDocumentDialog extends QDialog {
    /** Accepted new-document choices; no file has yet been created.
     * @param configurationType selected grammar/file type
     * @param includeAllTlvs true for a full reference template
     */
    public record Selection(FileType configurationType, boolean includeAllTlvs) {}
    private final List<FileType> types;
    private final QComboBox configurationTypeSelector = new QComboBox();
    private final QRadioButton includeAllTlvsButton = new QRadioButton("All TLVs");

    /**
     * Creates file-type and template choices without creating a document.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param types nonempty list of supported file types
     */
    public NewDocumentDialog(QWidget parent, List<FileType> types) {
        super(parent);
        this.types = types;
        setObjectName("newDocumentDialog");
        setWindowTitle("New Configuration");
        setMinimumWidth(480);
        var layout = new QVBoxLayout(this);
        var form = new QFormLayout();
        configurationTypeSelector.setObjectName("newFileType");
        types.forEach(entry -> configurationTypeSelector.addItem(entry.toString()));
        configurationTypeSelector.setCurrentIndex(-1);
        configurationTypeSelector.setPlaceholderText("Select type");
        form.addRow("Configuration type", configurationTypeSelector);
        layout.addLayout(form);
        var modes = new QHBoxLayout();
        var empty = new QRadioButton("Empty");
        empty.setChecked(true);
        includeAllTlvsButton.setObjectName("allTlvsMode");
        var group = new QButtonGroup(this);
        group.addButton(empty);
        group.addButton(includeAllTlvsButton);
        modes.addWidget(empty);
        modes.addWidget(includeAllTlvsButton);
        modes.addStretch();
        layout.addLayout(modes);
        var buttons = new QDialogButtonBox();
        var create = buttons.addButton("Create", QDialogButtonBox.ButtonRole.AcceptRole);
        create.setObjectName("createDocument");
        create.setEnabled(false);
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.accepted.connect(this::accept);
        buttons.rejected.connect(this::reject);
        configurationTypeSelector.currentIndexChanged.connect(index -> create.setEnabled(index >= 0));
        layout.addWidget(buttons);
    }

    /**
     * Returns the current dialog choices for the caller to apply after acceptance.
     *
     * <p>Call on the Qt UI thread.
     * @return current selection; consume only after the dialog is accepted
     */
    public Selection selection() {
        return new Selection(types.get(configurationTypeSelector.currentIndex()), includeAllTlvsButton.isChecked());
    }
}