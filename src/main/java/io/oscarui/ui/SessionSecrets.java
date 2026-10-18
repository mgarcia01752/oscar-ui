/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import java.util.Arrays;
import java.util.List;
import io.oscarui.service.SnmpSecretMask;

/** Session-only values. Never serialized or logged; shared key is supplied to Oscar. */
final class SessionSecrets implements AutoCloseable {
    private char[] shared = new char[0];

    private boolean obfuscateSnmpSecrets;
    private List<SnmpSecretMask.Rule> customRules = List.of();

    /**
     * Clears the session secret buffer and resets masking and custom SNMP rules.
     *
     * <p>Overwrites the owned character array; previously returned strings cannot be erased.
     */
    @Override public void close() {
        Arrays.fill(shared, '\0');
        shared = new char[0];
        obfuscateSnmpSecrets = false;
        customRules = List.of();
    }

    String sharedValue() { return new String(shared); }

    boolean hasShared() { return shared.length > 0; }

    boolean obfuscateSnmpSecrets() { return obfuscateSnmpSecrets; }

    List<SnmpSecretMask.Rule> customRules() {
        return customRules;
    }

    SnmpSecretMask masking() {
        return new SnmpSecretMask(obfuscateSnmpSecrets, customRules);
    }

    void replace(String shared, boolean obfuscateSnmpSecrets) {
        replace(shared, obfuscateSnmpSecrets, customRules);
    }

    void replace(String shared, boolean obfuscateSnmpSecrets,
                 List<SnmpSecretMask.Rule> customRules) {
        close();
        this.shared = shared.toCharArray();
        this.obfuscateSnmpSecrets = obfuscateSnmpSecrets;
        this.customRules = List.copyOf(customRules);
    }
}
