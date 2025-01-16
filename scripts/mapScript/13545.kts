@file:Depends("coreMindustry/menu", "调用菜单")
@file:Depends("coreMindustry/contentsTweaker", "修改核心单位,单位属性")
@file:Import("@coreMindustry/util/spawnAround.kt", sourceFile = true)

package mapScript

import arc.util.Align
import coreLibrary.lib.util.loop
import coreMindustry.MenuBuilder
import coreMindustry.lib.broadcast
import coreMindustry.lib.game
import coreMindustry.lib.listen
import coreMindustry.util.spawnAround
import mapScript.lib.modeIntroduce
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Items
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes.*
import mindustry.entities.units.StatusEntry
import mindustry.game.EventType
import mindustry.gen.*
import mindustry.type.Item
import mindustry.type.UnitType
import mindustry.world.Tile
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import org.intellij.lang.annotations.Language
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

name = "coreWar"

modeIntroduce(
    "Core", buildString {
        appendLine("[cyan]点击核心打开操作菜单")
        append("[yellow]挖取原矿,购买单位,征服敌人!")
    }
)

fun Float.format(i: Int = 2): String {
    return "%.${i}f".format(this)
}

val eCores = listOf(Blocks.coreAcropolis, Blocks.coreBastion, Blocks.coreCitadel)

val contentPatch
    @Language("JSON5")
    get() = """
{
  "block": {
    "core-shard": {
      "itemCapacity": 999999,
    },
    "core-foundation": {
      "unitType": "alpha",
      "itemCapacity": 999999,
    },
    "core-bastion": {
      "unitType": "alpha",
      "itemCapacity": 999999,
      "incinerateNonBuildable": false
    },
    "core-nucleus": {
      "unitType": "alpha",
      "itemCapacity": 999999,
    },
    "core-citadel": {
      "unitType": "alpha",
      "itemCapacity": 999999,
      "incinerateNonBuildable": false
    },
    "core-acropolis": {
      "unitType": "alpha",
      "itemCapacity": 999999,
      "incinerateNonBuildable": false
    },
  }
}
"""
val unitsCosts = mapOf(
    crawler to (62 to 6),
    flare to (50 to 7),
    risso to (70 to 8),
    retusa to (110 to 10),
    mace to (100 to 10),
    atrax to (125 to 11),
    minke to (140 to 12),
    horizon to (120 to 11),
    fortress to (450 to 21),
    bryde to (750 to 27),
    zenith to (550 to 23),
    scepter to (5000 to 71),
    antumbra to (6000 to 77),
    sei to (8500 to 92),
    vela to (18000 to 134),
    arkyid to (12000 to 110),
    reign to (52000 to 180),
    corvus to (67000 to 180),
    eclipse to (58000 to 180),
    omura to (75000 to 180),

    stell to (100 to 10),
    merui to (130 to 11),
    elude to (200 to 14),
    locus to (450 to 21),
    cleroi to (700 to 26),
    avert to (480 to 22),
    precept to (3000 to 55),
    anthicus to (4900 to 70),
    obviate to (4350 to 66),
    vanquish to (6000 to 77),
    tecta to (8000 to 89),
    quell to (8800 to 94),
    conquer to (45000 to 180),
    collaris to (80000 to 180),
    disrupt to (70000 to 180)
)

class CoreMenu(private val player: Player, private val core: CoreBuild): MenuBuilder<Unit>() {
    val item = if (core.block in eCores) Items.beryllium else Items.copper
    val rules = core.team.rules()

    fun sendTo() {
        launch(Dispatchers.IO) {
            sendTo(player, 60_000)
        }
    }

    private suspend fun List<UnitType>.build() {
        if (!isEmpty()) {
            newRow()
        }
        forEach { u ->
            val cost = unitsCosts[u] ?: (0 to 0)
            fun timeRate() = (1.2f - (Groups.unit.filter { it.type == u && it.team() == core.team }.size * 0.02f).pow(0.2f)).coerceAtLeast(0.05f).coerceAtMost(1f)
            fun need() = (cost.first * (1 + (Groups.unit.filter { it.type == u && it.team() == core.team }.size * 0.5f).pow(2) * 0.08f).coerceAtMost(2f)).toInt()
            fun enough() = core.items[item] >= need()
            option(buildString {
                appendLine(u.emoji())
                appendLine("${Iconc.statusElectrified}${(cost.second * timeRate()).toInt()}s")
                append("${item.emoji()}${if (enough()) "[white]" else "[lightgray]"}${need()}")
            }) {
                if (core.dead) {
                    player.sendMessage("[red]核心已被破坏")
                } else {
                    if (enough()) {
                        val unit = u.spawnAround(core, core.team)
                        if (unit != null) {
                            if (need() >= core.items[item] * 0.75f) {
                                Groups.player.filter { it.team() == core.team }.forEach {
                                    it.sendMessage("${player.name}[yellow] 花费了[white] ${item.emoji()}${need()} [yellow]购买了 [white]${u.emoji()}")
                                }
                            }
                            core.items.remove(item, need())
                            unit.apply(StatusEffects.electrified, cost.second * 60f * timeRate())
                        } else {
                            player.sendMessage("[red]生成失败")
                        }
                    } else {
                        player.sendMessage("[red]资源不足")
                    }
                }
                refresh()
            }
        }
    }

    private suspend fun serpuloMenu() {
        listOf(crawler, flare, risso, retusa).build()
        listOf(mace, atrax, minke, horizon).build()
        listOf(fortress, bryde, zenith).build()
        listOf(scepter, antumbra, sei).build()
        listOf(vela, arkyid).build()
        listOf(reign, corvus).build()
        listOf(eclipse, omura).build()
    }
    private suspend fun erekirMenu() {
        listOf(stell, merui, elude).build()
        listOf(locus, cleroi, avert).build()
        listOf(precept, anthicus, obviate).build()
        listOf(vanquish, tecta, quell).build()
        listOf(conquer, collaris, disrupt).build()
    }

    override suspend fun build() {
        title = item.emoji().toString().repeat(5)
        msg = "${item.emoji()}${core.items[item]}"
        lazyOption {
            fun value() = rules.blockDamageMultiplier
            fun need() = max((100 + (value() - 1) / 0.05 * 60).toInt(), 100)
            fun enough() = core.items[item] >= need()
            val text = "升级建筑攻击"
            if (enough()) {
                option("$text\n${value().format()} -> ${(value() + 0.05f).format()}\n${item.emoji()}${need()}")
                Groups.player.filter { it.team() == core.team }.forEach {
                    Call.label(it.con, "${player.name}[yellow] 花费了[white] ${item.emoji()}${need()} [yellow]升级建筑攻击(${value().format()} -> ${(value() + 0.05f).format()})",
                        8f,
                        core.x + Random.nextInt(-30, 30),
                        core.y + Random.nextInt(-30, 30),
                    )
                }
                rules.blockDamageMultiplier += 0.05f
                Call.setRules(Vars.state.rules)
                core.items.remove(item, need())
                refresh()
            } else {
                refreshOption(("$text\n${value().format()} -> ${(value() + 0.05f).format()}\n${item.emoji()}[lightgray]${need()}"))
            }
        }
        lazyOption {
            fun value() = rules.blockHealthMultiplier
            fun need() = max((100 + (value() - 1) / 0.05 * 180).toInt(), 300)
            fun enough() = core.items[item] >= need()
            val text = "升级建筑血量"
            if (enough()) {
                option("$text\n${value().format()} -> ${(value() + 0.05f).format()}\n${item.emoji()}${need()}")
                Groups.player.filter { it.team() == core.team }.forEach {
                    Call.label(it.con, "${player.name}[yellow] 花费了[white] ${item.emoji()}${need()} [yellow]升级建筑血量(${value().format()} -> ${(value() + 0.05f).format()})",
                        8f,
                        core.x + Random.nextInt(-30, 30),
                        core.y + Random.nextInt(-30, 30),
                    )
                }
                rules.blockHealthMultiplier += 0.05f
                Call.setRules(Vars.state.rules)
                core.items.remove(item, need())
                refresh()
            } else {
                refreshOption(("$text\n${value().format()} -> ${(value() + 0.05f).format()}\n${item.emoji()}[lightgray]${need()}"))
            }
        }
        newRow()
        lazyOption {
            fun value() = rules.unitDamageMultiplier
            fun need() = max((100 + (value() - 1) / 0.05 * 100).toInt(), 100)
            fun enough() = core.items[item] >= need()
            val text = "升级单位攻击"
            if (enough()) {
                option("$text\n${value().format()} -> ${(value() + 0.05f).format()}\n${item.emoji()}${need()}")
                Groups.player.filter { it.team() == core.team }.forEach {
                    Call.label(it.con, "${player.name}[yellow] 花费了[white] ${item.emoji()}${need()} [yellow]升级单位攻击(${value().format()} -> ${(value() + 0.05f).format()})",
                        8f,
                        core.x + Random.nextInt(-30, 30),
                        core.y + Random.nextInt(-30, 30),
                    )
                }
                rules.unitDamageMultiplier += 0.05f
                Call.setRules(Vars.state.rules)
                core.items.remove(item, need())
                refresh()
            } else {
                refreshOption(("$text\n${value().format()} -> ${(value() + 0.05f).format()}\n${item.emoji()}[lightgray]${need()}"))
            }
        }
        lazyOption {
            fun value() = rules.unitHealthMultiplier
            fun need() = max((100 + (value() - 1) / 0.05 * 500).toInt(), 500)
            fun enough() = core.items[item] >= need()
            val text = "升级单位血量"
            if (enough()) {
                option("$text\n${value().format()} -> ${(value() + 0.05f).format()}\n${item.emoji()}${need()}")
                Groups.player.filter { it.team() == core.team }.forEach {
                    Call.label(it.con, "${player.name}[yellow] 花费了[white] ${item.emoji()}${need()} [yellow]升级单位血量(${value().format()} -> ${(value() + 0.05f).format()})",
                        8f,
                        core.x + Random.nextInt(-30, 30),
                        core.y + Random.nextInt(-30, 30),
                    )
                }
                rules.unitHealthMultiplier += 0.05f
                Call.setRules(Vars.state.rules)
                core.items.remove(item, need())
                refresh()
            } else {
                refreshOption(("$text\n${value().format()} -> ${(value() + 0.05f).format()}\n${item.emoji()}[lightgray]${need()}"))
            }
        }
        newRow()
        option("${Iconc.itemLead}${Iconc.itemSand}${Iconc.itemCoal}${Iconc.itemScrap} -> ${item.emoji()}") {
            listOf(Items.lead, Items.sand, Items.coal, Items.scrap).forEach {
                val amount = core.items.get(it)
                core.items.remove(it, amount)
                core.items.add(item, amount)
            }
            refresh()
        }
        newRow()
        val otherItem = if (item == Items.copper) Items.beryllium else Items.copper
        option("${otherItem.emoji()} ---> ${item.emoji()}") {
            val amount = core.items.get(otherItem)
            core.items.remove(otherItem, amount)
            core.items.add(item, amount)
            refresh()
        }
        if (core.block !in eCores) {
            val cost = 100
            fun need() = ((Groups.unit.filter { it.type == mono && it.team() == core.team }.size / 2f).pow(2).coerceAtLeast(1f) * 100).toInt()
            fun enough() = core.items[item] >= need()
            option(buildString {
                appendLine(mono.emoji())
                append("${item.emoji()}${if (enough()) "[white]" else "[lightgray]"}${need()}")
            }) {
                if (core.dead) {
                    player.sendMessage("[red]核心已被破坏")
                } else {
                    if (enough()) {
                        val unit = mono.spawnAround(core, core.team)
                        if (unit != null) {
                            if (need() >= core.items[item] * 0.75f) {
                                Groups.player.filter { it.team() == core.team }.forEach {
                                    it.sendMessage("${player.name}[yellow] 花费了[white] ${item.emoji()}${need()} [yellow]购买了 [white]${mono.emoji()}")
                                }
                                unit.apply(StatusEffects.fast, Float.POSITIVE_INFINITY)
                                repeat(3) { unit.statuses.add(StatusEntry().set(StatusEffects.shielded, Float.POSITIVE_INFINITY)) }
                            }
                            core.items.remove(item, need())
                        } else {
                            player.sendMessage("[red]生成失败")
                        }
                    } else {
                        player.sendMessage("[red]资源不足")
                    }
                }
                refresh()
            }
        } else {
            option("${Iconc.alphaaaa} ---> ${Iconc.unitEvoke}") {
                if (player.unit().type == alpha) {
                    val p = player
                    evoke.create(p.team()).apply {
                        set(p.unit())
                        snapInterpolation()
                        p.unit(this)
                        spawnedByCore = true
                        add()
                    }
                } else {
                    refresh()
                }
            }
        }
        if (core.block in eCores) erekirMenu() else serpuloMenu()
        newRow()
        option("[white]关闭") { }
    }
}

val core2Label by autoInit { mutableMapOf<CoreBuild, WorldLabel>() }

onEnable {
    contextScript<coreMindustry.ContentsTweaker>().addPatch("CoreWar",contentPatch)
    loop(Dispatchers.game) {
        Call.infoPopup(
            buildString {
                append("队伍属性")
                Vars.state.teams.getActive().forEach { td ->
                    appendLine("\n[#${td.team.color}]${td.team}")
                    appendLine("${Iconc.turret}${Iconc.commandAttack}${td.team.rules().blockDamageMultiplier.format()}${Iconc.add}${td.team.rules().blockHealthMultiplier.format()}")
                    append("${Iconc.android}${Iconc.commandAttack}${td.team.rules().unitDamageMultiplier.format()}${Iconc.add}${td.team.rules().unitHealthMultiplier.format()}")
                }
            }, 0.51f,
            Align.topLeft, 350, 0, 0, 0
        )
        delay(200L)
    }
}

listen<EventType.TapEvent> {
    val player = it.player
    if (player.dead()) return@listen
    if (it.tile.block() is CoreBlock && it.tile.team() == player.team()) {
       CoreMenu(player, it.tile.build as CoreBuild).sendTo()
    }
}