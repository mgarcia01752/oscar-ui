package io.github.mgarcia01752.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentEditingTest {
    @TempDir Path temporary;
    private final OscarDocumentService service = new OscarDocumentService();

    private OscarDocumentService.Document open(String text) throws Exception {
        return service.open(Files.writeString(temporary.resolve("input.txt"), text));
    }

    @Test void editsExactRepeatedInstanceAndPreservesCommentsBomAndCrLf() throws Exception {
        String text = "\uFEFFDocsis {\r\n MaxCPEAllowed 3;\r\n MaxCPEAllowed /* before */ 004 /* after */;\r\n}";
        var original = open(text);
        var selected = original.root().children().get(1);
        assertEquals("004", selected.value());
        assertSame(original, service.replaceValue(original, selected, "004"));
        var changed = service.replaceValue(original, selected, "5");
        assertEquals(text.replace("004", "5"), changed.text());
        assertThrows(InspectionException.class, () -> service.replaceValue(changed, selected, "6"));
        assertThrows(InspectionException.class, () -> service.replaceValue(original, selected, "5; NetworkAccess 1"));
        var saved = service.save(changed, original.path(), original.diskHash());
        assertEquals(changed.text(), service.open(saved.path()).text());
    }

    @Test void deletesOnlySelectedRepeatedInstanceAndNeverWritesSource() throws Exception {
        String text = "Docsis { /* keep */ MaxCPEAllowed 3; MaxCPEAllowed 4; /* tail */ }";
        var original = open(text);
        var changed = service.delete(original, original.root().children().get(1));
        assertEquals(text.replace("MaxCPEAllowed 4;", ""), changed.text());
        assertEquals(text, Files.readString(original.path()));
        assertEquals(1, changed.root().children().size());
        assertThrows(InspectionException.class, () -> service.delete(original, original.root()));
        assertThrows(InspectionException.class, () -> service.delete(changed, original.root().children().get(0)));
    }

    @Test void deletesLastNestedChildAndAllowsInsertionAgain() throws Exception {
        var type = service.types().stream().filter(t -> t.header().equals("Docsis-4.0")).findFirst().orElseThrow();
        var empty = service.create(type, false);
        var parentDefinition = service.choices(empty, empty.root()).stream()
                .filter(d -> d.path().equals(java.util.List.of(17))).findFirst().orElseThrow();
        var withParent = open(type.header() + " { " + parentDefinition.name() + "; }");
        var parent = withParent.root().children().getFirst();
        var childDefinition = service.choices(withParent, parent).stream()
                .filter(d -> d.path().equals(java.util.List.of(17, 1))).findFirst().orElseThrow();
        var withChild = service.insert(withParent, parent, childDefinition, "2");
        var edited = service.replaceValue(withChild, withChild.root().children().getFirst().children().getFirst(), "3");
        assertEquals("3", edited.root().children().getFirst().children().getFirst().value());
        var deleted = service.delete(edited, edited.root().children().getFirst().children().getFirst());
        assertTrue(deleted.root().children().getFirst().children().isEmpty());
        assertTrue(service.choices(deleted, deleted.root().children().getFirst()).contains(childDefinition));
        var restored = service.insert(deleted, deleted.root().children().getFirst(), childDefinition, "2");
        assertEquals(2, OscarDocumentService.count(restored.root()));
        var removedParent = service.delete(restored, restored.root().children().getFirst());
        assertTrue(removedParent.root().children().isEmpty());
    }

    @Test void keepsUnmappedReferencesProtectedAndChecksExplicitlyIncomplete() throws Exception {
        var document = open("/* Oscar Desktop reference template: not deployment defaults. */\nDocsis { UnknownPlaceholder 1; }");
        var node = document.root().children().getFirst();
        assertFalse(service.canDelete(document, node));
        assertFalse(service.canEdit(document, node));
        var checks = DraftChecks.inspect(service, document, false);
        assertTrue(checks.stream().anyMatch(c -> c.id().equals("minimum-rules") && c.status().equals("NOT IMPLEMENTED")));
        assertTrue(checks.stream().anyMatch(c -> c.id().equals("shared-secret") && c.status().equals("MISSING")));
    }

    @Test void scaffoldsImmediateRegistryChildrenAndPopulatesNestedContainersExplicitly() throws Exception {
        for (String header : java.util.List.of("Docsis", "Docsis-4.0")) {
            var type = service.types().stream().filter(entry -> entry.header().equals(header)).findFirst().orElseThrow();
            var registry = com.comcast.oscar.dictionary.TlvRegistry.forFamily(type.family());
            String text = "\uFEFF/* keep */\r\n" + header + " {\r\n}\r\n";
            var original = open(text);
            var classifier = registry.find(java.util.List.of(22));
                if (classifier == null) {
                classifier = service.choices(original, original.root()).stream()
                    .filter(entry -> registry.children(entry.path()).stream()
                        .anyMatch(child -> service.isContainer(type, child)
                            && service.editingIssue(type, child).isEmpty()))
                    .findFirst().orElseThrow();
                }
            var changed = service.insert(original, original.root(), classifier, "");
            var parent = changed.root().children().getFirst();
            var expected = registry.children(classifier.path()).stream()
                    .filter(child -> !service.editingIssue(type, child).equals("Deferred editing scope"))
                    .filter(child -> child.maxInstances() != 0).map(com.comcast.oscar.dictionary.TlvDefinition::path).toList();
            assertEquals(expected, parent.children().stream().map(OscarDocumentService.Node::path).toList());
            assertTrue(parent.children().stream().allMatch(child -> child.children().isEmpty() && child.value().isEmpty()));
            assertTrue(parent.children().stream().allMatch(child -> service.canDelete(changed, child)));
            assertTrue(changed.text().startsWith("\uFEFF/* keep */\r\n"));
            assertFalse(changed.text().replace("\r\n", "").contains("\n"));
            assertEquals(text, Files.readString(original.path()));
                var nested = parent.children().stream().filter(OscarDocumentService.Node::container)
                    .findFirst().orElseThrow();
            assertTrue(service.canPopulate(changed, nested));
            var populated = service.populate(changed, nested);
            var updatedNested = populated.root().children().getFirst().children().stream()
                    .filter(child -> child.path().equals(nested.path())).findFirst().orElseThrow();
            assertEquals(registry.children(nested.path()).stream()
                            .filter(child -> !service.editingIssue(type, child).equals("Deferred editing scope"))
                            .filter(child -> child.maxInstances() != 0)
                            .map(com.comcast.oscar.dictionary.TlvDefinition::path).toList(),
                    updatedNested.children().stream().map(OscarDocumentService.Node::path).toList());
            assertFalse(service.canPopulate(populated, updatedNested));
            assertThrows(InspectionException.class, () -> service.populate(populated, nested));
            assertThrows(InspectionException.class, () -> service.populate(populated, updatedNested));
            var saved = service.save(populated, temporary.resolve(header + "-scaffold.txt"), null);
            assertEquals(populated.text(), service.open(saved.path()).text());
        }
    }
}
