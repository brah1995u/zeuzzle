package studio.cortex.templestack.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class PlacementGrade { PERFECT, STABLE, CROOKED, COLLAPSE }
enum class Modifier { NONE, WIND, GOLDEN_BLOCKS, NARROW_FOUNDATION, TITAN_STORM, MASTERY }

data class TempleLevel(
    val id: Int,
    val world: Int,
    val worldName: String,
    val name: String,
    val targetHeight: Int,
    val speed: Float,
    val modifier: Modifier,
    val twoStarStability: Float,
    val threeStarPerfects: Int,
    val rewardCoins: Int,
)

object TempleCampaign {
    private val worlds = listOf(
        "DAWN RUINS" to Modifier.NONE,
        "AEGEAN WIND" to Modifier.WIND,
        "HEPHAESTUS FORGE" to Modifier.GOLDEN_BLOCKS,
        "ORACLE HEIGHTS" to Modifier.NARROW_FOUNDATION,
        "TITAN STORM" to Modifier.TITAN_STORM,
        "CROWN OF OLYMPUS" to Modifier.MASTERY,
    )

    fun level(id: Int): TempleLevel {
        require(id in 1..60)
        val world = (id - 1) / 10 + 1
        val local = (id - 1) % 10 + 1
        val (worldName, modifier) = worlds[world - 1]
        val targets = listOf(8, 9, 10, 11, 12, 14, 15, 17, 19, 21)
        val title = when (modifier) {
            Modifier.NONE -> "MARBLE AWAKENING"
            Modifier.WIND -> "GUST OF AURORA"
            Modifier.GOLDEN_BLOCKS -> "FORGE RHYTHM"
            Modifier.NARROW_FOUNDATION -> "ORACLE'S LEDGE"
            Modifier.TITAN_STORM -> "THUNDER TRIAL"
            Modifier.MASTERY -> "CROWN ASCENT"
        }
        return TempleLevel(
            id = id,
            world = world,
            worldName = worldName,
            name = "$title $local",
            targetHeight = targets[local - 1] + (world - 1) * 5,
            speed = 1.15f + (world - 1) * .18f + local * .035f,
            modifier = modifier,
            twoStarStability = (62f - world * 3f).coerceAtLeast(38f),
            threeStarPerfects = 1 + (local - 1) / 3 + (world - 1) / 2,
            rewardCoins = 35 + id * 9,
        )
    }
}

data class TempleBlock(
    val center: Float,
    val width: Float,
    val grade: PlacementGrade,
    val golden: Boolean,
)

data class DropResult(
    val grade: PlacementGrade,
    val overlap: Float,
    val scoreAward: Int,
    val coinsAward: Int,
    val stability: Float,
    val collapsed: Boolean,
)

data class TempleSnapshot(
    val height: Int,
    val score: Int,
    val coins: Int,
    val perfects: Int,
    val combo: Int,
    val stability: Float,
    val collapsed: Boolean,
    val blocks: List<TempleBlock>,
)

/** Deterministic rules: renderers animate results, but never decide them. */
class TempleStackSession(
    private val foundationWidth: Float = 720f,
    private val perfectTolerance: Float = 20f,
) {
    private val placed = mutableListOf(TempleBlock(540f, foundationWidth, PlacementGrade.STABLE, false))
    private var score = 0
    private var coins = 0
    private var stability = 0f
    private var combo = 0
    private var perfects = 0
    private var collapsed = false

    val top: TempleBlock get() = placed.last()
    val height: Int get() = placed.size - 1

    fun drop(center: Float, width: Float, golden: Boolean = false, keystone: Boolean = false): DropResult {
        require(width > 0f)
        if (collapsed) return DropResult(PlacementGrade.COLLAPSE, 0f, 0, 0, stability, true)
        val left = max(center - width / 2f, top.center - top.width / 2f)
        val right = min(center + width / 2f, top.center + top.width / 2f)
        val overlap = (right - left).coerceAtLeast(0f)
        if (overlap <= 0f) {
            collapsed = true
            combo = 0
            stability = 100f
            return DropResult(PlacementGrade.COLLAPSE, 0f, 0, 0, stability, true)
        }
        val offset = abs(center - top.center)
        val grade = when {
            offset <= perfectTolerance && overlap >= top.width * .96f -> PlacementGrade.PERFECT
            overlap >= top.width * .58f -> PlacementGrade.STABLE
            else -> PlacementGrade.CROOKED
        }
        val nextWidth = if (golden) max(overlap, top.width * .78f) else overlap
        placed += TempleBlock((left + right) / 2f, nextWidth, grade, golden)
        combo = if (grade == PlacementGrade.PERFECT) combo + 1 else 0
        if (grade == PlacementGrade.PERFECT) perfects++
        stability = (stability + when (grade) {
            PlacementGrade.PERFECT -> -8f
            PlacementGrade.STABLE -> 3f
            PlacementGrade.CROOKED -> 14f
            PlacementGrade.COLLAPSE -> 100f
        }).coerceIn(0f, 100f)
        val scoreAward = when (grade) {
            PlacementGrade.PERFECT -> 100 + combo * 25 + if (keystone) 500 else 0
            PlacementGrade.STABLE -> 45
            PlacementGrade.CROOKED -> 20
            PlacementGrade.COLLAPSE -> 0
        } + if (golden) 75 else 0
        val coinsAward = (if (grade == PlacementGrade.PERFECT) 3 else 1) + if (golden) 12 else 0
        score += scoreAward
        coins += coinsAward
        if (stability >= 100f) { collapsed = true; combo = 0 }
        return DropResult(grade, overlap, scoreAward, coinsAward, stability, collapsed)
    }

    fun snapshot(): TempleSnapshot = TempleSnapshot(height, score, coins, perfects, combo, stability, collapsed, placed.toList())

    /** Aid effects are explicit and bounded; they can never revive a collapsed temple. */
    fun relieveStability(amount: Float) {
        if (!collapsed) stability = (stability - amount.coerceAtLeast(0f)).coerceAtLeast(0f)
    }
}

object StarRules {
    fun stars(level: TempleLevel, snapshot: TempleSnapshot): Int = when {
        snapshot.height < level.targetHeight || snapshot.collapsed -> 0
        snapshot.stability > level.twoStarStability -> 1
        snapshot.perfects < level.threeStarPerfects -> 2
        else -> 3
    }
}
