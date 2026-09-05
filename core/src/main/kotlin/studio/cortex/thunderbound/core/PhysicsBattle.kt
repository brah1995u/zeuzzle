package studio.cortex.thunderbound.core

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.Contact
import com.badlogic.gdx.physics.box2d.ContactImpulse
import com.badlogic.gdx.physics.box2d.ContactListener
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.physics.box2d.PolygonShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Array
import studio.cortex.thunderbound.engine.LevelDefinition
import studio.cortex.thunderbound.engine.Power
import kotlin.math.abs

internal enum class BattlePhase { INTRO, READY, FLYING, SETTLING, VICTORY, DEFEAT, PAUSED }
private const val PPM = 100f

internal class PhysicsBattle(val definition: LevelDefinition, private val reducedFlashes: Boolean) {
    private val world = World(Vector2(0f, -9.8f), true)
    private val entities = mutableListOf<BattleEntity>()
    private val debris = mutableListOf<Fragment>()
    private val pending = mutableSetOf<BattleEntity>()
    private var projectile: ProjectileEntity? = null
    private var accumulator = 0f
    private var phaseElapsed = 0f
    private var settleElapsed = 0f
    private var savedPhase = BattlePhase.READY
    var phase = BattlePhase.INTRO
        private set
    var castsLeft = definition.casts
        private set
    val enemiesLeft: Int get() = entities.count { it.kind == Kind.ENEMY && !it.destroyed }
    val power: Power get() = definition.power

    init {
        makeGround()
        makeFortress()
        world.setContactListener(object : ContactListener {
            override fun beginContact(contact: Contact) = Unit
            override fun endContact(contact: Contact) = Unit
            override fun preSolve(contact: Contact, oldManifold: com.badlogic.gdx.physics.box2d.Manifold) = Unit
            override fun postSolve(contact: Contact, impulse: ContactImpulse) {
                val a = contact.fixtureA.body.userData as? BattleEntity
                val b = contact.fixtureB.body.userData as? BattleEntity
                val strength = impulse.normalImpulses.maxOrNull() ?: 0f
                if (a?.kind == Kind.PROJECTILE && b != null) hit(b, strength * 34f)
                if (b?.kind == Kind.PROJECTILE && a != null) hit(a, strength * 34f)
                if (a?.kind == Kind.BLOCK && strength > 5f) hit(a, strength * 10f)
                if (b?.kind == Kind.BLOCK && strength > 5f) hit(b, strength * 10f)
                if ((a?.kind == Kind.PROJECTILE || b?.kind == Kind.PROJECTILE) && definition.power == Power.SKYBOLT) activate()
            }
        })
    }

    fun launch(drag: Vector2) {
        if (phase != BattlePhase.READY || castsLeft <= 0) return
        castsLeft--
        val direction = Vector2(drag).nor()
        val speed = (10f + 16f * (drag.len() / 220f)) * power.speed
        val p = createCircle(305f, 405f, 28f, 1.15f, Kind.PROJECTILE, 1f, Color("22d9ff")) as ProjectileEntity
        p.body.setBullet(true)
        p.body.linearVelocity = direction.scl(speed)
        projectile = p
        phase = BattlePhase.FLYING; phaseElapsed = 0f
    }
    fun cancelAim() = Unit
    fun togglePause() {
        if (phase == BattlePhase.PAUSED) { phase = savedPhase; return }
        if (phase in setOf(BattlePhase.READY, BattlePhase.FLYING, BattlePhase.SETTLING)) { savedPhase = phase; phase = BattlePhase.PAUSED }
    }
    fun activate() {
        val p = projectile ?: return
        if (p.activated) return
        p.activated = true
        val radius = when (power) {
            Power.SKYBOLT -> 205f
            Power.FORKSTORM -> 235f
            Power.EAGLE_DIVE -> 185f
            Power.AEGIS_ORB -> 170f
            Power.TEMPEST -> 270f
            Power.TITAN_BREAKER -> 300f
        }
        val origin = p.pixelPosition()
        entities.filter { it !== p && !it.destroyed && it.pixelPosition().dst(origin) < radius }.forEach {
            val falloff = 1f - it.pixelPosition().dst(origin) / radius
            hit(it, 55f + falloff * if (power == Power.TITAN_BREAKER) 150f else 95f)
            val push = Vector2(it.pixelPosition()).sub(origin).nor().scl(4f + 6f * falloff)
            if (it.body.type == BodyDef.BodyType.DynamicBody) it.body.applyLinearImpulse(push, it.body.worldCenter, true)
        }
        if (power == Power.FORKSTORM) repeat(2) { i ->
            val fragment = createCircle(origin.x + 15f * i, origin.y + 20f, 12f, .25f, Kind.PROJECTILE, 1f, Color("f5b82e")) as ProjectileEntity
            fragment.activated = true; fragment.body.linearVelocity = Vector2(6f + i * 2f, 4f + i * 2f); projectile = fragment
        }
        debris += Fragment(origin.x, origin.y, radius, 0.38f, Color("f5fdff"))
    }
    fun update(delta: Float) {
        if (phase == BattlePhase.PAUSED || phase == BattlePhase.VICTORY || phase == BattlePhase.DEFEAT) return
        phaseElapsed += delta
        if (phase == BattlePhase.INTRO && phaseElapsed > .8f) { phase = BattlePhase.READY; phaseElapsed = 0f }
        accumulator = (accumulator + delta.coerceAtMost(.10f)).coerceAtMost(.10f)
        var steps = 0
        while (accumulator >= 1f / 60f && steps++ < 6) { world.step(1f / 60f, 8, 3); accumulator -= 1f / 60f }
        if (pending.isNotEmpty()) {
            pending.forEach { entity -> if (!entity.destroyed) return@forEach; if (!entity.body.isDestroyedSafe()) world.destroyBody(entity.body); entities.remove(entity) }
            pending.clear()
        }
        debris.removeAll { it.update(delta) }
        projectile?.let { p -> if (p.destroyed || p.pixelPosition().y < -80f || p.pixelPosition().x > 2050f) { if (!p.destroyed) destroy(p); projectile = null } }
        if (enemiesLeft == 0) { phase = BattlePhase.VICTORY; return }
        if (phase == BattlePhase.FLYING && projectile == null) { phase = BattlePhase.SETTLING; settleElapsed = 0f }
        if (phase == BattlePhase.SETTLING) {
            settleElapsed += delta
            val moving = entities.any { !it.destroyed && it.body.type == BodyDef.BodyType.DynamicBody && it.body.linearVelocity.len2() > .08f }
            if (!moving && settleElapsed > .65f) phase = if (castsLeft > 0) BattlePhase.READY else BattlePhase.DEFEAT
            if (settleElapsed > 4f) phase = if (castsLeft > 0) BattlePhase.READY else BattlePhase.DEFEAT
        }
    }
    private fun Body.isDestroyedSafe(): Boolean = try { position; false } catch (_: Exception) { true }
    private fun hit(entity: BattleEntity, damage: Float) {
        if (entity.kind == Kind.GROUND || entity.kind == Kind.PROJECTILE || entity.destroyed) return
        entity.health -= damage
        if (entity.health <= 0f) destroy(entity)
    }
    private fun destroy(entity: BattleEntity) {
        if (entity.destroyed) return
        entity.destroyed = true; pending += entity
        val at = entity.pixelPosition()
        repeat(if (entity.kind == Kind.ENEMY) 9 else 5) { i -> debris += Fragment(at.x, at.y, 4f + i, .5f + i * .03f, entity.color) }
    }
    private fun makeGround() {
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.StaticBody; position.set(9.6f, 1.0f) })
        val shape = PolygonShape(); shape.setAsBox(12f, .25f); body.createFixture(shape, 1f); shape.dispose()
        entities += BattleEntity(body, Kind.GROUND, 999f, 0f, 0f, Color("153d86"))
    }
    private fun makeFortress() {
        val rows = definition.blockRows
        repeat(rows) { row ->
            val count = 4 - row.coerceAtMost(2)
            repeat(count) { column ->
                val x = 1340f + column * 104f + (if (row % 2 == 1) 46f else 0f)
                val y = 160f + row * 100f
                createBox(x, y, 88f, 76f, 3.2f + row * .5f, Kind.BLOCK, 80f + row * 30f, if (row % 2 == 0) Color("eaf3fa") else Color("f5b82e"))
            }
        }
        repeat(definition.enemyCount) { i -> createCircle(1540f + i * 115f, 190f + rows * 95f, 38f, .8f, Kind.ENEMY, 115f, Color("7440d5")) }
    }
    private fun createBox(x: Float, y: Float, w: Float, h: Float, density: Float, kind: Kind, hp: Float, color: Color): BattleEntity {
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody; position.set(x / PPM, y / PPM); angularDamping = .5f })
        val shape = PolygonShape(); shape.setAsBox(w / 2 / PPM, h / 2 / PPM)
        body.createFixture(FixtureDef().apply { this.shape = shape; this.density = density; friction = .65f; restitution = .05f }); shape.dispose()
        return BattleEntity(body, kind, hp, w, h, color).also { body.userData = it; entities += it }
    }
    private fun createCircle(x: Float, y: Float, radius: Float, density: Float, kind: Kind, hp: Float, color: Color): BattleEntity {
        val body = world.createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody; position.set(x / PPM, y / PPM); linearDamping = .05f; angularDamping = .2f })
        val shape = CircleShape(); shape.radius = radius / PPM
        body.createFixture(FixtureDef().apply { this.shape = shape; this.density = density; friction = .35f; restitution = .25f }); shape.dispose()
        val entity = if (kind == Kind.PROJECTILE) ProjectileEntity(body, hp, radius * 2, radius * 2, color) else BattleEntity(body, kind, hp, radius * 2, radius * 2, color)
        body.userData = entity; entities += entity; return entity
    }
    fun render(canvas: GameCanvas) {
        val s = canvas.shapes
        // Storm altar and original Zeus shape.
        s.begin(ShapeRenderer.ShapeType.Filled); s.color = Color("93aac1"); s.circle(270f, 180f, 104f); s.color = Color("f5b82e"); s.circle(270f, 180f, 72f)
        s.color = Color("eaf3fa"); s.circle(218f, 340f, 47f); s.rect(182f, 222f, 75f, 95f); s.color = Color("22d9ff"); s.circle(305f, 405f, 28f); s.end()
        entities.filter { it.kind != Kind.GROUND && !it.destroyed }.forEach { entity ->
            val p = entity.pixelPosition(); s.begin(ShapeRenderer.ShapeType.Filled); s.color = entity.color
            if (entity.width == entity.height) s.circle(p.x, p.y, entity.width / 2f) else s.rect(p.x - entity.width / 2f, p.y - entity.height / 2f, entity.width, entity.height)
            if (entity.kind == Kind.ENEMY) { s.color = Color("f5fdff"); s.circle(p.x - 11f, p.y + 8f, 9f); s.circle(p.x + 11f, p.y + 8f, 9f); s.color = Color("081c42"); s.circle(p.x - 10f, p.y + 8f, 3f); s.circle(p.x + 10f, p.y + 8f, 3f) }
            s.end()
        }
        debris.forEach { it.render(s, reducedFlashes) }
    }
    fun drawTrajectory(canvas: GameCanvas, origin: Vector2, drag: Vector2) {
        if (drag.len() < 2f) return
        val velocity = Vector2(drag).nor().scl(10f + 16f * (drag.len() / 220f))
        val s = canvas.shapes; s.begin(ShapeRenderer.ShapeType.Filled); s.color = Color("22d9ff")
        repeat(18) { i -> val t = i * .10f; s.circle(origin.x + velocity.x * PPM * t, origin.y + velocity.y * PPM * t - 490f * t * t, 5f) }; s.end()
    }
    fun dispose() { world.dispose() }
}

private enum class Kind { GROUND, BLOCK, ENEMY, PROJECTILE }
private open class BattleEntity(val body: Body, val kind: Kind, var health: Float, val width: Float, val height: Float, val color: Color) {
    var destroyed = false
    fun pixelPosition(): Vector2 = Vector2(body.position.x * PPM, body.position.y * PPM)
}
private class ProjectileEntity(body: Body, health: Float, width: Float, height: Float, color: Color) : BattleEntity(body, Kind.PROJECTILE, health, width, height, color) { var activated = false }
private class Fragment(private val x: Float, private val y: Float, private val size: Float, private var life: Float, private val color: Color) {
    fun update(delta: Float): Boolean { life -= delta; return life <= 0f }
    fun render(s: ShapeRenderer, reduced: Boolean) { if (!reduced) { s.begin(ShapeRenderer.ShapeType.Filled); s.color = Color(color).also { it.a = life.coerceIn(0f, 1f) }; s.circle(x, y, size * life); s.end() } }
}
