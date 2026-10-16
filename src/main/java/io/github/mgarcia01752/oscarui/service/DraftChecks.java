package io.github.mgarcia01752.oscarui.service;

import java.util.*;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService.*;

/** UI preview checks only. No encoder, protocol-minimum rules, or secret access. */
public final class DraftChecks {
    public record Check(String id, String status, String description) {}

    public static List<Check> inspect(OscarDocumentService service, Document document, boolean sharedSecretSet) {
        var result = new ArrayList<Check>();
        result.add(new Check("structure", "PASS", "Text parsed as " + document.type().header()));
        result.add(new Check("entries", document.root().children().isEmpty() ? "FAIL" : "PASS",
                document.root().children().isEmpty() ? "Document has no TLV entries" : "Document contains TLV entries"));
        if (document.reference()) result.add(new Check("reference", "UNVERIFIED", "Reference-template values need review"));
        inspectNodes(service, document, document.root(), result);
        result.add(new Check("shared-secret", sharedSecretSet ? "SET" : "MISSING",
                "Shared secret for this session (not used by this preview)"));
        result.add(new Check("minimum-rules", "NOT IMPLEMENTED", "Required TLVs and dependencies for this configuration type"));
        result.add(new Check("encoded-values", "NOT IMPLEMENTED", "Encoded lengths and complete value constraints"));
        result.add(new Check("binary-output", "NOT IMPLEMENTED", "Binary encoding and integrity generation"));
        return List.copyOf(result);
    }

    private static void inspectNodes(OscarDocumentService service, Document document, Node parent, List<Check> result) {
        Map<List<Integer>, Integer> counts = new HashMap<>();
        for (Node node : parent.children()) {
            String location = node.path() + " at offset " + node.start();
            var definition = service.definition(document, node);
            if (definition == null) {
                result.add(new Check("catalog", "UNVERIFIED", location + ": unmapped entry"));
                continue;
            }
            int count = counts.merge(node.path(), 1, Integer::sum);
            if (definition.maxInstances() >= 0 && count > definition.maxInstances()) {
                result.add(new Check("instances", "FAIL", location + ": catalog instance limit exceeded"));
            }
            String issue = service.valueIssue(document.type(), definition, node.value());
            if (!issue.isEmpty()) result.add(new Check("value-syntax", "FAIL", location + ": " + issue));
            inspectNodes(service, document, node, result);
        }
    }

    private DraftChecks() {}
}
