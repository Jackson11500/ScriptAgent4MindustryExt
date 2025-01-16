@file:Depends("wayzer/user/achievement", "成就")

package mapScript

import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import coreMindustry.lib.listen
import kotlinx.coroutines.Dispatchers
import mapScript.lib.modeIntroduce
import mindustry.ai.types.MissileAI
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.entities.units.StatusEntry
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Iconc
import mindustry.gen.Player
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import wayzer.lib.dao.PlayerData
import kotlin.math.pow

val achievement = contextScript<wayzer.user.Achievement>()

fun Player.achievement(name: String, exp: Int, broadcast: Boolean = false) {
    val profile = PlayerData[uuid()].profile
    if (profile != null)
        achievement.finishAchievement(profile, name, exp, broadcast)
}

/**@author xkldklp */
name = "幽默脚本"

modeIntroduce(
    "幽默之域", "[cyan]幽默描述"
)


listen<EventType.UnitDestroyEvent> { e ->
    Call.label("RIP 幽默${e.unit.type.localizedName}", e.unit.maxHealth / 400f, e.unit.x, e.unit.y, )
}
listen<EventType.BlockDestroyEvent> { e ->
   if (e.tile.block().health >= 150)
    Call.label("RIP 幽默${e.tile.block().localizedName}", e.tile.block().health / (e.tile.block().size * 1f).pow(2) / 50f, e.tile.x * 8f, e.tile.y * 8f)
}

