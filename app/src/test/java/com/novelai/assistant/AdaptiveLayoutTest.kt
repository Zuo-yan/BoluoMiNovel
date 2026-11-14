package com.novelai.assistant.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun compactPhonePortraitUsesBottomBarAndSingleListCol() {
        val m = computeAdaptiveMetrics(widthDp = 412.dp, heightDp = 915.dp)
        assertEquals(WindowWidthClass.COMPACT, m.widthClass)
        assertEquals(AdaptiveNavigationType.BOTTOM_BAR, m.navigationType)
        assertFalse(m.isTablet)
        assertFalse(m.isLandscape)
        assertFalse(m.twoPaneEnabled)
        assertFalse(m.dualPageReaderEnabled)
        assertEquals(1, m.listGridColumns)
    }

    @Test
    fun mediumTabletPortraitUsesNavigationRailAndTwoCols() {
        val m = computeAdaptiveMetrics(widthDp = 768.dp, heightDp = 1200.dp)
        assertEquals(WindowWidthClass.MEDIUM, m.widthClass)
        assertEquals(AdaptiveNavigationType.NAVIGATION_RAIL, m.navigationType)
        assertTrue(m.isTablet)
        assertFalse(m.isLandscape)
        assertFalse(m.twoPaneEnabled)
        assertFalse(m.dualPageReaderEnabled)
        assertEquals(2, m.listGridColumns)
    }

    @Test
    fun expandedTabletLandscapeEnablesTwoPaneAndDualPageReader() {
        val m = computeAdaptiveMetrics(widthDp = 1200.dp, heightDp = 768.dp)
        assertEquals(WindowWidthClass.EXPANDED, m.widthClass)
        assertEquals(AdaptiveNavigationType.NAVIGATION_RAIL, m.navigationType)
        assertTrue(m.isTablet)
        assertTrue(m.isLandscape)
        assertTrue(m.twoPaneEnabled)
        assertTrue(m.dualPageReaderEnabled)
        assertEquals(3, m.listGridColumns)
    }
}
