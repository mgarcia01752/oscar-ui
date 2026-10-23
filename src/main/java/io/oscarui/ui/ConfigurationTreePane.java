/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.oscarui.service.OscarDocumentService.Node;
import io.qt.core.Qt;
import io.qt.gui.QAction;
import io.qt.widgets.QMenu;
import io.qt.widgets.QTreeWidget;
import io.qt.widgets.QTreeWidgetItem;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Owns the configuration tree and the mapping between rows and document TLVs. */
final class ConfigurationTreePane extends QTreeWidget {
    private final Map<QTreeWidgetItem, Node> tlvsByItem = new HashMap<>();

    private final Map<Node, Node> parentsByTlv = new IdentityHashMap<>();

    private Node documentRoot;

    ConfigurationTreePane() {
        setObjectName("configurationTree");
        setHeaderLabels(List.of("Configuration", "Type", "Value"));
        setMinimumWidth(240);
        setColumnWidth(0, 240);
        setColumnWidth(1, 100);
        setAlternatingRowColors(true);
    }

    void configureContextMenu(List<QAction> editingActions) {
        setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu);
        customContextMenuRequested.connect(position -> {
            var selectedItem = itemAt(position);
            if (selectedItem == null) {
                return;
            }
            setCurrentItem(selectedItem);
            var contextMenu = new QMenu(this);
            try {
                editingActions.forEach(contextMenu::addAction);
                contextMenu.exec(viewport().mapToGlobal(position));
            } finally {
                contextMenu.dispose();
            }
        });
    }

    void displayRoot(Node rootTlv, boolean binaryBacked) {
        tlvsByItem.clear();
        parentsByTlv.clear();
        documentRoot = rootTlv;
        clear();
        var rootItem = new QTreeWidgetItem(List.of(rootTlv.tlvName(), "", binaryBacked ? "" : "Draft — not compiled"));
        addTopLevelItem(rootItem);
        populate(rootItem, rootTlv);
        rootItem.setExpanded(true);
        setCurrentItem(rootItem);
    }

    record ViewState(Node selected, List<Node> expanded, int vertical, int horizontal) {}

    ViewState rememberView() {
        return new ViewState(selectedTlv(), tlvsByItem.entrySet().stream()
                .filter(entry -> entry.getKey().isExpanded()).map(Map.Entry::getValue).toList(),
                verticalScrollBar().value(), horizontalScrollBar().value());
    }

    void restoreView(ViewState state) {
        if (state == null) {
            return;
        }
        tlvsByItem.forEach((item, node) -> item.setExpanded(state.expanded().stream()
                .anyMatch(previous -> previous.sourceStartOffset() == node.sourceStartOffset()
                        && previous.tlvPath().equals(node.tlvPath()))));
        selectCorresponding(state.selected());
        verticalScrollBar().setValue(state.vertical());
        horizontalScrollBar().setValue(state.horizontal());
    }

    void clearDocument() {
        tlvsByItem.clear();
        parentsByTlv.clear();
        documentRoot = null;
        clear();
    }

    Node selectedTlv() {
        return tlvsByItem.get(currentItem());
    }

    Node parentOf(Node childTlv) {
        return parentsByTlv.get(childTlv);
    }

    Node insertionParent() {
        Node selectedTlv = selectedTlv();
        if (selectedTlv == null) {
            return documentRoot;
        }
        return selectedTlv.isContainer() ? selectedTlv : parentsByTlv.getOrDefault(selectedTlv, documentRoot);
    }

    void setMicVisibility(Predicate<Node> isGeneratedMic, boolean showMicTlvs) {
        tlvsByItem.forEach((treeItem, tlvNode) -> {
            if (!isGeneratedMic.test(tlvNode)) {
                return;
            }
            if (!showMicTlvs && currentItem() == treeItem) {
                setCurrentItem(treeItem.parent());
            }
            treeItem.setHidden(!showMicTlvs);
            treeItem.setToolTip(0, tlvNode.tlvPath() + " — Generated integrity field (read-only)");
        });
    }

    void selectCorresponding(Node previousTlv) {
        if (previousTlv == null) {
            return;
        }
        tlvsByItem.forEach((treeItem, tlvNode) -> {
            if (tlvNode.sourceStartOffset() == previousTlv.sourceStartOffset() && tlvNode.tlvPath().equals(previousTlv.tlvPath())) {
                selectItem(treeItem);
            }
        });
    }

    void applyMask(io.oscarui.service.SnmpSecretMask masking) {
        tlvsByItem.forEach((item, node) -> {
            if (!node.tlvPath().isEmpty()) {
                item.setText(2, masking.value(node));
            }
        });
    }

    void selectLastChild(Node previousParent) {
        Node updatedParent = tlvsByItem.values().stream()
                .filter(tlvNode -> tlvNode.sourceStartOffset() == previousParent.sourceStartOffset()
                        && tlvNode.tlvPath().equals(previousParent.tlvPath()))
                .findFirst().orElse(documentRoot);
        // Insert appends in source order; Oscar may reorder encoded children.
        Node insertedTlv = updatedParent.childTlvs().stream()
                .max(java.util.Comparator.comparingInt(Node::sourceStartOffset)).orElseThrow();
        selectCorresponding(insertedTlv);
        expandSelection();
    }

    void expandSelection() {
        if (currentItem() != null) {
            currentItem().setExpanded(true);
        }
    }

    private void populate(QTreeWidgetItem treeItem, Node tlvNode) {
        tlvsByItem.put(treeItem, tlvNode);
        boolean unmappedReference = tlvNode.tlvPath().contains(Node.UNMAPPED_TLV_TYPE_CODE);
        treeItem.setText(1, unmappedReference ? "" : tlvNode.tlvPath().stream()
                .map(String::valueOf).collect(Collectors.joining(".")));
        treeItem.setToolTip(0, unmappedReference ? "Unmapped reference entry" : tlvNode.tlvPath().toString());
        for (Node childTlv : tlvNode.childTlvs()) {
            var childItem = new QTreeWidgetItem(List.of(childTlv.tlvName(), "", childTlv.encodedValueText()));
            treeItem.addChild(childItem);
            parentsByTlv.put(childTlv, tlvNode);
            populate(childItem, childTlv);
        }
    }

    private void selectItem(QTreeWidgetItem treeItem) {
        for (var ancestorItem = treeItem.parent(); ancestorItem != null; ancestorItem = ancestorItem.parent()) {
            ancestorItem.setExpanded(true);
        }
        setCurrentItem(treeItem);
        scrollToItem(treeItem);
    }
}
