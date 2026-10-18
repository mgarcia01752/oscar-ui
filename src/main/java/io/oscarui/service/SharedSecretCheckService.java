package io.oscarui.service;

import com.comcast.oscar.compiler.DocsisMicVerifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import static com.comcast.oscar.compiler.DocsisMicVerifier.Status.*;

/** Checks original binary MICs through OSCAR without opening an editor document. */
public final class SharedSecretCheckService {
    /** The supplied key matched all present keyed MICs, or at least one differed. */
    public enum Outcome { CORRECT, INCORRECT }

    /**
     * Checks a binary using compatible DOCSIS framing, trying D3.1 before D4.0.
     *
     * <p>Shared encodings do not establish a unique DOCSIS edition. No text is
     * compiled, no file is written, and no supplied secret is stored or logged.
     * A valid CM MIC is required before interpreting keyed digest outcomes.
     *
     * @param binaryPath file to read, regardless of its extension
     * @param secretBytes exact key bytes; the caller retains ownership
     * @return whether all present keyed MICs match the supplied key
     * @throws InspectionException if input cannot be safely checked
     */
    public Outcome check(Path binaryPath, byte[] secretBytes) throws InspectionException {
        if (secretBytes == null || secretBytes.length == 0) {
            throw new InspectionException("Enter a shared secret.");
        }
        byte[] binaryBytes;
        try (var input = Files.newInputStream(binaryPath)) {
            binaryBytes = input.readNBytes(OscarInspectionService.MAX_BYTES + 1);
        } catch (Exception failure) {
            throw new InspectionException("Cannot read the selected binary file.");
        }
        try {
            if (binaryBytes.length == 0 || binaryBytes.length > OscarInspectionService.MAX_BYTES) {
                throw new InspectionException("Select a nonempty binary within the supported file-size limit.");
            }
            for (var profile : DocsisMicVerifier.Profile.values()) {
                var result = DocsisMicVerifier.verify(binaryBytes, profile, secretBytes);
                if (result.cm() != MATCH || result.cmts() == NOT_PRESENT
                        || result.cmts() == CANNOT_VERIFY || result.extended() == CANNOT_VERIFY) {
                    continue;
                }
                return result.cmts() == MATCH && result.extended() != MISMATCH
                        ? Outcome.CORRECT : Outcome.INCORRECT;
            }
            throw new InspectionException("Unable to check this file. Select a DOCSIS binary with valid CM integrity "
                    + "and supported CMTS MICs. Text, damaged files, and missing MICs cannot be checked.");
        } catch (RuntimeException failure) {
            throw new InspectionException("OSCAR could not check this binary file.");
        } finally {
            Arrays.fill(binaryBytes, (byte) 0);
        }
    }
}
