@file:Depends("xkldklp/KVars")
@file:Depends("coreMindustry/menu", "调用菜单")
@file:Depends("wayzer/voteService", "投票实现")

package mapScript

import arc.graphics.Color
import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import coreMindustry.lib.listen
import mapScript.lib.modeIntroduce
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Blocks.*
import mindustry.content.Items
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.entities.Units
import mindustry.entities.units.StatusEntry
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.defense.Wall
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild
import mindustry.world.blocks.storage.CoreBlock
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

val menu = contextScript<coreMindustry.Menu>()

name = "律令·畸变"

modeIntroduce(
    "律令·畸变", buildString {
        appendLine("[yellow]随波次时间增加将会对全局施加随机畸变效果")
        appendLine("[lightgray]增加速度随畸变等级变化")
        appendLine()
        appendLine("[yellow]建造发射台与行星际发射终端并填充资源可以对我方施加正面律令")
        appendLine()
        append("[yellow]双击核心即可查看已施加的律令/畸变")
    }
)

val number = arrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
val char = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")

fun getRomanNum(level: Int): String {
    if (level <= 0) return ""
    return buildString {
        var num = level
        repeat(13) { i ->
            while (number[i] <= num) {
                num -= number[i]
                append(char[i])
            }
        }
    }
}

val kVars = contextScript<xkldklp.KVars>()

class Rule(
    val name: String,
    val desc: String,

    val level: Int,

    val effect: (Int.() -> Unit)? = null,
    val breakEffect: (Int.() -> Unit)? = null,
    val condition: (Int.() -> Boolean)? = null,
) {
    fun active() {
        effect?.invoke(data.level)
    }
    fun breakRule() {
        breakEffect?.invoke(data.level)
    }


    fun canAppear(): Boolean {
        return condition?.invoke(data.level) ?: true
    }
}

class RuleData(
    var level: Int = 0,
) {
    lateinit var rule: Rule
}
val ruleData by autoInit { mutableMapOf<Rule, RuleData>() }
val Rule.data
    get() = ruleData.getOrPut(this) { RuleData() }
        .also { it.rule = this }

val negativeRules by autoInit {
    listOf(
        listOf(
            Rule("强袭姿态", "加强敌方单位攻击,每一级乘以110%", 1, {
                Vars.state.rules.waveTeam.rules().unitDamageMultiplier *= 1.1f
            }, {
                Vars.state.rules.waveTeam.rules().unitDamageMultiplier /= 1.1f
            }),
            Rule("防御协议", "加强敌方单位防御,每一级乘以110%", 1, {
                Vars.state.rules.waveTeam.rules().unitHealthMultiplier *= 1.1f
            }, {
                Vars.state.rules.waveTeam.rules().unitHealthMultiplier /= 1.1f
            }),
            Rule("反击炮台", "加强敌方建筑攻击,每一级乘以110%", 1, {
                Vars.state.rules.waveTeam.rules().blockDamageMultiplier *= 1.1f
            }, {
                Vars.state.rules.waveTeam.rules().blockDamageMultiplier /= 1.1f
            }, {
                Vars.state.rules.attackMode
            }),
            Rule("固化建筑", "加强敌方建筑血量,每一级乘以110%", 1, {
                Vars.state.rules.waveTeam.rules().blockHealthMultiplier *= 1.1f
            }, {
                Vars.state.rules.waveTeam.rules().blockHealthMultiplier /= 1.1f
            }, {
                Vars.state.rules.attackMode
            }),
            Rule("无力化", "减弱我方单位攻击,每一级乘以90%", 1, {
                Vars.state.rules.defaultTeam.rules().unitDamageMultiplier *= 0.9f
            }, {
                Vars.state.rules.defaultTeam.rules().unitDamageMultiplier /= 0.9f
            }, {
                Vars.state.rules.attackMode
            }),
            Rule("劣化弹药", "减弱我方建筑攻击,每一级乘以90%", 1, {
                Vars.state.rules.defaultTeam.rules().blockDamageMultiplier *= 0.9f
            }, {
                Vars.state.rules.defaultTeam.rules().blockDamageMultiplier /= 0.9f
            }),
            Rule("脆弱结构", "减弱我方建筑血量,每一级乘以90%", 1, {
                Vars.state.rules.defaultTeam.rules().blockHealthMultiplier *= 0.9f
            }, {
                Vars.state.rules.defaultTeam.rules().blockHealthMultiplier /= 0.9f
            }),
            Rule("资源丢失", "我方随机一项资源减半", 1, {
                val list =
                    buildList { Vars.state.rules.defaultTeam.core().items.each { item, amount -> add(item to amount) } }
                if (list.isNotEmpty()) {
                    val item = list.random()
                    Vars.state.rules.defaultTeam.core().items.remove(item.first, item.second / 2)
                }
            }),
            Rule("暗中破坏", "我方墙壁被随机摧毁", 1, {
                Vars.state.rules.defaultTeam.data().buildings.filter { it.block is Wall }.forEach {
                    if (Random.nextFloat() >= 0.75f) {
                        it.kill()
                    }
                }
            }),
        ),
        listOf(
            Rule("运输劣化", "从运输器件中随机禁用,每一级禁用一个", 2, {
                val blocks = listOf(
                    conveyor,
                    titaniumConveyor,
                    plastaniumConveyor,
                    armoredConveyor,
                    distributor,
                    junction,
                    itemBridge,
                    phaseConveyor,
                    sorter,
                    invertedSorter,
                    router,
                    overflowGate,
                    underflowGate,
                    massDriver,
                    duct,
                    armoredDuct,
                    ductRouter,
                    overflowDuct,
                    underflowDuct,
                    ductBridge,
                    ductUnloader,
                    surgeConveyor,
                    surgeRouter,
                )
                val unbanned =
                    blocks.filter { it.environmentBuildable() && !Vars.state.rules.bannedBlocks.contains(it) }
                if (unbanned.isNotEmpty()) {
                    val target = unbanned.random()
                    Vars.state.rules.bannedBlocks.add(target)
                    Call.sendMessage("[white]${target.emoji()} [yellow]已被禁用")
                }
            }, null, {
                !(listOf(
                    conveyor,
                    titaniumConveyor,
                    plastaniumConveyor,
                    armoredConveyor,
                    distributor,
                    junction,
                    itemBridge,
                    phaseConveyor,
                    sorter,
                    invertedSorter,
                    router,
                    overflowGate,
                    underflowGate,
                    massDriver,
                    duct,
                    armoredDuct,
                    ductRouter,
                    overflowDuct,
                    underflowDuct,
                    ductBridge,
                    ductUnloader,
                    surgeConveyor,
                    surgeRouter,
                ).all { it in Vars.state.rules.bannedBlocks || !it.environmentBuildable() })
            }),
            Rule("量子屏障", "敌方每隔10s获得1s可叠加的保护效果,每级独立计算", 2, {
                loop(Dispatchers.game) {
                    Vars.state.rules.waveTeam.data().units.forEach {
                        it.statuses.add(StatusEntry().set(StatusEffects.shielded, 1f * 60f))
                    }
                    delay(10_000)
                }
            }),
            Rule("量子突袭", "敌方每隔5s有25%概率获得1s可叠加的2层加速效果,每级独立计算", 2, {
                loop(Dispatchers.game) {
                    Vars.state.rules.waveTeam.data().units.forEach {
                        if (Random.nextFloat() >= 0.75f)
                            repeat(2) { i ->
                                it.statuses.add(StatusEntry().set(StatusEffects.fast, 1f * 60f))
                            }
                    }
                    delay(10_000)
                }
            }),
            Rule("核心超载", "敌方每隔10s有25%概率获得1s可叠加的超时效果,每级独立计算", 2, {
                loop(Dispatchers.game) {
                    Vars.state.rules.waveTeam.data().units.forEach {
                        if (Random.nextFloat() >= 0.75f)
                            repeat(2) { i ->
                                it.statuses.add(StatusEntry().set(StatusEffects.overclock, 1f * 60f))
                            }
                    }
                    delay(10_000)
                }
            }),
            Rule("资源消散", "我方随机二项资源清空", 2, {
                repeat(2) {
                    val list =
                        buildList { Vars.state.rules.defaultTeam.core().items.each { item, amount -> add(item to amount) } }
                    if (list.isNotEmpty()) {
                        val item = list.random()
                        Vars.state.rules.defaultTeam.core().items.remove(item.first, item.second)
                    }
                }
            }),
            Rule("中级炮台禁用", "从强力中级炮台中随机禁用,每一级禁用一个", 2, {
                val blocks = listOf(
                    cyclone,
                    swarmer,
                    fuse,
                    scorch,
                    afflict,
                    disperse
                )
                val unbanned =
                    blocks.filter { it.environmentBuildable() && !Vars.state.rules.bannedBlocks.contains(it) }
                if (unbanned.isNotEmpty()) {
                    val target = unbanned.random()
                    Vars.state.rules.bannedBlocks.add(target)
                    Call.sendMessage("[white]${target.emoji()} [yellow]已被禁用")
                }
            }, null, {
                !(listOf(
                    cyclone,
                    swarmer,
                    fuse,
                    scorch,
                    afflict,
                    disperse
                ).all { it in Vars.state.rules.bannedBlocks || !it.environmentBuildable() })
            }),
            Rule("功能性散失", "从重要功能性建筑中随机禁用,每一级禁用一个", 2, {
                val blocks = listOf(
                    logicProcessor,
                    microProcessor,
                    hyperProcessor,
                    unloader,
                    overdriveDome,
                    overdriveProjector,
                    buildTower,
                    forceProjector,
                    mender,
                    mendProjector,
                    regenProjector,
                    shockwaveTower,
                    segment
                )
                val unbanned =
                    blocks.filter { it.environmentBuildable() && !Vars.state.rules.bannedBlocks.contains(it) }
                if (unbanned.isNotEmpty()) {
                    val target = unbanned.random()
                    Vars.state.rules.bannedBlocks.add(target)
                    Call.sendMessage("[white]${target.emoji()} [yellow]已被禁用")
                }
            }, null, {
                !(listOf(
                    logicProcessor,
                    microProcessor,
                    hyperProcessor,
                    unloader,
                    overdriveDome,
                    overdriveProjector,
                    buildTower,
                    forceProjector,
                    mender,
                    mendProjector,
                    regenProjector,
                    shockwaveTower,
                    segment
                ).all { it in Vars.state.rules.bannedBlocks || !it.environmentBuildable() })
            }),
        ),
        listOf(
            Rule("洋流", "每60s指定一个随机角度,我方单位若不朝向此则获得缓慢麻痹", 3, {
                loop(Dispatchers.game) {
                    val rot = 360 * Random.nextFloat()
                    Call.sendMessage("[yellow]<洋流>的角度朝向已经改变(${rot}°)")
                    repeat(60) {
                        Vars.state.rules.defaultTeam.data().units.forEach {
                            if (abs(it.rotation - rot) >= 60) {
                                it.apply(StatusEffects.slow, 1.5f * 60f)
                                it.apply(StatusEffects.electrified, 1.5f * 60f)
                            }
                        }
                        delay(1_000)
                    }
                }
            }, null, {
                this < 1
            }),
            Rule("巨兽", "敌方随机一个单位拥有4层bossBuff,死亡时转移给另一个单位", 3, {
                var unit: mindustry.gen.Unit? = null
                loop(Dispatchers.game) {
                    if (unit == null) {
                        val units = Vars.state.rules.waveTeam.data().units
                        if (!units.isEmpty) {
                            unit = units.random()
                            unit!!.apply {
                                repeat(if (hasEffect(StatusEffects.boss)) 3 else 4) {
                                    statuses.add(StatusEntry().set(StatusEffects.boss, Float.POSITIVE_INFINITY))
                                }
                            }
                        }
                    }
                    if (unit?.dead != false || unit?.isValid != true) {
                        unit = null
                    }
                    yield()
                }
            }, null, {
                this < 1
            }),
            Rule("威压", "在敌方24格内的我方单位每5s获得4s缴械", 3, {
                loop(Dispatchers.game) {
                    Vars.state.rules.defaultTeam.data().units.forEach {
                        Units.closest(Vars.state.rules.waveTeam, it.x, it.y, 24 * 8f) { u ->
                            it.apply(StatusEffects.disarmed, 4 * 60f)
                            true
                        }
                    }
                    delay(5_000)
                }
            }, null, {
                this < 1
            }),
            Rule("残光", "我方单位在射击时获得融化并将生命最大值变为当前生命值", 3, {
                loop(Dispatchers.game) {
                    Vars.state.rules.defaultTeam.data().units.forEach {
                        if (it.isShooting) {
                            it.apply(StatusEffects.melting, 0.5f * 60f)
                            it.maxHealth = it.health.coerceAtMost(it.type.health)
                        }
                    }
                    delay(100)
                }
            }, null, {
                this < 1
            }),
            Rule("磁暴", "每60s对全图进行磁暴打击,每级独立计算", 3, {
                loop(Dispatchers.game) {
                    repeat((sqrt(Vars.world.width() * Vars.world.height() * 1.00) / 100f).toInt().coerceAtLeast(1)) {
                        delay(1_000)
                        Call.createBullet(UnitTypes.navanax.weapons[4].bullet, Vars.state.rules.waveTeam, Vars.world.width() * 8f * Random.nextFloat(), Vars.world.height()* 8f  * Random.nextFloat(), 360 * Random.nextFloat(), UnitTypes.navanax.weapons[4].bullet.damage, 0f, 2f)
                    }
                    delay(60_000)
                }
            }, null, {
                this < 3
            }),
        ),
        listOf(
            Rule("终末", "我方炮台仅可存在120s,单位仅可存在240s", 4, {
                loop(Dispatchers.game) {
                    val units = mutableListOf<mindustry.gen.Unit>()
                    Vars.state.rules.defaultTeam.data().units.forEach {
                        if (it !in units) {
                            units.add(it)
                            launch(Dispatchers.game) {
                                delay(240_000)
                                it.kill()
                            }
                        }
                    }
                    yield()
                }
                loop(Dispatchers.game) {
                    val turrets = mutableListOf<TurretBuild>()
                    Vars.state.rules.defaultTeam.data().buildings.filter { it is TurretBuild }.forEach {
                        if (it !in turrets) {
                            turrets.add(it as TurretBuild)
                            launch(Dispatchers.game) {
                                delay(120_000)
                                it.kill()
                            }
                        }
                    }
                    yield()
                }
            }, null, {
                this < 1
            }),
            Rule("落幕", "直接摧毁你所有的炮台和单位,敌方单位获得一次性的永久加速", 4, {
                Vars.state.rules.defaultTeam.data().units.forEach {
                    it.kill()
                }

                Vars.state.rules.defaultTeam.data().buildings.filter { it is TurretBuild }.forEach {
                    it.kill()
                }

                Vars.state.rules.waveTeam.data().units.forEach {
                    it.apply(StatusEffects.fast, Float.POSITIVE_INFINITY)
                }
            }),
            Rule("天神下凡", "每隔60s在你所有的核心上生成一只天蝎和帝君", 4, {
                loop(Dispatchers.game) {
                   delay(60_000)
                    Vars.state.rules.defaultTeam.cores().forEach { c ->
                        listOf(UnitTypes.toxopid, UnitTypes.collaris).forEach {
                            it.create(Vars.state.rules.defaultTeam).apply {
                                set(c)
                                health = c.health
                                maxHealth = c.maxHealth
                                apply(StatusEffects.disarmed, (10f - c.block.size) * 60f)
                                add()
                            }
                        }
                    }
                }
            }, null, {
                this < 1
            }),
        ),
    )
}

val postiveRules by autoInit {
    listOf(
        listOf(
            Rule("我方攻击强化","加强我方攻击,每一级乘以110%", 1, {
                Vars.state.rules.defaultTeam.rules().unitDamageMultiplier *= 1.1f
                Vars.state.rules.defaultTeam.rules().blockDamageMultiplier *= 1.1f
            }),
            Rule("我方防御强化","加强我方防御,每一级乘以110%", 1, {
                Vars.state.rules.defaultTeam.rules().unitHealthMultiplier *= 1.1f
                Vars.state.rules.defaultTeam.rules().blockHealthMultiplier *= 1.1f
            }),
            Rule("我方建筑强化","加强我方建筑构建速度,每一级乘以120%", 1, {
                Vars.state.rules.defaultTeam.rules().buildSpeedMultiplier *= 1.2f
            }),
            Rule("无尽矿藏","每秒获得铜铅铍随机一种,数量等于10倍等级", 1, {
                loop(Dispatchers.game) {
                    Vars.state.rules.defaultTeam.core()?.items?.add(listOf(Items.copper, Items.lead, Items.beryllium).random() , 10)
                    delay(1_000)
                }
            }),
        ),
        listOf(
            Rule("独影成双","你从此方式外获得的独影采矿机将双倍获取", 2, {
                val units = mutableListOf<mindustry.gen.Unit>()
                loop(Dispatchers.game) {
                    Vars.state.rules.defaultTeam.data().units.filter { it.type == UnitTypes.mono && it !in units && !it.spawnedByCore}.forEach {
                        val u = UnitTypes.mono.spawn(Vars.state.rules.defaultTeam, it.x, it.y)
                        units.add(u)
                        units.add(it)
                    }
                    delay(1_000)
                }
            }, null, {
                this < 1 && UnitTypes.mono.supportsEnv(Vars.state.rules.env) && !Vars.state.rules.bannedUnits.contains(UnitTypes.mono)
            }),
            Rule("超频信号","你队伍的单位将会获得超频buff", 2, {
                loop(Dispatchers.game) {
                    Vars.state.rules.defaultTeam.data().units.forEach {
                        it.apply(StatusEffects.overdrive, Float.POSITIVE_INFINITY)
                    }
                    delay(1_000)
                }
            }, null, {
                this < 1
            }),
            Rule("无尽工厂","每秒获得硅玻璃石墨随机一种,数量等于10倍等级", 2, {
                loop(Dispatchers.game) {
                    Vars.state.rules.defaultTeam.core()?.items?.add(listOf(Items.silicon, Items.metaglass, Items.graphite).random() , 10)
                    delay(1_000)
                }
            }),
            Rule("畸变修正","应用时随机修正等级数的可修正畸变", 2, {
                repeat(this + 1) {
                    val rules = activeRules.filter { negativeRules.any { l -> it in l } && it.breakEffect != null }
                    if (rules.isNotEmpty()) {
                        val rule = rules.random()
                        val highestRule = activeRules.filter { it.name == rule.name }.maxBy { it.level }
                        highestRule.breakRule()
                        activeRules.remove(highestRule)
                        Call.sendMessage("[green]${highestRule.name} ${getRomanNum(highestRule.data.level)}\n${highestRule.desc}\n[green]已被修正！")
                    }
                }
            }),
        ),
        listOf(
            Rule("最强之人","核心机获得无敌", 3, {
                loop(Dispatchers.game) {
                    Vars.state.rules.defaultTeam.data().units.filter { it.spawnedByCore }.forEach {
                        it.apply(StatusEffects.invincible, Float.POSITIVE_INFINITY)
                    }
                    delay(1_000)
                }
            }, null, {
                this < 1
            }),
            Rule("英勇信号","你队伍的单位将会获得超时bossbuff", 3, {
                loop(Dispatchers.game) {
                    Vars.state.rules.defaultTeam.data().units.forEach {
                        it.apply(StatusEffects.overclock, Float.POSITIVE_INFINITY)
                        it.apply(StatusEffects.boss, Float.POSITIVE_INFINITY)
                    }
                    delay(1_000)
                }
            }, null, {
                this < 1
            }),
            Rule("电能涌动","敌方每0.2s受到一次电击与爆炸判定,每级独立计算", 3, {
                loop(Dispatchers.game) {
                    Vars.state.rules.waveTeam.data().units.forEach {
                        it.apply(StatusEffects.shocked)
                        it.apply(StatusEffects.blasted)
                    }
                    delay(200)
                }
            }),
            Rule("君临之雨","每30s降下大量帝君炮弹,每级独立计算", 3, {
                loop(Dispatchers.game) {
                    val rot = 360 * Random.nextFloat()
                    repeat((sqrt(Vars.world.width() * Vars.world.height() * 1.00) / 10f).toInt().coerceAtLeast(1)) {
                        delay(100)
                        Call.createBullet(UnitTypes.collaris.weapons[0].bullet, Vars.state.rules.defaultTeam, Vars.world.width() * 8f * Random.nextFloat(), Vars.world.height()* 8f  * Random.nextFloat(), rot, UnitTypes.collaris.weapons[0].bullet.damage, 2f, 1f)
                    }
                    delay(60_000)
                }
            }),
        ),
        listOf(
            Rule("掀桌","解禁所有被ban物品与单位,单位建筑伤害*2", 4, {
                Vars.state.rules.bannedBlocks.clear()
                Vars.state.rules.bannedUnits.clear()
                Vars.state.rules.defaultTeam.rules().unitDamageMultiplier *= 2f
                Vars.state.rules.defaultTeam.rules().blockDamageMultiplier *= 2f
            }, null, {
                this < 1
            }),
            Rule("光洁圣甲","你的单位免疫一些debuff,并获得加速与保护", 4, {
                loop(Dispatchers.game) {
                    Vars.state.rules.defaultTeam.data().units.forEach {
                        it.unapply(StatusEffects.slow)
                        it.unapply(StatusEffects.electrified)
                        it.unapply(StatusEffects.melting)
                        it.unapply(StatusEffects.sapped)
                        it.unapply(StatusEffects.freezing)
                        it.apply(StatusEffects.fast, Float.POSITIVE_INFINITY)
                        it.apply(StatusEffects.shielded, Float.POSITIVE_INFINITY)
                    }
                    yield()
                }
            }, null, {
                this < 1
            }),
            Rule("资源盛宴","资源填满核心！", 4, {
                Vars.content.items().forEach {
                    Vars.state.rules.defaultTeam.core()?.items()?.add(it, Vars.state.rules.defaultTeam.core().storageCapacity)
                }
            }),
        )
    )
}

val activeRules by autoInit { mutableListOf<Rule>() }

val waves get() = Vars.state.wave
val time get() = ((Vars.state.wave + 1) * Vars.state.rules.waveSpacing - Vars.state.wavetime + Vars.state.rules.initialWaveSpacing) / 60

var ruleLevel = 0
var level = 0
var postiveLevel = 0
onEnable {
    level = 0
    postiveLevel = 0
    ruleLevel = (kVars.ruleMode?.ruleLevel ?: Vars.state.rules.tags.getInt("@ruleLevel", 1)).coerceAtLeast(1).coerceAtMost(10)
    Vars.state.rules.apply {
        modeName = "[red]畸变 ${getRomanNum(ruleLevel)}"
        revealedBlocks.apply {
            add(launchPad)
            add(interplanetaryAccelerator)
        }
    }
    Call.setRules(Vars.state.rules)
    loop(Dispatchers.game) {
        if (time >= (300 / ruleLevel) * (level + 1)) {
            level++
            Call.sendMessage("[yellow]畸变程度加深！")
            var addRule: Rule
            addRule = negativeRules[0].filter { it.canAppear() }.random()
            if (level % 4 == 0) {
                if (negativeRules[1].filter { it.canAppear() }.isNotEmpty())
                    addRule = negativeRules[1].filter { it.canAppear() }.random()
            }
            if (level % 12 == 0) {
                if (negativeRules[2].filter { it.canAppear() }.isNotEmpty())
                    addRule = negativeRules[2].filter { it.canAppear() }.random()
            }
            if (level % 48 == 0) {
                if (negativeRules[3].filter { it.canAppear() }.isNotEmpty())
                    addRule = negativeRules[3].filter { it.canAppear() }.random()
            }
            if (addRule in activeRules) {
                addRule.data.level++
            } else {
                activeRules.add(addRule)
                addRule.data.level++
            }
            Call.sendMessage("[red]${addRule.name} ${getRomanNum(addRule.data.level)}\n${addRule.desc}")
            addRule.active()
            Call.setRules(Vars.state.rules)
        }
        var score = 0
        Vars.state.rules.defaultTeam.data().getBuildings(launchPad).forEach {
            score += it.items.total()
        }
        Vars.state.rules.defaultTeam.data().getBuildings(interplanetaryAccelerator).forEach {
            score += it.items.total() * 2
        }
        if (score >= (postiveLevel + 3f).pow(1.5f) * 100f) {
            postiveLevel++
            Call.sendMessage("[yellow]新的律令施加！")
            var addRule: Rule
            addRule = postiveRules[0].filter { it.canAppear() }.random()
            if (postiveLevel % 4 == 0) {
                if (postiveRules[1].filter { it.canAppear() }.isNotEmpty())
                    addRule = postiveRules[1].filter { it.canAppear() }.random()
            }
            if (postiveLevel % 12 == 0) {
                if (postiveRules[2].filter { it.canAppear() }.isNotEmpty())
                    addRule = postiveRules[2].filter { it.canAppear() }.random()
            }
            if (postiveLevel % 48 == 0) {
                if (postiveRules[3].filter { it.canAppear() }.isNotEmpty())
                    addRule = postiveRules[3].filter { it.canAppear() }.random()
            }
            if (addRule in activeRules) {
                addRule.data.level++
            } else {
                activeRules.add(addRule)
                addRule.data.level++
            }
            Call.sendMessage("[green]${addRule.name} ${getRomanNum(addRule.data.level)}\n${addRule.desc}")
            addRule.active()
            Call.setRules(Vars.state.rules)
        }
        yield()
    }
}

val playerLastTapTile: MutableMap<String, Pair<Tile, Int>> by autoInit { mutableMapOf() }
listen<EventType.TapEvent> {
    val player = it.player
    if (player.dead()) return@listen
    playerLastTapTile[player.uuid()] = it.tile to if (it.tile == (playerLastTapTile[player.uuid()]?.first ?: false)) (playerLastTapTile[player.uuid()]?.second?.plus(1) ?: 1) else 1
    if ((playerLastTapTile[player.uuid()]?.second ?: 0) >= 2){
        launch(Dispatchers.game) {
            if (it.tile.block() is CoreBlock)
                menu.menuBuilder("已经施加的律令/畸变") {
                    activeRules.forEach {
                        val secondaryColorValue = 255 / (it.level + 1)
                        option("${if (negativeRules.any { l -> it in l }) "[#${Color.rgb(255, secondaryColorValue, secondaryColorValue)}]" else "[#${Color.rgb(secondaryColorValue, 255, secondaryColorValue)}]"}${it.name} ${getRomanNum(it.data.level)}") { refresh() }
                        newRow()
                        option(it.desc) { refresh() }
                        newRow()
                        option("") { refresh() }
                        newRow()
                    }
                    option("退出") { }
                }.sendTo(player)
        //if (it.tile.block() is LaunchPad) player.(it.tile.build as LaunchPad.LaunchPadBuild)
        }
    }
}