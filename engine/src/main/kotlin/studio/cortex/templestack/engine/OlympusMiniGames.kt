package studio.cortex.templestack.engine

/** Pure, replayable mini-game rules. The presentation layer only animates these results. */
object OlympusMiniGames {
    const val slotCost = 25
    const val chestCost = 35
    private val symbols = listOf("BOLT", "GEM", "CROWN", "OWL", "LAUREL")

    data class SlotResult(val reels: List<String>, val rewardCoins: Int, val rewardCrystals: Int) {
        val headline: String get() = when {
            reels.distinct().size == 1 -> "DIVINE JACKPOT"
            reels[0] == reels[1] || reels[1] == reels[2] -> "PAIR OF FATE"
            else -> "TRY THE ORACLE AGAIN"
        }
    }

    data class ChestResult(val chest: Int, val rewardCoins: Int, val rewardLives: Int, val rewardAid: String?)

    fun spin(seed: Int): SlotResult {
        val reels = List(3) { index -> symbols[((seed * 31 + index * 17 + 13) and Int.MAX_VALUE) % symbols.size] }
        val jackpot = reels.distinct().size == 1
        val pair = !jackpot && (reels[0] == reels[1] || reels[1] == reels[2])
        return SlotResult(reels, if (jackpot) 220 else if (pair) 70 else 8, if (jackpot) 1 else 0)
    }

    fun openChest(chest: Int): ChestResult {
        require(chest in 0..2)
        return when (chest) {
            0 -> ChestResult(chest, 90, 0, null)
            1 -> ChestResult(chest, 40, 1, null)
            else -> ChestResult(chest, 55, 0, "MASON'S BLESSING")
        }
    }
}
