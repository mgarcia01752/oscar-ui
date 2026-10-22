/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import static org.junit.jupiter.api.Assertions.*;
import io.oscarui.service.OscarDocumentService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceSessionsTest {
    @TempDir Path directory;

    @Test void independentDirtyStatesNamesAndFileIdentities() throws Exception {
        var service = new OscarDocumentService();
        var workspace = new WorkspaceSessions();
        Path one = Files.createDirectories(directory.resolve("one")).resolve("same.txt");
        Path two = Files.createDirectories(directory.resolve("two")).resolve("same.txt");
        Files.writeString(one, "Docsis { NetworkAccess 1; }");
        Files.writeString(two, "Docsis { NetworkAccess 0; }");
        var first = workspace.add(service.open(one), false);
        var second = workspace.add(service.open(two), false);
        assertNotEquals(workspace.title(first), workspace.title(second));
        assertSame(first, workspace.forPath(one.getParent().resolve("../one/same.txt")));
        assertEquals(WorkspaceSessions.Format.TEXT, first.format());
        first.replace(first.document(), true);
        assertTrue(first.modified());
        assertFalse(second.modified());
        first.replace(first.document(), false);
        assertFalse(first.modified());
        workspace.activate(first);
        workspace.remove(first);
        assertSame(second, workspace.active());
        workspace.remove(second);
        assertNull(workspace.active());
    }

    @Test void draftsHaveStableDistinctIdentitiesAndUnsavedState() throws Exception {
        var service = new OscarDocumentService();
        var type = service.types().getFirst();
        var workspace = new WorkspaceSessions();
        var first = workspace.add(service.create(type, false), true);
        var second = workspace.add(service.create(type, false), true);
        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.name(), second.name());
        assertTrue(first.modified());
        assertSame(first, workspace.find(first.id()));
        workspace.remove(first);
        assertNull(workspace.find(first.id()));
        assertNull(workspace.forPath(null));
    }
}
