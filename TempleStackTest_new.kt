package studio.cortex.zeuschain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TempleStackEngineTest {
    @Test fun centeredPlacementIsPerfectAndBuilds() {
        val state = TempleStackState()
        val result = state.drop(540f, 760f)
        assertEquals(PlacementGrade.PERFECT, result.grade)
        assertEquals(1, state.height)
        assertTrue(result.scoreAward > 100)
    }

    @Test fun partialPlacementTrimsTheBlock() {
        val state = TempleStackState()
        val result = state.drop(760f, 760f)
        assertEquals(PlacementGrade.STABLE, result.grade)
        assertTrue(result.overlapWidth < 760f)
        assertEquals(result.overlapWidth, state.top.width)
    }

    @Test fun noOverlapCollapsesExactlyOnce() {
        val state = TempleStackState()
        val result = state.drop(1500f, 200f)
        assertEquals(PlacementGrade.COLLAPSE, result.grade)
        assertTrue(state.isCollapsed)
        assertEquals(0, state.height)
        assertEquals(PlacementGrade.COLLAPSE, state.drop(540f, 760f).grade)
    }

    @Test fun goldenBlockRewardsCoinsAndWidensTheNextPlatform() {
        val state = TempleStackState()
        val result = state.drop(540f, 400f, golden = true)
        assertEquals(PlacementGrade.PERFECT, result.grade)
        assertEquals(13, result.coinsAward)
        assertTrue(state.top.width > 400f)
    }

    @Test fun repeatedCrookedPlacementsTriggerEarthquakeCollapse() {
        val state = TempleStackState()
        var result: TempleDropResult? = null
        repeat(8) {
            result = state.drop(state.top.center + state.top.width * .8f, state.top.width)
            if (result!!.collapsed) return@repeat
        }
        assertTrue(result!!.collapsed)
        assertTrue(state.isCollapsed)
    }
}
