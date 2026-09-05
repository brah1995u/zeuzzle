package studio.cortex.templestack.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import studio.cortex.templestack.core.TempleStackGame

/** Shared responsive interaction layer. Every screen gets its own hit list and back policy. */
abstract class ZeusBaseScreen(internal val app: TempleStackGame) : ScreenAdapter() {
    protected val camera = OrthographicCamera()
    protected val viewport = ExtendViewport(1080f, 2160f, camera)
    protected val batch = SpriteBatch()
    protected val shapes = ShapeRenderer()
    protected val body = BitmapFont().apply { data.setScale(1.22f) }
    protected val heading = BitmapFont().apply { data.setScale(1.75f) }
    protected val display = BitmapFont().apply { data.setScale(2.42f) }
    private val pointer = Vector2()
    private val taps = mutableListOf<TapTarget>()
    private var pressed: TapTarget? = null
    protected var elapsed = 0f

    private data class TapTarget(val bounds: Rectangle, val enabled: Boolean, val action: () -> Unit)

    init {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(x: Int, y: Int, pointerId: Int, button: Int): Boolean {
                if (pointerId != 0) return false
                unproject(x, y)
                pressed = taps.asReversed().firstOrNull { it.enabled && it.bounds.contains(pointer) }
                return pressed != null || onCanvasDown(pointer.x, pointer.y)
            }
            override fun touchUp(x: Int, y: Int, pointerId: Int, button: Int): Boolean {
                if (pointerId != 0) return false
                unproject(x, y)
                val hit = pressed
                pressed = null
                if (hit != null && hit.enabled && hit.bounds.contains(pointer)) { hit.action(); return true }
                return onCanvasUp(pointer.x, pointer.y)
            }
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { onBack(); return true }
                return false
            }
        }
    }

    final override fun render(delta: Float) {
        elapsed += delta.coerceIn(0f, .05f)
        Gdx.gl.glClearColor(.015f, .035f, .10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        viewport.apply()
        batch.projectionMatrix = camera.combined
        shapes.projectionMatrix = camera.combined
        taps.clear()
        draw(delta)
    }

    protected abstract fun draw(delta: Float)
    protected abstract fun onBack()
    protected open fun onCanvasDown(x: Float, y: Float) = false
    protected open fun onCanvasUp(x: Float, y: Float) = false

    protected fun backdrop(texture: Texture, darkness: Float = 0f) {
        // UI is authored on a fixed portrait canvas; ExtendViewport exposes more on odd ratios
        // but never moves the background off that canvas.
        batch.begin(); batch.color = Color.WHITE; batch.draw(texture, 0f, 0f, 1080f, 2160f); batch.end()
        if (darkness > 0f) { shapes.begin(ShapeRenderer.ShapeType.Filled); shapes.color = Color(0.01f, .025f, .08f, darkness); shapes.rect(0f, 0f, 1080f, 2160f); shapes.end() }
    }

    protected fun card(bounds: Rectangle, accent: Color = ZeusPalette.gold, fill: Color = ZeusPalette.navy) {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(accent.r, accent.g, accent.b, .95f); shapes.rect(bounds.x, bounds.y, bounds.width, bounds.height)
        shapes.color = Color(fill.r, fill.g, fill.b, .98f); shapes.rect(bounds.x + 7f, bounds.y + 7f, bounds.width - 14f, bounds.height - 14f)
        shapes.color = Color(1f, 1f, 1f, .08f); shapes.rect(bounds.x + 15f, bounds.y + bounds.height - 28f, bounds.width - 30f, 8f)
        shapes.end()
    }

    protected fun label(value: String, bounds: Rectangle, font: BitmapFont = body, color: Color = Color.WHITE, align: Int = Align.center) {
        batch.begin()
        font.color = Color(.01f, .02f, .06f, .9f); font.draw(batch, value, bounds.x + 2f, bounds.y + bounds.height * .62f - 2f, bounds.width, align, false)
        font.color = color; font.draw(batch, value, bounds.x, bounds.y + bounds.height * .62f, bounds.width, align, false)
        batch.end()
    }

    protected fun action(label: String, bounds: Rectangle, primary: Boolean = false, enabled: Boolean = true, onTap: () -> Unit) {
        val down = pressed?.bounds == bounds
        if (primary && bounds.width / bounds.height <= 3.0f) {
            val lift = if (down) -10f else 0f
            batch.begin(); batch.color = if (enabled) Color.WHITE else Color(1f, 1f, 1f, .48f)
            batch.draw(app.assets.primaryCta, bounds.x, bounds.y + lift, bounds.width, bounds.height)
            batch.color = Color.WHITE; batch.end()
            label(label, Rectangle(bounds.x + bounds.width * .23f, bounds.y + lift + bounds.height * .29f, bounds.width * .54f, bounds.height * .30f), heading, ZeusPalette.navy)
            taps += TapTarget(bounds, enabled, onTap)
            return
        }
        val border = when { !enabled -> ZeusPalette.muted; primary -> ZeusPalette.gold; else -> ZeusPalette.cyan }
        val fill = when { !enabled -> Color.valueOf("25364C"); primary -> Color.valueOf("8A5917"); else -> Color.valueOf("123F86") }
        val lift = if (down) -6f else 0f
        card(Rectangle(bounds.x, bounds.y + lift, bounds.width, bounds.height), border, fill)
        label(label, Rectangle(bounds.x + 14f, bounds.y + lift, bounds.width - 28f, bounds.height), heading, if (enabled) Color.WHITE else ZeusPalette.muted)
        taps += TapTarget(bounds, enabled, onTap)
    }

    /** Invisible hit zone for artwork or cards; unlike [action], it never redraws the visual. */
    protected fun tap(bounds: Rectangle, enabled: Boolean = true, onTap: () -> Unit) {
        taps += TapTarget(bounds, enabled, onTap)
    }

    protected fun nav(back: (() -> Unit)? = null, home: (() -> Unit)? = null, title: String) {
        back?.let { action("‹", Rectangle(42f, 2018f, 104f, 92f), false, true, it) }
        card(Rectangle(170f, 2024f, 740f, 80f), ZeusPalette.cyan, Color.valueOf("08295D"))
        label(title, Rectangle(195f, 2035f, 690f, 54f), heading, ZeusPalette.marble)
        home?.let { action("⌂", Rectangle(934f, 2018f, 104f, 92f), false, true, it) }
    }

    protected fun statPill(text: String, x: Float, y: Float, width: Float) {
        card(Rectangle(x, y, width, 62f), ZeusPalette.cyan, Color.valueOf("082451"))
        label(text, Rectangle(x + 10f, y + 7f, width - 20f, 50f), body, ZeusPalette.marble)
    }

    private fun unproject(x: Int, y: Int) { pointer.set(x.toFloat(), y.toFloat()); viewport.unproject(pointer) }
    override fun resize(width: Int, height: Int) = viewport.update(width, height, true)
    override fun dispose() { batch.dispose(); shapes.dispose(); body.dispose(); heading.dispose(); display.dispose() }
}

object ZeusPalette {
    val navy = Color.valueOf("06142E")
    val blue = Color.valueOf("123F86")
    val cyan = Color.valueOf("55E7FF")
    val gold = Color.valueOf("F6C85F")
    val marble = Color.valueOf("F2F4F6")
    val danger = Color.valueOf("EF4F5F")
    val muted = Color.valueOf("7990AB")
}
