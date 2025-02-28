@file:Depends("wayzer/user/achievement", "成就")

package mapScript

import coreLibrary.lib.util.loop
import coreMindustry.lib.command
import coreMindustry.lib.game
import coreMindustry.lib.listen
import kotlinx.coroutines.Dispatchers
import mapScript.lib.modeIntroduce
import mindustry.ai.types.MissileAI
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.entities.units.StatusEntry
import mindustry.game.EventType
import mindustry.gen.*
import mindustry.type.StatusEffect
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
name = "升级！-精英"

modeIntroduce(
    "精英之域", "[cyan]单位击杀单位或建筑可升级至三级!\n[white]${Iconc.unitObviate}[red]因anuke代码限制极难升级\n[yellow]导弹类单位仅可通过击杀单位升级"
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
    var level: Int = 0,
    var lastDamage : mindustry.gen.Unit? = null,
    var dead: Boolean = false,
) {
    lateinit var unit: mindustry.gen.Unit


    fun nextLevelNeed(): Float {
        return levelNeed(level)
    }

    fun levelNeed(l: Int): Float {
        return unit.type.health
    }

    fun calculate() {
        if (dead) return
        dead = true
        if (lastDamage != null) {
            lastDamage!!.data.exp += unit.maxHealth
        }
    }
}
data class BuildingData(
    var lastDamage : mindustry.gen.Unit? = null,
    var dead: Boolean = false,
) {
    lateinit var build: Building

    fun calculate() {
        if (dead) return
        dead = true
        if (lastDamage != null) {
            lastDamage!!.data.exp += (if (build is CoreBuild) 2f else 0.2f) * build.maxHealth
        }
    }
}

onEnable {
    loop(Dispatchers.game) {
        Groups.player.forEach {
            Call.setHudText(it.con, buildString {
                if (it.unit().data.level < 3) {
                    appendLine(
                        "[white]${it.unit().type.emoji()} ${buildString { repeat(3) { i -> append(if (it.unit().data.level > i) "[yellow]${Iconc.star}" else "[white]${Iconc.star}") }}} ${
                            it.unit().data.exp.buildLineBar(
                                10,
                                it.unit().data.nextLevelNeed()
                            )
                        } [white]${it.unit().data.exp.format(1)}/${it.unit().data.nextLevelNeed().format(1)}"
                    )
                } else {
                    appendLine(
                        "[white]${it.unit().type.emoji()} [yellow]${Iconc.star}${Iconc.star}${Iconc.star} [yellow]MAX"
                    )
                }
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
            if (it.data.exp >= it.data.nextLevelNeed() && it.data.level < 3) {
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
                    StatusEffects.boss,
                    StatusEffects.muddy// blac修改 导弹限速
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

val buildData by autoInit { mutableMapOf<Building, BuildingData>() }
val Building.data get() = buildData.getOrPut(this) { BuildingData() }.also { it.build = this }



listen<EventType.UnitDamageEvent> { e ->
    val unit: mindustry.gen.Unit = e.unit
    val bullet: Bullet = e.bullet
    if (bullet.owner !is mindustry.gen.Unit) return@listen
    val damages = bullet.owner as mindustry.gen.Unit
    if ((damages.controller() as? MissileAI)?.shooter != null) {
        unit.data.lastDamage = (damages.controller() as MissileAI).shooter
    } else {
        unit.data.lastDamage = damages
    }
}
listen<EventType.UnitDestroyEvent> {
    it.unit.data.calculate()
}
listen<EventType.UnitBulletDestroyEvent> {
    if (it.bullet.owner is mindustry.gen.Unit) {
        val damages = it.bullet.owner as mindustry.gen.Unit
        if ((damages.controller() as? MissileAI)?.shooter != null) {
            it.unit.data.lastDamage = (damages.controller() as MissileAI).shooter
        } else {
            it.unit.data.lastDamage = damages
        }
        it.unit.data.calculate()
    }
}
listen<EventType.BuildDamageEvent> { e ->
    val build = e.build
    val bullet = e.source
    if (bullet.owner !is mindustry.gen.Unit) return@listen
    val damages = bullet.owner as mindustry.gen.Unit
    if ((damages.controller() as? MissileAI)?.shooter != null) {
        build.data.lastDamage = (damages.controller() as MissileAI).shooter
    } else {
        build.data.lastDamage = damages
    }
}
listen<EventType.BlockDestroyEvent> { e ->
    e.tile.build.data.calculate()
}
listen<EventType.BuildingBulletDestroyEvent> { e ->
    val build = e.build
    val bullet = e.bullet
    if (bullet.owner !is mindustry.gen.Unit) return@listen
    val damages = bullet.owner as mindustry.gen.Unit
    if ((damages.controller() as? MissileAI)?.shooter != null) {
        build.data.lastDamage = (damages.controller() as MissileAI).shooter
    } else {
        build.data.lastDamage = damages
    }
    build.data.calculate()
}

