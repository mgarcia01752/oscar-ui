package io.github.mgarcia01752.oscarui.ui;

import java.util.Arrays;

/** Session-only values. Never serialized or logged; shared key is supplied to Oscar. */
final class SessionSecrets implements AutoCloseable {
    private char[] community = new char[0];
    private char[] shared = new char[0];
    private boolean hashCommunityStrings;

    String communityValue() { return new String(community); }
    String sharedValue() { return new String(shared); }
    boolean hasShared() { return shared.length > 0; }

    boolean hashCommunityStrings() { return hashCommunityStrings; }

    void replace(String community, String shared, boolean hashCommunityStrings) {
        close();
        this.community = community.toCharArray();
        this.shared = shared.toCharArray();
        this.hashCommunityStrings = hashCommunityStrings;
    }

    @Override public void close() {
        Arrays.fill(community, '\0');
        Arrays.fill(shared, '\0');
        community = new char[0];
        shared = new char[0];
        hashCommunityStrings = false;
    }
}
