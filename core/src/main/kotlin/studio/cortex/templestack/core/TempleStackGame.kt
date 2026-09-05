package studio.cortex.templestack.core

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import studio.cortex.templestack.core.screens.SplashScreen
import studio.cortex.templestack.core.ui.ZeusAssets
import studio.cortex.templestack.engine.TempleProgress
import studio.cortex.templestack.engine.TempleProgressCodec

class TempleStackGame : Game() {
    lateinit var prefs: Preferences
    lateinit var assets: ZeusAssets
    var progress: TempleProgress = TempleProgress()
    fun save() { prefs.putString("save", TempleProgressCodec.encode(progress)).flush() }
    override fun create() {
        prefs = Gdx.app.getPreferences("zeus_temple_stack_progress")
        progress = TempleProgressCodec.decode(prefs.getString("save", null))
        assets = ZeusAssets()
        setScreen(SplashScreen(this))
    }
    override fun dispose() { super.dispose(); assets.dispose() }
}
