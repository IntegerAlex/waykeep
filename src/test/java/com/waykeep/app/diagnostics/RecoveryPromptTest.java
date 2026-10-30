package com.waykeep.app.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.Optional;
import java.util.UUID;

import com.waykeep.app.data.models.Track;

public class RecoveryPromptTest {

    @Test
    public void doesNotOfferWhileRecordingOrAfterCleanEnd() {
        DiagnosticsRecovery.RecoveryCandidate candidate = candidate();

        assertFalse(DiagnosticsRecovery.shouldOffer(true, Optional.of(candidate)));
        assertFalse(DiagnosticsRecovery.shouldOffer(false, Optional.empty()));
    }

    @Test
    public void offersForUncleanRecoverableSession() {
        assertTrue(DiagnosticsRecovery.shouldOffer(false, Optional.of(candidate())));
    }

    private DiagnosticsRecovery.RecoveryCandidate candidate() {
        return new DiagnosticsRecovery.RecoveryCandidate(
                UUID.fromString("5ca87f32-0e7d-4f03-9d7c-28e502192b82"),
                new Track.Id(42),
                UUID.fromString("7fa09361-201f-4a31-95c2-09a277067a11"),
                UUID.fromString("2f180602-9098-454e-9bd3-53170351c815"),
                new File("recording.jsonl"));
    }
}
