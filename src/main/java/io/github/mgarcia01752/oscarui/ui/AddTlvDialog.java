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
        super(parent);
        setObjectName("addTlvDialog");
        setWindowTitle("Add TLV - " + document.type().header());
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
        var add = buttons.addButton("Add", QDialogButtonBox.ButtonRole.AcceptRole);
        add.setObjectName("confirmAddTlv");
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
            metadata.setText(selected == null ? "No available TLVs" : selected.codec() + " | Length: "
                    + selected.lengthMin() + "-" + selected.lengthMax() + " | Version: "
                    + selected.supportedVersion() + " | Instances: "
                    + (selected.maxInstances() < 0 ? "Unlimited" : selected.maxInstances())
                    + (service.editingIssue(document.type(), selected).isEmpty() ? ""
                    : " | " + service.editingIssue(document.type(), selected)));
            update.run();
        });
        value.textChanged.connect(text -> update.run());
        search.textChanged.connect(text -> {
            selected = null;
            list.clear();
            String query = text.toLowerCase(Locale.ROOT);
            visible = choices.stream().filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(query)
                    || entry.path().toString().contains(query)).toList();
            visible.forEach(entry -> list.addItem(entry.name() + "   " + entry.path()));
            if (!visible.isEmpty()) list.setCurrentRow(0);
            else { description.clear(); metadata.setText("No available TLVs"); update.run(); }
        });
        search.textChanged.emit("");
    }

    Selection selection() { return new Selection(selected, value.text()); }
}