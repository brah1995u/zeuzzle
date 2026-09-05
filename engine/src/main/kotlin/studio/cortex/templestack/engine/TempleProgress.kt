package studio.cortex.templestack.engine

data class TempleProgress(
    val schema: Int = 2,
    val coins: Int = 180,
    val crystals: Int = 6,
    val lives: Int = 5,
    val lifeAnchorMillis: Long = 0L,
    val unlockedLevel: Int = 1,
    val stars: Map<Int, Int> = emptyMap(),
    val endlessBestHeight: Int = 0,
    val endlessBestScore: Int = 0,
    val achievementClaims: Set<String> = emptySet(),
    val ownedThemes: Set<String> = setOf("DAWN MARBLE"),
    val selectedTheme: String = "DAWN MARBLE",
    val aidInventory: Map<String, Int> = mapOf("ALIGNMENT" to 1, "CALM WIND" to 1, "MASON'S BLESSING" to 1),
    val dailyDay: Long = -1L,
    val dailyIndex: Int = 0,
    val music: Boolean = true,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val reducedFlashes: Boolean = false,
    val highContrast: Boolean = false,
) {
    fun withRecoveredLives(nowMillis: Long, recoveryMillis: Long = 15 * 60 * 1000L): TempleProgress {
        if (lives >= 5) return if (lifeAnchorMillis == 0L) this else copy(lifeAnchorMillis = 0L)
        val anchor = if (lifeAnchorMillis > 0L) lifeAnchorMillis else nowMillis
        val recovered = ((nowMillis - anchor).coerceAtLeast(0L) / recoveryMillis).toInt()
        if (recovered <= 0) return copy(lifeAnchorMillis = anchor)
        val nextLives = (lives + recovered).coerceAtMost(5)
        return copy(lives = nextLives, lifeAnchorMillis = if (nextLives == 5) 0L else anchor + recovered * recoveryMillis)
    }

    fun spendLife(nowMillis: Long): TempleProgress? {
        val ready = withRecoveredLives(nowMillis)
        if (ready.lives <= 0) return null
        val next = ready.lives - 1
        return ready.copy(lives = next, lifeAnchorMillis = if (next == 4 && ready.lifeAnchorMillis == 0L) nowMillis else ready.lifeAnchorMillis)
    }

    fun rewardLives(amount: Int): TempleProgress = copy(lives = (lives + amount.coerceAtLeast(0)).coerceAtMost(5), lifeAnchorMillis = if (lives + amount >= 5) 0L else lifeAnchorMillis)
    fun complete(level: TempleLevel, snapshot: TempleSnapshot): TempleProgress {
        val earnedStars = StarRules.stars(level, snapshot)
        if (earnedStars == 0) return this
        val prior = stars[level.id] ?: 0
        val firstClear = prior == 0
        return copy(
            coins = (coins + snapshot.coins + if (firstClear) level.rewardCoins else 0).coerceAtLeast(0),
            crystals = crystals + if (earnedStars == 3 && prior < 3) 1 else 0,
            unlockedLevel = maxOf(unlockedLevel, (level.id + 1).coerceAtMost(60)),
            stars = stars + (level.id to maxOf(prior, earnedStars)),
        )
    }

    fun claimDaily(day: Long): TempleProgress? {
        if (dailyDay == day) return null
        val rewards = intArrayOf(40, 55, 70, 85, 100, 125, 180)
        return copy(coins = coins + rewards[dailyIndex], dailyDay = day, dailyIndex = (dailyIndex + 1) % 7)
    }

    fun buyAid(id: String, price: Int): TempleProgress? {
        if (id.isBlank() || price < 0 || coins < price) return null
        return copy(coins = coins - price, aidInventory = aidInventory + (id to (aidInventory[id] ?: 0) + 1))
    }

    fun consumeAid(id: String): TempleProgress? {
        val count = aidInventory[id] ?: 0
        if (count <= 0) return null
        return copy(aidInventory = aidInventory + (id to count - 1))
    }
}

object TempleProgressCodec {
    fun encode(value: TempleProgress): String = buildString {
        append("v=${value.schema};c=${value.coins};g=${value.crystals};l=${value.lives};la=${value.lifeAnchorMillis};u=${value.unlockedLevel};")
        append("eh=${value.endlessBestHeight};es=${value.endlessBestScore};dd=${value.dailyDay};di=${value.dailyIndex};")
        append("m=${value.music};s=${value.sound};h=${value.haptics};rf=${value.reducedFlashes};hc=${value.highContrast};")
        append("stars=").append(value.stars.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" })
        append(";ac=").append(value.achievementClaims.sorted().joinToString(","))
        append(";ot=").append(value.ownedThemes.sorted().joinToString("|"))
        append(";st=").append(value.selectedTheme)
        append(";ai=").append(value.aidInventory.entries.sortedBy { it.key }.joinToString("|") { "${it.key}:${it.value.coerceAtLeast(0)}" })
    }

    fun decode(raw: String?): TempleProgress {
        if (raw.isNullOrBlank()) return TempleProgress()
        val fields = raw.split(';').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        fun number(key: String, fallback: Int) = fields[key]?.toIntOrNull()?.coerceAtLeast(0) ?: fallback
        val stars = fields["stars"].orEmpty().split(',').mapNotNull { entry ->
            entry.split(':').takeIf { it.size == 2 }?.let { (id, stars) -> id.toIntOrNull()?.let { n -> stars.toIntOrNull()?.coerceIn(0, 3)?.let { n to it } } }
        }.toMap().filterKeys { it in 1..60 }
        return TempleProgress(
            coins = number("c", 180), crystals = number("g", 6), lives = number("l", 5).coerceIn(0, 5),
            lifeAnchorMillis = fields["la"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L, unlockedLevel = number("u", 1).coerceIn(1, 60),
            endlessBestHeight = number("eh", 0), endlessBestScore = number("es", 0),
            dailyDay = fields["dd"]?.toLongOrNull()?.coerceAtLeast(-1L) ?: -1L, dailyIndex = number("di", 0).coerceIn(0, 6),
            music = fields["m"]?.toBooleanStrictOrNull() ?: true, sound = fields["s"]?.toBooleanStrictOrNull() ?: true,
            haptics = fields["h"]?.toBooleanStrictOrNull() ?: true, reducedFlashes = fields["rf"]?.toBooleanStrictOrNull() ?: false,
            highContrast = fields["hc"]?.toBooleanStrictOrNull() ?: false, stars = stars,
            achievementClaims = fields["ac"].orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
            ownedThemes = (fields["ot"].orEmpty().split('|').filter { it.isNotBlank() }.toSet() + "DAWN MARBLE"),
            selectedTheme = fields["st"].takeIf { it in fields["ot"].orEmpty().split('|') || it == "DAWN MARBLE" } ?: "DAWN MARBLE",
            aidInventory = decodeAid(fields["ai"]),
        )
    }

    private fun decodeAid(raw: String?): Map<String, Int> {
        val starter = TempleProgress().aidInventory.toMutableMap()
        raw.orEmpty().split('|').forEach { item ->
            val (id, amount) = item.split(':', limit = 2).let { it.getOrNull(0) to it.getOrNull(1)?.toIntOrNull() }
            if (!id.isNullOrBlank() && amount != null) starter[id] = amount.coerceAtLeast(0)
        }
        return starter
    }
}
