/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.oscarui.service.OscarDocumentService.Document;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns independent document snapshots and stable identities, without owning widgets or workers. */
final class WorkspaceSessions {
    enum Format { TEXT, BINARY }
    record SessionId(UUID value) {
        static SessionId create() { return new SessionId(UUID.randomUUID()); }
    }

    static final class Session {
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

        SessionId id() { return id; }
        Document document() { return document; }
        boolean modified() { return revision != savedRevision; }
        long revision() { return revision; }
        Format format() { return document.path() == null && document.origin() != null ? Format.BINARY : Format.TEXT; }
        Path path() { return document.path() != null ? document.path() : document.origin() == null ? null : document.origin().path(); }
        String name() { return path() == null ? untitled : path().getFileName().toString(); }
        String status() { return status; }
        void status(String value) { status = value; }
        ConfigurationTreePane.ViewState treeState() { return treeState; }
        int textScroll() { return textScroll; }
        int textHorizontalScroll() { return textHorizontalScroll; }

        void remember(ConfigurationTreePane.ViewState tree, int vertical, int horizontal) {
            treeState = tree;
            textScroll = vertical;
            textHorizontalScroll = horizontal;
        }

        void replace(Document replacement, boolean changed) {
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

    List<Session> all() { return List.copyOf(sessions); }
    Session active() { return active; }
    Session find(SessionId id) { return sessions.stream().filter(session -> session.id().equals(id)).findFirst().orElse(null); }
    void activate(Session session) { active = session; }

    Session add(Document document, boolean modified) {
        String untitled = document.path() == null && document.origin() == null ? "Untitled " + nextUntitled++ : "";
        var session = new Session(document, modified, untitled);
        sessions.add(session);
        return session;
    }

    void remove(Session session) {
        int index = sessions.indexOf(session);
        sessions.remove(session);
        if (active == session) {
            active = sessions.isEmpty() ? null : sessions.get(Math.min(index, sessions.size() - 1));
        }
    }

    Session forPath(Path path) {
        if (path == null) {
            return null;
        }
        return sessions.stream().filter(session -> session.path() != null && sameFile(session.path(), path))
                .findFirst().orElse(null);
    }

    String title(Session session) {
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
