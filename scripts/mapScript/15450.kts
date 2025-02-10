@file:Depends("coreMindustry/menu", "调用菜单")
@file:Depends("coreMindustry/contentsTweaker", "修改核心单位,单位属性")
@file:Depends("wayzer/user/achievement", "成就")
@file:Suppress("unused", "unused", "PropertyName", "PropertyName", "PropertyName", "PropertyName", "PropertyName",
    "PropertyName", "PropertyName", "PropertyName", "PropertyName", "PropertyName", "PropertyName", "PropertyName",
    "KotlinDeprecation", "KotlinDeprecation", "KotlinDeprecation", "Deprecated"
)

import arc.Events
import arc.math.geom.Geometry
import arc.math.geom.Point2
import arc.math.geom.Vec2
import arc.util.Time
import cf.wayzer.placehold.DynamicVar
import coreLibrary.lib.registerVarForType
import coreLibrary.lib.util.loop
import coreLibrary.lib.with
import coreMindustry.lib.*
import mindustry.Vars
import mindustry.content.*
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.*
import mindustry.type.StatusEffect
import mindustry.type.UnitType
import mindustry.world.Block
import mindustry.world.Tile
import org.intellij.lang.annotations.Language
import wayzer.MapManager
import wayzer.lib.dao.PlayerData
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

/**@author xkldklp
 * https://mdt.wayzer.top/v2/map/15450/latest
 */
name = "TankWars"
//监狱大战

val achievement = contextScript<wayzer.user.Achievement>()
fun Player.achievement(name: String, exp: Int) {
    val profile = PlayerData[uuid()].profile
    if (profile != null)
        achievement.finishAchievement(profile, name, exp, true)
}

val menu = contextScript<coreMindustry.Menu>()

val expMultiplier by autoInit { Vars.state.rules.tags.getFloat("@expMultiplier", 1f) }

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
    "stell": {
      "rotateSpeed": 14,
      "armor": 0,
      "speed": 0.9
    },
    "locus": {
      "rotateSpeed": 7.2,
      "armor": 0,
      "speed": 0.8
    },
    "precept": {
      "rotateSpeed": 3,
      "armor": 0,
    },
    "vanquish": {
      "rotateSpeed": 2,
      "armor": 0,
      "speed": 0.48
    },
    "conquer": {
      "rotateSpeed": 1,
      "armor": 0,
      "speed": 0.34
    },
    "mono.health": 9999999999
  },
  "block": {
    "arc": {
      "shootType.damage": 45,
      "solid": false,
      "underBullets": true
    },
    "lancer": {
      "solid": false,
      "underBullets": true,
      "shootType.pierceCap": 12
    },
    "segment": {
      "bulletDamage": 160,
      "reload": 32,
      "solid": false,
      "underBullets": true
    },
    "repair-point": {
      "health": 800,
      "solid": false,
      "underBullets": true
    },
    "repair-turret": {
      "health": 1400,
      "solid": false,
      "underBullets": true
    },
  }
}
"""

class Area(
    val floor: Block,
    var killMultiplier: Float = 1f,
    var serialKillMultiplier: Float = 1f,
    var expDropMultiplier: Float = 0.25f,

    var levelLoot: Boolean = false,
)

class Areas(
    val copper: Area = Area(Blocks.darkPanel1, serialKillMultiplier = 0.5f),
    val titanium: Area = Area(Blocks.darkPanel5, killMultiplier = 1.5f),
    val plastanium: Area = Area(Blocks.darkPanel3, killMultiplier = 2f, serialKillMultiplier = 2f, expDropMultiplier = 0.5f),
    val phaseFabric: Area = Area(Blocks.darkPanel2, killMultiplier = 1.5f, serialKillMultiplier = 2f),
    val surgeAlloy: Area = Area(Blocks.metalFloor4, killMultiplier = 3f, serialKillMultiplier = 3f, expDropMultiplier = 0.75f, levelLoot = true),
) {
    val all = arrayOf(
        copper,
        titanium,
        plastanium,
        phaseFabric,
        surgeAlloy
    )
}

val areas = Areas()

val units = arrayOf(
    UnitTypes.stell,
    UnitTypes.locus,
    UnitTypes.precept,
    UnitTypes.vanquish,
    UnitTypes.conquer,
    UnitTypes.mono
)

data class PlayerData(
    var level: Short = 1,
    var respawnTime: Long = 0,
    var exp: Float = 0f,
    var souls: Float = 0f,
    var kills: Int = 0,
    var joinCooldown: Long = 0,
    var block: Block? = null,
    var reward: Float = 1f,
    private var combatTime: Long = 0
) {
    lateinit var player: Player

    fun combat(outTime: Long = 20_000): Boolean {
        return combatTime + outTime > Time.millis()
    }
    fun resetCombat(offset: Long = 0L) {
        combatTime = Time.millis() + offset
    }
    fun outCombatTime(outTime: Long = 20_000): Long {
        return  (combatTime + outTime) - Time.millis()
    }


    val exp2NextLevel get() = (level * 3f).pow(2f)

    fun reset(unit: mindustry.gen.Unit, respawnNeedTime: Long = 10_000) {
        if (unit.data.reseted) return
            unit.data.reseted = true
        if (souls > 5)
            player.sendMessage("[yellow]你知道吗？阵亡状态下/shop可以花费灵魂购买升级")
        kills = 0
        unit.kill()
        if (!combat()) {
            respawnTime = Time.millis() + (respawnNeedTime / player.team().data.respawnTimeMultiplier.pow(2f) / 4f).toLong()
            return
        }
        exp -= exp * unit.data.area().expDropMultiplier
        respawnTime = Time.millis() + (respawnNeedTime / player.team().data.respawnTimeMultiplier.pow(2f)).toLong()
        val data = unit.data.lastDamage?.getPlayerData()
        if (data != null) {
            if (data.level - level <= 2 && level > 2 && (unit.data.area().levelLoot || level >= 20)) {
                player.sendMessage("[red]等级被掠夺！")
                level--
                if (data.level < 20) {
                    data.level++
                }
            }
            var rate = 1f
            if (unit.data.player?.getPlayerData() != null) {
                rate = unit.data.player!!.getPlayerData()!!.reward
                unit.data.player!!.getPlayerData()!!.reward = (unit.data.player!!.getPlayerData()!!.reward - 0.2f).coerceAtLeast(0.5f)
            }
            val killExp =
                (level + 1f).pow(2) * rate * data.player.unit().data.area().killMultiplier * expMultiplier * data.player.team().data.expMultiplier * if (data.level - level < -1) 1.5f else 1f
            data.exp += killExp
            data.player.sendMessage("[green]击杀！经验+${killExp} ${if (data.level - level < -1) "[cyan]跨级击杀！经验1.5倍" else ""}")
            data.kills++
            if (data.reward <= 1f) {
                data.reward = 1f
            }
            data.reward += 0.25f
            if (data.kills >= 5) {
                broadcast("{player} 杀疯了！ {kills} 连杀！".with("player" to data.player.name, "kills" to data.kills))
            }
            if (data.kills == 10) {
                broadcast(
                    "[red]OVERDRIVE [white]{player} 获得了弱化和加速..".with(
                        "player" to data.player.name,
                        "kills" to data.kills
                    )
                )
                data.player.unit().apply {
                    apply(StatusEffects.sapped, Float.POSITIVE_INFINITY)
                    apply(StatusEffects.fast, Float.POSITIVE_INFINITY)
                }
            }
            data.souls += data.kills.coerceAtMost(10) * data.player.unit().data.area().serialKillMultiplier
            if (unit.data.player?.getPlayerData() != null) {
                unit.data.player!!.getPlayerData()!!.reward = 1f
            }
        }
    }

    fun getUnitType(): UnitType {
        return units[floor(level / 5f).toInt()]
    }

    fun getStatusEffects(): Set<StatusEffect> {
        return buildSet {
            val effectLevel = level % 5 - 1
            if (effectLevel >= 1) {
                add(StatusEffects.overclock)
            }
            if (effectLevel >= 2) {
                add(StatusEffects.overdrive)
            }
            if (effectLevel >= 3) {
                add(StatusEffects.boss)
            }
        }
    }
}

registerVarForType<Player>().apply {
    registerChild("prefix.00rate", "击杀经验倍率", DynamicVar.obj {
        "[yellow]${(it.data.reward * 100).format(1)}%[]"
    })
}

val teamMap by autoInit { mutableMapOf<Team, MutableSet<String>>() }

val playerData by autoInit { mutableMapOf<String, PlayerData>() }
val Player.data
    get() = playerData.getOrPut(uuid()) { PlayerData() }
        .also { it.player = this }

fun String.getPlayerData(): PlayerData? {
   return playerData[this]
}

data class UnitData(
    var player: String? = null,
    var reseted: Boolean = false,
    var lastDamage: String? = null,
    var blockUsed: Boolean = false,
) {
    lateinit var unit: mindustry.gen.Unit

    fun area(): Area {
        return areas.all.find { it.floor == unit.floorOn()} ?: Area(Blocks.air)
    }
}
val unitData by autoInit { mutableMapOf<mindustry.gen.Unit, UnitData>() }
val mindustry.gen.Unit.data
    get() = unitData.getOrPut(this) { UnitData() }
        .also { it.unit = this }

data class TeamData(
    var expMultiplier: Float = 1f,
    var respawnTimeMultiplier: Float = 1f,
) {
    lateinit var team: Team
}
val teamData by autoInit { mutableMapOf<Team, TeamData>() }
val Team.data
    get() = teamData.getOrPut(this) { TeamData() }
        .also { it.team = this }

fun Player.respawn(pos: Vec2, upgrade: Boolean = false): mindustry.gen.Unit {
    val p = this
    return data.getUnitType().spawn(team(), pos).apply {
        p.data.getStatusEffects().forEach {
            apply(it, 99999f)
        }
        unit(this)
        if (!upgrade) {
            apply(StatusEffects.shielded, 5f * 60f)
            apply(StatusEffects.disarmed, 5f * 60f)
        }
        data.player = uuid()
    }
}

fun Float.format(i: Int = 2): String {
    return "%.${i}f".format(this)
}

fun Int.buildLineBar(length: Int = 20, max: Int = 20, color: Pair<String, String> = Pair("[green]", "[red]")): String {
    val num = this
    return buildString {
        repeat(length) {
            append("${
                when {
                    num > it * (max.toFloat() / length) -> color.first
                    else -> color.second
                }
            }|")
        }
    }
}


onEnable {
    contextScript<coreMindustry.ContentsTweaker>().addPatch("TW",contentPatch)
    Vars.state.rules.apply {
        bannedBlocks.clear()
        blockWhitelist = true
        hideBannedBlocks = true
        canGameOver = false
        unitCap = 999
        possessionAllowed = false
    }
    Team.all.forEach { it.rules().cheat = true }
    Call.setRules(Vars.state.rules)
    Team.sharded.cores().forEach { it.kill() }
    Groups.player.forEach { it.clearUnit() }
    Call.setCameraPosition(Vars.world.width() * 8f, Vars.world.height() * 8f)
    loop(Dispatchers.game) {
        Groups.player.forEach { p ->
            Call.setHudText(p.con, buildString {
                if (Time.millis() < p.data.respawnTime) {
                    appendLine("[red]你已阵亡 等待重生")
                    appendLine("[green]${((p.data.respawnTime - Time.millis()) / 1000f).format(1)}s")
                } else if (p.dead()) {
                    appendLine("[cyan]重生冷却完毕 双击战场回到战斗")
                }
                append("[cyan]Level ${p.data.level}[white] ")
                append("${p.data.exp.format(1)}/${p.data.exp2NextLevel.format(1)}")
                append(p.data.exp.toInt().buildLineBar(10, p.data.exp2NextLevel.toInt(), Pair("[cyan]", "[red]")))
                appendLine("[white]")
                append(p.data.getUnitType().emoji())
                append(" | ")
                p.data.getStatusEffects().forEach {
                    append(it.emoji())
                }
                appendLine()
                appendLine("[yellow]当前灵魂 [white]${p.data.souls.format(1)} [yellow]连杀 [white]${p.data.kills}")
                when {
                    p.dead() ->append("[red]阵亡")
                    p.data.combat() -> append("[red]交战[yellow]${(p.data.outCombatTime() / 1000f).format(1)}")
                    else -> append("[green]脱战中[lightgray]可安全重生")
                }
            })
        }
        delay(100L)
    }
    loop(Dispatchers.game) {
        Groups.unit.forEach {
            if (it.data.player != null && it.player?.data?.player?.uuid() != it.data.player) {
                it.data.player!!.getPlayerData()?.reset(it)
            }
        }
        Groups.player.forEach {
            if (it.team() == Team.sharded) {
                val teams = Team.all.toMutableSet().filter {
                    it !in Team.baseTeams && it != Team.get(255) && Groups.player.all { p -> p.team() != it }
                }
                val team = if (teamMap.any { m -> it.uuid() in m.value }) teamMap.toMap().filter { m -> it.uuid() in m.value }.keys.random() else teams.random()
                it.team(team)
                teamMap.getOrPut(team) { mutableSetOf() }.add(it.uuid())
            }
            if (it.data.exp >= it.data.exp2NextLevel) {
                it.data.exp -= it.data.exp2NextLevel
                it.data.level++
                it.sendMessage("[green]LevelUp!")
                if (it.data.level >= 20) {
                    it.sendMessage("[red]请注意，现在你被击杀将会掉级！")
                    broadcast("{player} [yellow]已经达到了 {level} [yellow]级！到达25级将会直接胜利！[cyan]击杀将会启动等级掠夺".with("player" to it.name, "level" to it.data.level))
                }
            }
            if (it.unit().type == UnitTypes.mono && !Vars.state.gameOver) {
                if (MapManager.current.id == 15450 || MapManager.current.id <= 1000) {
                    it.achievement("[purple][终极坦克！]", 150)
                    if (it.data.kills >= 30) {
                        it.achievement("[green][杀杀杀！]", 50)
                    }
                    if (it.data.souls >= 500) {
                        it.achievement("[green][灵魂，很多的灵魂！]", 50)
                    }
                    if (it.data.block == null) {
                        it.achievement("[green][辅助武器？真不熟！]", 50)
                    }
                    if (Groups.player.size() >= 20) {
                        it.achievement("[green][征服者！]", 50)
                    }
                    if (it.unit().data.area() == areas.copper) {
                        it.achievement("[green][铜区战神！]", 50)
                    }
                }
                Events.fire(EventType.GameOverEvent(it.team()))
                Vars.state.gameOver = true
            }
            if (!it.dead() && it.unit().type != it.data.getUnitType()){
                it.unit().data.reseted = true
                it.unit().kill()

                it.respawn(Vec2(it.unit().x, it.unit().y), true)
            }
            if (!it.unit().data.blockUsed && it.data.block != null && it.unit().health <= it.unit().maxHealth * 0.5f && !it.dead()) {
                Vars.world.tile((it.unit().x / 8).toInt(), (it.unit().y / 8).toInt()).setNet(it.data.block, it.team(), 0)
                it.unit().data.blockUsed = true
            }
            it.data.getStatusEffects().forEach { s ->
                it.unit().apply(s, 9999999f)
            }
        }
        yield()
    }
    loop(Dispatchers.game) {
        var level = 0
        Groups.player.forEach { level += it.data.level }
        level /= Groups.player.size().coerceAtLeast(1)
        Groups.player.filter { it.data.level <= level - 1 }.forEach {
            it.sendMessage("[green]等级小于平均等级(${level})1级，经验+${it.data.exp2NextLevel * 0.4f}")
            it.data.exp += it.data.exp2NextLevel * 0.4f
        }
        delay(20_000)
    }
    loop(Dispatchers.game) {
        delay(300_000)
        val events = buildList {
            add {
                launch(Dispatchers.game) {
                    broadcast("[pink]乱斗事件！[cyan]合金激战\n[yellow]60s内合金区不掉经验且取消等级掠夺".with())
                    areas.surgeAlloy.levelLoot = false
                    areas.surgeAlloy.expDropMultiplier = 0f
                    delay(60_000)
                    areas.surgeAlloy.levelLoot = true
                    areas.surgeAlloy.expDropMultiplier = 0.75f
                    broadcast("[red]合金激战事件已经结束".with())
                }
            }
            add {
                launch(Dispatchers.game) {
                    broadcast("[pink]乱斗事件！[cyan]超级武器\n[yellow]60s内攻击*2".with())
                    Vars.state.rules.unitDamageMultiplier *= 2f
                    Call.setRules(Vars.state.rules)
                    delay(60_000)
                    Vars.state.rules.unitDamageMultiplier /= 2f
                    Call.setRules(Vars.state.rules)
                    broadcast("[red]超级武器事件已经结束".with())
                }
            }
            add {
                launch(Dispatchers.game) {
                    broadcast("[pink]乱斗事件！[cyan]易碎城墙\n[yellow]60s内建筑血量*0.1".with())
                    Vars.state.rules.blockHealthMultiplier *= 0.1f
                    Call.setRules(Vars.state.rules)
                    delay(60_000)
                    Vars.state.rules.blockHealthMultiplier /= 0.1f
                    Call.setRules(Vars.state.rules)
                    broadcast("[red]易碎城墙事件已经结束".with())
                }
            }
            add {
                launch(Dispatchers.game) {
                    val rand = Random.nextInt(1, 10)
                    broadcast("[pink]乱斗事件！[cyan]连杀狂热\n[yellow]所有人的连杀数加{num}".with("num" to rand))
                    Groups.player.forEach {
                        it.data.kills += rand
                    }
                }
            }
        }
        events.random().invoke()
    }
}

val playerLastTapTile by autoInit { mutableMapOf<String, Pair<Tile, Int>>() }

listen<EventType.TapEvent> {
    val player = it.player
    val times = if (it.tile == (playerLastTapTile[player.uuid()]?.first ?: false)) (playerLastTapTile[player.uuid()]?.second?.plus(1) ?: 1) else 1
    playerLastTapTile[player.uuid()] = it.tile to times
    if (player.dead() && player.team() != Team.get(255) && it.tile.passable() && player.data.respawnTime <= Time.millis()) {
        when (times % 2) {
            0 -> {
                player.respawn(Vec2(it.tile.x * 8f, it.tile.y * 8f))
                player.data.resetCombat(-15_000)
                player.sendMessage("[green]你已重生！")
            }
            1 -> {
                player.sendMessage("[green]再次点击以重生至此")
            }
        }
    }
}

listenPacket2Server<UnitControlCallPacket> { con, packet ->
    val unit: mindustry.gen.Unit = packet.unit
    val owner = unit.data.player
    if (owner == null){
        true
    } else {
        if (con.player.uuid() != owner) {
            Call.announce(con, "[red]你不是该单位的主人！无法控制")
            false
        } else {
            true
        }
    }
}

listen<EventType.UnitDamageEvent> { e ->
    val unit: mindustry.gen.Unit = e.unit
    val bullet: Bullet = e.bullet
    if (bullet.owner !is mindustry.gen.Unit) return@listen
    unit.data.lastDamage = (bullet.owner as mindustry.gen.Unit).player?.uuid()
    unit.player.data.resetCombat()
    (bullet.owner as mindustry.gen.Unit).player?.data?.resetCombat()
}

listen<EventType.UnitDestroyEvent> {
    it.unit.player?.data?.reset(it.unit)
}
listen<EventType.UnitBulletDestroyEvent> {
    if (it.bullet.owner is mindustry.gen.Unit) {
        it.unit.data.lastDamage = (it.bullet.owner as mindustry.gen.Unit).player?.uuid()
        (it.bullet.owner as mindustry.gen.Unit).player?.data?.resetCombat()
    }
}

command("join", "加入别人的坦克小队") {
    type = CommandType.Client
    aliases = listOf("加入")
    usage = "<三位id>"
    body {
        if (player!!.data.joinCooldown >= Time.millis()) returnReply("[red]加入冷却中! {time}s".with("time" to ((player!!.data.joinCooldown - Time.millis()) / 1000f).format(1)))
        if (arg.isEmpty()) replyUsage()
        val playerT = arg.getOrNull(0)?.let {
            depends("wayzer/user/shortID")?.import<(String) -> String?>("getUUIDbyShort")?.invoke(it)
                ?.let { id -> Groups.player.find { it.uuid() == id } }
                ?: returnReply("[red]找不到玩家,请使用/list查询正确的3位id".with())
        }
        if (playerT == player) returnReply("[red]你加入自己干什么？".with())
        if (playerT?.team() == player!!.team()) returnReply("[red]你已经在这了。".with())
        if (playerT != null) {
            if (teamMap.getOrPut(playerT.team()) { mutableSetOf() }.size >= 3) returnReply("[red]对方小队已满！".with())
            teamMap.getOrPut(player!!.team()) { mutableSetOf() }.remove(player!!.uuid())
            player!!.team(playerT.team())
            teamMap.getOrPut(playerT.team()) { mutableSetOf() }.add(player!!.uuid())
            player!!.data.joinCooldown = Time.millis() + 120_000
            player!!.unit().kill()
            broadcast("{player} [yellow]加入了[white] {target} [yellow]的坦克小队。".with("player" to player!!.name, "target" to playerT.name))
        }
    }
}

suspend fun Player.shopMenu(){
    menu.sendMenuBuilder<kotlin.Unit>(
        this, 30_000, "[green]灵魂升级！",
        """
                [yellow]奉上你的灵魂！
                [cyan]你拥有 ${data.souls} 个灵魂
            """.trimIndent()
    ) {
        fun Float.level(): Int {
            return (this * 10f).toInt() - 9
        }
        add(listOf(
            "[cyan]经验倍率升级lv.${team().data.expMultiplier.level()} [yellow]${15 + team().data.expMultiplier.level()} Souls\n${team().data.expMultiplier.level().buildLineBar(10, 10)}" to {
                if (team().data.expMultiplier.level() < 10 && data.souls >= 15 + team().data.expMultiplier.level()) {
                    data.souls -= 15 + team().data.expMultiplier.level()
                    team().data.expMultiplier += 0.1f
                }
                shopMenu()
            }
        ))
        add(listOf(
            "[cyan]重生时间倍率升级lv.${team().data.respawnTimeMultiplier.level()} [yellow]${5 + team().data.respawnTimeMultiplier.level()} Souls\n${team().data.respawnTimeMultiplier.level().buildLineBar(10, 10)}" to {
                if (team().data.respawnTimeMultiplier.level() < 10 && data.souls >= 5 + team().data.respawnTimeMultiplier.level()) {
                    data.souls -= 5 + team().data.respawnTimeMultiplier.level()
                    team().data.respawnTimeMultiplier += 0.1f
                }
                shopMenu()
            }
        ))
        add(listOf(
            "[cyan]单位攻击倍率升级lv.${team().rules().unitDamageMultiplier.level()} [yellow]${20 + team().rules().unitDamageMultiplier.level()} Souls\n${team().rules().unitDamageMultiplier.level().buildLineBar(8, 8)}" to {
                if (team().rules().unitDamageMultiplier.level() < 8 && data.souls >= 20 + team().rules().unitDamageMultiplier.level()) {
                    data.souls -= 20 + team().rules().unitDamageMultiplier.level()
                    team().rules().unitDamageMultiplier += 0.1f
                    Call.setRules(Vars.state.rules)
                }
                shopMenu()
            }
        ))
        add(listOf("[cyan]辅助武器-自动炮台 ${data.block?.emoji()}\n重生时填装，当血量<50%时自动放出" to { shopMenu() }))
        add(listOf(
            "Arc${Iconc.blockArc} [yellow]10 Souls" to {
                if (data.block != Blocks.arc  && data.souls >= 10) {
                    data.souls -= 10
                    data.block = Blocks.arc
                }
                shopMenu()
            },
            "Lancer${Iconc.blockLancer} [yellow]40 Souls" to {
                if (data.block != Blocks.lancer  && data.souls >= 40) {
                    data.souls -= 40
                    data.block = Blocks.lancer
                }
                shopMenu()
            },
            "Segment${Iconc.blockSegment} [yellow]40 Souls" to {
                if (data.block != Blocks.segment  && data.souls >= 40) {
                    data.souls -= 40
                    data.block = Blocks.segment
                }
                shopMenu()
            }
        ))
        add(listOf(
            "RepairPoint${Iconc.blockRepairPoint} [yellow]60 Souls" to {
                if (data.block != Blocks.repairPoint  && data.souls >= 60) {
                    data.souls -= 60
                    data.block = Blocks.repairPoint
                }
                shopMenu()
            },
            "RepairTurret${Iconc.blockRepairTurret} [yellow]90 Souls" to {
                if (data.block != Blocks.repairTurret && data.souls >= 90) {
                    data.souls -= 90
                    data.block = Blocks.repairTurret
                }
                shopMenu()
            }
        ))
        add(listOf("取消" to {}))
    }
}

listen<EventType.PlayerLeave> {
    launch(Dispatchers.game) {
        val leaveTime = Time.millis()
        val uuid = it.player.uuid()
        val team = it.player.team()
        while (!Groups.player.any { p -> p.uuid() == uuid}){
            if (Time.timeSinceMillis(leaveTime) >= 30_000){
                teamMap[team]?.remove(uuid)
                break
            }
            yield()
        }
    }
}

command("upgrade", "灵魂升级") {
    type = CommandType.Client
    aliases = listOf("shop")
    body {
        if (player!!.dead())
            launch(Dispatchers.game) {
                player!!.shopMenu()
            }
        else
            returnReply("[red]死亡后才可打开".with())
    }
}


command("level", "CHEATER") {
    permission = id.replace("/", ".")
    body {
        player!!.data.level = arg.first().toShort()
    }
}

command("exp", "CHEATER") {
    permission = id.replace("/", ".")
    body {
        player!!.data.exp += arg.first().toFloat()
    }
}

command("soul", "CHEATER") {
    permission = id.replace("/", ".")
    body {
        player!!.data.souls += arg.first().toInt()
    }
}