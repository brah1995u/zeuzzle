package studio.cortex.templestack.core.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import studio.cortex.templestack.core.TempleStackGame
import studio.cortex.templestack.core.ui.ZeusBaseScreen
import studio.cortex.templestack.core.ui.ZeusPalette
import studio.cortex.templestack.engine.Modifier
import studio.cortex.templestack.engine.OlympusMiniGames
import studio.cortex.templestack.engine.PlacementGrade
import studio.cortex.templestack.engine.StarRules
import studio.cortex.templestack.engine.TempleCampaign
import studio.cortex.templestack.engine.TempleSnapshot
import studio.cortex.templestack.engine.TempleStackSession
import kotlin.math.sin

private fun TempleStackGame.go(next: Screen) { setScreen(next) }
private fun ZeusBaseScreen.home() = app.go(MainMenuScreen(app))

class SplashScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    override fun draw(delta: Float) {
        backdrop(app.assets.menu, .08f)
        label("ZEUS", Rectangle(80f, 1470f, 920f, 160f), display, ZeusPalette.gold)
        label("TEMPLE STACK", Rectangle(130f, 1370f, 820f, 94f), heading, ZeusPalette.marble)
        label("BUILD THE CROWN OF OLYMPUS", Rectangle(150f, 1285f, 780f, 55f), body, ZeusPalette.cyan)
        val width = 640f * MathUtils.clamp(elapsed / 1.55f, 0f, 1f)
        card(Rectangle(220f, 280f, 640f, 25f), ZeusPalette.gold, ZeusPalette.navy)
        shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled); shapes.color = ZeusPalette.cyan; shapes.rect(228f, 288f, width - 16f, 9f); shapes.end()
        if (elapsed > 1.8f || Gdx.input.justTouched()) home()
    }
    override fun onBack() = home()
}

class MainMenuScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    private var more = false
    override fun draw(delta: Float) {
        backdrop(app.assets.menu, .12f)
        statPill("♥ ${app.progress.lives}/5", 42f, 2028f, 195f); statPill("◉ ${app.progress.coins}", 255f, 2028f, 250f); statPill("◆ ${app.progress.crystals}", 523f, 2028f, 220f)
        action("MORE", Rectangle(782f, 2028f, 256f, 62f)) { more = !more }
        label("ZEUS", Rectangle(120f, 1580f, 840f, 140f), display, ZeusPalette.gold)
        label("TEMPLE STACK", Rectangle(160f, 1490f, 760f, 80f), heading, ZeusPalette.marble)
        label("THE CROWN AWAITS YOUR HAND", Rectangle(155f, 1425f, 770f, 48f), body, ZeusPalette.cyan)
        if (more) {
            card(Rectangle(660f, 1690f, 354f, 270f), ZeusPalette.gold, ZeusPalette.navy)
            action("DAILY ALTAR", Rectangle(682f, 1870f, 310f, 62f)) { app.go(DailyScreen(app)) }
            action("MINI GAMES", Rectangle(682f, 1788f, 310f, 62f)) { app.go(MiniGamesScreen(app)) }
            action("SETTINGS", Rectangle(682f, 1706f, 310f, 62f)) { app.go(SettingsScreen(app)) }
        }
        action("PLAY", Rectangle(205f, 750f, 670f, 292f), true) { app.go(PreLevelScreen(app, app.progress.unlockedLevel, false)) }
        action("ENDLESS OLYMPUS", Rectangle(180f, 634f, 720f, 94f)) { app.go(PreLevelScreen(app, 1, true)) }
        action("TEMPLE PATH", Rectangle(180f, 522f, 720f, 80f)) { app.go(TemplePathScreen(app)) }
        action("SHOP", Rectangle(52f, 330f, 300f, 82f)) { app.go(ShopScreen(app)) }
        action("ACHIEVEMENTS", Rectangle(390f, 330f, 300f, 82f)) { app.go(AchievementsScreen(app)) }
        action("LEADERBOARD", Rectangle(728f, 330f, 300f, 82f)) { app.go(LeaderboardScreen(app)) }
    }
    override fun onBack() { Gdx.app.exit() }
}

class TemplePathScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    private var world = 1
    override fun draw(delta: Float) {
        backdrop(app.assets.menu, .18f); nav({ home() }, { home() }, "TEMPLE PATH")
        label("WORLD $world  ·  ${TempleCampaign.level((world - 1) * 10 + 1).worldName}", Rectangle(90f, 1920f, 900f, 52f), body, ZeusPalette.cyan)
        repeat(10) { slot ->
            val id = (world - 1) * 10 + slot + 1; val x = if (slot % 2 == 0) 300f else 780f; val y = 1780f - slot * 150f
            val open = id <= app.progress.unlockedLevel; val boss = slot == 9
            val r = Rectangle(x - if (boss) 78f else 61f, y - if (boss) 78f else 61f, if (boss) 156f else 122f, if (boss) 156f else 122f)
            card(r, if (open) ZeusPalette.gold else ZeusPalette.muted, if (boss) Color.valueOf("4B2A71") else ZeusPalette.blue)
            label(if (open) "$id" else "LOCK", r, heading, if (open) Color.WHITE else ZeusPalette.muted)
            if (open) tap(r) { app.go(PreLevelScreen(app, id, false)) }
        }
        action("‹ WORLD", Rectangle(58f, 190f, 300f, 74f), false, world > 1) { world-- }
        action("WORLD ›", Rectangle(722f, 190f, 300f, 74f), false, world < 6) { world++ }
    }
    override fun onBack() = home()
}

class PreLevelScreen(app: TempleStackGame, private val levelId: Int, private val endless: Boolean) : ZeusBaseScreen(app) {
    override fun draw(delta: Float) {
        val level = TempleCampaign.level(levelId); backdrop(app.assets.menu, .2f); nav({ app.go(TemplePathScreen(app)) }, { home() }, if (endless) "ENDLESS OLYMPUS" else "LEVEL $levelId")
        card(Rectangle(105f, 1220f, 870f, 590f), ZeusPalette.gold, Color.valueOf("0A2E66"))
        label(if (endless) "ENDLESS OLYMPUS" else level.worldName, Rectangle(160f, 1670f, 760f, 55f), heading, ZeusPalette.gold)
        label(if (endless) "THE SKY HAS NO CEILING" else level.name, Rectangle(145f, 1585f, 790f, 60f), body, ZeusPalette.marble)
        label(if (endless) "BUILD AS HIGH AS YOU CAN" else "TARGET  ${level.targetHeight}  TEMPLE SEGMENTS", Rectangle(150f, 1475f, 780f, 48f), body, ZeusPalette.cyan)
        label(if (endless) "PERFECT DROPS MAKE THE CROWN GLOW" else "MODIFIER  ${level.modifier.name.replace('_', ' ')}", Rectangle(150f, 1395f, 780f, 44f), body, Color.WHITE)
        label("★ STABLE BUILD   ★★ LOW STRESS   ★★★ PERFECT MASON", Rectangle(140f, 1288f, 800f, 48f), body, ZeusPalette.gold)
        action("BUILD THE TEMPLE", Rectangle(205f, 930f, 670f, 292f), true) { app.go(GameplayScreen(app, levelId, endless)) }
    }
    override fun onBack() = app.go(TemplePathScreen(app))
}

class GameplayScreen(app: TempleStackGame, private val levelId: Int, private val endless: Boolean, savedSession: TempleStackSession? = null) : ZeusBaseScreen(app) {
    private val level = TempleCampaign.level(levelId)
    private var session = savedSession ?: TempleStackSession(if (level.modifier == Modifier.NARROW_FOUNDATION) 580f else 720f)
    private var resultShown = false
    private var feedback = ""
    override fun draw(delta: Float) {
        backdrop(app.assets.gameplay, .05f); val snapshot = session.snapshot()
        action("‹", Rectangle(42f, 2018f, 104f, 92f)) { app.go(PreLevelScreen(app, levelId, endless)) }
        statPill(if (endless) "HEIGHT ${snapshot.height}" else "HEIGHT ${snapshot.height}/${level.targetHeight}", 174f, 2028f, 360f)
        statPill("SCORE ${snapshot.score}", 550f, 2028f, 260f); action("Ⅱ", Rectangle(934f, 2018f, 104f, 92f)) { app.go(PauseScreen(app, levelId, endless, session)) }
        drawTemple(snapshot)
        if (!snapshot.collapsed && !resultShown) {
            val center = 540f + sin(elapsed * level.speed) * 300f; val topY = segmentY(snapshot.height + 1)
            drawSegment(center, session.top.width, topY, active = true, golden = (snapshot.height + 1) % 5 == 0)
        }
        label(feedback, Rectangle(100f, 1420f, 880f, 52f), heading, if (feedback == "PERFECT!") ZeusPalette.gold else ZeusPalette.danger)
        booster("ALIGN", 52f, app.progress.aidInventory["ALIGNMENT"] ?: 0) { feedback = "ALIGNMENT READY" }
        booster("CALM", 390f, app.progress.aidInventory["CALM WIND"] ?: 0) { feedback = "WIND CALMED" }
        booster("MASON", 728f, app.progress.aidInventory["MASON'S BLESSING"] ?: 0) { feedback = "MASON BLESSING" }
        if ((snapshot.height >= level.targetHeight && !endless || snapshot.collapsed) && !resultShown) finish(snapshot)
    }
    private fun drawTemple(snapshot: TempleSnapshot) {
        snapshot.blocks.forEachIndexed { index, block -> drawSegment(block.center, block.width, segmentY(index), golden = block.golden, active = false) }
    }
    // The art has intentional transparent breathing room above/below the marble. Its draw box
    // overlaps while the visible stone edges meet, so a temple never appears to float.
    private fun segmentY(index: Int) = 360f + index * 100f
    private fun drawSegment(center: Float, width: Float, y: Float, golden: Boolean, active: Boolean) {
        val h = 190f; batch.begin(); batch.color = if (golden) ZeusPalette.gold else if (active) ZeusPalette.cyan else Color.WHITE
        batch.draw(app.assets.foundation, center - width / 2f, y, width, h); batch.color = Color.WHITE; batch.end()
    }
    private fun booster(name: String, x: Float, count: Int, tap: () -> Unit) {
        card(Rectangle(x, 130f, 300f, 142f), ZeusPalette.cyan, ZeusPalette.blue)
        label(name, Rectangle(x + 15f, 205f, 270f, 42f), body, ZeusPalette.marble); label("×$count", Rectangle(x + 15f, 150f, 270f, 42f), heading, ZeusPalette.gold)
        tap(Rectangle(x, 130f, 300f, 142f), count > 0, tap)
    }
    override fun onCanvasUp(x: Float, y: Float): Boolean {
        if (x !in 55f..1025f || y !in 300f..1920f || session.snapshot().collapsed) return false
        val center = 540f + sin(elapsed * level.speed) * 300f; val next = session.height + 1
        val golden = next % 5 == 0; val outcome = session.drop(center, session.top.width, golden, golden)
        feedback = when (outcome.grade) { PlacementGrade.PERFECT -> "PERFECT!"; PlacementGrade.STABLE -> "STABLE"; PlacementGrade.CROOKED -> "CROOKED"; PlacementGrade.COLLAPSE -> "TEMPLE COLLAPSED" }
        return true
    }
    private fun finish(snapshot: TempleSnapshot) {
        resultShown = true
        if (!snapshot.collapsed && !endless) app.progress = app.progress.complete(level, snapshot)
        if (endless && !snapshot.collapsed) app.progress = app.progress.copy(endlessBestHeight = maxOf(app.progress.endlessBestHeight, snapshot.height), endlessBestScore = maxOf(app.progress.endlessBestScore, snapshot.score))
        app.save(); app.go(ResultScreen(app, levelId, endless, snapshot, !snapshot.collapsed))
    }
    override fun onBack() = app.go(PauseScreen(app, levelId, endless, session))
}

class PauseScreen(app: TempleStackGame, private val levelId: Int, private val endless: Boolean, private val session: TempleStackSession) : ZeusBaseScreen(app) {
    override fun draw(delta: Float) { backdrop(app.assets.gameplay, .55f); card(Rectangle(115f, 720f, 850f, 760f), ZeusPalette.gold, ZeusPalette.navy); label("TEMPLE PAUSED", Rectangle(170f, 1320f, 740f, 74f), heading, ZeusPalette.gold); action("RESUME", Rectangle(190f, 1140f, 700f, 100f), true) { app.go(GameplayScreen(app, levelId, endless, session)) }; action("RESTART", Rectangle(190f, 1010f, 700f, 88f)) { app.go(GameplayScreen(app, levelId, endless)) }; action("HOME", Rectangle(190f, 880f, 700f, 88f)) { home() } }
    override fun onBack() = app.go(GameplayScreen(app, levelId, endless, session))
}

class ResultScreen(app: TempleStackGame, private val levelId: Int, private val endless: Boolean, private val snapshot: TempleSnapshot, private val won: Boolean) : ZeusBaseScreen(app) {
    override fun draw(delta: Float) { backdrop(app.assets.gameplay, .58f); card(Rectangle(105f, 650f, 870f, 860f), if (won) ZeusPalette.gold else ZeusPalette.danger, ZeusPalette.navy); label(if (won) "TEMPLE COMPLETE" else "TEMPLE FELL", Rectangle(160f, 1350f, 760f, 80f), heading, if (won) ZeusPalette.gold else ZeusPalette.danger); label("HEIGHT ${snapshot.height}   SCORE ${snapshot.score}", Rectangle(150f, 1240f, 780f, 54f), body, Color.WHITE); label(if (won) "★  ★  ★" else "TRY AGAIN, ARCHITECT", Rectangle(170f, 1140f, 740f, 65f), heading, ZeusPalette.gold); action(if (won) "NEXT LEVEL" else "TRY AGAIN", Rectangle(180f, 940f, 720f, 105f), true) { app.go(PreLevelScreen(app, if (won && levelId < 60) levelId + 1 else levelId, endless)) }; action("TEMPLE PATH", Rectangle(180f, 805f, 720f, 85f)) { app.go(TemplePathScreen(app)) }; action("HOME", Rectangle(180f, 685f, 720f, 75f)) { home() } }
    override fun onBack() = home()
}

class ShopScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    private var themes = true
    override fun draw(delta: Float) { backdrop(app.assets.menu, .18f); nav({ home() }, { home() }, "OLYMPUS FORGE"); action("THEMES", Rectangle(70f, 1880f, 455f, 72f), themes) { themes = true }; action("DIVINE AID", Rectangle(555f, 1880f, 455f, 72f), !themes) { themes = false }; repeat(3) { i -> val y = 1590f - i * 330f; card(Rectangle(70f, y, 940f, 270f), if (i == 0) ZeusPalette.gold else ZeusPalette.cyan, ZeusPalette.navy); label(if (themes) listOf("DAWN MARBLE", "STORM LAPI S", "SUNSET GOLD")[i].replace(" ", "") else listOf("ALIGNMENT", "CALM WIND", "MASON BLESSING")[i], Rectangle(110f, y + 175f, 520f, 55f), heading, ZeusPalette.gold, com.badlogic.gdx.utils.Align.left); label(if (themes) "A temple finish forged for Olympus." else "One use during a run.", Rectangle(110f, y + 113f, 520f, 42f), body, Color.WHITE, com.badlogic.gdx.utils.Align.left); action(if (i == 0 && themes) "EQUIPPED" else "BUY ${50 + i * 45}", Rectangle(700f, y + 82f, 255f, 86f), i != 0 || !themes, true) { if (!themes) { app.progress = app.progress.buyAid(listOf("ALIGNMENT", "CALM WIND", "MASON'S BLESSING")[i], 50 + i * 45) ?: app.progress; app.save() } } } }
    override fun onBack() = home()
}

class AchievementsScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    private var campaign = true
    override fun draw(delta: Float) { backdrop(app.assets.menu, .2f); nav({ home() }, { home() }, "ACHIEVEMENTS"); action("CAMPAIGN", Rectangle(70f, 1880f, 455f, 72f), campaign) { campaign = true }; action("MASTERY", Rectangle(555f, 1880f, 455f, 72f), !campaign) { campaign = false }; repeat(4) { i -> val y = 1590f - i * 300f; card(Rectangle(70f, y, 940f, 242f), ZeusPalette.gold, ZeusPalette.navy); label("★", Rectangle(100f, y + 70f, 120f, 100f), display, ZeusPalette.gold); label(if (campaign) listOf("FIRST FOUNDATION", "TOWER OF TEN", "GOLDEN RHYTHM", "CROWN MAKER")[i] else listOf("PERFECT HAND", "ENDLESS SKY", "CALM ARCHITECT", "DIVINE COLLECTOR")[i], Rectangle(245f, y + 152f, 480f, 45f), heading, Color.WHITE, com.badlogic.gdx.utils.Align.left); label("${(i + 1) * 2}/${(i + 1) * 4} progress", Rectangle(245f, y + 95f, 480f, 38f), body, ZeusPalette.cyan, com.badlogic.gdx.utils.Align.left); action(if (i == 0) "CLAIM" else "LOCKED", Rectangle(755f, y + 80f, 200f, 72f), i == 0, i == 0) {} } }
    override fun onBack() = home()
}

class LeaderboardScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    private var tab = 0
    override fun draw(delta: Float) { backdrop(app.assets.menu, .22f); nav({ home() }, { home() }, "HALL OF HEROES"); listOf("CAMPAIGN", "ENDLESS", "PERFECT").forEachIndexed { i, t -> action(t, Rectangle(55f + i * 330f, 1880f, 310f, 70f), tab == i) { tab = i } }; card(Rectangle(70f, 1690f, 940f, 145f), ZeusPalette.gold, ZeusPalette.navy); label("YOUR LOCAL RECORD", Rectangle(110f, 1774f, 560f, 34f), body, ZeusPalette.cyan, com.badlogic.gdx.utils.Align.left); label("#4     ${if (tab == 0) app.progress.stars.values.sum() else app.progress.endlessBestHeight}", Rectangle(110f, 1710f, 560f, 55f), heading, ZeusPalette.gold, com.badlogic.gdx.utils.Align.left); val names = listOf("ATHENA", "APOLLO", "ARTEMIS", "YOU", "HERMES", "IRIS", "HELIOS"); names.forEachIndexed { i, name -> val y = 1480f - i * 150f; card(Rectangle(70f, y, 940f, 112f), if (name == "YOU") ZeusPalette.gold else ZeusPalette.cyan, ZeusPalette.navy); label("${i + 1}", Rectangle(95f, y + 25f, 80f, 54f), heading, ZeusPalette.gold); label(name, Rectangle(205f, y + 38f, 410f, 42f), heading, Color.WHITE, com.badlogic.gdx.utils.Align.left); label("${920 - i * 73}", Rectangle(760f, y + 38f, 190f, 42f), body, ZeusPalette.marble) } }
    override fun onBack() = home()
}

class DailyScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    override fun draw(delta: Float) { backdrop(app.assets.menu, .18f); nav({ home() }, { home() }, "DAILY ALTAR"); label("SEVEN DAYS OF DIVINE GIFTS", Rectangle(100f, 1900f, 880f, 45f), body, ZeusPalette.cyan); val active = app.progress.dailyIndex; repeat(7) { i -> val x = 90f + (i % 4) * 245f; val y = 1540f - (i / 4) * 290f; card(Rectangle(x, y, 205f, 220f), if (i == active) ZeusPalette.gold else ZeusPalette.cyan, ZeusPalette.navy); label("DAY ${i + 1}", Rectangle(x, y + 145f, 205f, 42f), body, Color.WHITE); label("+${40 + i * 20}", Rectangle(x, y + 78f, 205f, 46f), heading, ZeusPalette.gold) }; val today = System.currentTimeMillis() / 86_400_000L; val ready = app.progress.dailyDay != today; action(if (ready) "CLAIM TODAY'S GIFT" else "CLAIMED — TOMORROW", Rectangle(140f, 620f, 800f, 110f), ready, ready) { app.progress = app.progress.claimDaily(today) ?: app.progress; app.save() } }
    override fun onBack() = home()
}

class SettingsScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    override fun draw(delta: Float) { backdrop(app.assets.menu, .22f); nav({ home() }, { home() }, "SETTINGS"); val values = listOf(app.progress.music, app.progress.sound, app.progress.haptics, app.progress.reducedFlashes, app.progress.highContrast); listOf("MUSIC", "SOUND EFFECTS", "HAPTICS", "REDUCED FLASHES", "HIGH CONTRAST").forEachIndexed { i, name -> val y = 1730f - i * 220f; card(Rectangle(70f, y, 940f, 150f), ZeusPalette.cyan, ZeusPalette.navy); label(name, Rectangle(115f, y + 47f, 520f, 58f), heading, Color.WHITE, com.badlogic.gdx.utils.Align.left); action(if (values[i]) "ON" else "OFF", Rectangle(760f, y + 38f, 190f, 72f), values[i]) { app.progress = when (i) { 0 -> app.progress.copy(music = !app.progress.music); 1 -> app.progress.copy(sound = !app.progress.sound); 2 -> app.progress.copy(haptics = !app.progress.haptics); 3 -> app.progress.copy(reducedFlashes = !app.progress.reducedFlashes); else -> app.progress.copy(highContrast = !app.progress.highContrast) }; app.save() } }; action("ABOUT & PRIVACY", Rectangle(210f, 450f, 660f, 82f)) { app.go(AboutScreen(app)) } }
    override fun onBack() = home()
}

class AboutScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    override fun draw(delta: Float) { backdrop(app.assets.menu, .24f); nav({ app.go(SettingsScreen(app)) }, { home() }, "ABOUT"); card(Rectangle(90f, 800f, 900f, 830f), ZeusPalette.gold, ZeusPalette.navy); label("ZEUS: TEMPLE STACK", Rectangle(140f, 1460f, 800f, 72f), heading, ZeusPalette.gold); label("OFFLINE ARCADE", Rectangle(140f, 1375f, 800f, 40f), body, ZeusPalette.cyan); listOf("YOUR PROGRESS STAYS ON THIS DEVICE.", "NO ACCOUNT / NO ADS / NO TRACKING.", "LOCAL LEGENDS ARE OFFLINE RIVALS.").forEachIndexed { i, s -> label(s, Rectangle(135f, 1230f - i * 105f, 810f, 48f), body, Color.WHITE) } }
    override fun onBack() = app.go(SettingsScreen(app))
}

class MiniGamesScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    override fun draw(delta: Float) { backdrop(app.assets.menu, .18f); nav({ home() }, { home() }, "ORACLE ARCADE"); card(Rectangle(80f, 1230f, 920f, 500f), ZeusPalette.gold, ZeusPalette.navy); label("THUNDER REELS", Rectangle(140f, 1555f, 800f, 70f), heading, ZeusPalette.gold); label("THREE DIVINE SIGNS. ONE REWARD.", Rectangle(140f, 1465f, 800f, 45f), body, Color.WHITE); action("PLAY REELS", Rectangle(175f, 1300f, 730f, 90f), true) { app.go(ReelsScreen(app)) }; card(Rectangle(80f, 610f, 920f, 500f), ZeusPalette.cyan, ZeusPalette.navy); label("FATE CHESTS", Rectangle(140f, 935f, 800f, 70f), heading, ZeusPalette.gold); label("PICK ONE CHEST AND TAKE ITS BLESSING.", Rectangle(140f, 845f, 800f, 45f), body, Color.WHITE); action("CHOOSE A CHEST", Rectangle(175f, 680f, 730f, 90f), true) { app.go(ChestsScreen(app)) } }
    override fun onBack() = home()
}

class ReelsScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    private var result: OlympusMiniGames.SlotResult? = null
    override fun draw(delta: Float) { backdrop(app.assets.menu, .22f); nav({ app.go(MiniGamesScreen(app)) }, { home() }, "THUNDER REELS"); card(Rectangle(70f, 930f, 940f, 650f), ZeusPalette.gold, ZeusPalette.navy); label("SPIN THE ORACLE", Rectangle(150f, 1440f, 780f, 60f), heading, ZeusPalette.gold); (result?.reels ?: listOf("⚡", "?", "⚡")).forEachIndexed { i, sign -> card(Rectangle(125f + i * 300f, 1120f, 230f, 210f), ZeusPalette.cyan, ZeusPalette.blue); label(sign, Rectangle(125f + i * 300f, 1150f, 230f, 150f), display, ZeusPalette.gold) }; label(result?.headline ?: "THE SIGNS ARE WAITING", Rectangle(130f, 1035f, 820f, 48f), body, ZeusPalette.cyan); action("SPIN  ${OlympusMiniGames.slotCost} COINS", Rectangle(170f, 720f, 740f, 110f), true, app.progress.coins >= OlympusMiniGames.slotCost) { val spin = OlympusMiniGames.spin((System.currentTimeMillis() / 29L).toInt()); app.progress = app.progress.copy(coins = app.progress.coins - OlympusMiniGames.slotCost + spin.rewardCoins, crystals = app.progress.crystals + spin.rewardCrystals); app.save(); result = spin } }
    override fun onBack() = app.go(MiniGamesScreen(app))
}

class ChestsScreen(app: TempleStackGame) : ZeusBaseScreen(app) {
    private var opened: Int? = null
    override fun draw(delta: Float) { backdrop(app.assets.menu, .22f); nav({ app.go(MiniGamesScreen(app)) }, { home() }, "FATE CHESTS"); label("ONE CHOICE. ONE BLESSING.", Rectangle(130f, 1770f, 820f, 60f), heading, ZeusPalette.gold); repeat(3) { i -> val x = 65f + i * 345f; card(Rectangle(x, 1050f, 305f, 470f), if (opened == i) ZeusPalette.gold else ZeusPalette.cyan, ZeusPalette.navy); label(if (opened == i) "OPEN" else "CHEST ${i + 1}", Rectangle(x + 20f, 1310f, 265f, 70f), heading, ZeusPalette.gold); label(if (opened == i) "+${OlympusMiniGames.openChest(i).rewardCoins} COINS" else "TAP TO REVEAL", Rectangle(x + 20f, 1180f, 265f, 46f), body, Color.WHITE); action("", Rectangle(x, 1050f, 305f, 470f), false, opened == null && app.progress.coins >= OlympusMiniGames.chestCost) { val gift = OlympusMiniGames.openChest(i); app.progress = app.progress.copy(coins = app.progress.coins - OlympusMiniGames.chestCost + gift.rewardCoins); app.save(); opened = i } }; if (opened != null) action("BACK TO ARCADE", Rectangle(180f, 720f, 720f, 90f)) { app.go(MiniGamesScreen(app)) } }
    override fun onBack() = app.go(MiniGamesScreen(app))
}
