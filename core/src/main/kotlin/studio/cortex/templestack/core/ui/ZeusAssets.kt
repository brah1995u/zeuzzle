package studio.cortex.templestack.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable

/** Only original Zeus assets are allowed here. Never load legacy ui/ or atlas textures. */
class ZeusAssets : Disposable {
    val menu = Texture(Gdx.files.internal("olympus_menu_backdrop_v2.png"))
    val gameplay = Texture(Gdx.files.internal("olympus_gameplay_arena_v2.png"))
    val foundation = Texture(Gdx.files.internal("temple_foundation_v3.png"))
    val primaryCta = Texture(Gdx.files.internal("zeus_primary_cta_v1.png"))
    override fun dispose() { menu.dispose(); gameplay.dispose(); foundation.dispose(); primaryCta.dispose() }
}
