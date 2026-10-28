/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.settings;

import java.util.Arrays;
import java.util.List;
import io.oscarui.service.SnmpSecretMask;

/** Session-only values. Never serialized or logged; shared key is supplied to Oscar. */
public final class SessionSecrets implements AutoCloseable {
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

    /**
     * Returns a new string containing the session secret; callers must not log or persist it.
     * @return secret string, empty if none is set; this copy cannot be erased by close()
     */
    public String sharedValue() {
        return new String(shared);
    }

    /**
     * Reports whether a shared secret is present in this session.
     * @return true if the session holds a nonempty secret
     */
    public boolean hasShared() {
        return shared.length > 0;
    }

    /**
     * Reports whether SNMP display masking is enabled.
     * @return current display-masking setting
     */
    public boolean obfuscateSnmpSecrets() {
        return obfuscateSnmpSecrets;
    }

    /**
     * Returns the immutable custom masking rules for this session.
     * @return immutable rule list
     */
    public List<SnmpSecretMask.Rule> customRules() {
        return customRules;
    }

    /**
     * Builds a masking policy from the current session settings.
     * @return new display-masking policy
     */
    public SnmpSecretMask masking() {
        return new SnmpSecretMask(obfuscateSnmpSecrets, customRules);
    }

    /**
     * Erases the previous owned secret and replaces session-only secret and masking settings.
     * @param shared non-null secret text; kept only in memory
     * @param obfuscateSnmpSecrets true to mask sensitive SNMP values in displayed content
     */
    public void replace(String shared, boolean obfuscateSnmpSecrets) {
        replace(shared, obfuscateSnmpSecrets, customRules);
    }

    /**
     * Erases the previous owned secret and replaces session-only secret and masking settings.
     * @param shared non-null secret text; kept only in memory
     * @param obfuscateSnmpSecrets true to mask sensitive SNMP values in displayed content
     * @param customRules non-null custom rules copied into immutable session storage
     */
    public void replace(String shared, boolean obfuscateSnmpSecrets,
                 List<SnmpSecretMask.Rule> customRules) {
        close();
        this.shared = shared.toCharArray();
        this.obfuscateSnmpSecrets = obfuscateSnmpSecrets;
        this.customRules = List.copyOf(customRules);
    }
}
