package studio.cortex.templestack.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TempleStackSessionTest {
    @Test fun centeredBlockIsPerfect() {
        val session = TempleStackSession()
        assertEquals(PlacementGrade.PERFECT, session.drop(540f, 720f).grade)
        assertEquals(1, session.height)
    }

    @Test fun noOverlapCollapsesOnlyOnce() {
        val session = TempleStackSession()
        assertTrue(session.drop(1400f, 180f).collapsed)
        assertEquals(0, session.height)
        assertEquals(PlacementGrade.COLLAPSE, session.drop(540f, 720f).grade)
    }

    @Test fun dailyClaimIsIdempotent() {
        val first = TempleProgress().claimDaily(123L)!!
        assertEquals(null, first.claimDaily(123L))
        assertTrue(first.coins > TempleProgress().coins)
    }

    @Test fun lostLifeRecoversAfterTheConfiguredInterval() {
        val spent = TempleProgress().spendLife(1_000L)!!
        assertEquals(4, spent.lives)
        assertEquals(5, spent.withRecoveredLives(901_000L).lives)
    }

    @Test fun miniGameRewardsAreDeterministicAndNonNegative() {
        val slot = OlympusMiniGames.spin(22)
        assertEquals(3, slot.reels.size)
        assertTrue(slot.rewardCoins >= 0)
        assertTrue(OlympusMiniGames.openChest(2).rewardAid != null)
    }
}
