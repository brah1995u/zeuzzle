package studio.cortex.thunderbound.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import studio.cortex.templestack.core.TempleStackGame

fun main() {
    Lwjgl3Application(TempleStackGame(), Lwjgl3ApplicationConfiguration().apply {
        setTitle("Zeus: Temple Stack")
        setWindowedMode(432, 960)
        setResizable(false)
        useVsync(true)
        setForegroundFPS(60)
    })
}
