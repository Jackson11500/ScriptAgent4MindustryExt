@file:Depends("coreMindustry/menu", "调用菜单")

package mapScript

import coreMindustry.MenuBuilder
import coreMindustry.lib.command
import coreMindustry.lib.game
import coreMindustry.lib.player
import mapScript.lib.modeIntroduce
import mindustry.Vars
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.entities.Units
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Iconc
import mindustry.gen.Player
import mindustry.type.UnitType


/**@author xkldklp */
name = "英雄"

modeIntroduce(
    "英灵神殿", "[cyan]召唤英雄单位!\n" +
            "[yellow]每个队伍都可使用/hero指令召唤英雄单位\n" +
            "\n英雄单位在敌方禁造区时将会被削弱" +
            "\n英雄单位自带boss${Iconc.statusBoss}但是死亡将会永久削弱附近友方单位!" +
            "\n[red]且队伍单位血量属性下降10%"
)

val team2hero = mutableMapOf<Team, mindustry.gen.Unit>()

fun Team.heroValid(): Boolean {
    return !(team2hero.getOrDefault(this, null)?.dead ?: true)
}

onEnable {
    team2hero.clear()
}



command("hero", "召唤英雄单位") {
    body {
        HeroMenu(player!!).sendTo()
    }
}

class HeroMenu(private val player: Player): MenuBuilder<Unit>() {
    var tab: Int = 0

    val mindustry.gen.Unit.inEnemyArea: Boolean
        get() {
            val closestCore = Vars.state.teams.active
                .mapNotNull { it.cores.minByOrNull(this::dst2) }
                .minByOrNull(this::dst2) ?: return false
            return closestCore.team != team() && (Vars.state.rules.polygonCoreProtection || dst(closestCore) < Vars.state.rules.enemyCoreBuildRadius)
        }

    fun sendTo() {
        launch(Dispatchers.game) {
            sendTo(player, 60_000)
        }
    }

    fun hero(unit: UnitType, canSpawn: Boolean = true) {
        if (canSpawn) {
            option("${unit.emoji()}${unit.localizedName}${unit.emoji()}") {
                if (player.team().heroValid()) {
                    player.sendMessage("[red]英雄未死亡")
                    return@option
                }
                if (player.dead()) {
                    player.sendMessage("[red]你已死亡")
                    return@option
                }
                val p = player
                unit.create(player.team()).apply{
                    set(p.unit())
                    snapInterpolation()
                    if (!canPassOn() || inEnemyArea) {
                        p.sendMessage("[red]此处无法召唤此英雄")
                        return@option
                    }
                    apply(StatusEffects.boss)
                    apply(StatusEffects.electrified, 30 * 60f)
                    team2hero[p.team()] = this
                    add()
                    launch(Dispatchers.game) {
                        while (!dead) {
                            yield()
                            if (inEnemyArea) {
                                apply(StatusEffects.sapped, 2 * 60f)
                                apply(StatusEffects.electrified, 2 * 60f)
                            }
                        }
                        Units.nearby(team, x, y, 64 * 8f) {
                            it.apply(StatusEffects.sapped, Float.POSITIVE_INFINITY)
                        }
                        team.rules().unitHealthMultiplier *= 0.9f
                        Call.setRules(Vars.state.rules)
                    }
                }
            }
        } else {
            option("[red]未达到解锁条件") {
                refresh()
            }
        }
    }

    suspend fun mainMenu() {
        title = "[cyan]英灵神殿"
        msg = ""
        hero(UnitTypes.scepter)
        newRow()
        hero(UnitTypes.vela)
        newRow()
        hero(UnitTypes.arkyid)
        newRow()
        hero(UnitTypes.antumbra)
        newRow()
        hero(UnitTypes.quad)
        newRow()
        hero(UnitTypes.vanquish)
        newRow()
        hero(UnitTypes.tecta)
        newRow()
        hero(UnitTypes.quell)
    }

    override suspend fun build() {
        when(tab) {
            0 -> mainMenu()
        }
        newRow()
        option("[white]退出菜单") { }
    }
}