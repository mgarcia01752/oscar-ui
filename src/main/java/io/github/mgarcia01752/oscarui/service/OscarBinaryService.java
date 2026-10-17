package io.github.mgarcia01752.oscarui.service;

import com.comcast.oscar.configurationfile.ConfigurationFile;
import com.comcast.oscar.configurationfile.ConfigurationFileImport;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService.Document;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService.Node;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/** Oscar encoder adapter with source-file protection and atomic binary writes. */
public final class OscarBinaryService {
    private final OscarDocumentService documents;

    public OscarBinaryService(OscarDocumentService documents) { this.documents = documents; }

    public boolean requiresSharedSecret(Document document) {
        return !document.type().family().equals("packetcable");
    }

    public byte[] encode(Document document, String sharedSecret) throws InspectionException {
        if (requiresSharedSecret(document) && (sharedSecret == null || sharedSecret.isEmpty())) {
            throw new InspectionException("Set the shared secret in Settings > Secrets before saving binary.");
        }
        if (containsSnmp(document.root())) {
            throw new InspectionException("Binary export of SNMP statements is not supported yet.");
        }
        try {
            // The pinned legacy compiler logs its key at DEBUG. Disable that
            // specific logger before passing session credentials to Oscar.
            Configurator.setLevel("com.comcast.oscar.compiler.DocsisCompiler", Level.OFF);
            String text = document.text();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            var imported = new ConfigurationFileImport(new StringBuilder(text));
            if (imported.getConfigurationFileType() != document.type().id()) {
                throw new InspectionException("Oscar resolved a different configuration type.");
            }
            var compiled = new ConfigurationFile(document.type().id(), imported.getTlvBuilder(),
                    sharedSecret == null ? "" : sharedSecret);
            compiled.commit();
            byte[] bytes = compiled.toByteArray();
            if (bytes == null || bytes.length == 0) {
                throw new InspectionException("Oscar produced no binary output.");
            }
            return bytes;
        } catch (RuntimeException | LinkageError error) {
            // Upstream exception messages may contain input values; keep UI
            // diagnostics generic and never print the exception or credentials.
            throw new InspectionException("Oscar could not encode this configuration. Check its values, required TLVs, "
                    + "and the selected type. The destination was not changed.");
        }
    }

    public Path save(Document document, Path destination, String expectedHash, String sharedSecret)
            throws InspectionException {
        Path target = destination.toAbsolutePath().normalize();
        rejectTextDestination(document, target);
        byte[] bytes = encode(document, sharedSecret);
        rejectTextDestination(document, target);
        documents.saveBytes(target, bytes, expectedHash);
        return target;
    }

    private void rejectTextDestination(Document document, Path target) throws InspectionException {
        if (document.path() == null) return;
        try {
            if (target.equals(document.path()) || (Files.exists(target) && Files.exists(document.path())
                    && Files.isSameFile(target, document.path()))) {
                throw new InspectionException("Choose a different destination for binary output; keep the editable text file.");
            }
        } catch (IOException error) {
            throw new InspectionException("Cannot check the binary destination.", error);
        }
    }

    private boolean containsSnmp(Node node) {
        return node.name().equals("Snmp11") || node.name().equals("Snmp64")
                || node.children().stream().anyMatch(this::containsSnmp);
    }
}
