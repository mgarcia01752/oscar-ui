/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.settings;

import com.comcast.oscar.dictionary.TlvDefinition;
import io.oscarui.service.OscarDocumentService;
import io.oscarui.service.InspectionException;
import io.qt.core.Qt;
import io.qt.widgets.*;
import java.io.IOException;
import java.util.List;

/** Registry-derived top-level checkboxes, edited in a draft until Save succeeds. */
public final class CommonTlvSettingsDialog extends QDialog {
    /**
     * Loads registry-backed Common TLV preferences for editing in this dialog.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param service service used for registry, validation, preferences, or platform operations
     * @throws InspectionException if the registry or stored preferences cannot be loaded
     */
    public CommonTlvSettingsDialog(QWidget parent, OscarDocumentService service) throws InspectionException {
        this(parent, service, null);
    }

    /**
     * Loads registry-backed Common TLV preferences for editing in this dialog.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param service service used for registry, validation, preferences, or platform operations
     * @param activeType preferred file type, or null to use the first supported type
     * @throws InspectionException if the registry or stored preferences cannot be loaded
     */
    public CommonTlvSettingsDialog(QWidget parent, OscarDocumentService service,
                            OscarDocumentService.FileType activeType) throws InspectionException {
        super(parent);
        setObjectName("commonTlvSettingsDialog");
        setWindowTitle("User Defined Common TLVs");
        resize(720, 560);
        var draft = service.commonTlvs().copy();
        var types = service.types();
        var layout = new QVBoxLayout(this);
        layout.addWidget(new QLabel("<b>User Defined Common TLVs</b>"));
        var selector = new QComboBox();
        selector.setObjectName("commonTlvFileType");
        types.forEach(type -> selector.addItem(type.header()));
        if (activeType != null && types.contains(activeType)) {
            selector.setCurrentIndex(types.indexOf(activeType));
        } else {
            int docsisIndex = selector.findText("Docsis");
            if (docsisIndex >= 0) {
                selector.setCurrentIndex(docsisIndex);
            }
        }
        var form = new QFormLayout();
        form.addRow("File type", selector);
        layout.addLayout(form);
        var help = new QLabel("Select the top-level TLVs to show in Common TLVs. Required inputs stay checked.\n"
                + "Other entries start unchecked. Generated fields are managed by OSCAR.");
        help.setWordWrap(true);
        layout.addWidget(help);
        var search = new QLineEdit();
        search.setObjectName("commonTlvSearch");
        search.setPlaceholderText("Search TLV name or number");
        layout.addWidget(search);
        var tree = new QTreeWidget();
        tree.setObjectName("commonTlvCheckboxes");
        tree.setHeaderLabels(List.of("Common / Type", "Name", "Status"));
        tree.setRootIsDecorated(false);
        tree.setStyleSheet("QTreeView::indicator:unchecked { border: 1px solid palette(mid); "
                + "border-radius: 2px; background: palette(base); }");
        tree.setColumnWidth(0, 110);
        tree.setColumnWidth(1, 285);
        layout.addWidget(tree, 1);
        var error = new QLabel();
        error.setObjectName("commonTlvError");
        error.setWordWrap(true);
        layout.addWidget(error);
        var buttons = new QDialogButtonBox();
        var save = buttons.addButton(QDialogButtonBox.StandardButton.Save);
        save.setObjectName("saveCommonTlvs");
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        layout.addWidget(buttons);
        Runnable filter = () -> {
            String query = search.text().toLowerCase(java.util.Locale.ROOT);
            for (int index = 0; index < tree.topLevelItemCount(); index++) {
                var row = tree.topLevelItem(index);
                row.setHidden(!(row.text(0) + " " + row.text(1)).toLowerCase(java.util.Locale.ROOT).contains(query));
            }
        };
        Runnable populate = () -> {
            tree.blockSignals(true);
            try {
                tree.clear();
                var type = types.get(selector.currentIndex());
                var document = service.create(type, false);
                for (var definition : service.addDefinitions(document, document.root())) {
                    boolean required = draft.isRequired(type, definition);
                    String issue = service.insertionIssue(document, document.root(), definition);
                    var row = new QTreeWidgetItem(List.of(definition.path().getFirst().toString(), definition.name(),
                            required ? "Required input" : issue));
                    row.setData(0, Qt.ItemDataRole.UserRole, definition);
                    row.setCheckState(0, draft.isCommon(type, definition) ? Qt.CheckState.Checked : Qt.CheckState.Unchecked);
                    if (required || issue.equals("Generated automatically")) {
                        row.setFlags(row.flags().setFlag(Qt.ItemFlag.ItemIsUserCheckable, false));
                    }
                    tree.addTopLevelItem(row);
                }
                error.clear();
                save.setEnabled(true);
                filter.run();
            } catch (InspectionException exception) {
                error.setText("Could not load this file type's TLV catalog.");
                save.setEnabled(false);
            } finally {
                tree.blockSignals(false);
            }
        };
        tree.itemChanged.connect((row, column) -> {
            var definition = (TlvDefinition) row.data(0, Qt.ItemDataRole.UserRole);
            draft.setCommon(types.get(selector.currentIndex()), definition, row.checkState(0) == Qt.CheckState.Checked);
        });
        selector.currentIndexChanged.connect(index -> populate.run());
        search.textChanged.connect(text -> filter.run());
        buttons.accepted.connect(() -> {
            try {
                service.commonTlvs().save(draft);
                accept();
            } catch (IOException exception) {
                error.setText("Could not save Common TLVs. Check settings-file permissions and format. Previous choices were kept.");
            }
        });
        buttons.rejected.connect(this::reject);
        populate.run();
    }
}
