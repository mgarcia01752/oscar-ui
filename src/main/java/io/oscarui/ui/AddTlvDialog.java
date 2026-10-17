package io.oscarui.ui;

import com.comcast.oscar.dictionary.TlvDefinition;
import io.oscarui.service.OscarDocumentService;
import io.qt.core.QRegularExpression;
import io.qt.core.Qt;
import io.qt.gui.QRegularExpressionValidator;
import io.qt.widgets.*;
import java.util.List;
import java.util.Locale;

final class AddTlvDialog extends QDialog {
    record Selection(TlvDefinition definition, String value) {}
    private final QLineEdit value = new QLineEdit();
    private final QTreeWidget list = new QTreeWidget();
    private TlvDefinition selected;
    private boolean deletionRequested;

    AddTlvDialog(QWidget parent, OscarDocumentService service, OscarDocumentService.Document document,
                 OscarDocumentService.Node insertionParent, List<TlvDefinition> choices) {
        this(parent, service, document, insertionParent, choices, null);
    }

    AddTlvDialog(QWidget parent, OscarDocumentService service, OscarDocumentService.Document document,
                 OscarDocumentService.Node insertionParent, List<TlvDefinition> choices,
                 OscarDocumentService.Node editing) {
        super(parent);
        setObjectName(editing == null ? "addTlvDialog" : "editTlvDialog");
        setWindowTitle((editing == null ? "Add TLV - " : "Edit TLV - ") + document.type().header());
        resize(620, 540);
        var layout = new QVBoxLayout(this);
        layout.addWidget(new QLabel("Parent: " + insertionParent.name()));
        var search = new QLineEdit();
        search.setObjectName("tlvSearch");
        search.setPlaceholderText("Search TLVs");
        layout.addWidget(search);
        list.setObjectName("tlvChoices");
        list.setHeaderLabels(List.of("TLV", "Name"));
        list.setRootIsDecorated(false);
        list.setColumnWidth(0, 110);
        list.header().setSectionsClickable(true);
        list.header().setSortIndicatorShown(true);
        list.header().setSortIndicator(0, Qt.SortOrder.AscendingOrder);
        list.header().sortIndicatorChanged.connect((column, order) -> sortChoices());
        layout.addWidget(list, 1);
        var description = new QPlainTextEdit();
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
        form.addRow(valueLabel, value);
        layout.addLayout(form);
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
            boolean container = selected != null && service.isContainer(document.type(), selected);
            boolean supported = selected != null && service.editingIssue(document.type(), selected).isEmpty();
            value.setEnabled(supported && !container);
            String issue = selected == null ? "" : service.valueIssue(document.type(), selected, value.text());
            validation.setText(!value.text().isEmpty() || !supported ? issue : "");
            add.setEnabled(supported && issue.isEmpty() && (editing == null || !container));
        };
        list.currentItemChanged.connect((current, previous) -> {
            selected = current instanceof TlvItem item ? item.definition : null;
            value.clear();
            boolean numeric = selected != null && java.util.Set.of("integer", "unsigned_integer", "byte").contains(selected.codec());
            var oldValidator = value.validator();
            value.setValidator(numeric ? new QRegularExpressionValidator(new QRegularExpression("[+-]?[0-9]*"), value) : null);
            if (oldValidator != null) oldValidator.dispose();
            valueLabel.setText(numeric ? "Decimal value" : "Value");
            description.setPlainText(selected == null ? "" : selected.description());
            metadata.setText(selected == null ? "No available TLVs"
                    : valueHelp(selected, service.isContainer(document.type(), selected)));
            update.run();
        });
        value.textChanged.connect(text -> update.run());
        search.textChanged.connect(text -> {
            selected = null;
            list.clear();
            String query = text.toLowerCase(Locale.ROOT);
            var visible = choices.stream().filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(query)
                    || pathText(entry).contains(query)
                    || entry.description().toLowerCase(Locale.ROOT).contains(query)
                    || entry.codec().toLowerCase(Locale.ROOT).contains(query)
                    || entry.supportedVersion().toLowerCase(Locale.ROOT).contains(query)).toList();
            visible.forEach(entry -> list.addTopLevelItem(new TlvItem(entry)));
            sortChoices();
            if (!visible.isEmpty()) list.setCurrentItem(list.topLevelItem(0));
            else { description.clear(); metadata.setText("No available TLVs"); update.run(); }
        });
        search.textChanged.emit("");
        if (editing != null) {
            search.hide();
            list.setEnabled(false);
            list.setMaximumHeight(75);
            resize(620, 340);
            value.setText(editing.value());
            value.setFocus();
            value.selectAll();
        }
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

    private static final class TlvItem extends QTreeWidgetItem {
        private final TlvDefinition definition;

        TlvItem(TlvDefinition definition) {
            super(List.of(pathText(definition), definition.name()));
            this.definition = definition;
        }

        @Override public int compareTo(QTreeWidgetItem other) {
            if (!(other instanceof TlvItem item)) return super.compareTo(other);
            if (treeWidget().header().sortIndicatorSection() == 1) {
                int names = definition.name().compareToIgnoreCase(item.definition.name());
                if (names != 0) return names;
            }
            var left = definition.path();
            var right = item.definition.path();
            for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
                int order = Integer.compare(left.get(index), right.get(index));
                if (order != 0) return order;
            }
            return Integer.compare(left.size(), right.size());
        }
    }

    static String valueHelp(TlvDefinition definition, boolean container) {
        if (container) return "Container | Add values to its child TLVs";
        return switch (definition.codec()) {
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

    static boolean confirmDeletion(QWidget parent, OscarDocumentService.Node node) {
        String message = "Delete " + node.name() + "?"
                + (node.children().isEmpty() ? "" : "\nAll child TLVs will also be deleted.");
        return QMessageBox.question(parent, "Delete TLV", message,
                new QMessageBox.StandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.Cancel),
                QMessageBox.StandardButton.Cancel) == QMessageBox.StandardButton.Yes;
    }

    boolean deletionRequested() { return deletionRequested; }

    Selection selection() { return new Selection(selected, value.text()); }
}
