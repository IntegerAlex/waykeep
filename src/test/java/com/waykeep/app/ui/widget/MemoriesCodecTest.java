package com.waykeep.app.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.waykeep.app.data.backup.MemoriesCodec;

import org.junit.Test;

import java.util.List;

/**
 * Memories travel inside KMZ backups as memories.json; the codec must round-trip
 * and tolerate older or damaged backups (a backup must never fail to import
 * because of an optional payload).
 */
public class MemoriesCodecTest {

    @Test
    public void roundTripsMoodAndPhoto() {
        String json = MemoriesCodec.encode(List.of(
                new MemoriesCodec.Entry("aaa-bbb", 2, "images/memory-1.jpg"),
                new MemoriesCodec.Entry("ccc-ddd", 4, null)));

        List<MemoriesCodec.Entry> decoded = MemoriesCodec.decode(json);
        assertEquals(2, decoded.size());
        assertEquals("aaa-bbb", decoded.get(0).trackUuid());
        assertEquals(2, decoded.get(0).mood());
        assertEquals("images/memory-1.jpg", decoded.get(0).photoName());
        assertEquals(4, decoded.get(1).mood());
        assertEquals(null, decoded.get(1).photoName());
    }

    @Test
    public void decodesEntriesWithoutPhoto() {
        List<MemoriesCodec.Entry> decoded = MemoriesCodec.decode(
                "{\"version\":1,\"memories\":[{\"trackUuid\":\"x\"}]}");

        assertEquals(1, decoded.size());
        assertEquals("x", decoded.get(0).trackUuid());
        assertEquals(-1, decoded.get(0).mood());
        assertEquals(null, decoded.get(0).photoName());
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
