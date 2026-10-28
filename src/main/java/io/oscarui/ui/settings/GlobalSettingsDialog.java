/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.settings;

import io.oscarui.service.Appearance;
import io.oscarui.service.OscarDocumentService;
import io.oscarui.service.InspectionException;
import io.qt.widgets.*;

public final class GlobalSettingsDialog extends QDialog {
    private final QCheckBox showMicTlvsCheckbox = new QCheckBox("Show CM/CMTS MIC TLVs");
    private final QCheckBox showTextTlvNumbersCheckbox = new QCheckBox("Show TLV comments in configuration text");
    private final QCheckBox includeTemplateBannerCheckbox = new QCheckBox("Include template banner");
    private final QComboBox appearanceSelector = new QComboBox();

    /**
     * Creates settings controls; the caller applies accepted appearance and display choices.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param showMic initial visibility of generated MIC TLVs
     * @param showTextTlvNumbers initial visibility of TLV-number comments
     * @param appearance non-null appearance preference
     */
    public GlobalSettingsDialog(QWidget parent, boolean showMic, boolean showTextTlvNumbers, Appearance appearance) {
        this(parent, showMic, showTextTlvNumbers, appearance, new OscarDocumentService());
    }

    /**
     * Creates settings controls; the caller applies accepted appearance and display choices.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param showMic initial visibility of generated MIC TLVs
     * @param showTextTlvNumbers initial visibility of TLV-number comments
     * @param appearance non-null appearance preference
     * @param service service used for registry, validation, preferences, or platform operations
     */
    public GlobalSettingsDialog(QWidget parent, boolean showMic, boolean showTextTlvNumbers, Appearance appearance,
                         OscarDocumentService service) {
        this(parent, showMic, showTextTlvNumbers, appearance, service, null);
    }

    /**
     * Creates settings controls; the caller applies accepted appearance and display choices.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param showMic initial visibility of generated MIC TLVs
     * @param showTextTlvNumbers initial visibility of TLV-number comments
     * @param appearance non-null appearance preference
     * @param service service used for registry, validation, preferences, or platform operations
     * @param activeType preferred file type, or null to use the first supported type
     */
    public GlobalSettingsDialog(QWidget parent, boolean showMic, boolean showTextTlvNumbers, Appearance appearance,
                         OscarDocumentService service, OscarDocumentService.FileType activeType) {
        super(parent);
        setObjectName("globalSettingsDialog");
        setWindowTitle("Settings - Global");
        setMinimumWidth(360);
        var layout = new QVBoxLayout(this);
        showMicTlvsCheckbox.setObjectName("showMicCheckbox");
        showMicTlvsCheckbox.setChecked(showMic);
        layout.addWidget(showMicTlvsCheckbox);
        showTextTlvNumbersCheckbox.setObjectName("showTextTlvNumbersCheckbox");
        showTextTlvNumbersCheckbox.setChecked(showTextTlvNumbers);
        layout.addWidget(showTextTlvNumbersCheckbox);
        includeTemplateBannerCheckbox.setObjectName("includeTemplateBannerCheckbox");
        includeTemplateBannerCheckbox.setChecked(true);
        layout.addWidget(includeTemplateBannerCheckbox);
        appearanceSelector.setObjectName("appearanceMode");
        appearanceSelector.addItem("System");
        appearanceSelector.addItem("Light");
        appearanceSelector.addItem("Dark");
        appearanceSelector.setCurrentIndex(appearance.ordinal());
        var form = new QFormLayout();
        form.addRow("Appearance", appearanceSelector);
        layout.addLayout(form);
        var common = new QPushButton("User Defined Common TLVs...");
        common.setObjectName("userDefinedCommonTlvs");
        layout.addWidget(common);
        common.clicked.connect(() -> {
            try {
                var dialog = new CommonTlvSettingsDialog(this, service, activeType);
                try {
                    dialog.exec();
                } finally {
                    dialog.dispose();
                }
            } catch (InspectionException exception) {
                QMessageBox.warning(this, "Catalog unavailable", "Could not load the TLV registry.");
            }
        });
        var buttons = new QDialogButtonBox();
        buttons.addButton(QDialogButtonBox.StandardButton.Ok);
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.accepted.connect(this::accept);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }

    /**
     * Sets the template-banner checkbox without persisting preferences.
     *
     * <p>Call on the Qt UI thread.
     * @param enabled true to include the template banner
     */
    public void setIncludeTemplateBanner(boolean enabled) {
        includeTemplateBannerCheckbox.setChecked(enabled);
    }
    /**
     * Reports whether newly created templates should include the configured banner.
     *
     * <p>Call on the Qt UI thread.
     * @return current banner checkbox state
     */
    public boolean includeTemplateBanner() {
        return includeTemplateBannerCheckbox.isChecked();
    }
    /**
     * Reports whether generated MIC TLVs should be visible.
     *
     * <p>Call on the Qt UI thread.
     * @return current MIC visibility checkbox state
     */
    public boolean showMicTlvs() {
        return showMicTlvsCheckbox.isChecked();
    }
    /**
     * Reports whether TLV-number comments should be visible in text previews.
     *
     * <p>Call on the Qt UI thread.
     * @return current text-comment visibility checkbox state
     */
    public boolean showTextTlvNumbers() {
        return showTextTlvNumbersCheckbox.isChecked();
    }
    /**
     * Returns the selected application appearance preference.
     *
     * <p>Call on the Qt UI thread.
     * @return selected appearance preference
     */
    public Appearance appearance() {
        return Appearance.values()[appearanceSelector.currentIndex()];
    }
}
