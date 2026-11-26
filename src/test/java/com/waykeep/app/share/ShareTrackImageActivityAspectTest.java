package com.waykeep.app.share;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShareTrackImageActivityAspectTest {

    @Test
    public void aspectPresetsKeepTheirPixelHeights() {
        assertEquals(1350, ShareTrackImageActivity.heightFor(ShareTrackImageActivity.Aspect.PORTRAIT_4_5));
        assertEquals(1080, ShareTrackImageActivity.heightFor(ShareTrackImageActivity.Aspect.SQUARE_1_1));
        assertEquals(1920, ShareTrackImageActivity.heightFor(ShareTrackImageActivity.Aspect.STORY_9_16));
    }
}
