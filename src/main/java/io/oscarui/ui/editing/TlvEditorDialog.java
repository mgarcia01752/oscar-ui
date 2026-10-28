/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.editing;

import io.oscarui.ui.formatting.TlvDescriptionFormatter;

import com.comcast.oscar.dictionary.TlvDefinition;
import io.oscarui.service.OscarDocumentService;
import io.oscarui.service.CertificateInput;
import io.oscarui.service.SnmpSecretMask;
import io.qt.core.QRegularExpression;
import io.qt.core.Qt;
import io.qt.gui.QRegularExpressionValidator;
import io.qt.widgets.*;
import java.util.List;
import java.util.Locale;

public final class TlvEditorDialog extends QDialog {
    /** Selected registry entry and its editor text.
     * @param definition chosen TLV definition
     * @param value value text to validate and apply through the document service
     */
    public record Selection(TlvDefinition definition, String value) {}

    private final QLineEdit value = new QLineEdit();
    private SnmpValuePane snmp;
    private CertificateValuePane certificate;

    private final QTreeWidget list = new QTreeWidget();

    private TlvDefinition selected;

    private boolean deletionRequested;
    private SnmpSecretMask masking = new SnmpSecretMask(false, List.of());
    private final OscarDocumentService.Node editingNode;
    private boolean maskedOriginal;

    private static final class TlvItem extends QTreeWidgetItem {
        private final TlvDefinition definition;

        TlvItem(TlvDefinition definition) {
            super(List.of(pathText(definition), definition.name()));
            this.definition = definition;
        }

        /**
         * Orders a TLV row using the active Name or numeric TLV-path column.
         *
         * <p>Name ties use the numeric path; non-TLV rows use Qt ordering.
         * The row must belong to its chooser tree on the Qt UI thread.
         *
         * @param other non-null row to compare with this row
         * @return negative, zero, or positive according to the selected order
         */
        @Override
        public int compareTo(QTreeWidgetItem other) {
            if (!(other instanceof TlvItem item)) {
                return super.compareTo(other);
            }
            if (treeWidget().header().sortIndicatorSection() == 1) {
                int nameComparison = definition.name().compareToIgnoreCase(item.definition.name());
                if (nameComparison != 0) {
                    return nameComparison;
                }
            }
            var left = definition.path();
            var right = item.definition.path();
            for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
                int componentComparison = Integer.compare(left.get(index), right.get(index));
                if (componentComparison != 0) {
                    return componentComparison;
                }
            }
            return Integer.compare(left.size(), right.size());
        }
    }

    /**
     * Creates Add or Edit controls; the caller applies an accepted selection to the document.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param service service used for registry, validation, preferences, or platform operations
     * @param document non-null document snapshot; this operation does not mutate it
     * @param insertionParent container receiving the selected TLV, or null where no insertion is available
     * @param choices available definitions in display order
     */
    public TlvEditorDialog(QWidget parent, OscarDocumentService service, OscarDocumentService.Document document,
                 OscarDocumentService.Node insertionParent, List<TlvDefinition> choices) {
        this(parent, service, document, insertionParent, choices, null);
    }

    /**
     * Creates Add or Edit controls; the caller applies an accepted selection to the document.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param service service used for registry, validation, preferences, or platform operations
     * @param document non-null document snapshot; this operation does not mutate it
     * @param insertionParent container receiving the selected TLV, or null where no insertion is available
     * @param choices available definitions in display order
     * @param editing node to edit, or null for Add mode
     */
    public TlvEditorDialog(QWidget parent, OscarDocumentService service, OscarDocumentService.Document document,
                 OscarDocumentService.Node insertionParent, List<TlvDefinition> choices,
                 OscarDocumentService.Node editing) {
        super(parent);
        editingNode = editing;
        setObjectName(editing == null ? "addTlvDialog" : "editTlvDialog");
        setWindowTitle((editing == null ? "Add TLV - " : "Edit TLV - ") + document.configurationType().header());
        resize(780, 540);
        var layout = new QVBoxLayout(this);
        layout.addWidget(new QLabel("Parent: " + insertionParent.tlvName()));
        var search = new QLineEdit();
        search.setObjectName("tlvSearch");
        search.setPlaceholderText("Search TLVs");
        var modes = new QHBoxLayout();
        var common = new QRadioButton("Common TLVs");
        common.setObjectName("commonTlvs");
        var all = new QRadioButton("All TLVs");
        all.setObjectName("allTlvs");
        common.setChecked(true);
        modes.addWidget(common);
        modes.addWidget(all);
        modes.addStretch();
        layout.addLayout(modes);
        common.setVisible(editing == null);
        all.setVisible(editing == null);
        layout.addWidget(search);
        if (editing == null) {
            var availability = new QLabel("All TLVs includes uncommon entries. Disabled rows show why they cannot be added.");
            availability.setObjectName("tlvAvailabilityHint");
            availability.setWordWrap(true);
            layout.addWidget(availability);
        }
        list.setObjectName("tlvChoices");
        list.setHeaderLabels(editing == null ? List.of("Type", "Name", "Status") : List.of("Type", "Name"));
        list.setRootIsDecorated(false);
        list.setColumnWidth(0, 90);
        list.setColumnWidth(1, 280);
        list.header().setSectionsClickable(true);
        list.header().setSortIndicatorShown(true);
        list.header().setSortIndicator(0, Qt.SortOrder.AscendingOrder);
        list.header().sortIndicatorChanged.connect((column, order) -> sortChoices());
        layout.addWidget(list, 1);
        var description = new QPlainTextEdit();
        description.setObjectName("tlvDescription");
        description.setReadOnly(true);
        description.setMaximumHeight(135);
        layout.addWidget(description);
        var metadata = new QLabel();
        metadata.setObjectName("tlvValueHelp");
        metadata.setWordWrap(true);
        layout.addWidget(metadata);
        var form = new QFormLayout();
        var valueLabel = new QLabel("Value");
        value.setObjectName("tlvValue");
        value.setMaxLength(Integer.MAX_VALUE);
        form.addRow(valueLabel, value);
        layout.addLayout(form);
        snmp = new SnmpValuePane(() -> value.setText(snmp.statement()));
        snmp.hide();
        layout.addWidget(snmp);
        certificate = new CertificateValuePane(() -> value.setText(certificate.value()));
        certificate.hide();
        layout.addWidget(certificate);
        var validation = new QLabel();
        validation.setObjectName("tlvValidation");
        validation.setWordWrap(true);
        layout.addWidget(validation);
        var buttons = new QDialogButtonBox();
        var add = buttons.addButton(editing == null ? "Add" : "Apply", QDialogButtonBox.ButtonRole.AcceptRole);
        add.setObjectName(editing == null ? "confirmAddTlv" : "confirmEditTlv");
        add.setEnabled(false);
        if (editing != null) {
            var delete = buttons.addButton("Delete TLV", QDialogButtonBox.ButtonRole.ActionRole);
            delete.setObjectName("deleteEditedTlv");
            delete.setEnabled(service.canDelete(document, editing));
            delete.clicked.connect(() -> {
                if (confirmDeletion(this, editing)) {
                    deletionRequested = true;
                    accept();
                }
            });
        }
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.accepted.connect(this::accept);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
        Runnable update = () -> {
            boolean container = selected != null && service.isContainer(document.configurationType(), selected);
            boolean supported = selected != null && (editing == null
                    ? service.insertionIssue(document, insertionParent, selected).isEmpty()
                    : service.editingIssue(document.configurationType(), selected).isEmpty());
            value.setEnabled(supported && !container);
            String issue = isSnmp() ? snmp.issue() : isCertificate() ? certificate.issue() : "";
            if (selected != null && issue.isEmpty()) {
                issue = service.valueIssue(document.configurationType(), selected, value.text());
            }
            validation.setText(!value.text().isEmpty() || !supported ? issue : "");
            add.setEnabled(supported && issue.isEmpty() && (editing == null || !container));
        };
        list.currentItemChanged.connect((current, previous) -> {
            selected = current instanceof TlvItem item ? item.definition : null;
            value.clear();
            value.setVisible(!isSnmp() && !isCertificate());
            valueLabel.setVisible(!isSnmp() && !isCertificate());
            snmp.setVisible(isSnmp());
            certificate.setVisible(isCertificate());
            certificate.setValue("");
            if (isSnmp()) {
                snmp.setStatement("");
                snmp.applyMask(masking.enabled(), false);
            }
            boolean numeric = selected != null && java.util.Set.of("integer", "unsigned_integer", "byte").contains(selected.codec());
            var oldValidator = value.validator();
            value.setValidator(numeric ? new QRegularExpressionValidator(new QRegularExpression("[+-]?[0-9]*"), value) : null);
            if (oldValidator != null) {
                oldValidator.dispose();
            }
            value.setEchoMode(masking.protects(selected) ? QLineEdit.EchoMode.Password : QLineEdit.EchoMode.Normal);
            valueLabel.setText(numeric ? "Decimal value" : "Value");
            description.setPlainText(selected == null ? "" : TlvDescriptionFormatter.format(selected.description()));
            metadata.setText(selected == null ? "No available TLVs"
                    : valueHelp(selected, service.isContainer(document.configurationType(), selected)));
            update.run();
        });
        value.textChanged.connect(text -> update.run());
        Runnable refresh = () -> {
            TlvDefinition previousSelection = selected;
            String previousValue = value.text();
            selected = null;
            list.clear();
            String query = search.text().toLowerCase(Locale.ROOT);
            var visible = choices.stream()
                    .filter(entry -> editing != null || all.isChecked() || !insertionParent.tlvPath().isEmpty()
                            || service.commonTlvs().isCommon(document.configurationType(), entry))
                    .filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(query)
                    || pathText(entry).contains(query)
                    || entry.description().toLowerCase(Locale.ROOT).contains(query)
                    || entry.codec().toLowerCase(Locale.ROOT).contains(query)
                    || entry.supportedVersion().toLowerCase(Locale.ROOT).contains(query)).toList();
            visible.forEach(entry -> {
                var item = new TlvItem(entry);
                String issue = editing == null ? service.insertionIssue(document, insertionParent, entry) : "";
                item.setText(2, issue);
                item.setToolTip(1, issue);
                item.setToolTip(2, issue);
                item.setDisabled(!issue.isEmpty());
                list.addTopLevelItem(item);
            });
            sortChoices();
            TlvItem firstAvailable = null;
            TlvItem retained = null;
            for (int index = 0; index < list.topLevelItemCount(); index++) {
                var item = (TlvItem) list.topLevelItem(index);
                if (!item.isDisabled()) {
                    if (firstAvailable == null) {
                        firstAvailable = item;
                    }
                    if (item.definition.equals(previousSelection)) {
                        retained = item;
                    }
                }
            }
            list.setCurrentItem(retained != null ? retained : firstAvailable);
            if (retained != null) {
                if (isSnmp()) {
                    snmp.setStatement(previousValue);
                } else if (isCertificate()) {
                    certificate.setValue(previousValue);
                } else {
                    value.setText(previousValue);
                }
            }
            if (firstAvailable == null) {
                description.clear();
                metadata.setText("No available TLVs in this view. Try All TLVs or edit an existing entry.");
                update.run();
            }
        };
        search.textChanged.connect(text -> refresh.run());
        all.toggled.connect(checked -> refresh.run());
        refresh.run();
        if (editing != null) {
            search.hide();
            list.setEnabled(false);
            list.setMaximumHeight(75);
            resize(620, 340);
            if (isSnmp()) {
                snmp.setStatement(editing.encodedValueText());
            } else if (isCertificate()) {
                certificate.setValue(editing.encodedValueText());
                resize(620, 540);
            } else {
                value.setText(editing.encodedValueText());
            }
            if (isSnmp()) {
                snmp.findChild(QLineEdit.class, "snmpOid").setFocus();
            } else if (isCertificate()) {
                certificate.findChild(QPlainTextEdit.class, "certificateText").setFocus();
            } else {
                value.setFocus();
                value.selectAll();
            }
        }
    }

    /**
     * Refreshes displayed values according to the supplied masking policy; stored values are unchanged.
     *
     * <p>Call on the Qt UI thread.
     * @param masking non-null masking policy used only for presentation
     */
    public void applyMask(SnmpSecretMask masking) {
        this.masking = masking;
        boolean protectedValue = masking.protects(selected) || (editingNode != null && masking.protects(editingNode));
        value.setEchoMode(protectedValue ? QLineEdit.EchoMode.Password : QLineEdit.EchoMode.Normal);
        if (isSnmp()) {
            snmp.applyMask(masking.enabled(), editingNode != null && protectedValue);
        }
        if (editingNode != null && protectedValue) {
            maskedOriginal = true;
            value.clear();
            value.setPlaceholderText(SnmpSecretMask.TEXT_MASK + " (leave blank to keep)");
            var apply = findChild(QPushButton.class, "confirmEditTlv");
            apply.setEnabled(true);
            value.textChanged.connect(text -> {
                if (text.isEmpty()) {
                    apply.setEnabled(true);
                }
            });
        }
    }

    /**
     * Builds input help from the selected definition without changing registry metadata.
     *
     * <p>Call on the Qt UI thread.
     * @param definition registry definition describing the value to edit
     * @param container true when editing a container rather than a scalar
     * @return human-readable input help
     */
    public static String valueHelp(TlvDefinition definition, boolean container) {
        if (container) {
            return "Parent TLV | Add values to its child TLVs";
        }
        if (CertificateInput.supports(definition)) {
            return "Certificate | Choose DER / PEM / HEX file or paste HEX / PEM text";
        }
        return switch (definition.codec()) {
            case "oid" -> "SNMP varbind | Enter OID, type, and value separately";
            case "unsigned_integer" -> "Unsigned integer | Range: 0 to "
                    + java.math.BigInteger.ONE.shiftLeft(definition.lengthMax() * 8)
                        .subtract(java.math.BigInteger.ONE);
            case "integer" -> "Integer | Example: 1 (decimal)";
            case "byte" -> "Byte | Example: 1 (decimal)";
            case "string" -> "Text | Example: \"example\"";
            case "string_null_terminated" -> "Null-terminated text | Example: \"example\"";
            case "string_bits" -> "Bit string | Example: (00000001)";
            case "byte_array" -> "Hex bytes | Example: [00:01:FF]";
            case "double_byte_array" -> "Byte pairs | Example: (1,2)(3,4)";
            case "mac_address" -> "MAC address | Example: 00:11:22:33:44:55";
            case "byte_array_ipv4_address" -> "IPv4 address | Example: 192.0.2.1";
            case "byte_array_ipv6_address" -> "IPv6 address | Example: 2001:db8:0:0:0:0:0:1";
            case "transport_address_ipv4_address" -> "IPv4 address and port | Example: 192.0.2.1(161)";
            case "transport_address_ipv6_address" -> "IPv6 address and port | Example: 2001:db8:0:0:0:0:0:1(161)";
            case "byte_array_inet_transport_address" -> "IP address and port | Example: 192.0.2.1(161)";
            case "byte_array_inet_address" -> "IP address | Example: 192.0.2.1";
            default -> definition.codec().replace('_', ' ');
        };
    }

    /**
     * Prompts for deletion of the selected TLV; it does not delete document content.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param node TLV selected for deletion
     * @return true only after the user confirms deletion
     */
    public static boolean confirmDeletion(QWidget parent, OscarDocumentService.Node node) {
        String message = "Delete " + node.tlvName() + "?"
                + (node.childTlvs().isEmpty() ? "" : "\nAll child TLVs will also be deleted.");
        return QMessageBox.question(parent, "Delete TLV", message,
                new QMessageBox.StandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.Cancel),
                QMessageBox.StandardButton.Cancel) == QMessageBox.StandardButton.Yes;
    }

    /**
     * Reports whether the dialog was accepted through its delete action.
     *
     * <p>Call on the Qt UI thread.
     * @return true for an accepted delete request
     */
    public boolean deletionRequested() {
        return deletionRequested;
    }

    /**
     * Returns the current dialog choices for the caller to apply after acceptance.
     *
     * <p>Call on the Qt UI thread.
     * @return current selection; consume only after the dialog is accepted
     */
    public Selection selection() {
        return new Selection(selected, maskedOriginal && value.text().isEmpty()
                ? editingNode.encodedValueText() : value.text());
    }

    private boolean isCertificate() {
        return CertificateInput.supports(selected);
    }

    private boolean isSnmp() {
        return selected != null && selected.codec().equals("oid")
                && java.util.Set.of("Snmp11", "Snmp64").contains(selected.name());
    }

    private void sortChoices() {
        var items = new java.util.ArrayList<TlvItem>();
        for (int index = 0; index < list.topLevelItemCount(); index++) {
            items.add((TlvItem) list.topLevelItem(index));
        }
        items.sort((left, right) -> left.compareTo(right));
        if (list.header().sortIndicatorOrder() == Qt.SortOrder.DescendingOrder) {
            java.util.Collections.reverse(items);
        }
        var current = list.currentItem();
        boolean blocked = list.blockSignals(true);
        try {
            while (list.topLevelItemCount() > 0) list.takeTopLevelItem(0);
            items.forEach(list::addTopLevelItem);
            list.setCurrentItem(current);
        } finally { list.blockSignals(blocked); }
    }

    private static String pathText(TlvDefinition definition) {
        return definition.path().stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining("."));
    }
}
