package studio.cortex.thunderbound.engine

enum class Power(val displayName: String, val color: Long, val speed: Float) {
    SKYBOLT("SKYBOLT", 0x22D9FFFF, 1.0f),
    FORKSTORM("FORKSTORM", 0xF5B82EFF, 0.92f),
    EAGLE_DIVE("EAGLE DIVE", 0xF5FDFFFF, 1.05f),
    AEGIS_ORB("AEGIS ORB", 0x7440D5FF, 0.82f),
    TEMPEST("TEMPEST", 0x3DD18DFF, 0.90f),
    TITAN_BREAKER("TITAN BREAKER", 0xFF9F2BFF, 0.78f)
}

data class LevelDefinition(
    val id: Int,
    val world: Int,
    val name: String,
    val casts: Int,
    val enemyCount: Int,
    val blockRows: Int,
    val rewardCoins: Int,
    val power: Power
)

object Campaign {
    private val worldNames = listOf("TEMPEST COAST", "MARBLE REACH", "BRONZE VALE", "CRYSTAL SKY", "CLOUD CITADEL", "TITAN'S CROWN")
    fun level(id: Int): LevelDefinition {
        require(id in 1..ProductConfig.campaignLevels)
        val world = (id - 1) / 15 + 1
        val local = (id - 1) % 15 + 1
        return LevelDefinition(
            id = id, world = world, name = "${worldNames[world - 1]} $local",
            casts = if (local < 6) 4 else 3,
            enemyCount = 1 + (local - 1) / 4,
            blockRows = 2 + (local - 1) / 5,
            rewardCoins = 55 + id * 8,
            power = Power.entries[(world - 1) % Power.entries.size]
        )
    }
}

data class GameProgress(
    val schemaVersion: Int = 1,
    val coins: Int = 250,
    val gems: Int = 12,
    val unlockedLevel: Int = 1,
    val stars: Map<Int, Int> = emptyMap(),
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reducedFlashes: Boolean = false
) {
    fun withResult(level: Int, earnedStars: Int, earnedCoins: Int): GameProgress = copy(
        coins = coins + earnedCoins,
        unlockedLevel = maxOf(unlockedLevel, minOf(ProductConfig.campaignLevels, level + 1)),
        stars = stars + (level to maxOf(stars[level] ?: 0, earnedStars))
    )
}
