@file:Import("@coreMindustry/util/spawnAround.kt", sourceFile = true)
@file:Depends("coreMindustry/contentsTweaker", "修改核心单位,单位属性")
@file:Depends("wayzer/map/betterTeam")

package mapScript

import arc.Events
import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import coreMindustry.util.spawnAround
import mindustry.Vars
import mindustry.ai.types.CommandAI
import mindustry.content.Blocks
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.entities.Units
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.*
import org.intellij.lang.annotations.Language
import kotlin.math.ceil

val betterTeam = contextScript<wayzer.map.BetterTeam>()

fun Player.toCrux() {
    unit().kill()
    betterTeam.changeTeam(this, Team.crux)
    Call.sendMessage("${name()} [yellow]已被感染！")
}

val contentPatch
    @Language("JSON5")
    get() = """{
                  "block": {
                        "core-shard": {
                            "unitType": "dagger",
                            "solid": false
                        },
                        "core-foundation": {
                            "unitType": "stell",
                            "solid": false
                        },
                        "core-nucleus": {
                            "unitType": "fortress",
                            "solid": false
                        },
                        "door-large": {
                            "underBullets": true
                        },
                        "ripple": {
                            "underBullets": true
                        },
                        "fuse": {
                            "underBullets": true
                        }
                  },
                  "unit": {
                        "dagger": {
                            "health": 800,
                            "weapons.0.bullet.damage": 42,
                        },
                        "stell": {
                            "health": 1800,
                            "weapons.0.bullet.damage": 150
                        }
                  }
            }"""
onEnable {
    Vars.state.rules.tags.put("@disableSkills", "true")
    contextScript<coreMindustry.ContentsTweaker>().addPatch("100021", contentPatch)
    launch(Dispatchers.game) {
        delay(15_000)
        loop(Dispatchers.game) {
            if (!Vars.state.gameOver && Vars.state.rules.objectiveFlags.contains("win")) {
                Events.fire(EventType.GameOverEvent(Team.sharded))
                Vars.state.gameOver = true
            }
            if (!Vars.state.gameOver && Groups.player.none { it.team() == Team.sharded }) {
                Events.fire(EventType.GameOverEvent(Team.crux))
                Vars.state.gameOver = true
            }
            delay(100)
        }
        repeat(10) {
            Call.sendMessage("[yellow]还有${10 - it}秒感染波及到全域！")
            delay(1000)
        }
        if (Groups.player.size() > 1) {
            repeat(ceil(Groups.player.size() / 5f).toInt()) {
                Groups.player.filter { it.team() == Team.sharded }.toList().random().apply {
                    toCrux()
                }
            }
        } else {
            Call.sendMessage("[yellow]仅单人，启用单刷模式Buff！")
            Team.sharded.data().units.forEach {
                it.shield += it.maxHealth * 2
            }
        }
        var level = 0
        loop(Dispatchers.game) {
            if (level == 0 && Groups.unit.filter { it.team == Team.sharded }.size <= 5) {
                Call.sendMessage("[yellow]绝境之望！幸存单位获得强化Buff提升伤害与移速")
                Team.sharded.data().units.forEach {
                    it.apply(StatusEffects.overclock, 999999f)
                    it.health = it.maxHealth
                    it.shield += it.maxHealth / 4
                }
                Team.sharded.rules().unitDamageMultiplier += 0.25f
                level++
            }
            if (level == 1 && Groups.unit.filter { it.team == Team.sharded }.size <= 3) {
                Call.sendMessage("[yellow]绝境之望！幸存单位获得强化Buff提升大量伤害与移速")
                Team.sharded.data().units.forEach {
                    it.apply(StatusEffects.overdrive, 999999f)
                    it.health = it.maxHealth
                    it.shield += it.maxHealth / 2
                }
                Team.sharded.rules().unitDamageMultiplier += 0.5f
                level++
            }
            if (level == 2 && Groups.unit.filter { it.team == Team.sharded }.size <= 1) {
                Call.sendMessage("[yellow]绝境之望！幸存单位获得强化Buff提升大量伤害与大量血量")
                Team.sharded.data().units.forEach {
                    it.apply(StatusEffects.boss, 999999f)
                    it.apply(StatusEffects.shielded, 999999f)
                    it.health = it.maxHealth
                    it.shield += it.maxHealth * 2
                }
                Team.sharded.rules().unitDamageMultiplier += 1.25f
                level++
            }
            yield()
        }

        Call.sendMessage("[red]现已禁止重生！新加入玩家将会被直接感染")
        Groups.player.forEach {
            it.unit().spawnedByCore = false
        }
        loop(Dispatchers.game) {
            Groups.player.forEach {
                if (it.team() != Team.crux && it.unit().spawnedByCore) {
                    it.toCrux()
                }
                if (it.team() == Team.crux && it.unit().spawnedByCore) {
                    it.unit().kill()
                }
            }
            Team.crux.data().units.forEach {
                if (it.controller() !is CommandAI && !it.isPlayer)
                    it.controller(CommandAI())
            }
            yield()
        }
        Team.crux.data().cores.forEach {
            loop(Dispatchers.game) {
                yield()
                if (it.tile.team() != Team.crux) {
                    return@loop
                }
                val closest = Units.closest(Team.sharded, it.x, it.y) { true }?.dst(it) ?: return@loop
                delay((closest / (160 * 8f) * 20_000).toLong())
                when (it.block) {
                    Blocks.coreShard -> UnitTypes.crawler
                    Blocks.coreFoundation -> UnitTypes.mace
                    Blocks.coreNucleus -> UnitTypes.atrax
                    else -> UnitTypes.crawler
                }.spawnAround(it, Team.crux, 5)
            }
        }
        delay(300_000)
        Call.sendMessage("[yellow]感染加强！感染单位获得强化Buff提升伤害与移速")
        loop(Dispatchers.game) {
            Team.crux.data().units.forEach {
                it.apply(StatusEffects.overclock, 999999f)
            }
            delay(100)
        }
        delay(300_000)
        Call.sendMessage("[yellow]感染加强！感染单位获得强化Buff提升大量伤害与移速")
        loop(Dispatchers.game) {
            Team.crux.data().units.forEach {
                it.apply(StatusEffects.overdrive, 999999f)
            }
            delay(100)
        }
        delay(300_000)
        Call.sendMessage("[yellow]感染加强！感染单位获得强化Buff提升伤害与血量")
        loop(Dispatchers.game) {
            Team.crux.data().units.forEach {
                it.apply(StatusEffects.boss, 999999f)
            }
            delay(100)
        }
    }
}

