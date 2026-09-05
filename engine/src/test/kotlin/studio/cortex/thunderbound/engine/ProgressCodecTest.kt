package studio.cortex.thunderbound.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressCodecTest {
    @Test fun progress_round_trip_preserves_rewards() {
        val result = GameProgress().withResult(1, 3, 63)
        assertEquals(result, ProgressCodec.decode(ProgressCodec.encode(result)))
    }
    @Test fun campaign_has_ninety_valid_levels() {
        assertEquals(90, (1..90).map(Campaign::level).size)
        assertTrue(Campaign.level(90).world == 6)
    }
}
