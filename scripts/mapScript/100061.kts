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
name = "升级！"

modeIntroduce(
    "历战之域", "[cyan]单位击杀单位或建筑可升级!\n[white]${Iconc.unitObviate}[red]因anuke代码限制极难升级\n[yellow]导弹类单位仅可通过击杀单位升级"
)

fun Float.buildLineBar(
    length: Int = 20,
    max: Float = 20f,
    color: Pair<Pair<String, String>, String> = Pair(Pair("[yellow]", "[green]"), "[red]")
): String {
    val num = this
    return buildString {
        repeat(length) {
            append(
                "${
                    when {
                        num > it * (max / length) + max -> color.first.first
                        num > it * (max / length) -> color.first.second
                        else -> color.second
                    }
                }|"
            )
        }
    }
}

fun Float.format(i: Int = 2): String {
    return "%.${i}f".format(this)
}


data class UnitData(
    var exp: Float = 0f,
    var level: Int = 0
) {
    lateinit var unit: mindustry.gen.Unit

    fun nextLevelNeed(): Float {
        return levelNeed(level)
    }

    fun levelNeed(l: Int): Float {
        return (l + 1f).pow(2.5f) * unit.type.health / 2f
    }
}

onEnable {
    loop(Dispatchers.game) {
        Groups.player.forEach {
            Call.setHudText(it.con, buildString {
                appendLine(
                    "[white]${it.unit().type.emoji()} LV.${it.unit().data.level} ${
                        it.unit().data.exp.buildLineBar(
                            10,
                            it.unit().data.nextLevelNeed()
                        )
                    } [white]${it.unit().data.exp.format(1)}/${it.unit().data.nextLevelNeed().format(1)}"
                )
                append(
                    "[green]${Iconc.add} ${
                        it.unit().health.buildLineBar(
                            10,
                            it.unit().type.health
                        )
                    } [white]${it.unit().health.format(1)}/${it.unit().maxHealth.format(1)} ${
                        if (it.unit().statuses.size <= 20)
                            it.unit().statuses().joinToString("") { it.effect.emoji() }
                        else 
                            "[yellow]反正就是很多Buff!"
                    }"
                )
            })
        }
        delay(100)
    }
    loop(Dispatchers.game) {
        Groups.unit.forEach {
            if (it.speedMultiplier() >= 10) it.statuses.add(StatusEntry().set(StatusEffects.muddy, Float.POSITIVE_INFINITY))
            if (it.data.exp >= it.data.nextLevelNeed()) {
                it.data.exp -= it.data.nextLevelNeed()
                it.data.level++
                it.statuses.add(
                    StatusEntry().set(
                        listOf(
                            StatusEffects.overdrive,
                            StatusEffects.overclock,
                            StatusEffects.overclock,
                            StatusEffects.boss
                        ).random(), Float.POSITIVE_INFINITY
                    )
                )
                if (it.type == UnitTypes.obviate) {
                    it.player?.achievement("[green][逆天改命]", 100)
                }
            }
        }
        yield()
    }
    val missileBuffed = mutableListOf<mindustry.gen.Unit>()
    loop(Dispatchers.game) {
        Groups.unit.forEach {
            if (it !in missileBuffed && (it.controller() as? MissileAI)?.shooter != null) {
                val shooter = (it.controller() as MissileAI).shooter
                val statuses = shooter.statuses.toList()
                statuses.filter { it.effect in listOf(
                    StatusEffects.overdrive,
                    StatusEffects.overclock,
                    StatusEffects.boss
                )}.forEach { s ->
                    it.statuses.add(s)
                }
                missileBuffed.add(it)
            }
        }
        yield()
    }
}

val unitData by autoInit { mutableMapOf<mindustry.gen.Unit, UnitData>() }
val mindustry.gen.Unit.data get() = unitData.getOrPut(this) { UnitData() }.also { it.unit = this }

listen<EventType.UnitBulletDestroyEvent> { e ->
    val unit = e.unit
    var killer = e.bullet.owner as? mindustry.gen.Unit ?: return@listen
    if ((killer.controller() as? MissileAI)?.shooter != null) {
        killer = (killer.controller() as? MissileAI)!!.shooter
    }
    killer.data.exp += unit.maxHealth
}
listen<EventType.BuildingBulletDestroyEvent> { e ->
    val build = e.build
    var killer = e.bullet.owner as? mindustry.gen.Unit ?: return@listen
    if ((killer.controller() as? MissileAI)?.shooter != null) {
        killer = (killer.controller() as? MissileAI)!!.shooter
    }
    killer.data.exp += build.maxHealth * if (build is CoreBuild) 1f else 0.2f
}
