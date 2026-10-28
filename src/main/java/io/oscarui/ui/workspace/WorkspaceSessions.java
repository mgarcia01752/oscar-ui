/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.workspace;

import io.oscarui.service.OscarDocumentService.Document;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns independent document snapshots and stable identities, without owning widgets or workers. */
public final class WorkspaceSessions {
    public enum Format { TEXT, BINARY }
    /** Stable session identity, independent of file paths.
     * @param value UUID assigned when the session is created
     */
    public record SessionId(UUID value) {
        static SessionId create() { return new SessionId(UUID.randomUUID()); }
    }

    public static final class Session {
        private final SessionId id = SessionId.create();
        private final String untitled;
        private Document document;
        private long revision;
        private long savedRevision;
        private ConfigurationTreePane.ViewState treeState;
        private int textScroll;
        private int textHorizontalScroll;
        private String status = "";

        Session(Document document, boolean modified, String untitled) {
            this.document = document;
            this.untitled = untitled;
            revision = modified ? 1 : 0;
        }

        /**
         * Returns the stable identity used to route asynchronous results to this session.
         * @return stable session identifier
         */
        public SessionId id() {
            return id;
        }
        /**
         * Returns the current immutable document snapshot for this session.
         * @return current document snapshot
         */
        public Document document() {
            return document;
        }
        /**
         * Reports whether the session revision differs from its saved revision.
         * @return true when the current revision has not been saved
         */
        public boolean modified() {
            return revision != savedRevision;
        }
        /**
         * Returns the monotonically increasing revision used to reject stale asynchronous results.
         * @return current session revision
         */
        public long revision() {
            return revision;
        }
        /**
         * Reports whether this session is backed by text or binary input.
         * @return Text or Binary based on the current document origin
         */
        public Format format() {
            return document.path() == null && document.origin() != null ? Format.BINARY : Format.TEXT;
        }
        /**
         * Returns the text destination or imported binary origin, if present.
         * @return associated path, or null for an unsaved new document
         */
        public Path path() {
            return document.path() != null ? document.path() : document.origin() == null ? null : document.origin().path();
        }
        /**
         * Returns the filename or generated untitled label for this session.
         * @return filename or untitled display label
         */
        public String name() {
            return path() == null ? untitled : path().getFileName().toString();
        }
        /**
         * Reads or replaces this session's operation-status message.
         * @return current operation-status text
         */
        public String status() {
            return status;
        }
        /**
         * Reads or replaces this session's operation-status message.
         * @param value non-null operation-status text
         */
        public void status(String value) {
            status = value;
        }
        /**
         * Returns the last remembered tree selection, expansion, and scrolling state.
         * @return remembered view, or null before a view has been captured
         */
        public ConfigurationTreePane.ViewState treeState() {
            return treeState;
        }
        /**
         * Returns the last remembered vertical text scrollbar value.
         * @return vertical scrollbar value
         */
        public int textScroll() {
            return textScroll;
        }
        /**
         * Returns the last remembered horizontal text scrollbar value.
         * @return horizontal scrollbar value
         */
        public int textHorizontalScroll() {
            return textHorizontalScroll;
        }

        /**
         * Stores tree and text view positions for the next tab activation.
         * @param tree tree widget or captured tree state to retain
         * @param vertical vertical text scrollbar value
         * @param horizontal horizontal text scrollbar value
         */
        public void remember(ConfigurationTreePane.ViewState tree, int vertical, int horizontal) {
            treeState = tree;
            textScroll = vertical;
            textHorizontalScroll = horizontal;
        }

        /**
         * Replaces the document snapshot and advances its revision.
         * @param replacement new immutable document snapshot
         * @param changed true to retain unsaved state; false to mark the new revision saved
         */
        public void replace(Document replacement, boolean changed) {
            document = replacement;
            revision++;
            if (!changed) {
                savedRevision = revision;
            }
        }
    }

    private final List<Session> sessions = new ArrayList<>();
    private Session active;
    private int nextUntitled = 1;

    /**
     * Returns a snapshot of the open-session list in tab order.
     * @return immutable list snapshot containing the mutable sessions
     */
    public List<Session> all() {
        return List.copyOf(sessions);
    }
    /**
     * Returns the currently active session.
     * @return active session, or null when none is selected
     */
    public Session active() {
        return active;
    }
    /**
     * Looks up an open session by its stable identifier.
     * @param id stable session identifier
     * @return matching session, or null if it is no longer open
     */
    public Session find(SessionId id) {
        return sessions.stream().filter(session -> session.id().equals(id)).findFirst().orElse(null);
    }
    /**
     * Sets the active session without modifying document contents.
     * @param session open session to inspect or select; null is permitted when clearing active selection
     */
    public void activate(Session session) {
        active = session;
    }

    /**
     * Adds a document session with an independent identity and unsaved state.
     * @param document non-null document snapshot; this operation does not mutate it
     * @param modified true if the new document has unsaved changes
     * @return newly registered session; activation remains the caller's responsibility
     */
    public Session add(Document document, boolean modified) {
        String untitled = document.path() == null && document.origin() == null ? "Untitled " + nextUntitled++ : "";
        var session = new Session(document, modified, untitled);
        sessions.add(session);
        return session;
    }

    /**
     * Removes an open session and selects a neighbor if the active session is removed.
     * @param session open session to inspect or select; null is permitted when clearing active selection
     */
    public void remove(Session session) {
        int index = sessions.indexOf(session);
        sessions.remove(session);
        if (active == session) {
            active = sessions.isEmpty() ? null : sessions.get(Math.min(index, sessions.size() - 1));
        }
    }

    /**
     * Finds an open session referring to the same file, resolving filesystem aliases where possible.
     * @param path input path, or null when no file is associated
     * @return matching session, or null if the path is not open
     */
    public Session forPath(Path path) {
        if (path == null) {
            return null;
        }
        return sessions.stream().filter(session -> session.path() != null && sameFile(session.path(), path))
                .findFirst().orElse(null);
    }

    /**
     * Returns a tab label, adding path components when filenames collide.
     * @param session open session to inspect or select; null is permitted when clearing active selection
     * @return disambiguated tab label
     */
    public String title(Session session) {
        if (session.path() == null || sessions.stream().filter(other -> other.name().equals(session.name())).count() == 1) {
            return session.name();
        }
        Path path = canonical(session.path());
        for (int count = 2; count <= path.getNameCount(); count++) {
            String suffix = path.subpath(path.getNameCount() - count, path.getNameCount()).toString();
            boolean distinct = sessions.stream().filter(other -> other != session && other.path() != null)
                    .noneMatch(other -> canonical(other.path()).endsWith(suffix));
            if (distinct) {
                return suffix;
            }
        }
        return path.toString();
    }

    private static boolean sameFile(Path left, Path right) {
        try {
            if (Files.exists(left) && Files.exists(right)) {
                return Files.isSameFile(left, right);
            }
        } catch (IOException ignored) {
            // Fall back to normalized identities if a file has disappeared.
        }
        return canonical(left).equals(canonical(right));
    }

    private static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException ignored) {
            Path absolute = path.toAbsolutePath().normalize();
            try {
                return absolute.getParent().toRealPath().resolve(absolute.getFileName());
            } catch (IOException missingParent) {
                return absolute;
            }
        }
    }
}
