package studio.cortex.zeuschain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Deterministic, renderer-independent rules for Zeus: Temple Stack. */
enum class PlacementGrade { PERFECT, STABLE, CROOKED, COLLAPSE }

data class TempleBlock(
    val index: Int,
    val center: Float,
    val width: Float,
    val grade: PlacementGrade,
    val tilt: Float,
)

data class TempleDropResult(
    val grade: PlacementGrade,
    val overlapWidth: Float,
    val trimmedLeft: Float,
    val trimmedRight: Float,
    val scoreAward: Int,
    val coinsAward: Int,
    val stability: Float,
    val collapsed: Boolean,
)

/**
 * The visual game can animate these results freely, but the result itself is always
 * reproducible for the same block position and state.
 */
class TempleStackState(
    val baseCenter: Float = 540f,
    val baseWidth: Float = 760f,
    private val perfectTolerance: Float = 22f,
    private val collapseStability: Float = 100f,
) {
    private val mutableBlocks = mutableListOf(TempleBlock(0, baseCenter, baseWidth, PlacementGrade.STABLE, 0f))
    val blocks: List<TempleBlock> get() = mutableBlocks
    var score: Int = 0
        private set
    var coins: Int = 0
        private set
    var stability: Float = 0f
        private set
    var combo: Int = 0
        private set
    var isCollapsed: Boolean = false
        private set

    val height: Int get() = mutableBlocks.size - 1
    val top: TempleBlock get() = mutableBlocks.last()

    fun drop(center: Float, width: Float, golden: Boolean = false, keystone: Boolean = false): TempleDropResult {
        require(width > 0f) { "A temple block must have a positive width" }
        if (isCollapsed) return TempleDropResult(PlacementGrade.COLLAPSE, 0f, 0f, 0f, 0, 0, stability, true)

        val left = max(center - width / 2f, top.center - top.width / 2f)
        val right = min(center + width / 2f, top.center + top.width / 2f)
        val overlap = (right - left).coerceAtLeast(0f)
        if (overlap <= 0f) {
            isCollapsed = true
            combo = 0
            stability = collapseStability
            return TempleDropResult(PlacementGrade.COLLAPSE, 0f, 0f, 0f, 0, 0, stability, true)
        }

        val overlapCenter = (left + right) / 2f
        val centerOffset = abs(center - top.center)
        val ratio = overlap / width.coerceAtLeast(1f)
        val grade = when {
            centerOffset <= perfectTolerance && ratio >= .86f -> PlacementGrade.PERFECT
            ratio >= .46f -> PlacementGrade.STABLE
            else -> PlacementGrade.CROOKED
        }
        val tilt = when (grade) {
            PlacementGrade.PERFECT -> 0f
            PlacementGrade.STABLE -> ((center - top.center) / top.width * 5f).coerceIn(-5f, 5f)
            PlacementGrade.CROOKED -> ((center - top.center) / top.width * 12f).coerceIn(-12f, 12f)
            PlacementGrade.COLLAPSE -> 0f
        }
        val nextWidth = if (golden) max(overlap, top.width * .78f) else overlap
        val block = TempleBlock(mutableBlocks.size, overlapCenter, nextWidth, grade, tilt)
        mutableBlocks += block
        combo = if (grade == PlacementGrade.PERFECT) combo + 1 else 0
        stability = (stability + when (grade) {
            PlacementGrade.PERFECT -> -7f
            PlacementGrade.STABLE -> 2.5f
            PlacementGrade.CROOKED -> 14f
            PlacementGrade.COLLAPSE -> collapseStability
        }).coerceIn(0f, collapseStability)
        // A run of crooked placements destabilizes the temple even when blocks
        // still overlap. The final block is kept for the visual earthquake
        // frame, then the session ends exactly once.
        val earthquake = stability >= collapseStability
        if (earthquake) {
            isCollapsed = true
            combo = 0
        }
        val scoreAward = when (grade) {
            PlacementGrade.PERFECT -> 100 + combo * 25 + if (keystone) 500 else 0
            PlacementGrade.STABLE -> 40
            PlacementGrade.CROOKED -> 20
            PlacementGrade.COLLAPSE -> 0
        } + if (golden) 75 else 0
        val coinsAward = (if (grade == PlacementGrade.PERFECT) 3 else 1) + if (golden) 10 else 0
        score += scoreAward
        coins += coinsAward
        return TempleDropResult(grade, overlap, center - width / 2f - left, right - (center + width / 2f), scoreAward, coinsAward, stability, earthquake)
    }
}
