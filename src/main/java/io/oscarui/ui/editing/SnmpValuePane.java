/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.editing;

import com.comcast.oscar.ber.BERService;
import io.qt.widgets.*;
import java.math.BigInteger;
import java.util.regex.Pattern;

/** Structured SNMP varbind input; OSCAR still owns compilation and OID resolution. */
final class SnmpValuePane extends QWidget {
    private static final int IPV4_OCTET_COUNT = 4;
    private static final int MAX_IPV4_OCTET = (1 << Byte.SIZE) - 1;
    /*
     * Split an SNMP value statement into editor fields (no Snmp11/Snmp64 prefix or semicolon).
     * Example input: 1.3.6.1.2.1.1.5.0 OctetString "lab-modem"
     * Expected captures: group 1 = 1.3.6.1.2.1.1.5.0 (OID/name),
     * group 2 = OctetString (data-type token), group 3 = lab-modem (unquoted value).
     * Match the entire stripped input: OID/name, whitespace, word token, whitespace,
     * then a double-quoted value. DOTALL lets dots span line breaks; the value may be empty.
     * This extracts fields only; it does not validate OID syntax, supported types, or values.
     */
    private static final Pattern STATEMENT = Pattern.compile("(?s)^(.+?)\\s+(\\w+)\\s+\"(.*)\"$");
    private final QLineEdit oid = new QLineEdit();
    private final QComboBox dataType = new QComboBox();
    private final QLineEdit value = new QLineEdit();
    private final QLabel hint = new QLabel();
    private final Runnable changed;
    private boolean loading;
    private boolean maskedOriginal;
    private String originalType = "";

    SnmpValuePane(Runnable changed) {
        this.changed = changed;
        setObjectName("snmpValuePane");
        var form = new QFormLayout(this);
        form.setContentsMargins(0, 0, 0, 0);
        oid.setObjectName("snmpOid");
        oid.setPlaceholderText("1.3.6.1.2.1.1.5.0 or sysName.0");
        oid.setToolTip("Numeric OID or an OSCAR-resolvable symbolic name, including the instance/index.");
        dataType.setObjectName("snmpType");
        dataType.addItems(BERService.getDataTypeStringList());
        dataType.setCurrentText(BERService.OCTETSTRING);
        value.setObjectName("snmpValue");
        hint.setObjectName("snmpValueHint");
        hint.setWordWrap(true);
        form.addRow("OID", oid);
        form.addRow("Type", dataType);
        form.addRow("Value", value);
        form.addRow(hint);
        oid.textChanged.connect(ignored -> updateFields());
        value.textChanged.connect(ignored -> updateFields());
        dataType.currentTextChanged.connect(ignored -> updateFields());
    }

    void setStatement(String statement) {
        loading = true;
        maskedOriginal = false;
        oid.clear();
        value.clear();
        dataType.setCurrentText(BERService.OCTETSTRING);
        var match = STATEMENT.matcher(statement.strip());
        if (match.matches()) {
            oid.setText(match.group(1));
            String existingType = match.group(2);
            if (dataType.findText(existingType) < 0) {
                dataType.addItem(existingType);
            }
            dataType.setCurrentText(existingType);
            value.setText(match.group(3));
        } else if (!statement.isBlank()) {
            // Preserve unusual upstream syntax for correction rather than guessing a value.
            oid.setText(statement);
        }
        loading = false;
        updateFields();
    }

    void applyMask(boolean enabled, boolean keepOriginal) {
        var echo = enabled ? QLineEdit.EchoMode.Password : QLineEdit.EchoMode.Normal;
        oid.setEchoMode(echo);
        value.setEchoMode(echo);
        if (keepOriginal) {
            loading = true;
            maskedOriginal = true;
            originalType = dataType.currentText();
            oid.clear();
            value.clear();
            oid.setPlaceholderText("******** (leave both fields blank to keep)");
            value.setPlaceholderText("******** (leave both fields blank to keep)");
            loading = false;
            updateFields();
        }
    }

    String statement() {
        if (keepOriginal()) {
            return "";
        }
        return oid.text().strip() + " " + dataType.currentText() + " \"" + value.text() + "\"";
    }

    String issue() {
        if (keepOriginal()) {
            return "";
        }
        if (!oid.text().strip().matches("(?:\\.?[0-9]+(?:\\.[0-9]+)+|(?:[A-Za-z][A-Za-z0-9-]*::)?[A-Za-z][A-Za-z0-9_-]*(?:\\.[0-9]+)*)")) {
            return "Enter a numeric OID or symbolic name with its instance/index.";
        }
        String supplied = value.text();
        if (supplied.contains("\"") || supplied.contains("\n") || supplied.contains("\r")) {
            return "Enter one value without double quotes or line breaks; quotes are added automatically.";
        }
        String type = dataType.currentText();
        if (type.equals(BERService.HEXSTRING) && !supplied.matches("(?i)[0-9a-f]{2}(?::[0-9a-f]{2})*")) {
            return "Enter hex byte pairs separated by colons, for example 00:01:FF.";
        }
        if (type.equals(BERService.IPADDRESS)) {
            String[] octets = supplied.split("\\.", -1);
            if (octets.length != IPV4_OCTET_COUNT) {
                return "Enter an IPv4 address, for example 192.0.2.1.";
            }
            for (String octet : octets) {
                if (!octet.matches("[0-9]{1,3}") || Integer.parseInt(octet) > MAX_IPV4_OCTET) {
                    return "Each IPv4 octet must be between 0 and 255.";
                }
            }
        }
        if (isNumeric(type)) {
            try {
                if (!supplied.matches("-?[0-9]+")) {
                    return "Enter a decimal integer without spaces or quotes.";
                }
                BigInteger number = new BigInteger(supplied);
                BigInteger minimum = type.equals(BERService.INTEGER32) ? BigInteger.valueOf(Integer.MIN_VALUE) : BigInteger.ZERO;
                BigInteger maximum = type.equals(BERService.INTEGER32) ? BigInteger.valueOf(Integer.MAX_VALUE)
                        : BigInteger.ONE.shiftLeft(type.equals(BERService.COUNTER64) ? Long.SIZE : Integer.SIZE).subtract(BigInteger.ONE);
                if (number.compareTo(minimum) < 0 || number.compareTo(maximum) > 0) {
                    return "Value must be between " + minimum + " and " + maximum + ".";
                }
            } catch (NumberFormatException error) {
                return "Enter a decimal integer.";
            }
        }
        return "";
    }

    private boolean keepOriginal() {
        return maskedOriginal && oid.text().isEmpty() && value.text().isEmpty()
                && dataType.currentText().equals(originalType);
    }

    private static boolean isNumeric(String type) {
        return switch (type) {
            case BERService.INTEGER32, BERService.COUNTER32, BERService.COUNTER64,
                    BERService.GAUGE32, BERService.TIMETICKS -> true;
            default -> false;
        };
    }

    private void updateFields() {
        if (loading) {
            return;
        }
        hint.setText(switch (dataType.currentText()) {
            case BERService.INTEGER32 -> "Decimal integer: -2147483648 to 2147483647. Example: 2.";
            case BERService.COUNTER32, BERService.GAUGE32 -> "Unsigned decimal: 0 to 4294967295. Example: 100.";
            case BERService.COUNTER64 -> "Unsigned decimal: 0 to 18446744073709551615. OSCAR must accept this type before Apply is enabled.";
            case BERService.TIMETICKS -> "Hundredths of a second: 0 to 4294967295. Example: 100 = 1 second.";
            case BERService.IPADDRESS -> "IPv4 address. Example: 192.0.2.1.";
            case BERService.HEXSTRING -> "Hex byte pairs separated by colons. Example: 00:01:FF.";
            default -> "Text without surrounding quotes. Example: lab-name. Spaces are preserved.";
        } + " Quotes are added automatically.");
        changed.run();
    }
}
