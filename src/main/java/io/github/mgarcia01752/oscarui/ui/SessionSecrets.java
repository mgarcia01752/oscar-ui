package io.github.mgarcia01752.oscarui.ui;

import java.util.Arrays;

/** Session-only values. Never serialized, logged, or passed to an encoder. */
final class SessionSecrets implements AutoCloseable {
    private char[] community = new char[0];
    private char[] shared = new char[0];

    String communityValue() { return new String(community); }
    String sharedValue() { return new String(shared); }
    boolean hasShared() { return shared.length > 0; }

    void replace(String community, String shared) {
        close();
        this.community = community.toCharArray();
        this.shared = shared.toCharArray();
    }

    @Override public void close() {
        Arrays.fill(community, '\0');
        Arrays.fill(shared, '\0');
        community = new char[0];
        shared = new char[0];
    }
}
