package com.screenx.ai

import com.screenx.ai.utils.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun zero() = assertEquals("00:00", TimeFormat.elapsed(0))
    @Test fun secondsOnly() = assertEquals("00:05", TimeFormat.elapsed(5_000))
    @Test fun minutes() = assertEquals("02:03", TimeFormat.elapsed(123_000))
    @Test fun hours() = assertEquals("1:01:01", TimeFormat.elapsed(3_661_000))
    @Test fun roundsDownPartialSeconds() = assertEquals("00:01", TimeFormat.elapsed(1_999))
}
