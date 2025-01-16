@file:Depends("wayzer/user/achievement", "成就")
@file:Depends("xkldklp/KVars")
@file:Suppress("DEPRECATION")

package mapScript

import arc.Events
import arc.math.geom.Vec2
import arc.struct.IntSeq
import coreLibrary.lib.util.loop
import coreLibrary.lib.with
import coreMindustry.lib.*
import mindustry.Vars
import mindustry.content.Blocks.*
import mindustry.content.Items
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.WorldLabel
import mindustry.world.Tile
import wayzer.MapManager
import wayzer.lib.dao.PlayerData
import java.util.*
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**@author xkldklp */

val achievement = contextScript<wayzer.user.Achievement>()
fun Player.achievement(name: String, exp: Int, b: Boolean = false) {
    val profile = PlayerData[uuid()].profile
    if (profile != null)
        achievement.finishAchievement(profile, name, exp, b)
}

fun achieveAll(name: String, exp: Int) {
    Groups.player.forEach { it.achievement(name, exp) }
}

val kVars = contextScript<xkldklp.KVars>()

var buffed = false

val dailyRandomCode get() = run {
    val date = Date()
    (date.date.hashCode() + date.month.hashCode() + date.year.hashCode()).hashCode()
}
val dailyRandomCodeH get() = run {
    val date = Date()
    (date.date.hashCode() + date.month.hashCode() + date.year.hashCode() + (date.hours / 2).hashCode()).hashCode()
}


val ruleLevel by autoInit{ Vars.state.rules.tags.getInt("@ruleLevel", 0) }

class Rule(
    val name: String,
    val desc: String,

    val cost: Int,

    val effect: (() -> Unit)? = null,
    val condition: (() -> Boolean)? = null
) {
    fun active(): Pair<String, String> {
        effect?.invoke()
        return name to desc
    }

    fun canAppear(): Boolean {
        return condition?.invoke() ?: true
    }
}

val rules  by autoInit { buildList {
    add(buildList {
        add(Rule("[white]士气高涨", "[red](敌 单位攻击 * 125%)", 1, fun() {
            Team.crux.rules().unitDamageMultiplier *= 1.25f
        }))
        add(Rule("[white]脆弱建筑", "[red](友 建筑伤害 * 80%)", 1, fun() {
            Team.sharded.rules().blockDamageMultiplier *= 0.8f
        }))
        add(Rule("[white]构筑干扰", "[red](友 建造速度 * 80%) ", 1, fun() {
            Team.sharded.rules().buildSpeedMultiplier *= 0.8f
        }))
        add(Rule("[white]高速支援", "[red](敌 波次等待时间 * 75%)", 1, fun() {
            Vars.state.rules.waveSpacing *= 0.75f
        }))
        add(Rule("[white]轻盈单位", "[red](友 坠落伤害倍率 * 0%)", 1, fun() {
            Team.sharded.rules().unitCrashDamageMultiplier = 0f
        }))
        add(Rule("[white]分裂禁止", "[red]分裂炮被禁用", 1, fun() {
            Vars.state.rules.bannedBlocks.add(scatter)
        }))
        add(Rule("[white]无焰之地", "[red]火焰炮被禁用 火焰规则关闭", 1, fun() {
            Vars.state.rules.bannedBlocks.add(scorch)
            Vars.state.rules.fire = false
        }))
        add(Rule("[white]万桥物流", "[red]溢流门 连接器被禁用", 1, fun() {
            Vars.state.rules.bannedBlocks.add(overflowGate)
            Vars.state.rules.bannedBlocks.add(underflowGate)
            Vars.state.rules.bannedBlocks.add(junction)
        }))
        add(Rule("[white]神教退散", "[red]路由器 分配器被禁用", 1, fun() {
            Vars.state.rules.bannedBlocks.add(router)
            Vars.state.rules.bannedBlocks.add(distributor)
        }))
        add(Rule("[white]电网保护", "[red]电弧 蓝瑟被禁用", 1, fun() {
            Vars.state.rules.bannedBlocks.add(arc)
            Vars.state.rules.bannedBlocks.add(lancer)
        }))
        add(Rule("[white]梧桐吴签", "[red]初始铜铅归0", 1, fun() {
            launch(Dispatchers.game) {
                Team.sharded.core().items.set(Items.copper, 0)
                Team.sharded.core().items.set(Items.lead, 0)
                delay(500)
                Team.sharded.core().items.set(Items.copper, 0)
                Team.sharded.core().items.set(Items.lead, 0)
            }
        }))
    }.toMutableList())
    add(buildList {
        add(Rule("[sky]致命伏击", "[red]第一波立刻开始", 3, fun() {
            Vars.logic.runWave()
        }))
        add(Rule("[sky]阵地更换", "[red]刷怪圈秒杀范围 * 200% 核心保护区范围 * 150% ", 3, fun() {
            Vars.state.rules.enemyCoreBuildRadius *= 1.5f
            Vars.state.rules.dropZoneRadius *= 2f
        }))
        add(Rule("[sky]无智机械", "[red]逻辑被禁用", 3, fun() {
            arrayOf(logicProcessor, microProcessor, hyperProcessor).forEach {
                Vars.state.rules.bannedBlocks.add(it)
            }
        }))
        add(Rule("[sky]冗余构筑", "[red](友 建造速度 * 60% 单位建造速度 * 80%)", 3, fun() {
            Team.sharded.rules().buildSpeedMultiplier *= 0.6f
            Team.sharded.rules().unitBuildSpeedMultiplier *= 0.8f
        }))
        add(Rule("[sky]粗制滥造", "[red](友 建造速度 * 120% 建筑血量 * 60% 建筑伤害 * 80%)", 3, fun() {
            Team.sharded.rules().buildSpeedMultiplier *= 1.2f
            Team.sharded.rules().blockHealthMultiplier *= 0.6f
            Team.sharded.rules().blockDamageMultiplier *= 0.8f

        }))
        add(Rule("[sky]神之陨落", "[red]蜂群 气旋被禁用", 3, fun() {
            Vars.state.rules.bannedBlocks.add(swarmer)
            Vars.state.rules.bannedBlocks.add(cyclone)
        }))
        add(Rule("[sky]原始物流", "[red]钛带被禁用", 3, fun() {
            Vars.state.rules.bannedBlocks.add(titaniumConveyor)
        }))
        add(Rule("[sky]错误投送", "[red]仅核心能放入资源", 3, fun() {
            Vars.state.rules.onlyDepositCore = true
        }))
        add(Rule("[sky]指挥受损", "[red]不能附身单位", 3, fun() {
            Vars.state.rules.possessionAllowed = false
        }, fun(): Boolean {
            return Vars.state.rules.attackMode
        }))
    }.toMutableList())
    add(buildList {
        add(Rule("[cyan]致命一击", "[red](敌 单位攻击 * 200%)", 8, fun() {
            Team.crux.rules().unitDamageMultiplier *= 2f
        }))
        add(Rule("[cyan]此地禁空", "[red]空军工厂被禁用 核心机获得缴械缓慢", 8, fun() {
            Vars.state.rules.bannedBlocks.add(airFactory)
            loop(Dispatchers.game) {
                Groups.unit.filter { it.spawnedByCore }.forEach {
                    it.apply(StatusEffects.slow, Float.POSITIVE_INFINITY)
                    it.apply(StatusEffects.disarmed, Float.POSITIVE_INFINITY)
                }
                yield()
            }
        }))
        add(Rule("[cyan]万众一心", "[red]波次等待时间 * 60% 波数 * 140%", 8, fun() {
            Vars.state.rules.waveSpacing *= 0.6f
            Vars.state.rules.winWave = (Vars.state.rules.winWave * 1.4f).toInt()
        }))
        add(Rule("[cyan]构筑缓慢", "[red](友 建造速度 * 30% 单位建造速度 * 20%)", 8, fun() {
            Team.sharded.rules().buildSpeedMultiplier *= 0.3f
            Team.sharded.rules().unitBuildSpeedMultiplier *= 0.2f
        }))
        add(Rule("[cyan]永久破坏", "[red]重建禁止 (友 建筑血量 * 60%)", 8, fun() {
            Team.sharded.rules().blockHealthMultiplier *= 0.6f
            Vars.state.rules.ghostBlocks = false
        }))
        add(Rule("[cyan]巨型建筑", "[red](友 建造速度 * 80% 建筑花费 * 125%)", 8, fun() {
            Team.sharded.rules().buildSpeedMultiplier *= 0.8f
            Vars.state.rules.buildCostMultiplier *= 1.25f
        }))
        add(Rule("[cyan]巨型单位", "[red](友 单位建造速度 * 75% 单位花费 * 150%)", 8, fun() {
            Team.sharded.rules().unitBuildSpeedMultiplier *= 0.75f
            Team.sharded.rules().unitCostMultiplier *= 1.75f
        }))
        add(Rule("[cyan]恐怖袭击", "[red](敌 坠毁伤害倍率 * 500%)", 8, fun() {
            Team.crux.rules().unitCrashDamageMultiplier *= 5f
        }))
        add(Rule("[cyan]装卸罢工", "[red]装卸器被禁用", 8, fun() {
            Vars.state.rules.bannedBlocks.add(unloader)
        }))
    }.toMutableList())
    add(buildList {
        add(Rule("[purple]双管神教", "[red]仅能使用双管炮", 14, fun() {
            arrayOf(scatter,
                scorch,
                hail,
                arc,
                wave,
                lancer,
                swarmer,
                salvo,
                fuse,
                ripple,
                cyclone,
                foreshadow,
                spectre,
                meltdown,
                segment,
                parallax,
                tsunami).forEach {
                Vars.state.rules.bannedBlocks.add(it)
            }
        }))
        add(Rule("[purple]希望之光", "[red]敌方最后一个单位获得断断续续的无敌Buff", 14, fun() {
            loop(Dispatchers.game) {
                Team.crux.data().units.singleOrNull()?.apply(StatusEffects.invincible,9 * 60f)
                delay(10_000)
            }
        }))
        add(Rule("[purple]硅零时刻", "[red]每40s硅被清空", 14, fun() {
            loop(Dispatchers.game) {
                delay(40_000)
                Team.sharded.core().items.remove(Items.silicon, 99999)
            }
        }))
        add(Rule("[purple]劣化建筑", "[red](友 建筑速度 * 50% 建筑血量 * 50% \n建筑伤害 * 50% 建筑花费 * 150%)", 14, fun() {
            Team.sharded.rules().buildSpeedMultiplier *= 0.5f
            Team.sharded.rules().blockHealthMultiplier *= 0.5f
            Team.sharded.rules().blockDamageMultiplier *= 0.5f
            Vars.state.rules.buildCostMultiplier *= 1.5f
        }))
        add(Rule("[purple]仓促准备", "[red]初始物资减半", 14, fun() {
            launch(Dispatchers.game) {
                delay(500)
                Team.sharded.core().items.each { item, amount ->
                    Team.sharded.core().items.remove(item, amount / 2)
                }
            }
        }))
        add(Rule("[purple]非逆损坏", "[red]mono,nova,retusa和单位建筑修复器 被禁用", 14, fun() {
            Vars.state.rules.bannedUnits.add(UnitTypes.mono)
            Vars.state.rules.bannedUnits.add(UnitTypes.nova)
            Vars.state.rules.bannedUnits.add(UnitTypes.retusa)
            Vars.state.rules.bannedBlocks.add(repairPoint)
            Vars.state.rules.bannedBlocks.add(repairTurret)
            Vars.state.rules.bannedBlocks.add(mender)
            Vars.state.rules.bannedBlocks.add(mendProjector)
        }))
    }.toMutableList())
    add(buildList {
        add(Rule("[yellow]和平主义", "[red]禁止使用一切炮塔与钍反", 24, fun() {
            arrayOf(thoriumReactor,
                duo,
                scatter,
                scorch,
                hail,
                arc,
                wave,
                lancer,
                swarmer,
                salvo,
                fuse,
                ripple,
                cyclone,
                foreshadow,
                spectre,
                meltdown,
                segment,
                parallax,
                tsunami).forEach {
                Vars.state.rules.bannedBlocks.add(it)
            }
        }))
        add(Rule("[yellow]王牌部队", "[red]敌方单位拥有3个正面buff 敌 单位伤害 * 150%", 24, fun() {
            Team.crux.rules().unitDamageMultiplier *= 1.5f
            loop(Dispatchers.game) {
                Groups.unit.filter { it.team == Team.crux }.forEach {
                    it.apply(StatusEffects.overclock, Float.POSITIVE_INFINITY)
                    it.apply(StatusEffects.overdrive, Float.POSITIVE_INFINITY)
                    it.apply(StatusEffects.boss, Float.POSITIVE_INFINITY)
                }
                yield()
            }
        }))
        add(Rule("[yellow]自爆协议", "[red]我方建筑每10s受到max(2, 20%生命)伤害 单位受到max(20, 5%生命)伤害 ", 24, fun() {
            loop(Dispatchers.game) {
                delay(10_000)
                Groups.unit.filter { it.team == Team.sharded }.forEach {
                    it.health -= max(20f, it.health * 0.05f)
                }
                Team.sharded.data().buildings.forEach {
                    it.health -= max(2f, it.health * 0.2f)
                    if (it.health <= 0) {
                        it.kill()
                    }
                }
                var j = 1
                while(true) {
                    var i = 0
                    if (Team.sharded.data().buildings.size <= (j - 1) * 99 + i) {
                        break
                    }
                    Call.buildHealthUpdate(IntSeq(buildList {
                        while (i <= j * 99) {
                            if (Team.sharded.data().buildings.size <= (j - 1) * 99 + i) {
                                break
                            }
                            Team.sharded.data().buildings[i].apply {
                                add(id)
                                add(health.toInt())
                            }
                            i++
                        }
                        j++
                    }.toIntArray() ))
                }
            }
        }))
        add(Rule("[yellow]阴阳反转", "[red]我方建筑单位每30s反转受损血量与目前血量 若满血则扣除10%最大生命", 24, fun() {
            loop(Dispatchers.game) {
                delay(30_000)
                Groups.build.filter { it.team == Team.sharded }.forEach {
                    if (it.health >= it.maxHealth) {
                        it.maxHealth *= 0.9f
                        it.health *= 0.45f
                    } else {
                        it.health = it.maxHealth - it.health
                    }
                    if (it.health <= 0) {
                        it.kill()
                    }
                }
                Groups.unit.filter { it.team == Team.sharded }.forEach {
                    if (it.health >= it.maxHealth) {
                        it.maxHealth *= 0.9f
                        it.health *= 0.45f
                    } else {
                        it.health = it.maxHealth - it.health
                    }
                }
                var j = 1
                while(true) {
                    var i = 0
                    if (Team.sharded.data().buildings.size <= (j - 1) * 99 + i) {
                        break
                    }
                    Call.buildHealthUpdate(IntSeq(buildList {
                        while (i <= j * 99) {
                            if (Team.sharded.data().buildings.size <= (j - 1) * 99 + i) {
                                break
                            }
                            Team.sharded.data().buildings[i].apply {
                                add(id)
                                add(health.toInt())
                            }
                            i++
                        }
                        j++
                    }.toIntArray() ))
                }
            }
        }))
        add(Rule("[yellow]永生祝福", "[red]敌方单位获得保护+boss buff 每20s将血量提升至最大值", 24, fun() {
            loop(Dispatchers.game) {
                Groups.unit.filter { it.team == Team.crux }.forEach {
                    it.apply(StatusEffects.boss, Float.POSITIVE_INFINITY)
                    it.apply(StatusEffects.shielded, Float.POSITIVE_INFINITY)
                }
                yield()
            }
            loop(Dispatchers.game) {
                Groups.unit.filter { it.team == Team.crux }.forEach {
                    it.health = it.maxHealth
                }
                delay(20_000)
            }
        }))
        add(Rule("[yellow]物资丢失", "[red]没有初始物资", 24, fun() {
            launch(Dispatchers.game) {
                delay(500)
                Team.sharded.core().items.clear()
            }
        }))
    }.toMutableList())
} }

onEnable {
    buffed = false

    var rule = ruleLevel.toFloat().pow(1.7f) * 2.5f

    fun MutableList<Rule>.addRule(list: MutableList<Rule>) {
        val target = list.random(Random(dailyRandomCodeH + MapManager.current.id))
        list.remove(target)
        add(target)
        rule -= target.cost
    }
    val leftRules = rules.toMutableList()
    leftRules.forEach {
        it.removeIf { !it.canAppear() }
    }

    val gameRules = mutableListOf<Rule>()

    while (rule >= 1) {
        repeat(4) {
            val i = 4 - it
            if (rules[i].any { it.cost * 1.4f <= rule } && leftRules[i].isNotEmpty()) {
                gameRules.addRule(leftRules[i])
            }
        }
        if (rule >= 1 && leftRules[0].isNotEmpty()) {
            gameRules.addRule(leftRules[0])
        }
        if (leftRules[0].isEmpty()) {
            break
        }
    }
    launch(Dispatchers.game) {
        gameRules.forEach { it.active() }
        Call.setRules(Vars.state.rules)
        delay(5_000)
        val msg = buildString {
            appendLine("[cyan]检测到世界律令！")
            gameRules.sortedBy { it.cost }.reversed().forEach {
                appendLine()
                appendLine(it.name)
                appendLine(it.desc)
            }
        }
        Call.sendMessage(msg)
        WorldLabel.create().apply {
            set(Team.sharded?.core() ?: Vec2(0f,0f))
            text = msg
            add()
        }
    }
    var gameover = false
    loop(Dispatchers.game) {
        if ((!gameover && Vars.state.rules.winWave > 0 && Vars.state.wave >= Vars.state.rules.winWave && Team.crux.data().unitCount == 0) ||
            (!gameover && Team.crux.data().noCores() && Vars.state.rules.attackMode)) {
            Events.fire(EventType.GameOverEvent(Team.sharded))
            Events.fire(EventType.WinEvent())
            gameover = true
            broadcast("[green]---区块已占领！---".with(), type = MsgType.WarningToast)
            if (MapManager.current.id <= 1000) {
                when(ruleLevel) {
                    1 -> achieveAll("[green][异世-零地区]", 1000)
                    2 -> achieveAll("[green][异世-冰冻森]", 1000)
                    3 -> achieveAll("[green][异世-陨石带]", 1000)
                    4 -> achieveAll("[green][异世-风群岛]", 1000)
                    5 -> achieveAll("[purple][异世-焦油田]", 2000)
                    6 -> achieveAll("[purple][异世-冲击区]", 2000)
                    7 -> achieveAll("[purple][异世-裂谷道]", 3000)
                    8 -> {
                        achieveAll("[purple][异世-发射区]", 4500)
                        kVars.S1A9Event = true
                    }
                }
                gameRules.forEach {
                    achieveAll("[green][${it.name}]", it.cost * 50)
                }
            }
        }
        yield()
    }
}

val buffs by autoInit {
    buildList {
        add(Rule("[stat]刻印", "[green](友 单位建造速度 * 200% 单位建造花费 * 80%)", 0, fun() {
            Team.sharded.rules().unitBuildSpeedMultiplier *= 2f
            Team.sharded.rules().unitCostMultiplier *= 0.8f
        }))
        add(Rule("[stat]归一", "[green](友 单位攻击 * 200% 单位建造速度 * 75% 单位血量 * 150%)", 0, fun() {
            Team.sharded.rules().unitBuildSpeedMultiplier *= 0.75f
            Team.sharded.rules().unitDamageMultiplier *= 2f
            Team.sharded.rules().unitHealthMultiplier *= 1.5f
        }))
        add(Rule("[stat]陨星", "[green](友 单位坠落伤害倍率 + 200%)", 0, fun() {
            Team.sharded.rules().unitCrashDamageMultiplier += 2f
        }))
        add(Rule("[stat]幻境", "[green](敌 单位坠落伤害倍率 * 25% 单位伤害 * 50%)", 0, fun() {
            Team.crux.rules().unitCrashDamageMultiplier *= 0.25f
            Team.crux.rules().unitDamageMultiplier *= 0.5f
        }))
        add(Rule("[stat]始源", "[green](友 建筑攻击 * 150% 建筑血量 * 150% 建造速度 * 150%)", 0, fun() {
            Team.sharded.rules().buildSpeedMultiplier *= 1.5f
            Team.sharded.rules().blockHealthMultiplier *= 1.5f
            Team.sharded.rules().blockDamageMultiplier *= 1.5f
        }))
    }
}

fun Tile.buffText(name: String, desc: String) {
    WorldLabel.create().apply {
        set(this@buffText)
        text = buildString {
            appendLine(name)
            append(desc)
        }
        add()
    }
}

command("getCode", "获得支援码") {
    permission = id.replace("/", ".")
    body {
        returnReply("[cyan]{code}".with("code" to (MapManager.current.id + dailyRandomCode).hashCode().toString()))
    }
}

command("请求支援", "用于获得正面词条<上一关获得的支援码>") {
    usage = "<支援码>"
    body {
        if (arg.firstOrNull() == (MapManager.current.id + dailyRandomCode).hashCode().toString() && !buffed) {
            reply("[green]请求成功！".with())
            buffed = true
            val info = buffs.random(Random(dailyRandomCode + MapManager.current.id)).active()
            Call.setRules(Vars.state.rules)
            if (Vars.state.rules.attackMode) {
                Team.crux.cores().forEach {
                    it.tile.buffText(info.first, info.second)
                }
            } else {
                Vars.spawner.spawns.forEach {
                    it.buffText(info.first, info.second)
                }
            }
            Call.sendMessage(buildString {
                appendLine("[cyan]支援已被响应！")
                appendLine()
                appendLine(info.first)
                append(info.second)
            })
        } else {
            returnReply("[red]ERROR".with())
        }
    }
}


