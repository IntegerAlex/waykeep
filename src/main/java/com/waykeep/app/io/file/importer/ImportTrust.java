package com.waykeep.app.io.file.importer;

import android.content.Intent;

import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * CVE-2026-107056: {@link ImportActivity} is exported so other apps can hand it a GPX file.
 * Any app can also put arbitrary extras on that Intent, so an extra alone cannot mark a launch
 * as ours. Instead, Waykeep's own import UI registers a one-shot random token in this process
 * and hands it to the Intent it starts.
 */
public final class ImportTrust {

    private static final String EXTRA_TRUST_TOKEN = "com.waykeep.app.extra.IMPORT_TRUST_TOKEN";

    @Nullable
    private static String pendingToken;

    private ImportTrust() {
    }

    /**
     * Marks an Intent started by Waykeep's own import UI. Single use: the next
     * {@link #consume(Intent)} invalidates it.
     */
    public static Intent markInternal(Intent intent) {
        pendingToken = UUID.randomUUID().toString();
        return intent.putExtra(EXTRA_TRUST_TOKEN, pendingToken);
    }

    /**
     * @return true only if this Intent carries the token that {@link #markInternal(Intent)}
     * created in this process and that token has not been used yet.
     */
    public static boolean consume(Intent intent) {
        String offered = intent.getStringExtra(EXTRA_TRUST_TOKEN);
        boolean trusted = tokenMatches(offered, pendingToken);
        if (trusted) {
            pendingToken = null;
        }
        return trusted;
    }

    static boolean tokenMatches(@Nullable String offered, @Nullable String pending) {
        return pending != null && pending.equals(offered);
    }
}
