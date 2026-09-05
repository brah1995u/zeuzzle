package studio.cortex.thunderbound.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import studio.cortex.thunderbound.engine.Campaign
import studio.cortex.thunderbound.engine.GameProgress
import studio.cortex.thunderbound.engine.Power
import studio.cortex.thunderbound.engine.ProductConfig
import studio.cortex.thunderbound.engine.ProgressCodec

internal enum class View { MENU, MAP, SETTINGS, GAME, VICTORY, DEFEAT }

class ThunderboundScreen(private val game: ThunderboundGame, private val preferences: Preferences) : ScreenAdapter() {
    private val canvas = GameCanvas()
    private var progress = ProgressCodec.decode(preferences.getString("save", null))
    private var view = View.MENU
    private var selectedLevel = 1
    private var play: PhysicsBattle? = null
    private val pointer = Vector2()
    private val buttons = mutableListOf<ActionButton>()
    private var pressed: ActionButton? = null
    private var aiming = false
    private val aimOrigin = Vector2(270f, 370f)
    private val drag = Vector2()
    private var transition = 0f

    init {
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(x: Int, y: Int, pointerId: Int, button: Int): Boolean {
                toWorld(x, y)
                pressed = buttons.firstOrNull { it.bounds.contains(pointer) }
                if (pressed != null) return true
                val battle = play
                if (view == View.GAME && battle != null) {
                    if (battle.phase == BattlePhase.READY && pointer.dst(aimOrigin) < 145f) {
                        aiming = true; drag.set(0f, 0f); return true
                    }
                    if (battle.phase == BattlePhase.FLYING) { battle.activate(); return true }
                }
                return false
            }
            override fun touchDragged(x: Int, y: Int, pointerId: Int): Boolean {
                if (aiming) { toWorld(x, y); drag.set(aimOrigin).sub(pointer).limit(220f); return true }
                return false
            }
            override fun touchUp(x: Int, y: Int, pointerId: Int, button: Int): Boolean {
                toWorld(x, y)
                pressed?.let { if (it.bounds.contains(pointer)) it.action.invoke() }
                pressed = null
                if (aiming) {
                    if (drag.len() >= 42f) play?.launch(drag) else play?.cancelAim()
                    aiming = false
                }
                return true
            }
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    when (view) {
                        View.GAME -> play?.togglePause()
                        View.MAP, View.SETTINGS -> view = View.MENU
                        View.VICTORY, View.DEFEAT -> { play?.dispose(); play = null; view = View.MAP }
                        else -> Unit
                    }; return true
                }; return false
            }
        }
    }

    override fun render(delta: Float) {
        transition = MathUtils.clamp(transition + delta * 2f, 0f, 1f)
        canvas.begin()
        buttons.clear()
        when (view) {
            View.MENU -> drawMenu()
            View.MAP -> drawMap()
            View.SETTINGS -> drawSettings()
            View.GAME -> drawGame(delta)
            View.VICTORY -> drawResult(true)
            View.DEFEAT -> drawResult(false)
        }
    }

    override fun resize(width: Int, height: Int) { canvas.viewport.update(width, height, true) }
    override fun dispose() { play?.dispose(); canvas.dispose() }

    private fun toWorld(x: Int, y: Int) { pointer.set(x.toFloat(), y.toFloat()); canvas.viewport.unproject(pointer) }
    private fun save() { preferences.putString("save", ProgressCodec.encode(progress)).flush() }
    private fun go(to: View) { transition = 0f; view = to }

    private fun drawSky() {
        val s = canvas.shapes
        s.begin(ShapeRenderer.ShapeType.Filled)
        s.color = Color("07183f"); s.rect(0f, 0f, 1920f, 1080f)
        s.color = Color("0a58b5"); s.triangle(0f, 0f, 0f, 730f, 1050f, 0f)
        s.color = Color("153d86"); s.triangle(1920f, 1080f, 800f, 1080f, 1920f, 350f)
        s.color = Color("22d9ff55"); repeat(18) { i -> s.circle(80f + i * 118f, 890f + (i % 3) * 50f, 2f + (i % 4)) }
        s.end()
    }
    private fun label(text: String, x: Float, y: Float, color: Color = Color.WHITE, centered: Boolean = true, big: Boolean = false) {
        val f = if (big) canvas.title else canvas.font
        canvas.batch.begin(); f.color = Color("081c42")
        if (centered) f.draw(canvas.batch, text, 3f, y - 3f, 1920f, Align.center, false) else f.draw(canvas.batch, text, x + 3f, y - 3f)
        f.color = color
        if (centered) f.draw(canvas.batch, text, 0f, y, 1920f, Align.center, false) else f.draw(canvas.batch, text, x, y)
        canvas.batch.end()
    }
    private fun panel(x: Float, y: Float, w: Float, h: Float, color: Color = Color("153d86")) {
        canvas.shapes.begin(ShapeRenderer.ShapeType.Filled); canvas.shapes.color = Color("07183fcc"); canvas.shapes.rect(x + 8f, y - 8f, w, h)
        canvas.shapes.color = color; canvas.shapes.rect(x, y, w, h); canvas.shapes.color = Color("22d9ff"); canvas.shapes.rect(x, y + h - 7f, w, 7f); canvas.shapes.end()
    }
    private fun button(text: String, x: Float, y: Float, w: Float, h: Float, primary: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
        val b = Rectangle(x, y, w, h); val down = pressed?.bounds == b
        canvas.shapes.begin(ShapeRenderer.ShapeType.Filled)
        canvas.shapes.color = if (!enabled) Color("50627b") else if (primary) Color("f5b82e") else Color("0a58b5")
        canvas.shapes.rect(x, y + if (down) -4f else 0f, w, h); canvas.shapes.color = if (primary) Color("9a5415") else Color("07183f"); canvas.shapes.rect(x, y, w, 9f); canvas.shapes.end()
        label(text, x + w / 2f, y + h / 2f + 17f, if (primary) Color("081c42") else Color.WHITE)
        if (enabled) buttons += ActionButton(b, action)
    }

    private fun drawMenu() {
        drawSky()
        label(ProductConfig.title, 960f, 870f, Color("f5b82e"), big = true)
        label(ProductConfig.subtitle, 960f, 786f, Color("22d9ff"))
        // Original heroic silhouette, deliberately geometric rather than a derivative character asset.
        canvas.shapes.begin(ShapeRenderer.ShapeType.Filled)
        canvas.shapes.color = Color("eaf3fa"); canvas.shapes.circle(370f, 500f, 95f); canvas.shapes.rect(285f, 290f, 170f, 180f)
        canvas.shapes.color = Color("f5b82e"); canvas.shapes.rect(304f, 270f, 130f, 45f); canvas.shapes.color = Color("22d9ff"); canvas.shapes.circle(462f, 423f, 26f)
        canvas.shapes.end()
        panel(1070f, 285f, 580f, 390f)
        label("OLYMPUS NEEDS ITS THUNDER", 1360f, 620f, Color("f5fdff"))
        label("Break Titan fortresses. Restore the Sky Seals.", 1360f, 560f, Color("eaf3fa"))
        button("PLAY CAMPAIGN", 1110f, 440f, 500f, 120f, true) { go(View.MAP) }
        button("SETTINGS", 1110f, 320f, 500f, 86f) { go(View.SETTINGS) }
        label("◈  ${progress.coins}     ✦  ${progress.gems}", 1660f, 1010f, Color("f5b82e"), centered = false)
        label("CORTEX STUDIO  •  OFFLINE PLAY", 960f, 100f, Color("93aac1"))
    }

    private fun drawMap() {
        drawSky(); label("OLYMPUS MAP", 960f, 995f, Color("f5b82e"), big = true)
        label("WORLD ${(selectedLevel - 1) / 15 + 1}  •  ${Campaign.level(selectedLevel).name}", 960f, 920f, Color("eaf3fa"))
        for (i in 1..15) {
            val id = ((selectedLevel - 1) / 15) * 15 + i
            val x = 220f + ((i - 1) % 5) * 370f; val y = 650f - ((i - 1) / 5) * 190f
            val unlocked = id <= progress.unlockedLevel
            canvas.shapes.begin(ShapeRenderer.ShapeType.Filled); canvas.shapes.color = if (unlocked) Color("0a58b5") else Color("50627b"); canvas.shapes.circle(x, y, 68f)
            canvas.shapes.color = if (id == selectedLevel) Color("f5b82e") else Color("22d9ff"); canvas.shapes.circle(x, y, 57f); canvas.shapes.color = Color("153d86"); canvas.shapes.circle(x, y, 47f); canvas.shapes.end()
            label(if (unlocked) "$id" else "LOCK", x, y + 17f, if (unlocked) Color.WHITE else Color("93aac1"))
            if (unlocked) label("★".repeat(progress.stars[id] ?: 0), x, y - 88f, Color("f5b82e"))
            if (unlocked) buttons += ActionButton(Rectangle(x - 72f, y - 72f, 144f, 144f)) { selectedLevel = id }
        }
        val l = Campaign.level(selectedLevel)
        panel(610f, 78f, 700f, 150f)
        label("${l.name}  •  ${l.casts} CASTS  •  ${l.power.displayName}", 960f, 176f, Color("f5fdff"))
        button("BEGIN LEVEL ${l.id}", 1340f, 92f, 400f, 110f, true, selectedLevel <= progress.unlockedLevel) { startLevel() }
        button("‹", 90f, 920f, 100f, 74f) { go(View.MENU) }
    }

    private fun drawSettings() {
        drawSky(); label("SETTINGS", 960f, 940f, Color("f5b82e"), big = true); panel(510f, 230f, 900f, 560f)
        settingRow("DIVINE HAPTICS", 650f, 670f, progress.hapticsEnabled) { progress = progress.copy(hapticsEnabled = !progress.hapticsEnabled); save() }
        settingRow("SOUND & MUSIC", 650f, 540f, progress.soundEnabled) { progress = progress.copy(soundEnabled = !progress.soundEnabled); save() }
        settingRow("REDUCED FLASHES", 650f, 410f, progress.reducedFlashes) { progress = progress.copy(reducedFlashes = !progress.reducedFlashes); save() }
        label("All progress stays on this device. No account, ads or network needed.", 960f, 295f, Color("93aac1"))
        button("BACK", 710f, 120f, 500f, 100f) { go(View.MENU) }
    }
    private fun settingRow(text: String, x: Float, y: Float, enabled: Boolean, action: () -> Unit) {
        label(text, x, y + 24f, Color.WHITE, centered = false)
        val b = Rectangle(1120f, y - 22f, 170f, 74f)
        canvas.shapes.begin(ShapeRenderer.ShapeType.Filled); canvas.shapes.color = if (enabled) Color("22d9ff") else Color("50627b"); canvas.shapes.rect(b.x, b.y, b.width, b.height)
        canvas.shapes.color = if (enabled) Color("f5fdff") else Color("93aac1"); canvas.shapes.circle(if (enabled) 1252f else 1158f, y + 15f, 25f); canvas.shapes.end()
        buttons += ActionButton(b, action)
    }

    private fun startLevel() { play?.dispose(); play = PhysicsBattle(Campaign.level(selectedLevel), progress.reducedFlashes); go(View.GAME) }
    private fun drawGame(delta: Float) {
        val battle = play ?: return
        battle.update(delta)
        drawSky(); battle.render(canvas)
        panel(70f, 930f, 440f, 98f); label("CASTS  ${battle.castsLeft}", 290f, 990f, Color.WHITE)
        panel(1410f, 930f, 440f, 98f); label("TARGETS  ${battle.enemiesLeft}", 1630f, 990f, Color.WHITE)
        button("Ⅱ", 1740f, 790f, 110f, 95f) { battle.togglePause() }
        if (battle.phase == BattlePhase.READY) label("DRAG BACK TO AIM", 310f, 245f, Color("22d9ff"))
        if (aiming) battle.drawTrajectory(canvas, aimOrigin, drag)
        if (battle.phase == BattlePhase.FLYING && battle.power != Power.SKYBOLT) label("TAP TO UNLEASH ${battle.power.displayName}", 960f, 865f, Color("22d9ff"))
        if (battle.phase == BattlePhase.PAUSED) { panel(660f, 385f, 600f, 260f); label("PAUSED", 960f, 570f, Color("f5b82e"), big = true); button("RESUME", 755f, 435f, 410f, 90f, true) { battle.togglePause() } }
        when (battle.phase) { BattlePhase.VICTORY -> finishLevel(true); BattlePhase.DEFEAT -> finishLevel(false); else -> Unit }
    }
    private fun finishLevel(win: Boolean) {
        if (win) progress = progress.withResult(selectedLevel, if (play!!.castsLeft >= 2) 3 else 2, play!!.definition.rewardCoins)
        save(); go(if (win) View.VICTORY else View.DEFEAT)
    }
    private fun drawResult(win: Boolean) {
        drawSky(); val l = Campaign.level(selectedLevel); val accent = if (win) Color("3dd18d") else Color("d94749")
        panel(470f, 230f, 980f, 610f, accent); label(if (win) "FORTRESS FALLEN" else "THE TITANS ENDURE", 960f, 710f, accent, big = true)
        label(if (win) "${"★".repeat(progress.stars[selectedLevel] ?: 2)}  +${l.rewardCoins} COINS" else "Try a longer pull or strike the supports.", 960f, 585f, Color.WHITE)
        if (win) button("NEXT LEVEL", 700f, 410f, 520f, 112f, true) { selectedLevel = minOf(90, selectedLevel + 1); startLevel() }
        else button("RETRY", 700f, 410f, 520f, 112f, true) { startLevel() }
        button("RETURN TO MAP", 700f, 275f, 520f, 95f) { play?.dispose(); play = null; go(View.MAP) }
    }
    private data class ActionButton(val bounds: Rectangle, val action: () -> Unit)
}
