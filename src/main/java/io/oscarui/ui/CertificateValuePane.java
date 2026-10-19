/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.oscarui.service.CertificateInput;
import io.qt.widgets.*;
import java.nio.file.Files;
import java.nio.file.Path;

/** File and multiline certificate input shared by CVC add and edit dialogs. */
final class CertificateValuePane extends QWidget {
    private final QPlainTextEdit text = new QPlainTextEdit();
    private final QLabel status = new QLabel();
    private String encodedValue = "";
    private String issue = "Select a certificate or paste HEX / PEM text.";

    CertificateValuePane(Runnable changed) {
        setObjectName("certificateValuePane");
        var layout = new QVBoxLayout(this);
        layout.setContentsMargins(0, 0, 0, 0);
        var browse = new QPushButton("Choose certificate…");
        browse.setObjectName("chooseCertificate");
        layout.addWidget(browse);
        text.setObjectName("certificateText");
        text.setPlaceholderText("Paste HEX bytes or a complete PEM CERTIFICATE block");
        text.setMaximumHeight(140);
        layout.addWidget(text);
        status.setWordWrap(true);
        layout.addWidget(status);
        browse.clicked.connect(() -> {
            var selection = QFileDialog.getOpenFileName(this, "Select certificate", "",
                    "Certificates (*.der *.cer *.crt *.pem *.hex *.txt);;All files (*)");
            if (selection.result == null || selection.result.isEmpty()) {
                return;
            }
            try {
                Path path = Path.of(selection.result);
                byte[] certificate = CertificateInput.fromFile(Files.readAllBytes(path), path.getFileName().toString());
                text.setPlainText(CertificateInput.toOscarHex(certificate));
            } catch (java.io.IOException | IllegalArgumentException error) {
                QMessageBox.warning(this, "Certificate input", "Cannot read certificate. Select a readable DER, PEM or HEX file.");
            }
        });
        text.textChanged.connect(() -> {
            try {
                byte[] certificate = CertificateInput.parse(text.toPlainText());
                encodedValue = CertificateInput.toOscarHex(certificate);
                issue = "";
                status.setText(certificate.length + " bytes · Certificate trust is not checked");
            } catch (IllegalArgumentException error) {
                encodedValue = "";
                issue = error.getMessage();
                status.setText(issue);
            }
            changed.run();
        });
    }

    void setValue(String value) {
        text.setPlainText(value);
    }

    String value() {
        return encodedValue;
    }

    String issue() {
        return issue;
    }
}
