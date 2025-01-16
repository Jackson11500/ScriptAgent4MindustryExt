@file:Depends("wayzer/user/achievement", "成就")

package mapScript

import arc.util.Align
import arc.util.Time
import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import coreMindustry.lib.listen
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Player
import wayzer.lib.dao.PlayerData

/**@author xkldklp WayZer*/
name = "尸潮PVP"

val achievement = contextScript<wayzer.user.Achievement>()
fun Player.achievement(name: String, exp: Int) {
    val profile = PlayerData[uuid()].profile
    if (profile != null)
        achievement.finishAchievement(profile, name, exp, false)
}

onEnable {
    val startTime = Time.millis()
    val rawBlockHealth = Team.crux.rules().blockHealthMultiplier
    val rawUnitDamage = Team.crux.rules().unitDamageMultiplier
    loop(Dispatchers.game) {
        delay(2000)
        Team.crux.rules().apply {
            blockHealthMultiplier =
                (rawBlockHealth - Time.timeSinceMillis(startTime) / 1000 * 0.002f)
                    .coerceAtLeast(0.5f)
            unitDamageMultiplier = (rawUnitDamage - Time.timeSinceMillis(startTime) / 1000 * 0.001f).coerceAtLeast(0.5f)
        }
        Call.infoPopup(
            "[violet]红队建筑血量: [orange]${Team.crux.rules().blockHealthMultiplier}\n" +
                    "[violet]红队单位攻击: [orange]${Team.crux.rules().unitDamageMultiplier}",
            2.013f, Align.topLeft, 350, 0, 0, 0
        )
        Call.setRules(Vars.state.rules)
    }
}

listen<EventType.UnitDestroyEvent> { e ->
    if (e.unit.team != Team.crux) {
        e.unit.player?.achievement("[green][尸变之人]", 100)
        e.unit.type.spawn(Team.crux, e.unit.x, e.unit.y).health = e.unit.type.health * 0.4f
    }
}