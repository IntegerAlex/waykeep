package com.waykeep.app.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.waykeep.app.data.backup.MemoriesCodec;

import org.junit.Test;

import java.util.List;

/**
 * Memories travel inside KMZ backups as memories.json; the codec must round-trip
 * multiple photos per memory, still read the pre-multi-photo format, and tolerate
 * older or damaged backups (a backup must never fail to import because of an
 * optional payload).
 */
public class MemoriesCodecTest {

    @Test
    public void roundTripsMoodAndMultiplePhotos() {
        String json = MemoriesCodec.encode(List.of(
                new MemoriesCodec.Entry("aaa-bbb", 2, List.of("images/m1.jpg", "images/m2.jpg")),
                new MemoriesCodec.Entry("ccc-ddd", 4, List.of())));

        List<MemoriesCodec.Entry> decoded = MemoriesCodec.decode(json);
        assertEquals(2, decoded.size());
        assertEquals("aaa-bbb", decoded.get(0).trackUuid());
        assertEquals(2, decoded.get(0).mood());
        assertEquals(2, decoded.get(0).photoNames().size());
        assertEquals("images/m1.jpg", decoded.get(0).photoNames().get(0));
        assertEquals("images/m2.jpg", decoded.get(0).photoNames().get(1));
        assertEquals(4, decoded.get(1).mood());
        assertTrue(decoded.get(1).photoNames().isEmpty());
    }

    @Test
    public void decodesTheOldSinglePhotoFormat() {
        List<MemoriesCodec.Entry> decoded = MemoriesCodec.decode(
                "{\"version\":1,\"memories\":[{\"trackUuid\":\"x\",\"mood\":3,"
                        + "\"photo\":\"images/old.jpg\"}]}");

        assertEquals(1, decoded.size());
        assertEquals(3, decoded.get(0).mood());
        assertEquals(List.of("images/old.jpg"), decoded.get(0).photoNames());
    }

    @Test
    public void decodesEntriesWithoutPhotos() {
        List<MemoriesCodec.Entry> decoded = MemoriesCodec.decode(
                "{\"version\":1,\"memories\":[{\"trackUuid\":\"x\"}]}");

        assertEquals(1, decoded.size());
        assertEquals("x", decoded.get(0).trackUuid());
        assertEquals(-1, decoded.get(0).mood());
        assertTrue(decoded.get(0).photoNames().isEmpty());
    }

    @Test
    public void skipsEntriesWithoutUuid() {
        List<MemoriesCodec.Entry> decoded = MemoriesCodec.decode(
                "{\"version\":1,\"memories\":[{\"mood\":3},{\"trackUuid\":\"ok\",\"mood\":1}]}");

        assertEquals(1, decoded.size());
        assertEquals("ok", decoded.get(0).trackUuid());
    }

    @Test
    public void damagedJsonYieldsNothingInsteadOfFailingTheImport() {
        assertTrue(MemoriesCodec.decode("not json at all").isEmpty());
        assertTrue(MemoriesCodec.decode("").isEmpty());
    }
}
