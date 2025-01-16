@file:Depends("wayzer/map/betterTeam")
@file:Depends("coreMindustry/menu", "调用菜单")
@file:Depends("coreMindustry/contentsTweaker", "修改核心单位")
@file:Import("@coreMindustry/util/tools.kt", sourceFile = true)

import arc.Events
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.util.Time
import coreLibrary.lib.util.loop
import coreMindustry.MenuBuilder
import coreMindustry.lib.command
import coreMindustry.lib.game
import coreMindustry.lib.listen
import coreMindustry.lib.player
import coreMindustry.util.randomColor
import mapScript.lib.modeIntroduce
import mindustry.Vars
import mindustry.ai.types.GroundAI
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.content.UnitTypes.*
import mindustry.entities.Units
import mindustry.entities.units.StatusEntry
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.*
import mindustry.type.StatusEffect
import mindustry.type.UnitType
import mindustry.world.Tile
import mindustry.world.blocks.logic.MessageBlock.MessageBuild
import org.intellij.lang.annotations.Language
import kotlin.math.ceil
import kotlin.random.Random

val betterTeam = contextScript<wayzer.map.BetterTeam>()

name = "RWD IV"
modeIntroduce(
    "随机世界竞技场-IV", buildString {
        appendLine("[yellow]欢迎来到[cyan]星忆[orange]竞技场!")
        append("[cyan]选择你的闪耀之星,在多轮竞技中战胜对手!")
    }
)

val contentPatch
    @Language("JSON5")
    get() = """
{
  "unit": {
    "gamma": {
      "coreUnitDock": true,
      "targetPriority": -2,
      "lowAltitude": false,
      "speed": 10,
      "targetable": false,
      "hittable": false,
      "weapons.0.bullet.damage": 0
    },
    "beta": {
      "coreUnitDock": true,
      "targetPriority": -2,
      "lowAltitude": false,
      "speed": 10,
      "targetable": false,
      "hittable": false,
      "weapons.0.bullet.damage": 0
    },
    "alpha": {
      "coreUnitDock": true,
      "targetPriority": -2,
      "lowAltitude": false,
      "speed": 10,
      "targetable": false,
      "hittable": false,
      "weapons.0.bullet.damage": 0
    },
    "dagger": {
      "weapons.0.bullet.damage": 12
    },
    "stell": {
      "health": 190,
      "weapons.0.bullet.damage": 20
    },
    "nova": {
      "weapons.0.bullet.lifetime": 40
    },
    "pulasr": {
      "weapons.0.bullet.lightningLength": 14
    },
    "quasar": {
      "weapons.0.bullet.length": 170
    },
    "vela": {
      
    },
    "corvus": {
      
    },
    "merui": {
      "health": 180,
      "weapons.0.bullet.splashDamage": 18,
    },
    "elude": {
      "health": 150,
      "weapons.0.bullet.damage": 8
    },
    "locus": {
      "health": 620,
      "weapons.0.bullet.damage": 18
    },
    "cleroi": {
      "health": 460,
      "weapons.0.bullet.damage": 24,
      "weapons.0.bullet.splashDamage": 12,
      "weapons.2.bullet.damage": 9,
    },
    "precept": {
      "health": 1100,
      "weapons.0.bullet.damage": 30
    },
    "anthicus": {
      "health": 840,
      "weapons.0.bullet.spawnUnit.weapons.0.bullet.splashDamage": 40,
      "weapons.0.bullet.spawnUnit.targetable": false,
    },
  },
  "status": {
    "spore-slowed": {
      "damage": -0.05
    },
    "corroded": {
      "damage": 0.2,
      "damageMultiplier": 1.5,
      "speedMultiplier": 1.5,
    },
    "shielded": {
      "healthMultiplier": 1.4,
      "damageMultiplier": 0.75,
      "speedMultiplier": 0.75
    },
  }
}
"""

val teamTile = Blocks.metalFloor5
val enemyTile = Blocks.darkPanel3

var start = false
var stage = 0

class StarStar(
    val name: String,
    val units: List<UnitType>,
    val desc: String = "",
)

val teams by autoInit { mutableListOf(Team.blue, Team.neoplastic, Team.malis, Team.green) }
val allTeams = listOf(Team.blue, Team.neoplastic, Team.malis, Team.green)

val debuffs = listOf(StatusEffects.sapped, StatusEffects.melting, StatusEffects.electrified)
val buffs = listOf(StatusEffects.overclock, StatusEffects.overclock, StatusEffects.boss)

val starstars = buildList {
    add(StarStar("<未选定>", listOf(dagger, dagger, dagger, dagger, dagger)))
    add(StarStar("<根除者>", listOf(dagger, mace, fortress, scepter, reign)))
    add(StarStar("<歼星炮>", listOf(nova, pulsar, quasar, vela, corvus)))
    add(StarStar("<践踏者>", listOf(elude, atrax, spiroct, arkyid, toxopid)))
    add(StarStar("[征服]", listOf(stell, locus, precept, vanquish, conquer)))
    add(StarStar("[君临]", listOf(merui, cleroi, anthicus, tecta, collaris)))
}

fun Team.chooseStarUnit(unit: StarStar) {
    data.starstar = unit
    data.starstarL.text = "[cyan]${unit.name}\n[white]${unit.units.joinToString("") { it.emoji() }}"
}

class Memory(
    val name: String,
    val desc: String,
    val level: Int,
    val cond: (Team.() -> Unit)? = null,
    val starEffect: (mindustry.gen.Unit.() -> Unit)? = null,
    val teamEffect: (Team.() -> Unit)? = null,
    val vicEffect: (Team.() -> Unit)? = null,
    val defEffect: (Team.() -> Unit)? = null,
)

val memorys = buildList {
    add(buildList {
        add(Memory("战友", "添加一些T1到战场", 1, teamEffect = {
            val normalFactory = NormalSpawnFactory(data.randomSpawnVec(), data.starstar.units[0], 3)
            normalFactory.createLabel(this)
            data.unitSpawnFactors.add(normalFactory)
        }))
        add(Memory("挫伤经历", "获得一个负面buff和一个正面buff", 1, teamEffect = {
            data.unitSpawnFactors.find { it is StarSpawnFactory }?.buffs?.addAll(listOf(debuffs.random(), buffs.random()))
        }))
        add(Memory("恐怖袭击", "添加一些爬行者到战场", 1, teamEffect = {
            val normalFactory = NormalSpawnFactory(data.randomSpawnVec(), UnitTypes.crawler, 3)
            normalFactory.createLabel(this)
            data.unitSpawnFactors.add(normalFactory)
        }))
        add(Memory("浪人", "获得25%最大生命值护盾", 1, starEffect = {
            shield += maxHealth * 0.25f
        }))
        add(Memory("孢子寄生", "获得一层孢子", 1, teamEffect = {
            data.unitSpawnFactors.find { it is StarSpawnFactory }?.buffs?.add(StatusEffects.sporeSlowed)
        }))
        add(Memory("神眷", "获得一层弱化的保护", 1, teamEffect = {
            data.unitSpawnFactors.find { it is StarSpawnFactory }?.buffs?.add(StatusEffects.shielded)
        }))
        add(Memory("陌生情谊", "添加一些随机T1到战场", 1, teamEffect = {
            val normalFactory = NormalSpawnFactory(data.randomSpawnVec(), starstars.random().units[0], 3)
            normalFactory.createLabel(this)
            data.unitSpawnFactors.add(normalFactory)
        }))
        add(Memory("护核者", "单位血量*1.1", 1, teamEffect = {
            rules().unitHealthMultiplier *= 1.1f
            Call.setRules(Vars.state.rules)
        }))
        add(Memory("超绝之人", "将90%生命转为护盾", 1, starEffect = {
            health *= 0.1f
            shield += maxHealth * 0.9f
        }))
        add(Memory("武器保养", "单位攻击*1.1", 1, teamEffect = {
            rules().unitDamageMultiplier *= 1.1f
            Call.setRules(Vars.state.rules)
        }))
    })
    add(buildList {
        add(Memory("战友+", "添加一些T2到战场", 2, teamEffect = {
            val normalFactory = NormalSpawnFactory(data.randomSpawnVec(), data.starstar.units[1], 2)
            normalFactory.createLabel(this)
            data.unitSpawnFactors.add(normalFactory)
        }))
        add(Memory("正面情感", "获得一个正面buff", 2, teamEffect = {
            data.unitSpawnFactors.find { it is StarSpawnFactory }?.buffs?.addAll(listOf(buffs.random()))
        }))
        add(Memory("伙伴", "添加很多T1到战场", 2, teamEffect = {
            repeat(3) {
                val normalFactory = NormalSpawnFactory(data.randomSpawnVec(), data.starstar.units[0], 3)
                normalFactory.createLabel(this)
                data.unitSpawnFactors.add(normalFactory)
            }
        }))
        add(Memory("电击疗法", "获得麻痹debuff\n对附近单位施加电击", 2, starEffect = {
            apply(StatusEffects.electrified, Float.POSITIVE_INFINITY)
            launch(Dispatchers.game) {
                while(!dead) {
                    Units.nearbyEnemies(team, x, y, 20 * 8f) { u ->
                        u.apply(StatusEffects.electrified, 12f * 60f)
                        u.apply(StatusEffects.shocked)
                    }
                    yield()
                }
            }
        }))
        add(Memory("不屈霸王", "如果你的队伍生命值最低\n获得1层boss", 2, starEffect = {
            if (teams.minBy { it.data.health } == team) {
                repeat(1) { statuses.add(StatusEntry().set(StatusEffects.boss, Float.POSITIVE_INFINITY)) }
            }
        }))
        add(Memory("孢子寄生+", "获得3层孢子", 2, teamEffect = {
            repeat(3) {
                data.unitSpawnFactors.find { it is StarSpawnFactory }?.buffs?.add(StatusEffects.sporeSlowed)
            }
        }))
        add(Memory("好伙计", "添加一只T3到战场", 2, teamEffect = {
            val normalFactory = NormalSpawnFactory(data.randomSpawnVec(), data.starstar.units[2], 1)
            normalFactory.createLabel(this)
            data.unitSpawnFactors.add(normalFactory)
        }))
        add(Memory("混沌召唤师", "添加一些随机T2到战场", 2, teamEffect = {
            val normalFactory = NormalSpawnFactory(data.randomSpawnVec(), starstars[Random.nextInt(1, starstars.size)].units[1], 2)
            normalFactory.createLabel(this)
            data.unitSpawnFactors.add(normalFactory)
        }))
        add(Memory("武器匠师", "单位攻击*1.3", 2, teamEffect = {
            rules().unitDamageMultiplier *= 1.3f
            Call.setRules(Vars.state.rules)
        }))
        add(Memory("团队训练", "现在的全体单位获得一层buff", 2, teamEffect = {
            data.unitSpawnFactors.forEach {
                it.buffs.add(buffs.random())
            }
        }))
        add(Memory("易碎王冠", "胜利时单位攻击血量*1.1\n失败时单位攻击血量/1.1", 2, vicEffect = {
            rules().unitDamageMultiplier *= 1.1f
            rules().unitHealthMultiplier *= 1.1f
            Call.setRules(Vars.state.rules)
        }, defEffect = {
            rules().unitDamageMultiplier /= 1.1f
            rules().unitHealthMultiplier /= 1.1f
            Call.setRules(Vars.state.rules)
        }))
        add(Memory("诅咒冠冕", "单位攻击血量*1.2\n失败时额外损失一滴血", 2, vicEffect = {
            rules().unitDamageMultiplier *= 1.2f
            rules().unitHealthMultiplier *= 1.2f
        }, defEffect = {
            data.health--
        }))
    })
    add(buildList {
        add(Memory("角斗士", "获得2层boss", 3, teamEffect = {
            repeat(2) {
                data.unitSpawnFactors.find { it is StarSpawnFactory }?.buffs?.add(StatusEffects.boss)
            }
        }))
        add(Memory("魔鬼训练", "现在的全体单位获得俩层buff", 3, teamEffect = {
            data.unitSpawnFactors.forEach {
                it.buffs.addAll(listOf(buffs.random(), buffs.random()))
            }
        }))
        add(Memory("终·混沌召唤", "添加很多随机T2到战场", 3, teamEffect = {
            repeat(5) {
                val normalFactory = NormalSpawnFactory(data.randomSpawnVec(), starstars[Random.nextInt(1, starstars.size)].units[1], 2)
                normalFactory.createLabel(this)
                data.unitSpawnFactors.add(normalFactory)
            }
        }))
        add(Memory("狼群领袖", "添加一些T3到战场", 3, teamEffect = {
            repeat(2) {
                val normalFactory = NormalSpawnFactory(data.randomSpawnVec(), data.starstar.units[2], 2)
                normalFactory.createLabel(this)
                data.unitSpawnFactors.add(normalFactory)
            }
        }))
        add(Memory("传说级武器!", "单位攻击*1.6", 3, teamEffect = {
            rules().unitDamageMultiplier *= 1.6f
            Call.setRules(Vars.state.rules)
        }))
        add(Memory("最后的251S", "死亡倒计时..", 3, teamEffect = {
            data.unitSpawnFactors.forEach {
                it.buffs.addAll(listOf(StatusEffects.corroded,StatusEffects.corroded))
            }
        }))
        add(Memory("背水一战", "如果你的队伍仅剩一条命\n获得3层boss", 3, starEffect = {
            if (team.data.health == 1) {
                repeat(3) { statuses.add(StatusEntry().set(StatusEffects.boss, Float.POSITIVE_INFINITY)) }
            }
        }))
    })
}

open class UnitSpawnFactory(
    val vec: Vec2,
    var amount: Int,
    val buffs: MutableList<StatusEffect> = mutableListOf(),
) {
    lateinit var location: Tile
    lateinit var label: WorldLabel
    fun getLocation(spawnTile: Tile, team: Team, reverse: Boolean = false, rand: Boolean = false) {
        val nvec = Vec2(vec.x, vec.y)
        if (reverse) nvec.rotate(180f)
        val x = spawnTile.x * 8f + nvec.x + if (rand) Random.nextInt(-8, 8) else 0
        val y = spawnTile.y * 8f + nvec.y + if (rand) Random.nextInt(-8, 8) else 0
        location = Vars.world.tileWorld(x, y)
    }
    open fun createLabel(team: Team) { }
    open fun spawn(spawnTile: Tile, team: Team, reverse: Boolean = false, info: BattleInfo) { }
}

class NormalSpawnFactory(
    vec: Vec2,
    val unit: UnitType,
    amount: Int,
): UnitSpawnFactory(vec, amount) {
    override fun spawn(spawnTile: Tile, team: Team, reverse: Boolean, info: BattleInfo) {
        repeat(amount) {
            getLocation(spawnTile, team, reverse,true)
            unit.create(team).apply {
                set(location)
                snapInterpolation()
                if (buffs.isNotEmpty())
                    statuses.addAll(buildList { buffs.forEach { add(StatusEntry().set(it, Float.POSITIVE_INFINITY)) } })
                add()
                unit2Info[this] = info
            }
        }
    }
    override fun createLabel(team: Team) {
        getLocation(team.data.teamSpawnTile, team)
        val unitlabel = WorldLabel.create().apply {
            set(location)
            fontSize = 2f
            snapInterpolation()
            add()
        }
        launch(Dispatchers.game) {
            while (unitlabel.isAdded) {
                unitlabel.apply {
                    getLocation(team.data.teamSpawnTile, team)
                    set(location)
                    text = buildString {
                        append(unit.emoji().repeat(amount))
                        buffs.forEach {
                            append(it.emoji())
                        }
                    }
                    snapInterpolation()
                }
                delay(1000)
            }
        }
    }
}

class StarSpawnFactory(
    vec: Vec2,
): UnitSpawnFactory(vec, 1) {
    override fun spawn(spawnTile: Tile, team: Team, reverse: Boolean, info: BattleInfo) {
        getLocation(spawnTile, team, reverse)
        val unitType = team.data.starstar.units[team.data.starlevel]
        val u = unitType.create(team).apply {
            set(location)
            snapInterpolation()
            if (buffs.isNotEmpty())
                statuses.addAll(buildList { buffs.forEach { add(StatusEntry().set(it, Float.POSITIVE_INFINITY)) } })
            add()
            Call.effect(Fx.dynamicSpikes, x, y, 50f, team.color)
        }
        if (team.isHost(info)) {
            info.hostStar = u
        } else {
            info.guestStar = u
        }
        unit2Info[u] = info
    }
    override fun createLabel(team: Team) {
        getLocation(team.data.teamSpawnTile, team)
        val unitlabel = WorldLabel.create().apply {
            set(location)
            fontSize = 2f
            z = 999f
            snapInterpolation()
            add()
        }
        launch(Dispatchers.game) {
            while (unitlabel.isAdded) {
                unitlabel.apply {
                    getLocation(team.data.teamSpawnTile, team)
                    set(location)
                    text = buildString {
                        append(team.data.starstar.units[team.data.starlevel].emoji())
                        buffs.forEach {
                            append(it.emoji())
                        }
                        appendLine()
                        append("${randomColor()}闪${randomColor()}耀${randomColor()}之${randomColor()}星")
                    }
                    snapInterpolation()
                }
                delay(1000)
            }
        }
    }
}

class TeamData(
    var starstarT: Tile = Vars.world.tile(0,0),
    var starstar: StarStar = starstars[0],
    val memoriesT: Array<Tile?> = arrayOfNulls(99),
    var memories: Array<Memory?> = arrayOfNulls(99),

    var health: Int = 10,
    val starmenu: MutableList<StarStar> = mutableListOf(),
    val unitSpawnFactors: MutableList<UnitSpawnFactory> = mutableListOf(),
    var starlevel: Int = 0,
    var memoryUnlock: Int = 0
) {
    lateinit var starstarL: WorldLabel
    val memoriesL= mutableListOf<WorldLabel>()

    lateinit var teamSpawnTile: Tile
    lateinit var teamSpawnArea: Pair<Float, Float>
    lateinit var enemySpawnTile: Tile

    private val seed = Random.nextInt()

    val mChosenMap = mutableMapOf<Int, Boolean>()

    fun randomMemory(level: Int, index: Int): Set<Memory> {
        return buildSet {
            var i = 0
            while (size < 3) {
                i++
                add(memorys[level].random(Random(seed + index + i * 10000)))
            }
        }
    }

    fun randomSpawnVec(): Vec2 {
        val x = teamSpawnArea.first * 2 * Random.nextFloat() - teamSpawnArea.first
        val y = teamSpawnArea.second * 2 * Random.nextFloat() - teamSpawnArea.second
        return Vec2(x,y)
    }

    fun init() {
        health = 10
        starlevel = 0
        memoryUnlock = 0
        val nList = starstars.toMutableList()
        nList.removeAt(0)
        repeat(2) {
            val nStar = nList.random()
            starmenu.add(nStar)
            nList.remove(nStar)
        }
    }
}

class BattleInfo(
    val host: Team,
    val guest: Team,
    val mirror: Boolean = false,
    var ended: Boolean = false
) {
    lateinit var hostStar: mindustry.gen.Unit
    lateinit var guestStar: mindustry.gen.Unit
}

fun Team.isHost(info: BattleInfo): Boolean {
    return info.host == this
}

val team2Data by autoInit { mutableMapOf<Team, TeamData>() }

val Team.data get() = team2Data.getOrPut(this) { TeamData() }

val unit2Info by autoInit { mutableMapOf<mindustry.gen.Unit, BattleInfo>() }

val mindustry.gen.Unit.batInfo get() = unit2Info.getOrDefault(this, null)

fun memoryLevel(index: Int): Int {
    return when (index % 6) {
        0 -> 1
        1 -> 1
        2 -> 2
        3 -> 1
        4 -> 2
        5 -> 3
        else -> 1
    } - 1
}

class AreaAI(): GroundAI() {
    override fun updateMovement() {
        var move = true
        /*
        if (target != null && unit.hasWeapons()) {
            if (unit.type.circleTarget) {
                circleAttack(120f)
            } else {
                moveTo(target, unit.type.range * 0.6f)
                unit.lookAt(target)
            }
            move = false
        }

         */
        val info = unit.batInfo
        if (info != null && target == null) {
            val enemyStar = if (unit.team.isHost(info)) info.guestStar else info.hostStar
            if (!enemyStar.dead && !unit.within(enemyStar, unit.type.weapons.minOf { it.range() } * 0.8f)) {
                if (move) {
                    moveTo(enemyStar, unit.type.weapons.minOf { it.range() } * 0.6f)
                    unit.lookAt(enemyStar)
                }
            }
        }
        if (unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.riseSpeed)
        }
        faceTarget()
    }
}

var aiTeamEnable = false

command("aiTeam", "") {
    permission = id.replace("/", ".")
    body {
        aiTeamEnable = true
    }
}

onEnable {
    aiTeamEnable = false
    contextScript<coreMindustry.ContentsTweaker>().addPatch("100031",contentPatch)
    Vars.state.rules.apply {
        possessionAllowed = false
    }
    Call.setRules(Vars.state.rules)
    loop(Dispatchers.game) {
        Groups.player.forEach {
            if (it.team() == Team.sharded && start) {
                betterTeam.randomTeam(it, forcePVP = true)
            }
            if (it.unit().spawnedByCore) {
                it.unit().apply(StatusEffects.disarmed, 99999f)
            }
        }
        yield()
    }
    loop(Dispatchers.game) {
        if (start) {
            Call.setHudText(buildString {
                teams.sortedBy { -it.data.health }.forEach {
                    appendLine("[#${it.color}]${Iconc.itemThorium.toString().repeat(it.data.health)}")
                }
            })
        }
        delay(500)
    }
	loop(Dispatchers.game) {
        allTeams.forEach a@{
                it.data().getBuildings(Blocks.worldMessage).forEach {
                    it as MessageBuild
                    val text = it.message.split(":")
                    if (text.getOrNull(0) == "@reg") {
                        if (text.getOrNull(1) == "starstar") {
                            it.team.data.starstarT = it.tile
                            it.kill()
                        }
                        if (text.getOrNull(1) == "memory") {
                            if (text.getOrNull(2)?.toIntOrNull() != null) {
                                it.team.data.memoriesT[text.getOrNull(2)!!.toInt() - 1] = it.tile
                                it.kill()
                            }
                        }
                        if (text.getOrNull(1) == "areaA" && text.getOrNull(2) != null) {
                            val text2 = text[2].split(",")
                            val x = text2.getOrNull(0)?.toFloatOrNull() ?: return@a
                            val y = text2.getOrNull(1)?.toFloatOrNull() ?: return@a
                            it.team.data.teamSpawnTile = it.tile
                            it.team.data.teamSpawnArea = (x * 8f) to (y * 8f)
                            it.kill()
                        }
                        if (text.getOrNull(1) == "areaB") {
                            it.team.data.enemySpawnTile = it.tile
                            it.kill()
                        }
                    }
                }
            }
        delay(100)
    }
    launch(Dispatchers.game) {
        stage = 0
        var startTime = Time.millis()
        while (Time.millis() - 30_000 <= startTime) {
            Call.setHudText(buildString {
                appendLine("[yellow]等待玩家中${".".repeat((Time.millis() % 1200).toInt() / 200 % 6)}")
                if (aiTeamEnable) {
                    append("[green]AI开启,游戏即将开始..")
                } else {
                    if (Groups.player.size() >= 4)
                        append("[green]游戏即将开始..")
                    else if (Groups.player.size() >= 2)
                        append("[yellow]快速开始需要玩家数:4")
                    else {
                        append("[red]开始最小需要玩家数:2")
                        startTime = Time.millis()
                    }
                }
            })
            if (Time.millis() - 10_000 <= startTime && Groups.player.size() >= 4) {
                break
            }
            delay(100)
        }
        start = true
        stage = 1
        betterTeam.bannedTeam = setOf(Team.sharded)
        betterTeam.updateBannedTeam(forcePVP = true)
        Call.sendMessage("[yellow]游戏开始!")
        allTeams.forEach {
            val starlabel = WorldLabel.create().apply {
                set(it.data.starstarT)
                fontSize = 2f
                text = buildString {
                    appendLine("[cyan]闪耀之星未确定..")
                    append("[lightgray]点击选定")
                }
                snapInterpolation()
                add()
            }
            it.data.starstarL = starlabel
            it.data.memoriesT.forEach b@{ t ->
                if (t == null) return@b
                val momorylabel = WorldLabel.create().apply {
                    set(t)
                    fontSize = 1.5f
                    text = buildString {
                        append("[lightgray]锁定的记忆体..")
                    }
                    snapInterpolation()
                    add()
                }
                it.data.memoriesL.add(momorylabel)
            }
            it.data.init()
        }
        Call.sendMessage("[yellow]请在20s内选定完成${randomColor()}闪${randomColor()}耀${randomColor()}之${randomColor()}星[yellow]!")
        Groups.player.forEach {
            Call.setCameraPosition(it.con, it.core().x, it.core().y)
        }
        delay(20_000)
        allTeams.forEach {
            if (it.data.starstar == starstars[0]) {
                it.chooseStarUnit(it.data.starmenu.random())
            }
            val starFactory = StarSpawnFactory(it.data.randomSpawnVec())
            starFactory.createLabel(it)
            it.data.unitSpawnFactors.add(starFactory)
        }
        Call.sendMessage("[yellow]${randomColor()}闪${randomColor()}耀${randomColor()}之${randomColor()}星[yellow]选择已经锁定!")
        stage = 2
        while (teams.size >= 2) {
            if (stage % 3 == 0) {
                teams.forEach {
                    it.data.memoriesL.getOrNull(it.data.memoryUnlock)?.apply {
                        text = "[cyan]记忆待植入.."
                    }
                    if (Groups.player.count { p -> p.team() == it } == 0 && aiTeamEnable) {
                        chooseMemory(it.data.randomMemory(memoryLevel(it.data.memoryUnlock), it.data.memoryUnlock).random(), it.data.memoryUnlock, it)
                    }
                    it.data.memoryUnlock++
                }
                Call.sendMessage("[yellow][TIPS]有的新的记忆体可植入")
            }
            if (stage % 7 == 0) {
                teams.forEach {
                    if (it.data.starlevel < 2) {
                        it.data.starlevel++
                    }
                }
                Call.sendMessage("[yellow][TIPS]闪耀之星升级!")
            }
            val hostNums = ceil(teams.size / 2f).toInt()
            val teamsCopy = teams.toMutableList()
            val hosts = mutableListOf<Team>()
            repeat(hostNums) {
                val team = teamsCopy.random()
                teamsCopy.remove(team)
                hosts.add(team)
            }
            val infos = buildList {
                var lastTeam = Team.sharded
                hosts.forEach {
                    if (teamsCopy.isNotEmpty()) {
                        lastTeam = teamsCopy.random()
                        teamsCopy.remove(lastTeam)
                        add(BattleInfo(it, lastTeam))
                    } else add(BattleInfo(it, lastTeam, true))
                }

            }
            Call.sendMessage(buildString {
                appendLine("[cyan]竞技场战况播报：")
                infos.forEach {
                    appendLine("[#${it.host.color}]${it.host} [white]vs [#${it.guest.color}]${it.guest}[white]${if (it.mirror) " (镜像)" else ""}")
                }
                append("[cyan]将在20s后开始..")
            })
            delay(20_000)
            infos.forEach {
                it.host.data.unitSpawnFactors.forEach { u ->
                    u.spawn(it.host.data.teamSpawnTile, it.host, false, it)
                }
                it.guest.data.unitSpawnFactors.forEach { u ->
                    u.spawn(it.host.data.enemySpawnTile, it.guest, true, it)
                }
                it.host.data.memories.forEach { m ->
                    m?.starEffect?.invoke(it.hostStar)
                }
                it.guest.data.memories.forEach { m ->
                    m?.starEffect?.invoke(it.guestStar)
                }
            }
            Groups.unit.forEach {
                if (!it.spawnedByCore) {
                    it.controller(AreaAI())
                }
            }
            startTime = Time.millis()
            while (infos.count { !it.ended } >= 1) {
                if (Time.millis() - 60_000 >= startTime) {
                    Groups.unit.forEach { u ->
                        if (!u.spawnedByCore) {
                            u.apply(StatusEffects.fast, Float.POSITIVE_INFINITY)
                            u.apply(StatusEffects.corroded, Float.POSITIVE_INFINITY)
                        }
                    }
                }
                infos.forEach {
                    if (it.ended) return@forEach
                    var winner: Team? = null
                    var loser: Team? = null
                    if (it.guestStar.dead){
                        winner = it.host
                        loser = it.guest
                    }
                    if (it.hostStar.dead) {
                        winner = it.guest
                        loser = it.host
                    }
                    if (winner != null) {
                        repeat(3) { i ->
                            Groups.unit.forEach { u ->
                                if (u.batInfo == it)
                                    u.kill()
                            }
                        }
                        winner.data.memories.forEach {
                            it?.vicEffect?.invoke(winner)
                        }
                        it.ended = true
                        Call.sendMessage(buildString {
                            appendLine("")
                            appendLine("[#${it.host.color}]${it.host} [white]vs [#${it.guest.color}]${it.guest}[white]${if (it.mirror) " (镜像)" else ""}")
                            appendLine("[#${winner.color}]${winner} [yellow]胜利!")
                        })
                        if (!it.mirror || loser == it.host) {
                            loser!!
                            loser.data.memories.forEach {
                                it?.defEffect?.invoke(loser)
                            }
                            loser.data.health -= 1
                            if (loser.data.health <= 0) {
                                teams.remove(loser)
                                Call.sendMessage("[#${loser.color}]${loser} [red]出局!")
                            }
                        }
                    }
                }
                yield()
            }
            stage++
        }
        Call.sendMessage("[#${teams[0].color}]${teams[0]} [yellow]赢得了本场比赛的胜利!")
        Events.fire(EventType.GameOverEvent(teams[0]))
        Vars.state.gameOver = true
    }
}

listen<EventType.TapEvent> {
    if (it.player.team() !in teams) return@listen
    if (it.player.team().data.starstarT.within(it.tile, 16f)) {
        StarMenu(it.player, stage == 1).sendTo()
    }
    it.player.team().data.memoriesT.forEach { t ->
        val index = it.player.team().data.memoriesT.indexOf(t)
        if (t?.within(it.tile, 16f) == true && it.player.team().data.memoryUnlock > index && !it.player.team().data.mChosenMap.getOrDefault(index, false)) {
            MemoryMenu(it.player, memoryLevel(index), index).sendTo()
        }
    }
}

class StarMenu(private val player: Player, private val choose: Boolean): MenuBuilder<Unit>() {
    var tab: Int = 0
    val team = player.team()
    var unit: StarStar = team.data.starstar

    fun sendTo() {
        launch(Dispatchers.game) {
            sendTo(player, 60_000)
        }
    }
    private suspend fun mainMenu() {
        title = "[cyan]闪耀之星"
        msg = "[yellow]选择你队伍的闪耀之星\n[red]选定后不可更改!"

        team.data.starmenu.forEach {
            option("${it.name}\n${it.units.joinToString("") { it.emoji() } }") {
                tab = 1
                unit = it
                refresh()
            }
        }

        newRow()
        option("[white]退出菜单") { }
    }

    private suspend fun infoMenu() {
        title = "[cyan]闪耀之星"
        msg = "[cyan]${unit.name}\n[white]${unit.units.joinToString("") { it.emoji() }}"

        if (choose) {
            option("[green]选定!") {
                if (team == player.team() && stage == 1) {
                    team.chooseStarUnit(unit)
                }
            }
            newRow()
        }
        option("[white]退出菜单") { }
    }

    override suspend fun build() {
        if (!choose)
            tab = 1
        when(tab) {
            0 -> mainMenu()
            1 -> infoMenu()
        }
    }
}

fun chooseMemory(memory: Memory, index: Int, team: Team) {
    team.data.mChosenMap[index] = true
    team.data.memories[index] = memory
    memory.teamEffect?.invoke(team)
    team.data.memoriesL[index].apply {
        text = "[cyan]${memory.name}\n[white]${memory.desc}"
    }
}

class MemoryMenu(private val player: Player, private val level: Int, private val index: Int): MenuBuilder<Unit>() {
    var tab: Int = 0
    val team = player.team()
    lateinit var memory: Memory

    fun sendTo() {
        launch(Dispatchers.game) {
            sendTo(player, 60_000)
        }
    }
    private suspend fun mainMenu() {
        title = "[cyan]记忆植入"
        msg = "[yellow]选择记忆植入你的闪耀之星\n[red]选定后不可更改!"

        team.data.randomMemory(level, index).forEach {
            option("[cyan]${it.name}") {
                tab = 1
                memory = it
                refresh()
            }
        }

        newRow()
        option("[white]退出菜单") { }
    }

    private suspend fun infoMenu() {
        title = "[cyan]记忆植入"
        msg = "[cyan]${memory.name}\n[white]${memory.desc}"

        option("[green]选定!") {
            if (team == player.team() && !team.data.mChosenMap.getOrDefault(index, false)) {
                chooseMemory(memory, index, team)
            }
        }
        newRow()

        option("[white]退出菜单") { }
    }

    override suspend fun build() {
        when(tab) {
            0 -> mainMenu()
            1 -> infoMenu()
        }
    }
}