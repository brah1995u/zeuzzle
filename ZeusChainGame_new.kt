package studio.cortex.zeuschain.core

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.utils.TimeUtils
import studio.cortex.zeuschain.TileRank

open class ZeusChainGame(val platform:PlatformServices=NoPlatformServices,private val qaStart:String?=null):Game(){
    lateinit var assets:Assets;lateinit var progress:ProgressGateway;lateinit var audio:Audio
    var selectedLevel=1
    var returnToPausedGame=false
    var pendingResultReward=false
    private var lastBackAt=0L
override fun create(){Gdx.input.setCatchKey(Input.Keys.BACK,true);Gdx.input.inputProcessor=object:InputAdapter(){override fun keyDown(keycode:Int):Boolean{if(keycode!=Input.Keys.BACK)return false;handleSystemBack();return true}};assets=Assets();progress=ProgressGateway();audio=Audio(progress);if(qaStart=="temple_gameplay")showTempleGameplay() else showTempleMenu()}
    fun handleSystemBack(){val now=TimeUtils.millis();if(now-lastBackAt<400L)return;lastBackAt=now;(screen as? AppScreen)?.onSystemBack()?:Gdx.app.exit()}
    private fun route(next:Screen){val old=screen;screen=next;if(old!==next)old?.dispose()}
    fun showSplash()=route(SplashScreen(this))
    fun showMenu(){audio.playMusic("menu");route(MenuScreen(this))}
fun showTempleMenu(){audio.playMusic("menu");route(TempleMenuScreen(this))}
fun showTempleSettings(){route(TempleSettingsScreen(this))}
fun showTempleGameplay(){audio.playMusic("battle");route(TempleGameplayScreen(this))}
    fun showTempleResult(won:Boolean,height:Int,score:Int,coins:Int)=route(TempleResultScreen(this,won,height,score,coins))
    fun showMap()=route(MapScreen(this))
    fun showPreLevel(level:Int){selectedLevel=level;route(PreLevelScreen(this,level))}
    fun showGame(level:Int=selectedLevel,restart:Boolean=false){selectedLevel=level;returnToPausedGame=false;audio.playMusic("battle");route(GameplayScreen(this,level))}
    fun showEndlessGame(){returnToPausedGame=false;audio.playMusic("battle");route(GameplayScreen(this,25,endless=true))}
    fun showEndlessResult(score:Int,maxTilePower:Int)=route(EndlessResultScreen(this,score,maxTilePower))
    fun showCrystalStorm(qaChaos:Boolean=false){audio.playMusic("battle");route(CrystalStormScreen(this,qaChaos))}
    fun showResult(level:Int,won:Boolean,score:Int,stars:Int){pendingResultReward=true;if(won)audio.sfx("reward");route(ResultScreen(this,level,won,score,stars))}
    fun showSettings(fromPause:Boolean=false){returnToPausedGame=fromPause;route(SettingsScreen(this))}
    fun showAbout()=route(AboutScreen(this))
    fun showPrivacy()=route(PrivacyScreen(this))
    fun showShop()=route(ShopScreen(this))
    fun showAchievements()=route(AchievementsScreen(this))
    fun showLeaderboard()=route(LeaderboardScreen(this))
    fun showDaily()=route(DailyRewardScreen(this))
    fun showComplete()=route(CompleteScreen(this))
    fun haptic(type:Haptic){if(progress.data.settings.haptics)platform.haptic(type)}
    override fun pause(){progress.save();audio.pause();super.pause()}
    override fun resume(){super.resume();audio.resume()}
    override fun dispose(){progress.save();screen?.dispose();audio.dispose();assets.dispose()}
}
