package studio.cortex.thunderbound

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import studio.cortex.templestack.core.TempleStackGame

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize(TempleStackGame(), AndroidApplicationConfiguration().apply { useImmersiveMode = true })
    }
}
