package com.waykeep.app.io.file.importer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * CVE-2026-107056 guard: a file that did not come from Waykeep's own import UI must never be
 * treated as trusted, not even when the launching app leaves the token empty.
 */
public class ImportTrustTest {

    @Test
    public void tokenFromOwnUiIsTrusted() {
        assertTrue(ImportTrust.tokenMatches("abc", "abc"));
    }

    @Test
    public void emptyTokenIsRejectedWhenNothingWasMarked() {
        assertFalse(ImportTrust.tokenMatches(null, null));
    }

    @Test
    public void forgedTokenIsRejected() {
        assertFalse(ImportTrust.tokenMatches("guessed", "abc"));
    }

    @Test
    public void tokenIsRejectedOnceConsumed() {
        assertFalse(ImportTrust.tokenMatches("abc", null));
    }
}
