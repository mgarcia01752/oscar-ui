package io.github.mgarcia01752.oscarui.ui;

import com.comcast.oscar.dictionary.TlvDefinition;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService;
import io.qt.core.QRegularExpression;
import io.qt.gui.QRegularExpressionValidator;
import io.qt.widgets.*;
import java.util.List;
import java.util.Locale;

final class AddTlvDialog extends QDialog {
    record Selection(TlvDefinition definition, String value) {}
    private final QLineEdit value = new QLineEdit();
    private final QListWidget list = new QListWidget();
    private List<TlvDefinition> visible = List.of();
    private TlvDefinition selected;

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
            add.setEnabled(supported && issue.isEmpty());
        };
        list.currentRowChanged.connect(index -> {
            selected = index >= 0 && index < visible.size() ? visible.get(index) : null;
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
            visible = choices.stream().filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(query)
                    || entry.path().toString().contains(query)
                    || entry.description().toLowerCase(Locale.ROOT).contains(query)
                    || entry.codec().toLowerCase(Locale.ROOT).contains(query)
                    || entry.supportedVersion().toLowerCase(Locale.ROOT).contains(query)).toList();
            visible.forEach(entry -> list.addItem(entry.name() + "   " + entry.path()));
            if (!visible.isEmpty()) list.setCurrentRow(0);
            else { description.clear(); metadata.setText("No available TLVs"); update.run(); }
        });
        search.textChanged.emit("");
        if (editing != null) {
            search.hide();
            list.setEnabled(false);
            list.setMaximumHeight(45);
            resize(620, 340);
            value.setText(editing.value());
            value.setFocus();
            value.selectAll();
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

    Selection selection() { return new Selection(selected, value.text()); }
}