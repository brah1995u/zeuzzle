package studio.cortex.thunderbound.core

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.StretchViewport

class ThunderboundGame : Game() {
    override fun create() {
        Box2D.init()
        setScreen(OlympusMergeScreen(Gdx.app.getPreferences("thunderbound_progress")))
    }
}

internal class GameCanvas : Disposable {
    val camera = OrthographicCamera()
    val viewport = FitViewport(1920f, 1080f, camera)
    val shapes = ShapeRenderer()
    val batch = SpriteBatch()
    val font = BitmapFont().apply { data.setScale(1.65f) }
    val title = BitmapFont().apply { data.setScale(3.4f) }
    fun begin() {
        viewport.apply()
        Gdx.gl.glClearColor(0.027f, 0.094f, 0.247f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined
    }
    override fun dispose() { shapes.dispose(); batch.dispose(); font.dispose(); title.dispose() }
}

/** Portrait canvas shared by the Olympus Merge and Crystal Storm screens. */
internal class PortraitCanvas : Disposable {
    val camera = OrthographicCamera()
    val viewport = StretchViewport(858f, 1920f, camera)
    val shapes = ShapeRenderer()
    val batch = SpriteBatch()
    val font = BitmapFont()

    fun begin() {
        viewport.apply()
        Gdx.gl.glClearColor(0.01f, 0.035f, 0.09f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }
}

internal fun Color(hex: String): Color = Color.valueOf(hex)
