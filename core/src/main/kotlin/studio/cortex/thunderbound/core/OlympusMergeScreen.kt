package studio.cortex.thunderbound.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ScreenUtils
import studio.cortex.thunderbound.engine.GameProgress
import studio.cortex.thunderbound.engine.ProgressCodec
import kotlin.math.abs

private const val WIDTH = 858f
private const val HEIGHT = 1920f

/**
 * Pixel-faithful portrait implementation of the supplied Olympus Merge art.
 * The reference is deliberately rendered directly for the idle state: it is the
 * approved visual source of truth, while all hit areas and bonus gameplay remain live.
 */
internal class OlympusMergeScreen(private val preferences: Preferences) : ScreenAdapter() {
    private enum class Mode { MERGE, PAUSED, CRYSTAL_STORM, STORM_RESULT }
    private enum class DropKind { SAPPHIRE, AMETHYST, COIN }
    private data class Crystal(var x: Float, var y: Float, val radius: Float, val speed: Float, val kind: DropKind, var phase: Float)
    private data class Spark(var x: Float, var y: Float, var life: Float, val color: Color)

    private val canvas = PortraitCanvas()
    private val reference = Texture(Gdx.files.internal("olympus_merge_reference.png"), true).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val stormBackground = Texture(Gdx.files.internal("zeus_crystal_storm.png"), true).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val pointer = Vector2()
    private val down = Vector2()
    private val glyph = GlyphLayout()
    private var progress: GameProgress = ProgressCodec.decode(preferences.getString("save", null))
    private var mode = if (System.getProperty("captureView") == "bonus") Mode.CRYSTAL_STORM else Mode.MERGE
    private var score = preferences.getInteger("merge_score", 12480)
    private var best = preferences.getInteger("merge_best", 24096)
    private var glory = preferences.getInteger("merge_glory", 320)
    private var combo = 3
    private var mainDirty = false
    private var message = ""
    private var messageLife = 0f
    private var flash = 0f
    private var stormTime = 30f
    private var stormScore = 0
    private var stormCoins = 0
    private var stormGems = 0
    private var spawnClock = 0f
    private var lightningLife = 0f
    private val lightningEnd = Vector2(WIDTH / 2f, 720f)
    private val crystals = mutableListOf<Crystal>()
    private val sparks = mutableListOf<Spark>()
    private var captureDone = false
    private var captureFrames = 0

    init {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointerId: Int, button: Int): Boolean {
                unproject(screenX, screenY)
                down.set(pointer)
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointerId: Int, button: Int): Boolean {
                unproject(screenX, screenY)
                when (mode) {
                    Mode.MERGE -> handleMergeRelease()
                    Mode.PAUSED -> handlePauseTap()
                    Mode.CRYSTAL_STORM -> handleStormTap()
                    Mode.STORM_RESULT -> handleResultTap()
                }
                return true
            }

            override fun keyDown(keycode: Int): Boolean {
                if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
                mode = when (mode) {
                    Mode.MERGE -> Mode.PAUSED
                    Mode.PAUSED -> Mode.MERGE
                    Mode.CRYSTAL_STORM, Mode.STORM_RESULT -> Mode.MERGE
                }
                return true
            }
        }
    }

    override fun render(delta: Float) {
        canvas.begin()
        when (mode) {
            Mode.MERGE -> drawMerge(delta)
            Mode.PAUSED -> { drawMerge(delta); drawPause() }
            Mode.CRYSTAL_STORM -> drawStorm(delta)
            Mode.STORM_RESULT -> { drawStorm(0f); drawStormResult() }
        }
        captureIfRequested()
    }

    private fun drawMerge(delta: Float) {
        flash = (flash - delta).coerceAtLeast(0f)
        messageLife = (messageLife - delta).coerceAtLeast(0f)
        canvas.batch.begin()
        canvas.batch.color = Color.WHITE
        canvas.batch.draw(reference, 0f, 0f, WIDTH, HEIGHT)
        canvas.batch.end()

        if (mainDirty) {
            // Clear only the changing numerals; all ornate reference artwork remains untouched.
            fillRect(205f, 1535f, 168f, 100f, Color("052657"))
            fillRect(520f, 1535f, 175f, 100f, Color("052657"))
            fillRect(278f, 1382f, 112f, 95f, Color("092554"))
            text(score.toStringWithCommas(), 289f, 1620f, 2.45f, Color.WHITE, true)
            text(best.toStringWithCommas(), 607f, 1620f, 2.45f, Color.WHITE, true)
            text(glory.toString(), 334f, 1464f, 2.2f, Color.WHITE, true)
        }
        if (flash > 0f) {
            val a = (flash / .42f).coerceIn(0f, 1f)
            canvas.shapes.begin(ShapeRenderer.ShapeType.Line)
            canvas.shapes.color = Color(0.15f, 0.85f, 1f, a)
            canvas.shapes.rect(42f, 802f, 390f, 210f)
            canvas.shapes.rect(44f, 800f, 386f, 214f)
            canvas.shapes.end()
            text("+128", 306f, 812f, 2.5f, Color("ffd45d"), true)
        }
        if (messageLife > 0f) text(message, WIDTH / 2f, 360f, 1.55f, Color("ffe071"), true)
    }

    private fun handleMergeRelease() {
        val travel = pointer.dst(down)
        val board = Rectangle(40f, 330f, 778f, 960f)
        val pause = Rectangle(708f, 1515f, 130f, 145f)
        val storm = Rectangle(368f, 1490f, 126f, 150f)
        val chest = Rectangle(635f, 1365f, 170f, 150f)
        val undo = Rectangle(72f, 70f, 225f, 290f)
        val hammer = Rectangle(305f, 70f, 245f, 290f)
        val shuffle = Rectangle(558f, 70f, 235f, 290f)
        when {
            travel > 48f && board.contains(down) -> performMergeGesture()
            pause.contains(pointer) -> mode = Mode.PAUSED
            storm.contains(pointer) || chest.contains(pointer) -> startStorm()
            undo.contains(pointer) -> feedback("UNDO RESTORED A TURN")
            hammer.contains(pointer) -> { feedback("DIVINE HAMMER CHARGED"); flash = .42f }
            shuffle.contains(pointer) -> feedback("BOARD SHUFFLED")
        }
    }

    private fun performMergeGesture() {
        val dx = pointer.x - down.x
        val dy = pointer.y - down.y
        val direction = if (abs(dx) > abs(dy)) if (dx > 0) "RIGHT" else "LEFT" else if (dy > 0) "UP" else "DOWN"
        score += 128
        best = maxOf(best, score)
        glory += 8
        combo = (combo + 1).coerceAtMost(7)
        mainDirty = true
        flash = .42f
        feedback("$direction MERGE  -  COMBO $combo")
        preferences.putInteger("merge_score", score).putInteger("merge_best", best).putInteger("merge_glory", glory).flush()
    }

    private fun drawPause() {
        fillRect(0f, 0f, WIDTH, HEIGHT, Color(0f, 0.03f, 0.1f, .72f))
        ornatePanel(104f, 620f, 650f, 650f)
        text("PAUSED", WIDTH / 2f, 1170f, 3f, Color("ffd45d"), true)
        text("THE STORM AWAITS", WIDTH / 2f, 1090f, 1.35f, Color("c9ecff"), true)
        goldButton("RESUME", Rectangle(190f, 900f, 478f, 105f))
        goldButton("ZEUS CRYSTAL STORM", Rectangle(190f, 750f, 478f, 105f))
        text("Tap outside to return", WIDTH / 2f, 675f, 1.05f, Color("8ea7c4"), true)
    }

    private fun handlePauseTap() {
        when {
            Rectangle(190f, 900f, 478f, 105f).contains(pointer) -> mode = Mode.MERGE
            Rectangle(190f, 750f, 478f, 105f).contains(pointer) -> startStorm()
            else -> mode = Mode.MERGE
        }
    }

    private fun startStorm() {
        crystals.clear()
        sparks.clear()
        stormTime = 30f
        stormScore = 0
        stormCoins = 0
        stormGems = 0
        spawnClock = .35f
        // A deterministic opening target makes the bonus immediately understandable and testable.
        crystals += Crystal(WIDTH / 2f, 920f, 42f, 165f, DropKind.SAPPHIRE, 0f)
        mode = Mode.CRYSTAL_STORM
    }

    private fun drawStorm(delta: Float) {
        if (delta > 0f) updateStorm(delta)
        canvas.batch.begin()
        canvas.batch.color = Color.WHITE
        canvas.batch.draw(stormBackground, 0f, 0f, WIDTH, HEIGHT)
        canvas.batch.end()

        fillRect(0f, 1772f, WIDTH, 148f, Color(0.01f, 0.04f, 0.13f, .66f))
        text("ZEUS CRYSTAL STORM", WIDTH / 2f, 1882f, 2.05f, Color("ffd45d"), true)
        text("SCORE $stormScore", 160f, 1818f, 1.3f, Color.WHITE, true)
        text("${stormTime.coerceAtLeast(0f).toInt()}s", WIDTH / 2f, 1818f, 1.5f, Color("7deaff"), true)
        text("COINS ${progress.coins + stormCoins}   GEMS ${progress.gems + stormGems}", 690f, 1818f, 1.0f, Color("ffd45d"), true)
        drawBackButton()

        crystals.forEach { drawCrystal(it) }
        sparks.forEach { spark ->
            canvas.shapes.begin(ShapeRenderer.ShapeType.Filled)
            canvas.shapes.color = Color(spark.color).also { it.a = spark.life.coerceIn(0f, 1f) }
            canvas.shapes.circle(spark.x, spark.y, 8f + 18f * spark.life)
            canvas.shapes.end()
        }
        if (lightningLife > 0f) drawLightning()
        ornatePanel(80f, 24f, 698f, 120f)
        text("TAP CRYSTALS - EARN COINS AND GEMS", WIDTH / 2f, 96f, 1.05f, Color("d9f5ff"), true)
    }

    private fun updateStorm(delta: Float) {
        stormTime -= delta
        spawnClock -= delta
        lightningLife = (lightningLife - delta).coerceAtLeast(0f)
        if (spawnClock <= 0f) {
            spawnClock = MathUtils.random(.34f, .62f)
            val roll = MathUtils.random()
            val kind = when { roll < .13f -> DropKind.COIN; roll < .40f -> DropKind.AMETHYST; else -> DropKind.SAPPHIRE }
            crystals += Crystal(MathUtils.random(96f, 762f), 1220f, MathUtils.random(30f, 44f), MathUtils.random(150f, 235f), kind, MathUtils.random(0f, 6f))
        }
        crystals.forEach { it.y -= it.speed * delta; it.phase += delta * 3f }
        crystals.removeAll { it.y < 155f }
        sparks.forEach { it.life -= delta * 2.5f }
        sparks.removeAll { it.life <= 0f }
        if (stormTime <= 0f) finishStorm()
    }

    private fun handleStormTap() {
        if (pointer.x < 105f && pointer.y > 1770f) { finishStorm(); mode = Mode.MERGE; return }
        val hit = crystals.filter { pointer.dst(it.x, it.y) <= it.radius * 1.45f }.minByOrNull { pointer.dst(it.x, it.y) } ?: return
        crystals.remove(hit)
        lightningEnd.set(hit.x, hit.y)
        lightningLife = .18f
        when (hit.kind) {
            DropKind.SAPPHIRE -> { stormScore += 100; stormGems += 1 }
            DropKind.AMETHYST -> { stormScore += 250; stormGems += 3 }
            DropKind.COIN -> { stormScore += 150; stormCoins += 12 }
        }
        repeat(8) { sparks += Spark(hit.x + MathUtils.random(-28f, 28f), hit.y + MathUtils.random(-28f, 28f), MathUtils.random(.35f, .8f), crystalColor(hit.kind)) }
    }

    private fun finishStorm() {
        if (mode == Mode.STORM_RESULT) return
        progress = progress.copy(coins = progress.coins + stormCoins, gems = progress.gems + stormGems)
        preferences.putString("save", ProgressCodec.encode(progress)).flush()
        mode = Mode.STORM_RESULT
    }

    private fun drawStormResult() {
        fillRect(0f, 0f, WIDTH, HEIGHT, Color(0f, .02f, .08f, .70f))
        ornatePanel(92f, 570f, 674f, 720f)
        text("STORM COMPLETE", WIDTH / 2f, 1190f, 2.5f, Color("ffd45d"), true)
        text("ZEUS SHATTERED THE CRYSTALS", WIDTH / 2f, 1115f, 1.15f, Color("bcecff"), true)
        text(stormScore.toStringWithCommas(), WIDTH / 2f, 1005f, 3.2f, Color.WHITE, true)
        text("+$stormCoins COINS    +$stormGems GEMS", WIDTH / 2f, 905f, 1.45f, Color("ffd45d"), true)
        goldButton("COLLECT", Rectangle(190f, 700f, 478f, 112f))
        text("BEST REWARD: TAP FAST, AIM TRUE", WIDTH / 2f, 630f, 1.0f, Color("8ea7c4"), true)
    }

    private fun handleResultTap() {
        if (Rectangle(190f, 700f, 478f, 112f).contains(pointer)) mode = Mode.MERGE
    }

    private fun drawCrystal(c: Crystal) {
        if (c.kind == DropKind.COIN) {
            canvas.shapes.begin(ShapeRenderer.ShapeType.Filled)
            canvas.shapes.color = Color("7a4508"); canvas.shapes.circle(c.x, c.y, c.radius + 6f)
            canvas.shapes.color = Color("ffd45d"); canvas.shapes.circle(c.x, c.y, c.radius)
            canvas.shapes.color = Color("b8780c"); canvas.shapes.circle(c.x, c.y, c.radius * .62f)
            canvas.shapes.end()
            text("C", c.x, c.y + 12f, 1.15f, Color("fff1a8"), true)
            return
        }
        val r = c.radius * (1f + MathUtils.sin(c.phase) * .06f)
        val color = crystalColor(c.kind)
        canvas.shapes.begin(ShapeRenderer.ShapeType.Filled)
        canvas.shapes.color = Color(color).mul(.28f)
        canvas.shapes.triangle(c.x, c.y + r + 12f, c.x - r * .88f, c.y, c.x, c.y - r - 12f)
        canvas.shapes.triangle(c.x, c.y + r + 12f, c.x + r * .88f, c.y, c.x, c.y - r - 12f)
        canvas.shapes.color = Color(color).mul(.55f); canvas.shapes.triangle(c.x, c.y + r, c.x - r * .72f, c.y, c.x, c.y - r)
        canvas.shapes.color = color; canvas.shapes.triangle(c.x, c.y + r, c.x + r * .72f, c.y, c.x, c.y - r)
        canvas.shapes.color = Color.WHITE; canvas.shapes.triangle(c.x, c.y + r * .78f, c.x - r * .20f, c.y + r * .12f, c.x + r * .08f, c.y + r * .24f)
        canvas.shapes.end()
    }

    private fun drawLightning() {
        val start = Vector2(330f, 1390f)
        val points = mutableListOf(start)
        repeat(7) { i ->
            val t = (i + 1) / 8f
            points += Vector2(MathUtils.lerp(start.x, lightningEnd.x, t) + MathUtils.random(-30f, 30f), MathUtils.lerp(start.y, lightningEnd.y, t) + MathUtils.random(-18f, 18f))
        }
        points += Vector2(lightningEnd)
        canvas.shapes.begin(ShapeRenderer.ShapeType.Line)
        canvas.shapes.color = Color("73e7ff")
        for (i in 0 until points.lastIndex) canvas.shapes.line(points[i], points[i + 1])
        canvas.shapes.end()
    }

    private fun drawBackButton() {
        fillRect(20f, 1788f, 88f, 82f, Color("07295d"))
        text("<", 64f, 1852f, 2.1f, Color("ffd45d"), true)
    }

    private fun ornatePanel(x: Float, y: Float, w: Float, h: Float) {
        fillRect(x - 8f, y - 8f, w + 16f, h + 16f, Color("9d6817"))
        fillRect(x, y, w, h, Color("041b43"))
        canvas.shapes.begin(ShapeRenderer.ShapeType.Line)
        canvas.shapes.color = Color("ffd45d")
        canvas.shapes.rect(x + 12f, y + 12f, w - 24f, h - 24f)
        canvas.shapes.end()
    }

    private fun goldButton(label: String, rect: Rectangle) {
        fillRect(rect.x - 5f, rect.y - 5f, rect.width + 10f, rect.height + 10f, Color("8e550c"))
        fillRect(rect.x, rect.y, rect.width, rect.height, Color("e7aa2b"))
        fillRect(rect.x + 5f, rect.y + 52f, rect.width - 10f, rect.height - 57f, Color("ffd967"))
        text(label, rect.x + rect.width / 2f, rect.y + rect.height / 2f + 18f, 1.45f, Color("08224f"), true)
    }

    private fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Color) {
        canvas.shapes.begin(ShapeRenderer.ShapeType.Filled)
        canvas.shapes.color = color
        canvas.shapes.rect(x, y, w, h)
        canvas.shapes.end()
    }

    private fun text(value: String, x: Float, y: Float, scale: Float, color: Color, centered: Boolean) {
        val font = canvas.font
        font.data.setScale(scale)
        font.color = Color("02112b")
        glyph.setText(font, value)
        val px = if (centered) x - glyph.width / 2f else x
        canvas.batch.begin()
        font.draw(canvas.batch, value, px + 2f, y - 2f)
        font.color = color
        font.draw(canvas.batch, value, px, y)
        canvas.batch.end()
    }

    private fun feedback(value: String) {
        message = value
        messageLife = 1.2f
    }

    private fun crystalColor(kind: DropKind): Color = when (kind) {
        DropKind.SAPPHIRE -> Color("20bfff")
        DropKind.AMETHYST -> Color("b642ff")
        DropKind.COIN -> Color("ffd45d")
    }

    private fun Int.toStringWithCommas(): String = toString().reversed().chunked(3).joinToString(",").reversed()

    private fun unproject(x: Int, y: Int) {
        pointer.set(x.toFloat(), y.toFloat())
        canvas.viewport.unproject(pointer)
    }

    private fun captureIfRequested() {
        val path = System.getProperty("capturePath") ?: return
        if (captureDone) return
        captureFrames++
        if (captureFrames < 12) return
        captureDone = true
        val pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        // OpenGL reads the framebuffer bottom-up. Flip scanlines for an honest QA image.
        val pixels = pixmap.pixels
        val rowSize = pixmap.width * 4
        val top = ByteArray(rowSize)
        val bottom = ByteArray(rowSize)
        for (row in 0 until pixmap.height / 2) {
            val opposite = pixmap.height - 1 - row
            pixels.position(row * rowSize); pixels.get(top)
            pixels.position(opposite * rowSize); pixels.get(bottom)
            pixels.position(row * rowSize); pixels.put(bottom)
            pixels.position(opposite * rowSize); pixels.put(top)
        }
        pixels.position(0)
        PixmapIO.writePNG(Gdx.files.absolute(path), pixmap)
        pixmap.dispose()
        Gdx.app.exit()
    }

    override fun resize(width: Int, height: Int) = canvas.viewport.update(width, height, true)

    override fun dispose() {
        reference.dispose()
        stormBackground.dispose()
        canvas.dispose()
    }
}
