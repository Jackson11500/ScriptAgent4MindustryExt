@file:Depends("coreMindustry/menu", "调用菜单")
@file:Depends("coreMindustry/contentsTweaker", "修改核心单位,单位属性")
@file:Depends("wayzer/voteService", "投票实现")
@file:Depends("wayzer/map/betterTeam", "更改玩家队伍")
@file:Depends("wayzer/user/achievement", "成就")

package mapScript

import arc.math.Mathf
import arc.math.geom.Geometry
import arc.math.geom.Point2
import arc.util.Time
import coreLibrary.lib.util.loop
import coreLibrary.lib.with
import coreMindustry.lib.*
import mindustry.Vars
import mindustry.ai.Pathfinder
import mindustry.ai.types.FlyingAI
import mindustry.ai.types.GroundAI
import mindustry.content.Blocks
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.*
import mindustry.type.UnitType
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import org.intellij.lang.annotations.Language
import wayzer.VoteService
import wayzer.lib.dao.PlayerData
import wayzer.map.BetterTeam
import kotlin.math.ceil
import kotlin.random.Random

/** @author xkldklp */

val menu = contextScript<coreMindustry.Menu>()
val betterTeam = contextScript<BetterTeam>()

val achievement = contextScript<wayzer.user.Achievement>()
fun Player.achievement(name: String, exp: Int) {
    val profile = PlayerData[uuid()].profile
    if (profile != null)
        achievement.finishAchievement(profile, name, exp, true)
}

fun Float.format(i: Int = 2): String {
    return "%.${i}f".format(this)
}

fun Float.buildLineBar(length: Int = 20, max: Float = 20f, color: Pair<Pair<String, String>, String> = Pair(Pair("[yellow]","[green]"), "[red]")): String {
    val num = this
    return buildString {
        repeat(length) {
            append("${
                when {
                    num > it * (max / length) + max -> color.first.first
                    num > it * (max / length) -> color.first.second
                    else -> color.second
                }
            }|")
        }
    }
}

fun Int.buildLineBar(length: Int = 20, max: Int = 20, color: Pair<Pair<String, String>, String> = Pair(Pair("[yellow]","[green]"), "[red]")): String {
    return toFloat().buildLineBar(length, max.toFloat(), color)
}

/**
 * @return 无法找到合适位置，返回null
 */
fun UnitType.spawnAround(pos: Posc, team: Team, radius: Int = 10): mindustry.gen.Unit? {
    return create(team).apply {
        set(pos)
        val valid = mutableListOf<Point2>()
        Geometry.circle(tileX(), tileY(), radius) { x, y ->
            if (canPass(x, y) && (isGrounded || Vars.world.tile(x, y)?.floor()?.isDeep == false))
                valid.add(Point2(x, y))
        }
        val r = valid.randomOrNull() ?: return null
        x = r.x * Vars.tilesize.toFloat()
        y = r.y * Vars.tilesize.toFloat()
        add()
    }
}

val contentPatch
    @Language("JSON5")
    get() = """
{
  "unit": {
    "mono": {
      "targetable": false,
      "hittable": false,
      "speed": 7.5,
      "rotateSpeed": 8,
      "buildSpeed": 0.5
    },
    "dagger": {
      "health": 210,
      "armor": 1,
      "weapons.0.bullet.collidesAir": false,
      "targetAir": false,
      "abilities.0.amount": 5
    },
    "merui": {
      "health": 150,
      "armor": 0,
      "weapons.0.bullet.collidesAir": true,
      "targetAir": true,
      "weapons.0.reload": 120,
      "weapons.0.bullet.splashDamage": 20,
    },
    "nova": {
      "health": 180,
      "canBoost": false,
      "weapons.0.bullet.healPercent": 0,
      "weapons.0.bullet.healAmount": 50,
      "weapons.0.bullet.collidesAir": false,
      "targetAir": false,
    },
    "mace": {
      "health": 315,
      "armor": 3,
      "speed": 0.4,
      "range": 110,
      "maxRange": 110,
      "weapons.0.reload": 44,
      "weapons.1.reload": 44,
      "weapons.0.bullet.range": 110,
      "weapons.0.bullet.lifetime": 26,
      "weapons.0.bullet.collidesAir": false,
      "targetAir": false,
    },
    "pulsar": {
      "health": 240,
      "armor": 2,
      "speed": 0.5,
      "canBoost": false,
      "weapons.0.bullet.damage": 7,
      "weapons.0.bullet.healPercent": 0,
      "weapons.0.bullet.healAmount": 10,
      "weapons.0.bullet.lightningType.healPercent": 0,
      "weapons.0.bullet.lightningType.healAmount": 10,
      "abilities.0.max": 20,
    },
    "stell": {
      "health": 375,
      "armor": 10,
      "speed": 0.25,
      "rotateSpeed": 1.25,
      "weapons.0.rotateSpeed": 1.1
    },
    "fortress": {
      "health": 720,
      "armor": 4,
      "speed": 0.4,
      "weapons.0.bullet.lifetime": 240,
      "weapons.0.bullet.speed": 1
    },
    "quasar": {
      "health": 180,
      "armor": -10,
      "speed": 0.45,
      "canBoost": false,
      "targetAir": false,
      "weapons.0.bullet.damage": 16,
      "weapons.0.bullet.collidesAir": false,
    },
    "locus": {
      "health": 750,
      "armor": 15,
      "speed": 0.2,
      "rotateSpeed": 0.8,
      "weapons.0.bullet.damage": 28,
      "targetAir": false,
      "weapons.0.bullet.collidesAir": false,
      "weapons.0.rotateSpeed": 0.8
    },
    "flare": {
      "armor": 0,
      "health": 70,
      "speed": 0.9
    },
    "horizon": {
      "armor": 6,
      "health": 270,
      "speed": 0.8
    },
    "zenith": {
      "armor": 3,
      "health": 510,
      "speed": 0.7,
      "weapons.0.shoot.shots": 4,
      "weapons.0.reload": 120,
      "weapons.1.shoot.shots": 4,
      "weapons.1.reload": 120,
    }
  },
  "block": {
    "core-shard": {
      "unitType": "mono",
      "underBullets": true,
    },
    "core-foundation": {
      "unitType": "mono",
      "underBullets": true,
    },
    "core-bastion": {
      "unitType": "mono",
      "underBullets": true,
    },
    "core-nucleus": {
      "unitType": "mono",
      "underBullets": true,
    },
    "core-citadel": {
      "unitType": "mono",
      "underBullets": true,
    },
    "core-acropolis": {
      "unitType": "mono",
      "underBullets": true,
    }
  },
  "status": {
    "burning": {
      "damage": 0.005
    },
    "overdrive": {
      "damageMultiplier": 1.2,
      "healthMultiplier": 0.85,
      "damage": 0,
      "permanent": false
    },
    "overclock": {
      "speedMultiplier": 1.5,
      "damageMultiplier": 1.3,
      "healthMultiplier": 0.5,
      "reloadMultiplier": 1,
    },
    "boss": {
      "permanent": false
    },
    "electrified": {
      "speedMultiplier": 1,
      "damageMultiplier": 1.6,
    },
    "disarmed": {
      "damageMultiplier": 0.7,
      "reloadMultiplier": 1.8,
      "disarm": false
    },
    "fast": {
      "speedMultiplier": 2,
      "reloadMultiplier": 1.3,
      "healthMultiplier": 0.75,
    }
  }
}
"""

class ScriptUnitType(
    val unitType: UnitType,
    val cost: Int,
    val name: String,
    val desc: String = "",
    val unlockNeed: Int = 0,
)
val unitToScriptUnitTypes = mutableMapOf<UnitType, ScriptUnitType>()

class ScriptUnitTypes(
    val dagger: ScriptUnitType = ScriptUnitType(UnitTypes.dagger, 12, "尖刀", "最基础的尖刀,拥有较为强大的火力"),
    val nova: ScriptUnitType = ScriptUnitType(UnitTypes.nova, 18, "尖刀治疗形", "牺牲了火力与装甲,可以治疗友方单位与建筑", 1),
    val merui: ScriptUnitType = ScriptUnitType(UnitTypes.merui, 24, "尖刀爆破形↗", "牺牲了装甲与攻速,造成范围伤害,可对空打击", 1),
    val mace: ScriptUnitType = ScriptUnitType(UnitTypes.mace, 55, "尖刀Mk2", "加强了装甲的尖刀,装有长距火焰喷射器", 2),
    val pulsar: ScriptUnitType = ScriptUnitType(UnitTypes.pulsar, 70, "尖刀闪电形↗", "由尖刀Mk2改装,武器换成了短距修复闪电发射器,可对空,可以给周围单位增加能量护盾", 3),
    val stell: ScriptUnitType = ScriptUnitType(UnitTypes.stell, 120, "尖刀坦克↗", "由尖刀Mk2改装,拥有强大的装甲,可对空,缺点是机动性过差", 3),
    val fortress: ScriptUnitType = ScriptUnitType(UnitTypes.fortress, 180, "尖刀Mk3", "科技结晶,带有单兵大炮,但是极易被躲避", 7),
    val quasar: ScriptUnitType = ScriptUnitType(UnitTypes.quasar, 220, "尖刀护盾形", "尖刀Mk3变种,拥有随身力场护盾,其强大的激光发射器将会是低级单位的梦魇", 10),
    val locus: ScriptUnitType = ScriptUnitType(UnitTypes.locus, 360, "尖刀坦克Mk2", "更加强大的坦克,它会将撕碎一切面前之敌,即使在立场庇护之下,提前是..他们不会跑走", 12),

    val flare: ScriptUnitType = ScriptUnitType(UnitTypes.flare, 40, "飞行尖刀↗", "他飞起来了！血量较低,但是在空中仅有极少单位会击中飞行尖刀", 1),
    val horizon: ScriptUnitType = ScriptUnitType(UnitTypes.horizon, 120, "尖刀轰炸机↗", "向地面空投炸弹,这对没有防空的队伍将是毁灭性的", 5),
    val zenith: ScriptUnitType = ScriptUnitType(UnitTypes.zenith, 500, "尖刀战斗机↗", "发射蜂群导弹,空战之王！", 12),
    ) {
    fun toList(): List<ScriptUnitType> {
        return listOf(
            dagger,
            nova,
            merui,
            mace,
            pulsar,
            stell,
            fortress,
            quasar,
            locus,
            flare,
            horizon,
            zenith
        )
    }

    init {
        toList().forEach {
            unitToScriptUnitTypes.put(it.unitType, it)
        }
    }
}



val scriptUnitTypes = ScriptUnitTypes()

class Command(
    val name: String,
    val desc: String = "",
    val cost: Float,
    val unlockNeed: Int = 0,
    val effect: (mindustry.gen.Unit.() -> Unit)? = null,
    val lastingTime: Int,
    val lastCommand: Boolean = false,
    val activeEffect: (Team.() -> Unit)? = null,
) {
    fun active(team: Team, player: Player) {
        if (team.data.commandCharge >= cost) {
            team.data.command = this
            activeEffect?.invoke(team)
            if (lastCommand) {
                team.data.commandEndTime = Long.MAX_VALUE
                team.data.commandCharge = -114514f
                broadcast("{player}[red]下达最后指令-{name}！".with("player" to player.name, "name" to name))
                broadcast("[red]---{name}---\n{desc}".with("name" to name, "desc" to desc), type = MsgType.WarningToast)
            } else {
                team.data.commandCharge -= cost
                team.data.commandEndTime = Time.millis() + lastingTime
                broadcast("{player}[yellow]使用了战术指令-{name}！".with("player" to player.name, "name" to name))
            }
        }
    }
}

class Commands(
    val atk: Command = Command("突击", "[red]舍弃一部分装甲,[green]小幅提升单位战斗力", 10f, 0, fun mindustry.gen.Unit.() {
        apply(StatusEffects.overdrive, 2 * 60f)
    }, 15_000),
    val def: Command = Command("固守", "[red]大幅减少移速,[green]提升单位战斗力", 10f, 0, fun mindustry.gen.Unit.() {
        apply(StatusEffects.slow, 2 * 60f)
        apply(StatusEffects.boss, 2 * 60f)
    }, 15_000),

    val ap: Command = Command("穿甲射击", "[red]减少攻速,[green]提升单位伤害,对高甲单位特效", 20f, 2, fun mindustry.gen.Unit.() {
        apply(StatusEffects.electrified, 2 * 60f)
    }, 30_000),
    val qs: Command = Command("快速射击", "[red]减少单位伤害,[green]提升攻速,对低甲单位特效", 20f, 2, fun mindustry.gen.Unit.() {
        apply(StatusEffects.disarmed, 2 * 60f)
    }, 15_000),

    val storm: Command = Command("暴风强攻", "[red]舍弃大部分装甲,[green]大幅增加单位战斗力及移速", 50f, 4, fun mindustry.gen.Unit.() {
        apply(StatusEffects.overclock, 2 * 60f)
    }, 20_000),
    val protect: Command = Command("保护III", "[red]大幅减少移速,[green]提升单位血量", 50f, 4, fun mindustry.gen.Unit.() {
        apply(StatusEffects.slow, 2 * 60f)
        apply(StatusEffects.shielded, 2 * 60f)
    }, 20_000),

    val overload: Command = Command("核心过载",
        "[green]单位攻击+50%\n[red]建筑血量-50%\n单位受到持续伤害",
        100f,
        8,
        fun mindustry.gen.Unit.() {
            apply(StatusEffects.corroded, 2 * 60f)
        },
        Int.MAX_VALUE,
        true,
        fun Team.() {
            rules().blockHealthMultiplier *= 0.5f
            rules().unitDamageMultiplier *= 1.5f
        }),
    val end: Command = Command("终末契约",
        "[green]单位攻击*500%\n[red]60s后直接失败",
        100f,
        8,
        null,
        Int.MAX_VALUE,
        true,
        fun Team.() {
            rules().unitDamageMultiplier *= 5f
            launch(Dispatchers.game) {
                delay(60_000)
                data().destroyToDerelict()
            }
        }),
    val daggerStorm: Command = Command("尖刀风暴",
        "[green]单位上限增加等于指挥等级的数值\n[red]单位攻击-20%",
        100f,
        8,
        null,
        Int.MAX_VALUE,
        true,
        fun Team.() {
            rules().unitDamageMultiplier *= 0.8f
            data.unitCap += data.level
        }),
    val fastfastfast: Command = Command("超速姿态", "[green]单位移速攻速大幅增加\n[red]单位血量-25%", 100f, 8, fun mindustry.gen.Unit.() {
        apply(StatusEffects.fast, 2 * 60f)
    }, Int.MAX_VALUE, true, fun Team.() {


    }),

    ) {
    fun toList(): List<Command> {
        return listOf(
            atk,
            def,
            ap,
            qs,
            storm,
            protect,
            overload,
            end,
            daggerStorm,
            fastfastfast
        )
    }
}

val commands = Commands()

data class TeamData(
    var coin: Int = 0,
    var level: Int = 0,
    var point: Int = 0,
    var commandCharge: Float = 0f,
    var command: Command? = null,
    var commandEndTime: Long = 0,
    val unlock: MutableList<Any> = mutableListOf(),
    val leader: MutableList<String> = mutableListOf(),
    var exp: Float = 0f,
    var unitCap: Int = 10,
) {
    lateinit var team: Team

    fun nextLevelNeed(): Float {
        return Mathf.pow(level.toFloat(), 1.05f) * 20f + 40f
    }

    fun upgrade() {
        exp -= nextLevelNeed()
        level++
        point += level / 8 + 1
        unitCap += 2
        broadcast("[yellow]队伍指挥等级提升！".with(), quite = true, players = Groups.player.filter { it.team() == team })
    }
}
val teamData by autoInit { mutableMapOf<Team, TeamData>() }
val Team.data
    get() = teamData.getOrPut(this) { TeamData() }
        .also { it.team = this }

var start = false

onDisable {
    start = false
}

val voteService = contextScript<VoteService>()

val gameStartTime by autoInit { Time.millis() }

fun Player.isCommander(): Boolean {
    return uuid() in team().data.leader
}
fun Player.isCommanderText(): String {
    return if (isCommander()) "[green]你已拥有指挥权限" else "[red]你不是队伍领袖！"
}

fun VoteService.register() {
    val _100031 = contextScript<mapScript._100031>()
    addSubVote("将自己选定为队伍领袖", "", "leader", "领袖选举") {
        val team = player!!.team()
        val player = player!!
        _100031.apply {
            if (player.uuid() in team.data.leader) returnReply("[red]你已经队伍领袖了".with())
        }
        if (team == Team.get(255)) returnReply("[red]你为什么想当观察者的领袖?".with())

        canVote = canVote.let { default -> { default(it) && it.team() == team } }
        start(player, "选定发起者为队伍领袖({team.colorizeName}[yellow]队)".with("team" to team)) {
            _100031.apply {
                team.data.leader.add(player.uuid())
            }
            broadcast("{player}[yellow]成为了队伍领袖！".with("player" to player))
        }
    }
}

class BetterGroundAI(): GroundAI() {
    override fun updateMovement() {
        var move = true
        val core: Building? = unit.closestEnemyCore()
        if (target != null && unit.hasWeapons()) {
            if (unit.type.circleTarget) {
                circleAttack(120f)
            } else {
                moveTo(target, unit.type.range * 0.8f)
                unit.lookAt(target)
            }
            move = false
        } else {
            if (core != null && unit.within(core, unit.range() / 1.3f + core.block.size * Vars.tilesize / 2f)) {
                target = core
                for (mount in unit.mounts) {
                    if (mount.weapon.controllable && mount.weapon.bullet.collidesGround) {
                        mount.target = core
                    }
                }
            }
        }
        if (core == null || !unit.within(core, unit.type.range * 0.5f)) {
            //no reason to move if there's nothing there
            if (core == null && (!Vars.state.rules.waves || closestSpawner == null)) {
                move = false
            }
            if (move) pathfind(Pathfinder.fieldCore)
        }
        if (unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()) {
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.riseSpeed)
        }
        faceTarget()
    }
}

onEnable {
    voteService.register()
    contextScript<coreMindustry.ContentsTweaker>().addPatch("100031",contentPatch)

    Vars.state.rules.possessionAllowed = false
    Call.setRules(Vars.state.rules)

    launch(Dispatchers.game) {
        delay(360_000)
        Call.sendMessage("[yellow]作战控制系统重新连线!\n手操控制单位已经解锁")
        Vars.state.rules.possessionAllowed = true
        Call.setRules(Vars.state.rules)
    }

    loop(Dispatchers.game) {
        Groups.unit.forEach {
            if (!it.isPlayer && it.controller() !is FlyingAI && it.controller() !is GroundAI) {
                it.controller(if (it.type.flying) FlyingAI() else BetterGroundAI())
            }
        }
        yield()
    }

    //处理玩家每秒结算
    loop(Dispatchers.game) {
        val cores = Groups.build.filterIsInstance<CoreBuild>()
        val battleLine = buildString {
            cores.sortedBy { it.x }.forEach {
                append("[#${it.team.color}]|")
            }
        }
        Groups.player.forEach {
            val data = it.team().data
            Call.setHudText(it.con, """
                            [yellow]队伍金钱 ${data.coin} [green]+${data.level + data.team.data().cores.size}/s
                            [coral]队伍指挥等级 ${data.level} | 队伍指挥点 ${data.point}
                            ${if (data.command?.lastCommand == true) "[red]最后指令：${data.command?.name}" else "[acid]战术指令：${data.command?.name} [white]| [green]${((data.commandEndTime - Time.millis()) / 1000f).coerceAtLeast(0f).format(1)}s"} 
                            [sky]指令充能：${data.commandCharge.buildLineBar(10,100f)} [sky]${data.commandCharge.format(1)}%
                            $battleLine
                        """.trimIndent())
        }
        delay(300L)
    }

    //处理队伍每秒结算
    loop(Dispatchers.game) {
            Vars.state.teams.getActive().forEach teamForEach@{
                it.team.data.leader.forEach { uuid ->
                    val player = Groups.player.find { it.uuid() == uuid } ?: return@teamForEach
                    if (player.team() != it.team) betterTeam.changeTeam(player, it.team)
                }
                it.team.data.coin += (it.team.data.level * 0.5f).toInt() + it.cores.size * (it.team.data.level / 15 + 1)
                if (Time.millis() <= it.team.data.commandEndTime) {
                    it.units.forEach { it.team.data.command?.effect?.invoke(it) }
                } else {
                    it.team.data.command = null
                }
                if (it.team.data.exp >= it.team.data.nextLevelNeed()) {
                    it.team.data.upgrade()
                }
                it.team.data.exp += it.cores.size
            }
        delay(1000L)
    }
}

suspend fun Player.chooseMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 60_000, "",
        buildString {
            appendLine("[cyan]部分决策需要领袖权限")
            appendLine("[cyan]使用/vote leader选举领袖")
            appendLine("[yellow] 目前的队伍领袖")
            appendLine()
            team().data.leader.forEach { uuid ->
                Groups.player.find { it.uuid() == uuid }?.let { appendLine("[white]${it.name}") }
            }
        }
    ) {
        add(listOf("单位" to { unitMenu(core) }))
        //add(listOf("支援" to { supportMenu(core) }))
        add(listOf("指令" to { commandMenu(core) }))

        this += listOf(
            "退出" to {}
        )

        this += listOf(
            "[yellow]为了解决你的同步问题\n请务必安装ContentsTweaker" to { Call.openURI(con, "https://github.com/way-zer/ContentsTweaker/releases/download/v2.0.2/ContentsTweaker-2.0.2.jar") }
        )
    }
}
suspend fun Player.unitMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 60_000, "单位",
        ""
    ) {
        scriptUnitTypes.toList().sortedBy { it.cost }.forEach {
            if (it.unlockNeed <= 0 || it in team().data.unlock) {
                add(listOf("${if (team().data.coin >= it.cost) "[green]" else "[red]"}${it.unitType.emoji()} ${it.name} ${it.unitType.emoji()}" to {
                    unitSpawnMenu(core, it)
                }))
            } else {
                add(listOf("[gray]${it.unitType.emoji()} ${it.name} ${it.unitType.emoji()}" to {
                    unitUnlockMenu(core, it)
                }))
            }
        }

        this += listOf(
            "返回" to { chooseMenu(core) }
        )
    }
}
suspend fun Player.unitSpawnMenu(core: CoreBuild, unit: ScriptUnitType) {
    menu.sendMenuBuilder<Unit>(
        this, 60_000, unit.name, "${unit.desc}\n[yellow]队伍金钱 ${team().data.coin} [white]| [gold]需要金钱 ${unit.cost}"
    ) {
        add(listOf(
            "${if (team().data.coin >= unit.cost) "[green]" else "[red]"}生成！" to {
                if (team() == core.team)
                    if (team().data.coin >= unit.cost) {
                        if (team().data().units.toList().count { it.type != UnitTypes.mono } < team().data.unitCap) {
                            if (unit.cost >= team().data.coin / 4) {
                                broadcast("{player}[yellow]花费了 {cost} 队伍金钱购买了 {unit}".with("player" to this@unitSpawnMenu, "cost" to unit.cost, "unit" to unit.name), players = Groups.player.filter { it.team() == team() })
                            }
                            team().data.coin -= unit.cost
                            unit.unitType.spawnAround(core, team())
                            unitSpawnMenu(core, unit)
                        } else {
                            sendMessage("[red]单位到达上限！")
                        }
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
            }
        ))
        this += listOf(
            "返回" to { unitMenu(core) }
        )
    }
}
suspend fun Player.unitUnlockMenu(core: CoreBuild, unit: ScriptUnitType) {
    menu.sendMenuBuilder<Unit>(
        this, 60_000, unit.name, "${unit.desc}\n[yellow]队伍指挥点 ${team().data.point} [white]| [gold]需要指挥点 ${unit.unlockNeed}\n" +
                isCommanderText()
    ) {
        add(listOf(
            "${if (team().data.point >= unit.unlockNeed) "[green]" else "[red]"}解锁！" to {
                if (isCommander()) {
                    if (team() == core.team && unit !in team().data.unlock)
                        if (team().data.point >= unit.unlockNeed) {
                            team().data.point -= unit.unlockNeed
                            broadcast("{player}[yellow]花费了 {cost} 点指挥点数解锁了 {unit}".with("player" to this@unitUnlockMenu,
                                "cost" to unit.unlockNeed,
                                "unit" to unit.name), players = Groups.player.filter { it.team() == team() })
                            team().data.unlock.add(unit)
                            unitSpawnMenu(core, unit)
                        } else {
                            sendMessage("[red]指挥点不足！")
                        }
                } else {
                    sendMessage("[red]你不是队伍领袖！")
                }
            }
        ))
        this += listOf(
            "返回" to { unitMenu(core) }
        )
    }
}

suspend fun Player.supportMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 60_000, "", "[sky]花费指挥点获得支援!\n[yellow]目前的指挥点：${team().data.point}\n" +  isCommanderText()

    ) {
        add(listOf("[cyan]兵营扩建\n[yellow]增加单位上限 ${team().data.unitCap}[green](+2)" to {
            if (isCommander()) {
                if (team().data.point >= 1 && team() == core.team) {
                    broadcast("{player}[yellow]花费了 1 点指挥点数使用了 兵营扩建".with("player" to this@supportMenu),
                        players = Groups.player.filter { it.team() == team() })
                    team().data.point--
                    team().data.unitCap += 2
                    supportMenu(core)
                }
            } else {
                sendMessage("[red]你不是队伍领袖！")
            }
        }))
        add(listOf("[cyan]单位修复\n[yellow]回复友方血量(20s 5%/s)" to {
            if (isCommander()) {
                if (team().data.point >= 1 && team() == core.team) {
                    broadcast("{player}[yellow]花费了 1 点指挥点数使用了 单位修复".with("player" to this@supportMenu), players = Groups.player.filter { it.team() == team() })
                    team().data.point--
                    launch(Dispatchers.game) {
                        val startTime = Time.millis()
                        while (Time.millis() - startTime <= 20_000) {
                            team().data().units.forEach {
                                it.heal(it.maxHealth * 0.05f)
                            }
                            delay(1000L)
                        }
                    }
                    supportMenu(core)
                }
            } else {
                sendMessage("[red]你不是队伍领袖！")
            }
        }))

        this += listOf(
            "返回" to { chooseMenu(core) }
        )
    }
}
suspend fun Player.commandMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 60_000, "",
        ""
    ) {
        commands.toList().filter { !it.lastCommand }.sortedBy { it.cost }.forEach {
            if (it.unlockNeed <= 0 || it in team().data.unlock) {
                add(listOf("${if (team().data.commandCharge >= it.cost) "[green]" else "[red]"}${it.name}" to {
                    commandReleaseMenu(core, it)
                }))
            } else {
                add(listOf("[gray]${it.name}" to {
                    commandUnlockMenu(core, it)
                }))
            }
        }

        add(listOf("[red]最后指令" to { if (isCommander()) lastCommandMenu(core) else sendMessage("[red]你不是队伍领袖！") }))

        this += listOf(
            "返回" to { chooseMenu(core) }
        )
    }
}
suspend fun Player.lastCommandMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 60_000, "",
        ""
    ) {
        commands.toList().filter { it.lastCommand }.forEach {
            if (it.unlockNeed <= 0 || it in team().data.unlock) {
                add(listOf("${if (team().data.commandCharge >= it.cost) "[green]" else "[red]"}${it.name}" to {
                    commandReleaseMenu(core, it)
                }))
            } else {
                add(listOf("[gray]${it.name}" to {
                    commandUnlockMenu(core, it)
                }))
            }
        }

        this += listOf(
            "返回" to { commandMenu(core) }
        )
    }
}

suspend fun Player.commandReleaseMenu(core: CoreBuild, command: Command) {
    menu.sendMenuBuilder<Unit>(
        this, 60_000, command.name, "${command.desc}\n[yellow]队伍充能 ${team().data.commandCharge.format()}% [white]| [gold]需要充能 ${command.cost}% "
    ) {
        add(listOf(
            "${if (team().data.commandCharge >= command.cost) "[green]" else "[red]"}下达！" to {
                if (team() == core.team)
                    if (team().data.commandCharge >= command.cost) {
                        command.active(team(), this@commandReleaseMenu)
                    } else {
                        sendMessage("[red]充能不足！")
                    }
            }
        ))
        this += listOf(
            "返回" to { if (command.lastCommand) lastCommandMenu(core) else commandMenu(core) }
        )
    }
}
suspend fun Player.commandUnlockMenu(core: CoreBuild, command: Command) {
    menu.sendMenuBuilder<Unit>(
        this, 60_000, command.name,
        """
            ${command.desc}
            [yellow]队伍指挥点 ${team().data.point} [white]| [gold]需要指挥点 ${command.unlockNeed}
        """.trimIndent()
    ) {
        add(listOf(
            "${if (team().data.point >= command.unlockNeed) "[green]" else "[red]"}解锁！" to {
                if (team() == core.team && command !in team().data.unlock)
                    if (team().data.point >= command.unlockNeed) {
                        team().data.point -= command.unlockNeed
                        broadcast("{player}[yellow]花费了 {cost} 点指挥点数解锁了 {unit}".with("player" to this@commandUnlockMenu, "cost" to command.unlockNeed, "unit" to command.name), players = Groups.player.filter { it.team() == team() })
                        team().data.unlock.add(command)
                        commandReleaseMenu(core, command)
                    } else {
                        sendMessage("[red]指挥点不足！")
                    }
            }
        ))
        this += listOf(
            "返回" to { commandMenu(core) }
        )
    }
}

listen<EventType.TapEvent> {
    val player = it.player
    if (player.dead()) return@listen
    if (it.tile.team() == player.team()){
        launch(Dispatchers.game) {
            if (it.tile.block() == Blocks.coreAcropolis) player.chooseMenu(it.tile.build as CoreBuild)
        }
    }
}

listen<EventType.UnitDestroyEvent> {
    val unit = it.unit
    val amount = (unitToScriptUnitTypes.getOrDefault(unit.type, null)?.cost ?: 0) * 0.1f
    if (unit.team == Team.sharded) {
        Team.malis.data.exp += amount
        Team.malis.data.commandCharge += Random.nextFloat() * 2f
        Team.sharded.data.exp += amount * 0.1f
    } else {
        Team.sharded.data.exp += amount
        Team.sharded.data.commandCharge += Random.nextFloat() * 2f
        Team.malis.data.exp += amount * 0.1f
    }
}

listen<EventType.GameOverEvent> {
    val team = it.winner
    if (team.data.command == commands.end) {
        team.data.leader.forEach { uuid -> Groups.player.find { it.uuid() == uuid }?.achievement("终末使者", 100) }
    }
}

command("point", "CHEATER") {
    permission = id.replace("/", ".")
    body {
        player!!.team().data.point += arg.first().toInt()
    }
}

command("coin", "CHEATER") {
    permission = id.replace("/", ".")
    body {
        player!!.team().data.coin += arg.first().toInt()
    }
}

command("charge", "CHEATER") {
    permission = id.replace("/", ".")
    body {
        player!!.team().data.commandCharge += arg.first().toFloat()
    }
}