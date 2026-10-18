package io.oscarui.ui;

import java.util.Arrays;

/** Session-only values. Never serialized or logged; shared key is supplied to Oscar. */
final class SessionSecrets implements AutoCloseable {
    private char[] shared = new char[0];

    private boolean hashCommunityStrings;

    /**
     * Clears the session secret buffer and resets the hashing preference.
     *
     * <p>Overwrites the owned character array; previously returned strings cannot be erased.
     */
    @Override public void close() {
        Arrays.fill(shared, '\0');
        shared = new char[0];
        hashCommunityStrings = false;
    }

    String sharedValue() { return new String(shared); }

    boolean hasShared() { return shared.length > 0; }

    boolean hashCommunityStrings() { return hashCommunityStrings; }

    void replace(String shared, boolean hashCommunityStrings) {
        close();
        this.shared = shared.toCharArray();
        this.hashCommunityStrings = hashCommunityStrings;
    }
}
