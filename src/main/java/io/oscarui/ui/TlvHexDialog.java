package io.oscarui.ui;

import io.oscarui.service.SelectedTlvHexService.HexNode;
import io.qt.gui.QFontDatabase;
import io.qt.widgets.*;
import java.util.List;

/** Read-only hierarchical hex view of one selected TLV instance. */
final class TlvHexDialog extends QDialog {
    TlvHexDialog(QWidget parent, HexNode selected) {
        super(parent);
        setObjectName("tlvHexDialog");
        setWindowTitle("TLV Hex - " + selected.name());
        resize(850, 420);
        var layout = new QVBoxLayout(this);
        var note = new QLabel("Current values encoded by OSCAR. Hex includes type, length, and value."
                + " Parent rows include their child bytes; integrity fields are not recalculated.");
        note.setWordWrap(true);
        layout.addWidget(note);
        var tree = new QTreeWidget();
        tree.setObjectName("tlvHexTree");
        tree.setHeaderLabels(List.of("Name", "Hex"));
        tree.setAlternatingRowColors(true);
        tree.setColumnWidth(0, 280);
        tree.addTopLevelItem(row(selected));
        tree.expandAll();
        layout.addWidget(tree);
        var buttons = new QDialogButtonBox();
        buttons.addButton(QDialogButtonBox.StandardButton.Close);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }

    private QTreeWidgetItem row(HexNode node) {
        var item = new QTreeWidgetItem(List.of(node.name(), node.hex()));
        item.setFont(1, QFontDatabase.systemFont(QFontDatabase.SystemFont.FixedFont));
        item.setToolTip(1, node.hex());
        for (HexNode child : node.children()) {
            item.addChild(row(child));
        }
        return item;
    }
}
