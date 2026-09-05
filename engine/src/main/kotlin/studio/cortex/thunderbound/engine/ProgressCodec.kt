package studio.cortex.thunderbound.engine

/** A compact, dependency-free save representation. Unknown/missing fields safely fall back. */
object ProgressCodec {
    fun encode(progress: GameProgress): String = buildString {
        append("v=${progress.schemaVersion};c=${progress.coins};g=${progress.gems};u=${progress.unlockedLevel};")
        append("s=${progress.soundEnabled};h=${progress.hapticsEnabled};r=${progress.reducedFlashes};")
        append("stars=")
        append(progress.stars.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" })
    }

    fun decode(raw: String?): GameProgress {
        if (raw.isNullOrBlank()) return GameProgress()
        val pairs = raw.split(';').mapNotNull { token -> token.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        fun int(key: String, fallback: Int) = pairs[key]?.toIntOrNull()?.coerceAtLeast(0) ?: fallback
        val stars = pairs["stars"].orEmpty().split(',').mapNotNull { item ->
            item.split(':').takeIf { it.size == 2 }?.let { (a, b) -> a.toIntOrNull()?.let { id -> b.toIntOrNull()?.let { id to it.coerceIn(0, 3) } } }
        }.toMap()
        return GameProgress(
            coins = int("c", 250), gems = int("g", 12),
            unlockedLevel = int("u", 1).coerceIn(1, ProductConfig.campaignLevels), stars = stars,
            soundEnabled = pairs["s"]?.toBooleanStrictOrNull() ?: true,
            hapticsEnabled = pairs["h"]?.toBooleanStrictOrNull() ?: true,
            reducedFlashes = pairs["r"]?.toBooleanStrictOrNull() ?: false
        )
    }
}
