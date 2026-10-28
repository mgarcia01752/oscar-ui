/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.workspace;

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
public final class ConfigurationTreePane extends QTreeWidget {
    private final Map<QTreeWidgetItem, Node> tlvsByItem = new HashMap<>();

    private final Map<Node, Node> parentsByTlv = new IdentityHashMap<>();

    private Node documentRoot;

    /**
     * Creates an empty configuration tree on the Qt UI thread.
     * The containing workspace assumes Qt ownership when attaching this widget.
     */
    public ConfigurationTreePane() {
        setObjectName("configurationTree");
        setHeaderLabels(List.of("Configuration", "Type", "Value"));
        setMinimumWidth(240);
        setColumnWidth(0, 240);
        setColumnWidth(1, 100);
        setAlternatingRowColors(true);
    }

    /**
     * Connects the shared editing actions to the selected tree row context menu.
     *
     * <p>Call on the Qt UI thread.
     * @param editingActions shared actions in display order; must be non-null
     */
    public void configureContextMenu(List<QAction> editingActions) {
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

    /**
     * Replaces tree rows and node mappings with the supplied document root.
     *
     * <p>Call on the Qt UI thread.
     * @param rootTlv non-null root of the document to display
     * @param binaryBacked true when the document originates from binary input
     */
    public void displayRoot(Node rootTlv, boolean binaryBacked) {
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

    /** Snapshot used to restore the displayed tree after switching tabs.
     * @param selected selected node, or null
     * @param expanded expanded nodes in the captured document
     * @param vertical vertical scrollbar value
     * @param horizontal horizontal scrollbar value
     */
    public record ViewState(Node selected, List<Node> expanded, int vertical, int horizontal) {}

    /**
     * Captures the selected TLV, expanded nodes, and scrollbar positions.
     *
     * <p>Call on the Qt UI thread.
     * @return immutable view snapshot
     */
    public ViewState rememberView() {
        return new ViewState(selectedTlv(), tlvsByItem.entrySet().stream()
                .filter(entry -> entry.getKey().isExpanded()).map(Map.Entry::getValue).toList(),
                verticalScrollBar().value(), horizontalScrollBar().value());
    }

    /**
     * Restores selection, expansion, and scrolling for a previously captured view.
     *
     * <p>Call on the Qt UI thread.
     * @param state captured view state, or null when no view has been remembered
     */
    public void restoreView(ViewState state) {
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

    /**
     * Clears tree rows and their node mappings.
     *
     * <p>Call on the Qt UI thread.
     */
    public void clearDocument() {
        tlvsByItem.clear();
        parentsByTlv.clear();
        documentRoot = null;
        clear();
    }

    /**
     * Returns the document node associated with the selected row.
     *
     * <p>Call on the Qt UI thread.
     * @return selected node, or null when no row is selected
     */
    public Node selectedTlv() {
        return tlvsByItem.get(currentItem());
    }

    /**
     * Looks up the parent of a node in the current displayed tree.
     *
     * <p>Call on the Qt UI thread.
     * @param childTlv node whose parent is requested
     * @return parent node, or null for the root or an unknown node
     */
    public Node parentOf(Node childTlv) {
        return parentsByTlv.get(childTlv);
    }

    /**
     * Selects the appropriate container for adding a TLV from the current selection.
     *
     * <p>Call on the Qt UI thread.
     * @return selected container or its parent; may be null without a document
     */
    public Node insertionParent() {
        Node selectedTlv = selectedTlv();
        if (selectedTlv == null) {
            return documentRoot;
        }
        return selectedTlv.isContainer() ? selectedTlv : parentsByTlv.getOrDefault(selectedTlv, documentRoot);
    }

    /**
     * Shows or hides generated MIC rows without changing document data.
     *
     * <p>Call on the Qt UI thread.
     * @param isGeneratedMic predicate identifying generated MIC nodes
     * @param showMicTlvs true to display generated MIC rows
     */
    public void setMicVisibility(Predicate<Node> isGeneratedMic, boolean showMicTlvs) {
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

    /**
     * Selects a node matching the previous TLV path and source position.
     *
     * <p>Call on the Qt UI thread.
     * @param previousTlv previously selected node, or null to leave selection unchanged
     */
    public void selectCorresponding(Node previousTlv) {
        if (previousTlv == null) {
            return;
        }
        tlvsByItem.forEach((treeItem, tlvNode) -> {
            if (tlvNode.sourceStartOffset() == previousTlv.sourceStartOffset() && tlvNode.tlvPath().equals(previousTlv.tlvPath())) {
                selectItem(treeItem);
            }
        });
    }

    /**
     * Refreshes displayed values according to the supplied masking policy; stored values are unchanged.
     *
     * <p>Call on the Qt UI thread.
     * @param masking non-null masking policy used only for presentation
     */
    public void applyMask(io.oscarui.service.SnmpSecretMask masking) {
        tlvsByItem.forEach((item, node) -> {
            if (!node.tlvPath().isEmpty()) {
                item.setText(2, masking.value(node));
            }
        });
    }

    /**
     * Selects the last displayed child of the corresponding parent after insertion.
     *
     * <p>Call on the Qt UI thread.
     * @param previousParent parent node from the preceding document snapshot
     */
    public void selectLastChild(Node previousParent) {
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

    /**
     * Expands the selected row, if one exists.
     *
     * <p>Call on the Qt UI thread.
     */
    public void expandSelection() {
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
