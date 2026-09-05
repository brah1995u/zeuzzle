package studio.cortex.templestack.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.StretchViewport
import studio.cortex.templestack.engine.Modifier
import studio.cortex.templestack.engine.OlympusMiniGames
import studio.cortex.templestack.engine.PlacementGrade
import studio.cortex.templestack.engine.TempleCampaign
import studio.cortex.templestack.engine.TempleLevel
import studio.cortex.templestack.engine.TempleProgress
import studio.cortex.templestack.engine.TempleProgressCodec
import studio.cortex.templestack.engine.TempleSnapshot
import studio.cortex.templestack.engine.TempleStackSession
import kotlin.math.sin

private enum class TempleView { SPLASH, MENU, MAP, PRELEVEL, GAME, PAUSE, RESULT, FORGE, ACHIEVEMENTS, DAILY, LEADERBOARD, SETTINGS, ABOUT, MINIGAMES, SLOT, CHEST }

private data class Hit(val bounds: Rectangle, val action: () -> Unit)
private data class LeaderEntry(val rank: Int, val name: String, val value: Int, val isPlayer: Boolean)

/** Portrait presentation layer. All progression and drop outcomes remain in engine. */
class TempleStackScreen(private val game: TempleStackGame, private val preferences: Preferences) : ScreenAdapter() {
    private val camera = OrthographicCamera()
    private val viewport = StretchViewport(1080f, 1920f, camera)
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val font = BitmapFont().apply { data.setScale(1.45f) }
    private val heading = BitmapFont().apply { data.setScale(2.05f) }
    private val title = BitmapFont().apply { data.setScale(3.05f) }
    private val menuArt = Texture(Gdx.files.internal("olympus_menu_backdrop_v2.png"))
    private val gameplayArt = Texture(Gdx.files.internal("olympus_gameplay_arena_v2.png"))
    private val marbleBlock = Texture(Gdx.files.internal("temple_block_marble_v2.png"))
    private val templeFoundation = Texture(Gdx.files.internal("temple_foundation_marble_v2.png"))
    private val olympusAtlas = Texture(Gdx.files.internal("zeus_olympus_ui_atlas_v3.png"))
    private val oracleArt = Texture(Gdx.files.internal("zeus_oracle_minigames_v1.png"))
    private val pointer = Vector2()
    private val hits = mutableListOf<Hit>()
    private var pressed: Hit? = null
    private var view = when (System.getProperty("captureView")?.uppercase()) {
        "MENU" -> TempleView.MENU
        "MAP" -> TempleView.MAP
        "PRELEVEL" -> TempleView.PRELEVEL
        "GAME" -> TempleView.GAME
        "PAUSE" -> TempleView.PAUSE
        "RESULT" -> TempleView.RESULT
        "FORGE" -> TempleView.FORGE
        "ACHIEVEMENTS" -> TempleView.ACHIEVEMENTS
        "DAILY" -> TempleView.DAILY
        "LEADERBOARD" -> TempleView.LEADERBOARD
        "SETTINGS" -> TempleView.SETTINGS
        "MINIGAMES" -> TempleView.MINIGAMES
        "SLOT" -> TempleView.SLOT
        "CHEST" -> TempleView.CHEST
        else -> TempleView.SPLASH
    }
    private var previous = TempleView.MENU
    private var elapsed = 0f
    private var selectedLevel = 1
    private var endless = false
    private var session: TempleStackSession? = null
    private var result: TempleSnapshot? = null
    private var resultWon = false
    private var resultCommitted = false
    private var feedback = ""
    private var feedbackTime = 0f
    private var leaderboardTab = 0
    private var forgeTab = 0
    private var achievementTab = 0
    private var menuExpanded = false
    private var slotResult: OlympusMiniGames.SlotResult? = null
    private var chestOpened: Int? = null
    private var windCalmSeconds = 0f
    private var alignmentDrops = 0
    private var captureFrames = 0
    private var captureDone = false
    private var progress = TempleProgressCodec.decode(preferences.getString("save", null))

    init {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(x: Int, y: Int, pointerId: Int, button: Int): Boolean {
                if (pointerId != 0) return false
                map(x, y)
                pressed = hits.asReversed().firstOrNull { it.bounds.contains(pointer) }
                return pressed != null || (view == TempleView.GAME && arena().contains(pointer))
            }

            override fun touchUp(x: Int, y: Int, pointerId: Int, button: Int): Boolean {
                if (pointerId != 0) return false
                map(x, y)
                val target = pressed
                pressed = null
                if (target != null && target.bounds.contains(pointer)) { target.action(); return true }
                if (view == TempleView.GAME && arena().contains(pointer)) dropBlock()
                return true
            }

            override fun keyDown(keycode: Int): Boolean {
                if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
                when (view) {
                    TempleView.SPLASH -> show(TempleView.MENU)
                    TempleView.MENU -> Gdx.app.exit()
                    TempleView.GAME -> show(TempleView.PAUSE)
                    TempleView.PAUSE -> show(TempleView.GAME)
                    TempleView.RESULT -> show(TempleView.MAP)
                    else -> show(previous)
                }
                return true
            }
        }
        if (view == TempleView.GAME) {
            endless = true
            startRun()
        }
        if (view == TempleView.RESULT) {
            resultWon = true
            result = TempleSnapshot(12, 1840, 165, 5, 4, 8f, false, emptyList())
        }
    }

    override fun render(delta: Float) {
        elapsed += delta.coerceIn(0f, .05f)
        feedbackTime = (feedbackTime - delta).coerceAtLeast(0f)
        windCalmSeconds = (windCalmSeconds - delta).coerceAtLeast(0f)
        Gdx.gl.glClearColor(.02f, .06f, .15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        viewport.apply()
        batch.projectionMatrix = camera.combined
        shapes.projectionMatrix = camera.combined
        hits.clear()
        when (view) {
            TempleView.SPLASH -> drawSplash()
            TempleView.MENU -> drawMenu()
            TempleView.MAP -> drawMap()
            TempleView.PRELEVEL -> drawPreLevel()
            TempleView.GAME -> drawGame()
            TempleView.PAUSE -> drawPause()
            TempleView.RESULT -> drawResult()
            TempleView.FORGE -> drawForge()
            TempleView.ACHIEVEMENTS -> drawAchievements()
            TempleView.DAILY -> drawDaily()
            TempleView.LEADERBOARD -> drawLeaderboard()
            TempleView.SETTINGS -> drawSettings()
            TempleView.ABOUT -> drawAbout()
            TempleView.MINIGAMES -> drawMiniGames()
            TempleView.SLOT -> drawSlotMachine()
            TempleView.CHEST -> drawLuckyChest()
        }
        captureIfRequested()
    }

    private fun captureIfRequested() {
        val path = System.getProperty("capturePath") ?: return
        if (captureDone || ++captureFrames < 12) return
        captureDone = true
        val pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.width, Gdx.graphics.height)
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

    override fun resize(width: Int, height: Int) = viewport.update(width, height, true)
    override fun dispose() { batch.dispose(); shapes.dispose(); font.dispose(); heading.dispose(); title.dispose(); menuArt.dispose(); gameplayArt.dispose(); marbleBlock.dispose(); templeFoundation.dispose(); olympusAtlas.dispose(); oracleArt.dispose() }

    private fun map(x: Int, y: Int) { pointer.set(x.toFloat(), y.toFloat()); viewport.unproject(pointer) }
    private fun save() { preferences.putString("save", TempleProgressCodec.encode(progress)).flush() }
    private fun refreshLives() {
        val refreshed = progress.withRecoveredLives(System.currentTimeMillis())
        if (refreshed != progress) { progress = refreshed; save() }
    }
    private fun show(next: TempleView) { if (next != TempleView.SPLASH) previous = view; view = next; elapsed = 0f }
    private fun level() = TempleCampaign.level(selectedLevel)
    private fun arena() = Rectangle(55f, 260f, 970f, 1330f)

    private fun background(texture: Texture, shade: Float = 1f) {
        batch.begin(); batch.color = Color(1f, 1f, 1f, shade); batch.draw(texture, 0f, 0f, 1080f, 1920f); batch.color = Color.WHITE; batch.end()
    }

    private fun sky() {
        background(menuArt, .34f)
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color.valueOf("06142ECC"); shapes.rect(0f, 0f, 1080f, 1920f)
        shapes.color = Color.valueOf("55E7FF22"); repeat(20) { i -> shapes.circle(55f + i * 57f, 1780f - (i % 5) * 55f, 3f + i % 4) }
        shapes.end()
    }

    private fun panel(bounds: Rectangle, accent: Color = Color.valueOf("F6C85F"), fill: Color = Color.valueOf("06142EEB")) {
        batch.begin()
        batch.color = Color(1f, 1f, 1f, fill.a)
        batch.draw(olympusAtlas, bounds.x, bounds.y, bounds.width, bounds.height, 485, 450, 385, 350, false, false)
        batch.color = Color.WHITE
        batch.end()
    }

    private fun text(value: String, bounds: Rectangle, fontRef: BitmapFont = font, color: Color = Color.WHITE, align: Int = Align.center) {
        batch.begin(); fontRef.color = Color.valueOf("030A18"); fontRef.draw(batch, value, bounds.x + 2f, bounds.y + bounds.height * .58f - 2f, bounds.width, align, false)
        fontRef.color = color; fontRef.draw(batch, value, bounds.x, bounds.y + bounds.height * .58f, bounds.width, align, false); batch.end()
    }

    private fun button(label: String, bounds: Rectangle, primary: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
        val isDown = pressed?.bounds == bounds
        batch.begin()
        batch.color = when { !enabled -> Color.valueOf("5E718C"); primary -> Color.WHITE; else -> Color.valueOf("C4F4FF") }
        val sx = if (primary) 48 else 512
        val sy = if (primary) 110 else 175
        val sw = if (primary) 412 else 358
        val sh = if (primary) 290 else 200
        batch.draw(olympusAtlas, bounds.x, bounds.y + if (isDown) -7f else 0f, bounds.width, bounds.height, sx, sy, sw, sh, false, false)
        batch.color = Color.WHITE
        batch.end()
        text(label, bounds, heading, if (primary) Color.valueOf("06142E") else Color.WHITE)
        if (enabled) hits += Hit(bounds, action)
    }

    private fun olympusToggle(bounds: Rectangle, active: Boolean) {
        batch.begin()
        batch.color = if (active) Color.WHITE else Color.valueOf("6D7E91")
        batch.draw(olympusAtlas, bounds.x, bounds.y, bounds.width, bounds.height, 512, 175, 358, 200, false, false)
        val knobX = if (active) bounds.x + bounds.width - bounds.height + 5f else bounds.x - 5f
        batch.color = Color.WHITE
        batch.draw(olympusAtlas, knobX, bounds.y + 5f, bounds.height - 10f, bounds.height - 10f, 920, 120, 280, 280, false, false)
        batch.end()
    }

    private fun icon(label: String, x: Float, y: Float, action: () -> Unit) {
        val bounds = Rectangle(x, y, 86f, 86f)
        batch.begin(); batch.draw(olympusAtlas, bounds.x, bounds.y, bounds.width, bounds.height, 920, 120, 280, 280, false, false); batch.end()
        text(label, bounds, heading, Color.WHITE); hits += Hit(bounds, action)
    }

    private fun header(label: String, showHome: Boolean = true, back: () -> Unit) {
        button("BACK", Rectangle(42f, 1780f, 170f, 76f)) { back() }
        batch.begin(); batch.draw(olympusAtlas, 205f, 1774f, 670f, 104f, 512, 175, 358, 200, false, false); batch.end()
        text(label, Rectangle(220f, 1790f, 640f, 70f), heading, Color.WHITE)
        if (showHome) button("HOME", Rectangle(868f, 1780f, 170f, 76f)) { show(TempleView.MENU) }
    }

    private fun drawSplash() {
        background(menuArt)
        val pulse = 1f + sin(elapsed * 4f) * .04f
        text("ZEUS", Rectangle(90f, 1510f, 900f, 160f * pulse), title, Color.valueOf("F6C85F"))
        text("TEMPLE STACK", Rectangle(150f, 1415f, 780f, 80f), heading, Color.valueOf("55E7FF"))
        text("BUILD THE CROWN OF OLYMPUS", Rectangle(190f, 1325f, 700f, 56f), font, Color.valueOf("F2F4F6"))
        panel(Rectangle(250f, 160f, 580f, 22f), Color.valueOf("8C541D"), Color.valueOf("06142E"))
        shapes.begin(ShapeRenderer.ShapeType.Filled); shapes.color = Color.valueOf("55E7FF"); shapes.rect(258f, 168f, 564f * MathUtils.clamp(elapsed / 1.8f, 0f, 1f), 6f); shapes.end()
        if (elapsed > 2.05f || Gdx.input.justTouched()) show(TempleView.MENU)
    }

    private fun drawMenu() {
        refreshLives()
        background(menuArt)
        batch.begin(); batch.draw(olympusAtlas, 120f, 1455f, 840f, 225f, 48, 110, 412, 290, false, false); batch.end()
        text("ZEUS", Rectangle(165f, 1575f, 750f, 105f), title, Color.valueOf("F6C85F"))
        text("TEMPLE STACK", Rectangle(185f, 1488f, 710f, 70f), heading, Color.WHITE)
        panel(Rectangle(60f, 1762f, 344f, 74f)); text("LIVES ${progress.lives}/5  COINS ${progress.coins}", Rectangle(72f, 1768f, 320f, 62f), font, Color.valueOf("F6C85F"))
        button("MORE", Rectangle(870f, 1762f, 170f, 74f)) { menuExpanded = !menuExpanded }
        if (menuExpanded) {
            panel(Rectangle(648f, 1430f, 340f, 292f), Color.valueOf("55E7FF"))
            button("DAILY ALTAR", Rectangle(670f, 1630f, 296f, 64f)) { menuExpanded = false; show(TempleView.DAILY) }
            button("MINI GAMES", Rectangle(670f, 1550f, 296f, 64f)) { menuExpanded = false; show(TempleView.MINIGAMES) }
            button("SETTINGS", Rectangle(670f, 1470f, 296f, 64f)) { menuExpanded = false; show(TempleView.SETTINGS) }
        }
        button("PLAY", Rectangle(185f, 555f, 710f, 125f), true) { selectedLevel = progress.unlockedLevel; show(TempleView.PRELEVEL) }
        button("ENDLESS OLYMPUS", Rectangle(210f, 415f, 660f, 98f)) { endless = true; startRun() }
        button("TEMPLE PATH", Rectangle(210f, 310f, 660f, 78f)) { show(TempleView.MAP) }
        button("SHOP", Rectangle(75f, 192f, 286f, 76f)) { show(TempleView.FORGE) }
        button("ACHIEVEMENTS", Rectangle(397f, 192f, 286f, 76f)) { show(TempleView.ACHIEVEMENTS) }
        button("LEADERBOARD", Rectangle(719f, 192f, 286f, 76f)) { show(TempleView.LEADERBOARD) }
    }

    private fun drawMap() {
        sky(); header("TEMPLE PATH") { show(TempleView.MENU) }
        text("${level().worldName}  /  WORLD ${level().world}", Rectangle(160f, 1690f, 760f, 55f), font, Color.valueOf("55E7FF"))
        for (slot in 0 until 10) {
            val id = (level().world - 1) * 10 + slot + 1
            val x = if (slot % 2 == 0) 300f else 780f
            val y = 1510f - slot * 125f
            val open = id <= progress.unlockedLevel
            val current = id == selectedLevel
            val stars = progress.stars[id] ?: 0
            val marker = when {
                slot == 9 -> olympusAtlas
                !open -> olympusAtlas
                stars > 0 -> olympusAtlas
                else -> olympusAtlas
            }
            val size = if (slot == 9 || current) 142f else 118f
            batch.begin(); batch.color = if (current) Color.WHITE else Color(1f, 1f, 1f, .92f); batch.draw(marker, x - size / 2f, y - size / 2f, size, size, 900, if (open) 490 else 490, 280, 310, false, false); batch.color = Color.WHITE; batch.end()
            text(if (open) "$id" else "LOCK", Rectangle(x - 55f, y - 31f, 110f, 62f), font, if (open) Color.WHITE else Color.valueOf("AAB7C8"))
            if (open && stars > 0) text("$stars STARS", Rectangle(x - 75f, y - 100f, 150f, 38f), font, Color.valueOf("F6C85F"))
            if (open) hits += Hit(Rectangle(x - 70f, y - 70f, 140f, 140f)) { selectedLevel = id; show(TempleView.PRELEVEL) }
        }
        button("WORLD ${maxOf(1, level().world - 1)}", Rectangle(70f, 115f, 290f, 76f), enabled = level().world > 1) { selectedLevel -= 10 }
        button("WORLD ${minOf(6, level().world + 1)}", Rectangle(720f, 115f, 290f, 76f), enabled = level().world < 6 && progress.unlockedLevel >= level().world * 10 + 1) { selectedLevel += 10 }
    }

    private fun drawPreLevel() {
        sky(); val level = level(); header("LEVEL ${level.id}") { show(TempleView.MAP) }
        panel(Rectangle(120f, 765f, 840f, 640f)); text(level.worldName, Rectangle(180f, 1265f, 720f, 48f), font, Color.valueOf("55E7FF"))
        text(level.name, Rectangle(150f, 1165f, 780f, 74f), heading, Color.valueOf("F6C85F"))
        text("TARGET: ${level.targetHeight} MARBLE BLOCKS", Rectangle(170f, 1070f, 740f, 52f), font, Color.WHITE)
        text("MODIFIER: ${level.modifier.name.replace('_', ' ')}", Rectangle(170f, 980f, 740f, 48f), font, Color.valueOf("F2F4F6"))
        text("2 STARS: STABILITY ${level.twoStarStability.toInt()}", Rectangle(170f, 885f, 740f, 42f), font, Color.valueOf("F6C85F"))
        text("3 STARS: ${level.threeStarPerfects} PERFECT DROPS", Rectangle(170f, 825f, 740f, 42f), font, Color.valueOf("F6C85F"))
        button("BUILD THE TEMPLE", Rectangle(180f, 580f, 720f, 110f), true) { endless = false; startRun() }
    }

    private fun startRun() {
        refreshLives()
        if (!endless && progress.lives <= 0) { feedback = "NO LIVES — RETURN WHEN THE ALTAR RESTORES ONE"; feedbackTime = 2f; show(TempleView.PRELEVEL); return }
        session = TempleStackSession(if (level().modifier == Modifier.NARROW_FOUNDATION) 580f else 720f); result = null; resultCommitted = false; feedback = ""; windCalmSeconds = 0f; alignmentDrops = 0; show(TempleView.GAME)
    }

    private fun drawGame() {
        background(gameplayArt); val state = session ?: return; val snapshot = state.snapshot(); val current = level()
        header(if (endless) "ENDLESS OLYMPUS" else "LEVEL ${current.id}", showHome = false) { show(TempleView.PRELEVEL) }
        button("PAUSE", Rectangle(868f, 1780f, 170f, 76f)) { show(TempleView.PAUSE) }
        panel(Rectangle(78f, 1640f, 924f, 104f)); text(if (endless) "HEIGHT ${snapshot.height}" else "HEIGHT ${snapshot.height}/${current.targetHeight}", Rectangle(95f, 1660f, 260f, 60f), font, Color.valueOf("F6C85F"), Align.left)
        text("SCORE ${snapshot.score}", Rectangle(390f, 1660f, 300f, 60f), font, Color.WHITE)
        text("COINS ${snapshot.coins}", Rectangle(730f, 1660f, 240f, 60f), font, Color.valueOf("F6C85F"), Align.right)
        drawTemple(snapshot)
        if (!snapshot.collapsed) {
            val center = movingCenter(current, snapshot.height)
            val golden = current.modifier == Modifier.GOLDEN_BLOCKS && snapshot.height > 0 && (snapshot.height + 1) % 5 == 0
            templePiece(center, state.top.width, nextBlockY(snapshot.height), golden, true)
            if (alignmentDrops > 0) {
                shapes.begin(ShapeRenderer.ShapeType.Filled); shapes.color = Color.valueOf("55E7FFAA"); shapes.rect(state.top.center - 3f, 1435f, 6f, 185f); shapes.end()
            }
            text(if (golden) "GOLDEN BLOCK" else "TAP TO DROP", Rectangle(250f, 125f, 580f, 56f), font, if (golden) Color.valueOf("F6C85F") else Color.valueOf("F2F4F6"))
        }
        aidButton("ALIGN", "ALIGNMENT", 66f) { alignmentDrops = 2 }
        aidButton("WIND", "CALM WIND", 270f) { windCalmSeconds = 5f }
        aidButton("BLESS", "MASON'S BLESSING", 474f) { state.relieveStability(7f) }
        if (feedbackTime > 0f) text(feedback, Rectangle(240f, 345f, 600f, 70f), heading, feedbackColor())
        if (snapshot.collapsed) finish(false)
        else if (!endless && snapshot.height >= current.targetHeight) finish(true)
    }

    private fun movingCenter(level: TempleLevel, height: Int): Float {
        val wind = if (windCalmSeconds <= 0f && (level.modifier == Modifier.WIND || level.modifier == Modifier.TITAN_STORM || level.modifier == Modifier.MASTERY)) sin(elapsed * 1.45f) * 58f else 0f
        return 540f + sin(elapsed * level.speed * 2f) * 320f + wind
    }

    private fun aidButton(short: String, id: String, x: Float, effect: () -> Unit) {
        val count = progress.aidInventory[id] ?: 0
        val bounds = Rectangle(x, 42f, 186f, 72f)
        button(short, bounds, primary = count > 0, enabled = count > 0) {
            val next = progress.consumeAid(id) ?: return@button
            progress = next; save(); effect(); feedback = "$id ACTIVE"; feedbackTime = .8f
        }
        text("x$count", Rectangle(x + 130f, 88f, 46f, 20f), font, Color.valueOf("F6C85F"))
    }

    private fun drawTemple(snapshot: TempleSnapshot) {
        val baseWidth = snapshot.blocks.first().width + 120f
        val baseHeight = baseWidth * .34f
        batch.begin()
        batch.color = Color.WHITE
        batch.draw(templeFoundation, 540f - baseWidth / 2f, 200f, baseWidth, baseHeight)
        batch.end()
        snapshot.blocks.drop(1).takeLast(12).forEachIndexed { index, block -> templePiece(block.center, block.width, 310f + index * 52f, block.golden, false) }
    }

    private fun nextBlockY(height: Int) = (310f + height.coerceAtMost(12) * 52f).coerceAtMost(980f)

    private fun templePiece(center: Float, width: Float, y: Float, golden: Boolean, active: Boolean) {
        val height = (width * .23f).coerceIn(86f, 166f)
        batch.begin()
        batch.color = when {
            golden -> Color.valueOf("FFD45C")
            active -> Color.valueOf("D5FAFF")
            else -> Color.WHITE
        }
        batch.draw(marbleBlock, center - width / 2f, y, width, height)
        batch.color = Color.WHITE
        batch.end()
    }

    private fun dropBlock() {
        val state = session ?: return
        val snap = state.snapshot(); if (snap.collapsed) return
        val current = level(); val golden = current.modifier == Modifier.GOLDEN_BLOCKS && snap.height > 0 && (snap.height + 1) % 5 == 0
        val result = state.drop(movingCenter(current, snap.height), state.top.width, golden, !endless && (snap.height + 1) % 10 == 0)
        if (alignmentDrops > 0) alignmentDrops--
        feedback = if (result.collapsed) "THE TEMPLE FELL" else "${result.grade.name}  +${result.scoreAward}"
        feedbackTime = .7f
    }

    private fun feedbackColor() = when { feedback.startsWith("PERFECT") -> Color.valueOf("F6C85F"); feedback.startsWith("THE") -> Color.valueOf("EF4F5F"); else -> Color.valueOf("55E7FF") }

    private fun drawPause() {
        background(gameplayArt, .55f)
        shapes.begin(ShapeRenderer.ShapeType.Filled); shapes.color = Color.valueOf("06142E99"); shapes.rect(0f, 0f, 1080f, 1920f); shapes.end()
        panel(Rectangle(120f, 565f, 840f, 850f), Color.valueOf("F6C85F"))
        text("TEMPLE PAUSED", Rectangle(185f, 1230f, 710f, 100f), title, Color.valueOf("F6C85F"))
        text("YOUR TEMPLE IS SAFE", Rectangle(230f, 1145f, 620f, 48f), font, Color.valueOf("F2F4F6"))
        button("RESUME", Rectangle(210f, 950f, 660f, 108f), true) { show(TempleView.GAME) }
        button("RESTART", Rectangle(235f, 818f, 610f, 88f)) { startRun() }
        button("SETTINGS", Rectangle(235f, 710f, 610f, 78f)) { show(TempleView.SETTINGS) }
        button("HOME", Rectangle(335f, 605f, 410f, 70f)) { show(TempleView.MENU) }
    }

    private fun finish(won: Boolean) {
        if (resultCommitted) return
        resultCommitted = true; resultWon = won; result = session?.snapshot()
        val snapshot = result ?: return
        progress = if (endless) progress.copy(endlessBestHeight = maxOf(progress.endlessBestHeight, snapshot.height), endlessBestScore = maxOf(progress.endlessBestScore, snapshot.score), coins = progress.coins + snapshot.coins)
        else if (won) progress.complete(level(), snapshot) else (progress.spendLife(System.currentTimeMillis()) ?: progress).copy(coins = progress.coins + snapshot.coins)
        save(); show(TempleView.RESULT)
    }

    private fun drawResult() {
        background(menuArt, if (resultWon) .82f else .68f); val snap = result ?: return
        panel(Rectangle(90f, 400f, 900f, 1080f), if (resultWon) Color.valueOf("F6C85F") else Color.valueOf("EF4F5F"))
        text(if (resultWon) "TEMPLE COMPLETE" else "THE TEMPLE FELL", Rectangle(150f, 1260f, 780f, 105f), title, if (resultWon) Color.valueOf("F6C85F") else Color.valueOf("EF4F5F"))
        val stars = if (endless) 0 else if (resultWon) progress.stars[level().id] ?: 1 else 0
        batch.begin()
        repeat(3) { index ->
            batch.color = if (index < stars) Color.WHITE else Color(1f, 1f, 1f, .24f)
            batch.draw(olympusAtlas, 390f + index * 104f, 1130f, 86f, 86f, 45, 850, 310, 350, false, false)
        }
        batch.color = Color.WHITE
        batch.end()
        text("HEIGHT ${snap.height}", Rectangle(180f, 1015f, 720f, 62f), heading, Color.WHITE)
        text("SCORE ${snap.score}", Rectangle(180f, 900f, 720f, 62f), heading, Color.valueOf("55E7FF"))
        text("+${snap.coins} Drachma", Rectangle(180f, 790f, 720f, 62f), heading, Color.valueOf("F6C85F"))
        button(if (resultWon && !endless) "NEXT LEVEL" else "TRY AGAIN", Rectangle(185f, 590f, 710f, 110f), true) { if (resultWon && !endless && selectedLevel < 60) selectedLevel++; startRun() }
        button("TEMPLE PATH", Rectangle(210f, 460f, 660f, 88f)) { show(TempleView.MAP) }
        button("HOME", Rectangle(335f, 345f, 410f, 72f)) { show(TempleView.MENU) }
    }

    private fun drawForge() {
        sky(); header("FORGE OF OLYMPUS") { show(TempleView.MENU) }
        button("THEMES", Rectangle(82f, 1660f, 440f, 74f), primary = forgeTab == 0) { forgeTab = 0 }
        button("DIVINE AID", Rectangle(558f, 1660f, 440f, 74f), primary = forgeTab == 1) { forgeTab = 1 }
        if (forgeTab == 0) drawThemeForge() else drawAidForge()
    }

    private fun drawThemeForge() {
        text("COSMETICS NEVER CHANGE COLLISION OR SCORE", Rectangle(100f, 1585f, 880f, 48f), font, Color.valueOf("55E7FF"))
        val names = listOf("DAWN MARBLE", "AEGEAN AZURE", "FORGE BRONZE", "ORACLE SAPPHIRE")
        names.forEachIndexed { i, name ->
            val card = Rectangle(80f, 1320f - i * 255f, 920f, 210f); panel(card); text(name, Rectangle(220f, card.y + 120f, 520f, 58f), heading, Color.valueOf("F6C85F"))
            batch.begin()
            batch.color = when (i) { 1 -> Color.valueOf("8DEEFF"); 2 -> Color.valueOf("E6AB62"); 3 -> Color.valueOf("A996FF"); else -> Color.WHITE }
            batch.draw(marbleBlock, 110f, card.y + 62f, 92f, 92f)
            batch.color = Color.WHITE
            batch.end()
            val owned = name in progress.ownedThemes
            val equipped = name == progress.selectedTheme
            text(if (equipped) "EQUIPPED" else if (owned) "OWNED THEME" else "COSMETIC THEME", Rectangle(220f, card.y + 58f, 520f, 45f), font, Color.valueOf("F2F4F6"), Align.left)
            val price = 180 + i * 120
            button(if (equipped) "EQUIPPED" else if (owned) "EQUIP" else "$price", Rectangle(745f, card.y + 62f, 200f, 75f), !equipped, !equipped && (owned || progress.coins >= price)) {
                progress = if (owned) progress.copy(selectedTheme = name) else progress.copy(coins = progress.coins - price, ownedThemes = progress.ownedThemes + name, selectedTheme = name)
                save(); feedback = if (owned) "EQUIPPED" else "FORGED"; feedbackTime = .8f
            }
        }
    }

    private fun drawAidForge() {
        text("ONE-RUN HELP — NEVER REQUIRED TO WIN", Rectangle(100f, 1585f, 880f, 48f), font, Color.valueOf("55E7FF"))
        val aids = listOf(
            Triple("ALIGNMENT", "shows the center cue for 2 drops", 65),
            Triple("CALM WIND", "removes wind for 5 seconds", 85),
            Triple("MASON'S BLESSING", "reduces stability by 7", 105),
        )
        aids.forEachIndexed { index, (id, detail, price) ->
            val card = Rectangle(80f, 1320f - index * 260f, 920f, 214f); panel(card, Color.valueOf("55E7FF")); text(id, Rectangle(135f, card.y + 125f, 530f, 52f), heading, Color.valueOf("F6C85F"), Align.left)
            text(detail, Rectangle(135f, card.y + 72f, 550f, 38f), font, Color.WHITE, Align.left)
            text("OWNED ${progress.aidInventory[id] ?: 0}", Rectangle(135f, card.y + 28f, 400f, 32f), font, Color.valueOf("A7C9E7"), Align.left)
            button("$price", Rectangle(745f, card.y + 67f, 200f, 75f), true, progress.coins >= price) { progress = progress.buyAid(id, price) ?: progress; save() }
        }
    }

    private fun drawAchievements() {
        sky(); header("ACHIEVEMENTS") { show(TempleView.MENU) }
        button("BUILDER", Rectangle(82f, 1660f, 440f, 74f), primary = achievementTab == 0) { achievementTab = 0 }
        button("OLYMPUS", Rectangle(558f, 1660f, 440f, 74f), primary = achievementTab == 1) { achievementTab = 1 }
        val cleared = progress.stars.count { it.value > 0 }; val stars = progress.stars.values.sum()
        val rows = if (achievementTab == 0) listOf(
            AchievementRow("FIRST FOUNDATION", cleared, 1, 50), AchievementRow("TEMPLE ARCHITECT", cleared, 10, 90),
            AchievementRow("MASTER BUILDER", cleared, 30, 160), AchievementRow("STAR SEEKER", stars, 30, 120),
        ) else listOf(
            AchievementRow("ENDLESS LEGEND", progress.endlessBestHeight, 30, 100), AchievementRow("SKY SCRAPER", progress.endlessBestHeight, 60, 180),
            AchievementRow("CROWN ASCENT", progress.unlockedLevel, 60, 220), AchievementRow("DAILY DEVOTEE", progress.dailyIndex, 7, 110),
        )
        rows.forEachIndexed { i, row -> drawAchievement(row, 1370f - i * 245f) }
    }

    private data class AchievementRow(val name: String, val value: Int, val target: Int, val reward: Int)
    private fun drawAchievement(row: AchievementRow, y: Float) {
        val id = row.name.lowercase().replace(' ', '_')
        val complete = row.value >= row.target; val claimed = id in progress.achievementClaims
        val card = Rectangle(80f, y, 920f, 190f); panel(card, if (complete && !claimed) Color.valueOf("F6C85F") else Color.valueOf("123F86"))
        batch.begin(); batch.color = if (complete) Color.WHITE else Color(1f, 1f, 1f, .55f); batch.draw(olympusAtlas, 105f, y + 42f, 108f, 108f, 45, 850, 310, 350, false, false); batch.color = Color.WHITE; batch.end()
        text(row.name, Rectangle(235f, y + 108f, 450f, 50f), heading, Color.valueOf("F6C85F"), Align.left)
        text("PROGRESS ${row.value.coerceAtMost(row.target)} / ${row.target}", Rectangle(235f, y + 55f, 450f, 38f), font, Color.WHITE, Align.left)
        text("REWARD ${row.reward} COINS", Rectangle(235f, y + 20f, 450f, 30f), font, Color.valueOf("55E7FF"), Align.left)
        button(if (claimed) "CLAIMED" else if (complete) "CLAIM" else "LOCKED", Rectangle(745f, y + 57f, 180f, 72f), complete && !claimed, complete && !claimed) { progress = progress.copy(coins = progress.coins + row.reward, achievementClaims = progress.achievementClaims + id); save() }
    }

    private fun drawDaily() {
        sky(); header("DAILY ALTAR") { show(TempleView.MENU) }
        text("7 DAYS OF DIVINE GIFTS", Rectangle(130f, 1640f, 820f, 58f), heading, Color.valueOf("F6C85F"))
        val currentDay = progress.dailyIndex % 7
        repeat(7) { index ->
            val x = 105f + (index % 4) * 225f; val y = 1280f - (index / 4) * 260f
            val tile = Rectangle(x, y, 185f, 200f); val current = index == currentDay
            panel(tile, if (current) Color.valueOf("F6C85F") else Color.valueOf("123F86"))
            if (current) batch.begin().also { batch.draw(olympusAtlas, x + 65f, y + 149f, 56f, 56f, 920, 120, 280, 280, false, false); batch.end() }
            text(if (current) "TODAY" else "DAY ${index + 1}", Rectangle(x, y + 130f, 185f, 44f), font, if (current) Color.valueOf("F6C85F") else Color.WHITE)
            text("+${40 + index * 20} COINS", Rectangle(x, y + 72f, 185f, 45f), font, Color.valueOf("F6C85F"))
        }
        val today = System.currentTimeMillis() / 86_400_000L; val available = progress.dailyDay != today
        button(if (available) "CLAIM TODAY'S GIFT" else "CLAIMED — COME BACK TOMORROW", Rectangle(165f, 420f, 750f, 110f), available, available) { progress = progress.claimDaily(today) ?: progress; save() }
    }

    private fun drawLeaderboard() {
        sky(); header("HALL OF HEROES") { show(TempleView.MENU) }
        val entries = leaderboardEntries()
        val player = entries.first { it.isPlayer }
        panel(Rectangle(70f, 1542f, 940f, 158f), Color.valueOf("F6C85F"))
        text("YOUR OLYMPUS RECORD", Rectangle(170f, 1650f, 740f, 38f), font, Color.valueOf("F6C85F"))
        leaderboardMetric("RANK", "#${player.rank}", 245f, 1589f)
        leaderboardMetric(if (leaderboardTab == 0) "STARS" else if (leaderboardTab == 1) "HEIGHT" else "SCORE", player.value.toString(), 540f, 1589f)
        leaderboardMetric("LOCAL", "YOU", 835f, 1589f)

        val tabLabels = listOf("CAMPAIGN", "ENDLESS", "PERFECT")
        tabLabels.forEachIndexed { index, label ->
            button(label, Rectangle(72f + index * 316f, 1422f, 300f, 72f), primary = leaderboardTab == index) { leaderboardTab = index }
        }
        text("LOCAL LEGENDS — OFFLINE RECORDS", Rectangle(130f, 1355f, 820f, 40f), font, Color.valueOf("55E7FF"))

        // The podium is intentionally compact: Aero's strongest idea is hierarchy first, then
        // full readable cards. Here ranks 1–3 receive crowns while the player is never hidden.
        entries.take(3).forEachIndexed { index, entry ->
            val widths = floatArrayOf(270f, 330f, 270f)
            val heights = floatArrayOf(132f, 164f, 132f)
            val xs = floatArrayOf(92f, 375f, 718f)
            val y = if (index == 1) 1160f else 1192f
            val card = Rectangle(xs[index], y, widths[index], heights[index])
            panel(card, if (entry.isPlayer) Color.valueOf("F6C85F") else Color.valueOf("123F86"))
            text("RANK ${entry.rank}", Rectangle(card.x, card.y + card.height - 48f, card.width, 35f), font, Color.valueOf("F6C85F"))
            text(entry.name, Rectangle(card.x + 18f, card.y + 48f, card.width - 36f, 42f), font, Color.WHITE)
            text(leaderValue(entry.value), Rectangle(card.x + 12f, card.y + 13f, card.width - 24f, 35f), font, Color.valueOf("F2F4F6"))
        }
        entries.drop(3).take(7).forEachIndexed { index, entry -> drawLeaderboardRow(entry, 1050f - index * 112f) }
    }

    private fun leaderboardMetric(label: String, value: String, centerX: Float, y: Float) {
        text(label, Rectangle(centerX - 110f, y + 35f, 220f, 28f), font, Color.valueOf("A7C9E7"))
        text(value, Rectangle(centerX - 110f, y - 8f, 220f, 48f), heading, Color.WHITE)
    }

    private fun drawLeaderboardRow(entry: LeaderEntry, y: Float) {
        val card = Rectangle(90f, y, 900f, 88f)
        panel(card, if (entry.isPlayer) Color.valueOf("F6C85F") else Color.valueOf("123F86"))
        shapes.begin(ShapeRenderer.ShapeType.Filled); shapes.color = if (entry.isPlayer) Color.valueOf("F6C85F") else Color.valueOf("55E7FF"); shapes.circle(148f, y + 44f, 31f); shapes.color = Color.valueOf("06142E"); shapes.circle(148f, y + 44f, 25f); shapes.end()
        text("${entry.rank}", Rectangle(120f, y + 19f, 56f, 48f), font, Color.WHITE)
        text(entry.name, Rectangle(210f, y + 36f, 360f, 38f), font, Color.WHITE, Align.left)
        text(if (entry.isPlayer) "YOUR RECORD" else "OLYMPUS BUILDER", Rectangle(210f, y + 11f, 360f, 28f), font, Color.valueOf("A7C9E7"), Align.left)
        panel(Rectangle(700f, y + 14f, 235f, 58f), Color.valueOf("55E7FF"), Color.valueOf("0A2E66"))
        text(leaderValue(entry.value), Rectangle(716f, y + 25f, 203f, 32f), font, Color.valueOf("F2F4F6"))
    }

    private fun leaderValue(value: Int) = when (leaderboardTab) {
        0 -> "$value STARS"
        1 -> "$value FLOORS"
        else -> "$value PTS"
    }

    private fun leaderboardEntries(): List<LeaderEntry> {
        val names = listOf("ATHENA", "HERMES", "ARTEMIS", "APOLLO", "HESTIA", "NIKE", "IRIS", "HELIOS", "SELENE")
        val demo = when (leaderboardTab) {
            0 -> listOf(156, 141, 129, 115, 104, 91, 77, 64, 48)
            1 -> listOf(124, 109, 93, 76, 63, 52, 41, 34, 27)
            else -> listOf(680, 570, 490, 410, 350, 290, 240, 190, 150)
        }
        val playerValue = when (leaderboardTab) {
            0 -> progress.stars.values.sum()
            1 -> progress.endlessBestHeight
            else -> progress.endlessBestScore
        }
        return (names.zip(demo) + ("YOU" to playerValue)).sortedByDescending { it.second }.mapIndexed { index, (name, value) -> LeaderEntry(index + 1, name, value, name == "YOU") }
    }

    private fun drawMiniGames() {
        sky(); header("ORACLE ARCADE") { show(TempleView.MENU) }
        text("RISK A LITTLE / WIN DIVINE TREASURES", Rectangle(120f, 1640f, 840f, 55f), font, Color.valueOf("55E7FF"))
        panel(Rectangle(85f, 1080f, 910f, 390f), Color.valueOf("F6C85F"))
        batch.begin(); batch.draw(oracleArt, 690f, 1170f, 245f, 190f, 125, 55, 995, 720, false, false); batch.end()
        text("THUNDER REELS", Rectangle(145f, 1320f, 790f, 72f), title, Color.valueOf("F6C85F"))
        text("MATCH THE SIGNS OF OLYMPUS", Rectangle(145f, 1238f, 790f, 45f), font, Color.WHITE)
        text("ENTRY  ${OlympusMiniGames.slotCost} COINS", Rectangle(145f, 1165f, 790f, 42f), font, Color.valueOf("A7C9E7"))
        button("PLAY THUNDER REELS", Rectangle(200f, 1108f, 680f, 90f), true) { slotResult = null; show(TempleView.SLOT) }
        panel(Rectangle(85f, 575f, 910f, 390f), Color.valueOf("55E7FF"))
        batch.begin(); batch.draw(oracleArt, 685f, 675f, 255f, 190f, 20, 850, 390, 360, false, false); batch.end()
        text("FATE CHESTS", Rectangle(145f, 815f, 790f, 72f), title, Color.valueOf("F6C85F"))
        text("OPEN ONE CHEST — KEEP ITS BLESSING", Rectangle(145f, 733f, 790f, 45f), font, Color.WHITE)
        text("ENTRY  ${OlympusMiniGames.chestCost} COINS", Rectangle(145f, 660f, 790f, 42f), font, Color.valueOf("A7C9E7"))
        button("CHOOSE A CHEST", Rectangle(200f, 603f, 680f, 90f), true) { chestOpened = null; show(TempleView.CHEST) }
    }

    private fun drawSlotMachine() {
        sky(); header("THUNDER REELS") { show(TempleView.MINIGAMES) }
        panel(Rectangle(70f, 805f, 940f, 560f), Color.valueOf("F6C85F"))
        batch.begin(); batch.color = Color(1f, 1f, 1f, .25f); batch.draw(oracleArt, 150f, 900f, 780f, 365f, 125, 55, 995, 720, false, false); batch.color = Color.WHITE; batch.end()
        text("SPIN THE ORACLE", Rectangle(150f, 1250f, 780f, 65f), heading, Color.valueOf("F6C85F"))
        val result = slotResult
        val symbols = result?.reels ?: listOf("?", "?", "?")
        symbols.forEachIndexed { index, symbol ->
            val reel = Rectangle(135f + index * 285f, 970f, 245f, 180f); panel(reel, Color.valueOf("55E7FF"), Color.valueOf("0A2E66")); text(symbol, reel, heading, Color.WHITE)
        }
        if (result != null) {
            text(result.headline, Rectangle(150f, 895f, 780f, 48f), font, Color.valueOf("55E7FF"))
            text("+${result.rewardCoins} COINS${if (result.rewardCrystals > 0) "   +${result.rewardCrystals} CRYSTAL" else ""}", Rectangle(150f, 850f, 780f, 42f), font, Color.valueOf("F6C85F"))
        }
        button("SPIN  ${OlympusMiniGames.slotCost} COINS", Rectangle(190f, 635f, 700f, 110f), true, progress.coins >= OlympusMiniGames.slotCost) {
            val spin = OlympusMiniGames.spin((System.currentTimeMillis() / 29L).toInt())
            progress = progress.copy(coins = progress.coins - OlympusMiniGames.slotCost + spin.rewardCoins, crystals = progress.crystals + spin.rewardCrystals)
            slotResult = spin; save()
        }
    }

    private fun drawLuckyChest() {
        sky(); header("FATE CHESTS") { show(TempleView.MINIGAMES) }
        text("ONE CHOICE. ONE BLESSING.", Rectangle(140f, 1510f, 800f, 55f), heading, Color.valueOf("F6C85F"))
        text(if (chestOpened == null) "CHOOSE A CHEST / ENTRY ${OlympusMiniGames.chestCost} COINS" else "THE ORACLE HAS SPOKEN", Rectangle(110f, 1438f, 860f, 42f), font, Color.valueOf("55E7FF"))
        repeat(3) { index ->
            val card = Rectangle(90f + index * 330f, 875f, 280f, 390f)
            val opened = chestOpened == index
            panel(card, if (opened) Color.valueOf("F6C85F") else Color.valueOf("55E7FF"))
            batch.begin(); batch.draw(oracleArt, card.x + 25f, card.y + 190f, 230f, 150f, 20 + index * 410, 850, 390, 360, false, false); batch.end()
            text(if (opened) "OPEN" else "CHEST ${index + 1}", Rectangle(card.x + 15f, card.y + 240f, card.width - 30f, 60f), heading, if (opened) Color.valueOf("F6C85F") else Color.WHITE)
            text(if (opened) chestRewardLabel(index) else "TAP TO REVEAL", Rectangle(card.x + 15f, card.y + 135f, card.width - 30f, 70f), font, Color.WHITE)
            if (chestOpened == null && progress.coins >= OlympusMiniGames.chestCost) hits += Hit(card) { openChest(index) }
        }
        if (chestOpened != null) button("BACK TO ARCADE", Rectangle(210f, 610f, 660f, 92f)) { show(TempleView.MINIGAMES) }
    }

    private fun openChest(index: Int) {
        if (chestOpened != null || progress.coins < OlympusMiniGames.chestCost) return
        val gift = OlympusMiniGames.openChest(index)
        var next = progress.copy(coins = progress.coins - OlympusMiniGames.chestCost + gift.rewardCoins)
        if (gift.rewardLives > 0) next = next.rewardLives(gift.rewardLives)
        val aid = gift.rewardAid
        if (aid != null) next = next.copy(aidInventory = next.aidInventory + (aid to (next.aidInventory[aid] ?: 0) + 1))
        progress = next; chestOpened = index; save()
    }

    private fun chestRewardLabel(index: Int): String {
        val reward = OlympusMiniGames.openChest(index)
        return when {
            reward.rewardAid != null -> "+1 MASON'S BLESSING"
            reward.rewardLives > 0 -> "+${reward.rewardCoins} COINS  +1 LIFE"
            else -> "+${reward.rewardCoins} COINS"
        }
    }

    private fun drawSettings() {
        sky(); header("SETTINGS") { show(TempleView.MENU) }
        val rows = listOf("MUSIC" to progress.music, "SOUND EFFECTS" to progress.sound, "HAPTICS" to progress.haptics, "REDUCED FLASHES" to progress.reducedFlashes, "HIGH CONTRAST" to progress.highContrast)
        rows.forEachIndexed { index, (label, active) ->
            val card = Rectangle(68f, 1510f - index * 210f, 944f, 150f); panel(card, Color.valueOf("123F86")); text(label, Rectangle(125f, card.y + 48f, 520f, 54f), heading, Color.WHITE, Align.left)
            val toggle = Rectangle(725f, card.y + 37f, 220f, 76f)
            button(if (active) "ON" else "OFF", toggle, primary = active) {
                progress = when (index) { 0 -> progress.copy(music = !progress.music); 1 -> progress.copy(sound = !progress.sound); 2 -> progress.copy(haptics = !progress.haptics); 3 -> progress.copy(reducedFlashes = !progress.reducedFlashes); else -> progress.copy(highContrast = !progress.highContrast) }
                save()
            }
        }
        button("ABOUT & PRIVACY", Rectangle(250f, 300f, 580f, 78f)) { show(TempleView.ABOUT) }
    }

    private fun drawAbout() {
        sky(); header("ABOUT & PRIVACY") { show(TempleView.SETTINGS) }
        panel(Rectangle(90f, 630f, 900f, 890f), Color.valueOf("F6C85F"))
        text("ZEUS: TEMPLE STACK", Rectangle(160f, 1370f, 760f, 72f), heading, Color.valueOf("F6C85F"))
        text("OFFLINE ARCADE", Rectangle(160f, 1295f, 760f, 40f), font, Color.valueOf("55E7FF"))
        text("YOUR PROGRESS STAYS ON THIS DEVICE.", Rectangle(145f, 1155f, 790f, 48f), font, Color.WHITE)
        text("NO ACCOUNT / NO ONLINE LEADERBOARD", Rectangle(145f, 1080f, 790f, 45f), font, Color.WHITE)
        text("NO ADS / NO PERSONAL DATA COLLECTION", Rectangle(145f, 1005f, 790f, 45f), font, Color.WHITE)
        text("LOCAL LEGENDS USE SEEDED DEMO RIVALS", Rectangle(145f, 900f, 790f, 45f), font, Color.valueOf("A7C9E7"))
        button("BACK TO SETTINGS", Rectangle(240f, 710f, 600f, 86f)) { show(TempleView.SETTINGS) }
    }
}
