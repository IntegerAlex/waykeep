package com.waykeep.app.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Locale;

public class StringUtilsDateRangeTest {

    @Test
    public void formatsSingleDay() {
        assertEquals("Nov 24, 2026",
                StringUtils.formatDateRange(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 24), Locale.US));
    }

    @Test
    public void formatsRangeWithEndash() {
        assertEquals("Nov 1, 2026 – Nov 24, 2026",
                StringUtils.formatDateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 24), Locale.US));
    }
}
