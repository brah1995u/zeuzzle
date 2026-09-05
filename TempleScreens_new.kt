package studio.cortex.zeuschain.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import kotlin.math.sin
import studio.cortex.zeuschain.PlacementGrade
import studio.cortex.zeuschain.TempleStackState

abstract class TempleScreen(protected val game: ZeusChainGame) : ScreenAdapter() {
    protected val ui = Ui(game.assets)
    override fun resize(width: Int, height: Int) = ui.viewport.update(width, height, true)
    open fun onSystemBack() = game.showTempleMenu()
    protected fun title(text: String) {
        ui.fitText(text, Rectangle(190f, 1825f, 700f, 76f), game.assets.title, Theme.GOLD, .78f)
    }
}

class TempleMenuScreen(game: ZeusChainGame) : TempleScreen(game) {
    override fun render(delta: Float) {
        ui.begin(); ui.background(Backdrop.FIGMA_DAY, shade = .12f)
        game.assets.batch.begin(); game.assets.batch.draw(game.assets.ui("title_banner"), 95f, 1370f, 890f, 390f); game.assets.batch.end()
        ui.fitText("ZEUS", Rectangle(220f, 1580f, 640f, 90f), game.assets.title, Theme.GOLD, .9f)
        ui.fitText("TEMPLE STACK", Rectangle(210f, 1500f, 660f, 66f), game.assets.heading, Theme.CYAN, .78f)
        ui.fitText("BUILD THE TEMPLE OF OLYMPUS", Rectangle(190f, 1440f, 700f, 38f), game.assets.small, Theme.MARBLE, .82f)
        if (ui.button("PLAY", Rectangle(170f, 1060f, 740f, 146f), primary = true)) game.showTempleGameplay()
        if (ui.button("ENDLESS OLYMPUS", Rectangle(190f, 920f, 700f, 102f))) game.showTempleGameplay()
        if (ui.button("TEMPLE PATH", Rectangle(190f, 800f, 700f, 102f))) game.showTempleGameplay()
        ui.cleanPanel(Rectangle(160f, 560f, 760f, 150f), Color.valueOf("071D46DD"))
        ui.fitText("DROP MARBLE BLOCKS • FIND PERFECT BALANCE", Rectangle(190f, 610f, 700f, 45f), game.assets.small, Theme.MARBLE, .66f)
        val size = 82f
        if (ui.iconButton(game.assets.ui("icon_settings"), Rectangle(916f, 1760f, size, size))) game.showTempleSettings()
    }
}

class TempleSettingsScreen(game: ZeusChainGame) : TempleScreen(game) {
    override fun render(delta: Float) {
        ui.begin(); ui.background(Backdrop.FIGMA_TEMPLE_NIGHT, shade = .30f)
        ui.header("SETTINGS", onBack = { game.showTempleMenu() }, onHome = { game.showTempleMenu() })
        val s = game.progress.data.settings
        val rows = listOf(
            Triple("MUSIC", s.music, "icon_music"),
            Triple("SOUND EFFECTS", s.sounds, "icon_sound"),
            Triple("HAPTICS", s.haptics, "icon_haptic"),
            Triple("REDUCED FLASHES", s.reducedFlashes, "icon_shield"),
            Triple("HIGH-CONTRAST TEMPLE", s.highContrastPath, "icon_bolt"),
        )
        rows.forEachIndexed { index, row ->
            val y = 1375f - index * 220f
            ui.cleanPanel(Rectangle(68f, y, 944f, 168f), Color.valueOf("071D46F0"))
            ui.icon(row.third, Rectangle(94f, y + 23f, 110f, 110f), if (index != 2 || game.platform.hapticsSupported) 1f else .48f)
            ui.fitText(row.first, Rectangle(230f, y + 62f, 450f, 54f), game.assets.heading, Theme.MARBLE, .72f)
            if (ui.toggle(Rectangle(730f, y + 22f, 220f, 112f), row.second, index != 2 || game.platform.hapticsSupported)) {
                game.progress.mutate { when (index) {
                    0 -> it.settings.music = !it.settings.music
                    1 -> it.settings.sounds = !it.settings.sounds
                    2 -> it.settings.haptics = !it.settings.haptics
                    3 -> it.settings.reducedFlashes = !it.settings.reducedFlashes
                    else -> it.settings.highContrastPath = !it.settings.highContrastPath
                } }
                game.audio.reconcile()
            }
        }
    }
}

class TempleGameplayScreen(game: ZeusChainGame) : TempleScreen(game) {
    private val state = TempleStackState()
    private var elapsed = 0f
    private var movingCenter = 540f
    private var lastResult: PlacementGrade? = null
    private var resultTimer = 0f
    private val targetHeight = 12

    override fun render(delta: Float) {
        elapsed += delta.coerceAtMost(.1f); resultTimer = (resultTimer - delta).coerceAtLeast(0f)
        ui.begin(); ui.background(Backdrop.FIGMA_DAY, shade = .18f)
        ui.header("TEMPLE STACK", onBack = { game.showTempleMenu() }, onHome = { game.showTempleMenu() })
        ui.cleanPanel(Rectangle(78f, 1660f, 924f, 120f), Color.valueOf("071D46F0"))
        ui.fitText("HEIGHT ${state.height} / $targetHeight", Rectangle(98f, 1700f, 285f, 42f), game.assets.small, Theme.GOLD, .62f)
        ui.fitText("SCORE ${state.score}", Rectangle(390f, 1700f, 300f, 42f), game.assets.small, Theme.MARBLE, .62f)
        ui.fitText("COINS ${state.coins}", Rectangle(700f, 1700f, 275f, 42f), game.assets.small, Theme.GOLD, .62f)

        movingCenter = 540f + sin(elapsed * 1.7f) * 250f
        drawArena()
        if (!state.isCollapsed && state.height < targetHeight) drawBlock(movingCenter, state.top.width, 620f, Theme.CYAN, true)
        if (resultTimer > 0f) ui.fitText(lastResult?.name ?: "", Rectangle(200f, 430f, 680f, 54f), game.assets.heading, when (lastResult) { PlacementGrade.PERFECT -> Theme.GOLD; PlacementGrade.COLLAPSE -> Theme.DANGER; else -> Theme.CYAN }, .72f)
        else if (!state.isCollapsed) ui.fitText("TAP TO DROP", Rectangle(230f, 430f, 620f, 44f), game.assets.small, Theme.MARBLE, .68f)

        game.assets.batch.begin(); game.assets.batch.draw(game.assets.zeusStormSprite, 380f, 155f, 320f, 300f); game.assets.batch.end()
        if (!state.isCollapsed && Gdx.input.justTouched() && ui.pointer().y in 500f..1600f) {
            val result = state.drop(movingCenter, state.top.width, golden = state.height % 5 == 4, keystone = state.height > 0 && state.height % 7 == 0)
            lastResult = result.grade; resultTimer = 0.65f; game.haptic(if (result.collapsed) Haptic.ERROR else if (result.grade == PlacementGrade.PERFECT) Haptic.REWARD else Haptic.IMPACT)
            if (result.collapsed || state.height >= targetHeight) game.showTempleResult(!result.collapsed, state.height, state.score, state.coins)
        }
    }

    private fun drawArena() {
        val s = game.assets.shapes
        s.begin(ShapeRenderer.ShapeType.Filled)
        s.color = Theme.GOLD_DARK; s.rect(170f, 330f, 740f, 24f)
        s.color = Theme.GOLD; s.rect(180f, 354f, 720f, 12f)
        s.end()
        state.blocks.drop(1).takeLast(14).forEachIndexed { i, block -> drawBlock(block.center, block.width, 370f + i * 92f, Theme.MARBLE, false, block.tilt) }
    }

    private fun drawBlock(center: Float, width: Float, y: Float, color: Color, active: Boolean, tilt: Float = 0f) {
        val s = game.assets.shapes
        s.begin(ShapeRenderer.ShapeType.Filled)
        s.color = Color.valueOf("8C541D"); s.rect(center - width / 2f + 8f, y - 8f, width, 78f)
        s.color = if (active) Color.valueOf("C8F7FF") else color; s.rect(center - width / 2f, y, width, 70f)
        s.color = if (active) Theme.CYAN else Theme.GOLD_LIGHT; s.rect(center - width / 2f + 8f, y + 52f, width - 16f, 7f)
        s.end()
    }
}

class TempleResultScreen(game: ZeusChainGame, private val won: Boolean, private val height: Int, private val score: Int, private val coins: Int) : TempleScreen(game) {
    override fun render(delta: Float) {
        ui.begin(); ui.background(Backdrop.FIGMA_TEMPLE_NIGHT, shade = .28f)
        ui.header(if (won) "TEMPLE COMPLETE" else "TEMPLE FELL", onBack = { game.showTempleMenu() }, onHome = { game.showTempleMenu() })
        ui.cleanPanel(Rectangle(90f, 620f, 900f, 900f), Color.valueOf("06142EF5"), if (won) Theme.GOLD else Theme.DANGER)
        ui.fitText(if (won) "OLYMPUS RISES" else "THE EARTHQUAKE WINS", Rectangle(140f, 1260f, 800f, 80f), game.assets.title, if (won) Theme.GOLD else Theme.DANGER, .72f)
        ui.fitText("HEIGHT  $height", Rectangle(180f, 1070f, 720f, 60f), game.assets.heading, Theme.CYAN, .82f)
        ui.fitText("SCORE  $score", Rectangle(180f, 950f, 720f, 60f), game.assets.heading, Theme.MARBLE, .82f)
        ui.fitText("+$coins COINS", Rectangle(180f, 830f, 720f, 60f), game.assets.heading, Theme.GOLD, .82f)
        if (ui.button(if (won) "NEXT LEVEL" else "TRY AGAIN", Rectangle(190f, 670f, 700f, 120f), primary = true)) game.showTempleGameplay()
        if (ui.button("MAIN MENU", Rectangle(240f, 530f, 600f, 96f))) game.showTempleMenu()
    }
}
