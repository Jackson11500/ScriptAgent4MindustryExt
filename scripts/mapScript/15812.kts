@file:Depends("coreMindustry/menu", "调用菜单")
@file:Depends("coreMindustry/contentsTweaker", "修改核心单位,单位属性")
@file:Depends("wayzer/user/achievement", "成就")
@file:Import("@coreMindustry/util/spawnAround.kt", sourceFile = true)

package mapScript

import arc.graphics.Color
import arc.math.Mathf
import arc.math.geom.Geometry
import arc.math.geom.Vec2
import arc.util.Align
import arc.util.Time
import coreLibrary.lib.util.loop
import coreMindustry.MenuBuilder
import coreMindustry.lib.game
import coreMindustry.lib.listen
import coreMindustry.lib.listenPacket2Server
import coreMindustry.util.spawnAround
import mindustry.Vars
import mindustry.ai.UnitCommand
import mindustry.content.*
import mindustry.entities.Units
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.*
import mindustry.type.UnitType
import mindustry.world.Tile
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import org.intellij.lang.annotations.Language
import kotlin.math.ceil
import kotlin.random.Random

/**@author xkldklp
 * https://mdt.wayzer.top/v2/map/15812/latest
 */
name = "starWar"

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

val unitsWithCosts = listOf(
    //Tier 0
    Pair(UnitTypes.nova, listOf(Pair(Items.copper, 7))),//mineTier 0
    Pair(UnitTypes.dagger, listOf(Pair(Items.copper, 5))),
    Pair(UnitTypes.crawler, listOf(Pair(Items.copper, 2))),

    //Tier 1
    Pair(UnitTypes.pulsar, listOf(Pair(Items.copper, 40))),//mineTier 1
    Pair(UnitTypes.flare, listOf(Pair(Items.copper, 5), Pair(Items.lead, 5))),
    Pair(UnitTypes.mace, listOf(Pair(Items.copper, 15), Pair(Items.lead, 5))),

    //Tier 2
    Pair(UnitTypes.poly, listOf(Pair(Items.copper, 80), Pair(Items.lead, 40))),//mineTier 2
    Pair(UnitTypes.fortress, listOf(Pair(Items.copper, 30), Pair(Items.lead, 30), Pair(Items.titanium, 10))),
    Pair(UnitTypes.spiroct, listOf(Pair(Items.copper, 40), Pair(Items.lead, 30), Pair(Items.titanium, 10))),

    //Tier 3
    Pair(UnitTypes.mega, listOf(Pair(Items.copper, 160), Pair(Items.lead, 120), Pair(Items.titanium, 80))),//mineTier 3
    Pair(UnitTypes.vela, listOf(Pair(Items.copper, 120), Pair(Items.lead, 80), Pair(Items.titanium, 40), Pair(Items.thorium, 20))),
    Pair(UnitTypes.scepter, listOf(Pair(Items.copper, 120), Pair(Items.lead, 80), Pair(Items.titanium, 40), Pair(Items.thorium, 20))),

    //Tier 4
    Pair(UnitTypes.reign, listOf(Pair(Items.copper, 500), Pair(Items.lead, 250), Pair(Items.titanium, 80), Pair(Items.thorium, 50))),
)

val unitsOneTier = 3

val unitToCost = buildMap {
    unitsWithCosts.forEach {
        put(it.first, it.second)
    }
}

val contentPatch
    @Language("JSON5")
    get() = """
{
  "unit": {
    "alpha": {
      "mineTier": -1,
    },
    "beta": {
      "mineTier": -1,
    },
    "gamma": {
      "mineTier": -1,
    },
    "evoke": {
      "mineTier": -1,
    },
    "incite": {
      "mineTier": -1,
    },
    "emanate": {
      "mineTier": -1,
    },
    "quasar": {
      "mineTier": 0,
      "mineSpeed": 0,
    },
    "nova": {
      "mineTier": 0,
      "mineSpeed": 0,
    },
    "pulsar": {
      "mineTier": 1,
      "mineSpeed": 0,
    },
    "poly": {
      "mineTier": 2,
      "mineSpeed": 0,
    },
    "mega": {
      "mineTier": 3,
      "mineSpeed": 0,
    }
  },
}"""

onEnable {
    contextScript<coreMindustry.ContentsTweaker>().addPatch("starWar",contentPatch)
}

val playerInMenu by autoInit { mutableMapOf<Player, Boolean>() }

class AttackMode(
    val name: String,
    val action: mindustry.gen.Unit.() -> Unit,
)

fun mindustry.gen.Unit.commandNearBy(point: Vec2): Boolean {
    val valid = mutableListOf<Vec2>()
    Geometry.circle((point.x / 8).toInt(), (point.y / 8).toInt(), 8) { x, y ->
        if (canPass(x, y) && (isGrounded || Vars.world.tile(x, y)?.floor()?.isDeep == false))
            valid.add(Vec2(x * 8f, y * 8f))
    }
    if (valid.isEmpty()) return false
    command().commandPosition(valid.random())
    return true
}

fun mindustry.gen.Unit.stop2AttackNearBy(point: Vec2): Boolean {
    if (command().targetPos == null) return false
    val enemy = Units.closestEnemy(team, x, y, range() * 1.2f) {
        it.within(point, 16 * 8f)
    }
    if (enemy != null) {
        var length = range()
        var times = 0
        while (true) {
            val vec = Vec2(enemy.x, enemy.y)
            vec.setAngle(Mathf.angle(enemy.x - x, enemy.y - y))
            vec.setLength(length)
            val target = Vec2(enemy.x - vec.x, enemy.y - vec.y)
            val valid = mutableListOf<Tile>()
            Geometry.circle((target.x / 8).toInt(), (target.y / 8).toInt(), ceil(hitSize / 8).toInt()) { x, y ->
                valid.add(Vars.world.tile(x, y))
            }
            if (valid.all { canPass(it.x.toInt(), it.y.toInt()) && (!isGrounded || !it.floor().isDeep) }
                && (!isGrounded || Vars.world.tile((target.x / 8).toInt(), (target.y / 8).toInt())?.floor()?.isDeep == false)){
                command().attackTarget = enemy
                command().commandPosition(target)
                break
            } else {
                length -= 4f
            }
            times++
            if (times >= 20) break
        }
        return times < 20
    } else {
        return false
    }
}

val attackModes = listOf(
    AttackMode("推进") {
        val leader = data.leader ?: return@AttackMode
        if (!isCommandable) return@AttackMode
        val point = Vec2(leader.x + leader.vel.x * 60f * 4f, leader.y + leader.vel.y * 60f * 4f)
        if (!point.within(this, 12f * 8f)) {
            if (command().targetPos?.within(point, 12f * 8f) != true || !Vars.world.tile((command().targetPos.x / 8).toInt(), (command().targetPos.y / 8).toInt()).passable()) {
                commandNearBy(point)
            }
        }
    },
    AttackMode("突击") {
        val leader = data.leader ?: return@AttackMode
        if (!isCommandable) return@AttackMode
        val vec = Vec2(leader.aimX, leader.aimY).setAngle(Mathf.angle(leader.aimX - x, leader.aimY - y)).setLength(leader.dst(leader.aimX, leader.aimY).coerceAtMost(48 * 8f))
        val point = Vec2(leader.x + vec.x, leader.y + vec.y)
        if (!stop2AttackNearBy(point))
            if (!point.within(this, 12f * 8f)) {
                if (command().targetPos?.within(point, 12f * 8f) != true || !Vars.world.tile((command().targetPos.x / 8).toInt(), (command().targetPos.y / 8).toInt()).passable()) {
                    commandNearBy(point)
                }
            }
    },
    AttackMode("固守") {
        //do nothing
    }

)

data class TeamData(
    var playerUnitCap: Int = 6,
) {
    lateinit var team: Team
}
val teamData by autoInit { mutableMapOf<Team, TeamData>() }
val Team.data
    get() = teamData.getOrPut(this) { TeamData() }
        .also { it.team = this }

data class PlayerData(
    /** 射击起始时间 */
    var shootingTime: Long = 0,
    var inMenu: Boolean = false,
) {
    lateinit var player: Player
}

val playerData by autoInit { mutableMapOf<String, PlayerData>() }
val Player.data
    get() = playerData.getOrPut(uuid()) { PlayerData() }
        .also { it.player = this }

data class UnitData(
    var leader: mindustry.gen.Unit? = null,
    var unitCap: Int = 6,
    var mode: AttackMode = attackModes[0],
) {
    lateinit var unit: mindustry.gen.Unit
}

val unitData by autoInit { mutableMapOf<mindustry.gen.Unit, UnitData>() }
val mindustry.gen.Unit.data
    get() =
        unitData.getOrPut(this) { UnitData() }
            .also { it.unit = this }

class ShopMenu(private val player: Player, private val core: CoreBuild): MenuBuilder<Unit>() {

    lateinit var unit: UnitType

    var tab: Int = 0

    suspend fun sendTo() {
        playerInMenu[player] = true
        sendTo(player, 60_000)
        playerInMenu[player] = false
    }

    private suspend fun coreMenu() {
        title = "刻印单位"
        msg = """
                [yellow]在此生产单位，获得新资源可开启新单位刻印
            """.trimIndent()
        val coreItems = core.items

        //哦天哪我在写什么屎山算了不优化了开摆
        if (coreItems.has(Items.copper)) {
            unitsWithCosts.subList(0, unitsOneTier).forEach {
                option(it.first.emoji()) {
                    unit = it.first; tab = 1; refresh()
                }
            }
            newRow()
            val newUnit = unitsWithCosts[unitsOneTier]
            option(newUnit.first.emoji()) {
                unit = newUnit.first; tab = 1; refresh()
            }
        }
        if (coreItems.has(Items.lead)) {
            unitsWithCosts.subList(unitsOneTier + 1, unitsOneTier * 2).forEach {
                option(it.first.emoji()) {
                    unit = it.first; tab = 1; refresh()
                }
            }
            newRow()
            val newUnit = unitsWithCosts[unitsOneTier * 2]
            option(newUnit.first.emoji()) {
                unit = newUnit.first; tab = 1; refresh()
            }
        }
        if (coreItems.has(Items.titanium)) {
            unitsWithCosts.subList(unitsOneTier * 2 + 1, unitsOneTier * 3).forEach {
                option(it.first.emoji()) {
                    unit = it.first; tab = 1; refresh()
                }
            }
            newRow()
            val newUnit = unitsWithCosts[unitsOneTier * 3]
            option(newUnit.first.emoji()) {
                unit = newUnit.first; tab = 1; refresh()
            }
        }
        if (coreItems.has(Items.thorium)) {
            unitsWithCosts.subList(unitsOneTier * 3 + 1, unitsOneTier * 4).forEach {
                option(it.first.emoji()) {
                    unit = it.first; tab = 1; refresh()
                }
            }
            newRow()
            val newUnit = unitsWithCosts[unitsOneTier * 4]
            option(newUnit.first.emoji()) {
                unit = newUnit.first; tab = 1; refresh()
            }
        }
    }

    private suspend fun unitMenu() {
        val coreItems = core.items
        val enough = unitToCost[unit]?.all { it.second <= coreItems.get(it.first) } ?: true

        title = "刻印单位"
        msg = buildString {
            appendLine("${unit.emoji()}${unit.localizedName}${unit.emoji()}")
            unitToCost[unit]?.forEach {
                append(it.first.emoji())
                if (enough) {
                    append("[green]")
                } else {
                    append("[red]")
                }
                appendLine(" ${coreItems.get(it.first)} / ${it.second}")
                append("[white]")
            }
        }

        lazyOption {
            if (enough) {
                option("[green]刻印单位！")
            } else {
                refreshOption("[red]资源不足！")
            }
            unitToCost[unit]?.forEach {
                coreItems.remove(it.first, it.second)
            }
            unit.spawnAround(core, core.team)
            refresh()
        }

        newRow()
        option("[white]返回上一级") { tab = 0; refresh() }
    }

    override suspend fun build() {
        when (tab) {
            0 -> coreMenu()
            1 -> unitMenu()
        }
        newRow()
        option("[white]退出菜单") { }
    }
}

class CommandMenu(private val player: Player): MenuBuilder<Unit>() {
    lateinit var unit: mindustry.gen.Unit

    var tab: Int = 0

    suspend fun sendTo() {
        playerInMenu[player] = true
        sendTo(player, 60_000)
        playerInMenu[player] = false
    }

    private suspend fun commandMenu() {
        title = "号令星辉！"
        msg = """
                [yellow]点击号令星辉会指挥附近可控单位
            """.trimIndent()
        if (player.unit().type != UnitTypes.quasar) {
            option("[red]此星辉单位无指挥能力\n[cyan]重生为星辉指挥官!") {
                player.unit(UnitTypes.quasar.spawnAround(player.closestCore(), player.team())?.apply {
                    spawnedByCore = true
                    apply(StatusEffects.electrified, 9999999f)
                })
                player.unit().data.unitCap = player.team().data.playerUnitCap
            }
        } else {
            option("[yellow]号令星辉！") {
                val units = mutableListOf<mindustry.gen.Unit>()
                Units.nearby(player.team(), player.x, player.y, 12 * 8f) {
                    if (it.isCommandable && !it.isPlayer && it.data.leader?.isValid != true)
                        units.add(it)
                }
                val left = player.unit().data.unitCap - Groups.unit.count { it.data.leader == player.unit() }
                if (left <= 0) {
                    player.sendMessage("[red]指挥单位已达上限")
                    return@option
                }
                if (units.isEmpty()) {
                    player.sendMessage("[red]附近没有可控单位")
                    return@option
                }
                val sortedUnits = units.sortedBy { it.dst(player.unit()) + if (it.type.mineTier >= 0) 999999 else 0}
                val controlUnits = sortedUnits.subList(0, left.coerceAtMost(sortedUnits.size))
                controlUnits.forEach {
                    it.data.leader = player.unit()
                    it.data.mode = attackModes[0]
                    launch {
                        repeat(6) { i ->
                            Call.transferItemEffect(Items.copper, it.x, it.y, player.unit())
                            delay(1_00)
                        }
                    }
                    Call.effect(Fx.unitEnvKill, it.x, it.y, 0f, Color.red)
                }
                player.sendMessage("[yellow]已号令 ${controlUnits.size} 个星辉单位[white] ${controlUnits.joinToString(separator = "") { it.type.emoji() }}")
            }
        }
        Groups.unit.forEach {
            if (it.data.leader != player.unit()) return@forEach
            newRow()
            option("${it.type.emoji()}|${it.data.mode.name}") {
                unit = it
                tab = 1; refresh()
            }
        }
        if (Groups.unit.count { u -> u.data.leader == player.unit()} >= 2) {
            newRow()
            option("[red]解散所有单位") {
                Groups.unit.toList().filter { u -> u.data.leader == player.unit()}.forEach {
                    it.data.leader = null
                    launch {
                        repeat(6) { i ->
                            Call.transferItemEffect(Items.copper, player.x, player.y, it)
                            delay(1_00)
                        }
                    }
                    Call.effect(Fx.unitCapKill, it.x, it.y, 0f, Color.red)
                }
            }
        }
    }

    private suspend fun unitCommandMenu() {
        title = "号令星辉！"
        msg = buildString {
            appendLine("${unit.type.emoji()}${unit.type.localizedName}${unit.type.emoji()}")
            appendLine(unit.health.buildLineBar(20, unit.maxHealth))
            appendLine("[white]战术模式 ${unit.data.mode.name}")
            appendLine()
            appendLine("[white]推进 - 跟随单位移动")
            appendLine("[white]突击 - 跟随单位射击位置移动,卡距离攻击")
            append("[white]固守 - 原地不动")
        }
        attackModes.forEach {
            lazyOption {
                if (it == unit.data.mode) {
                    refreshOption("[green]${it.name}")
                } else {
                    option("[lightgray]${it.name}")
                }
                unit.data.mode = it
                refresh()
            }
            newRow()
        }
        attackModes.forEach {
            lazyOption {
                option("[cyan]全部切换\n[lightgray]${it.name}")
                Groups.unit.forEach { u ->
                    if (u.data.leader == player.unit())
                        u.data.mode = it
                }
                refresh()
            }
        }
        newRow()
        option("[red]取消指挥单位") {
            unit.data.leader = null
            launch {
                repeat(6) {
                    Call.transferItemEffect(Items.copper, player.x, player.y, unit)
                    delay(1_00)
                }
            }
            Call.effect(Fx.unitCapKill, unit.x, unit.y, 0f, Color.red)
            tab = 0; refresh() }
        newRow()
        option("[white]返回上一级") { tab = 0; refresh() }
    }

    override suspend fun build() {
        when (tab) {
            0 -> commandMenu()
            1 -> unitCommandMenu()
        }
        newRow()
        option("[white]退出菜单") { }
    }
}


fun Player.checkView(x: Float = 1f, y: Float = 1f): Boolean {
    if (unit().aimX > con.viewX - con.viewWidth / 2 * x) {
        return false
    }
    if (unit().aimY < con.viewY - con.viewHeight / 2 * y) {
        return false
    }
    return true
}


onEnable {
    Vars.state.rules.apply {
        unitCap = 999
    }
    Call.setRules(Vars.state.rules)
    loop(Dispatchers.game) {
        Groups.player.forEach {
            if (it.unit().isShooting) {
                if (it.data.shootingTime == 0L)
                    it.data.shootingTime = Time.millis()
            } else {
                it.data.shootingTime = 0L
            }
        }
        yield()
    }
    loop(Dispatchers.game) {
        Groups.player.forEach {
            Call.infoPopup(
                it.con,
                buildString {
                    appendLine("[cyan]--正在指挥的星辉单位--[white]")
                    Groups.unit.forEach unitForEach@{ u ->
                        if (u.data.leader != it.unit()) return@unitForEach
                        appendLine("${u.type.emoji()} ${u.health.buildLineBar(20, u.maxHealth)}")
                        appendLine("[white]战术模式 ${u.data.mode.name}")
                    }
                    append("[cyan]--正在指挥的星辉单位--")
                }, 1.013f,
                Align.top, 0, 0, 0, 1000
            )
            Call.setHudText(it.con,
                buildString {
                    appendLine("[cyan]点击屏幕的最左侧打开控兵菜单")
                    appendLine("[lightgray]其他控兵方式被禁用,你必须通过此操作来控兵")
                    appendLine("[yellow]点击屏幕的最右侧快捷设置所有单位的战术模式")
                    appendLine("[lightgray]上 - 推进|中 - 突击|下 - 固守")
                    append(Groups.unit.count { u -> u.data.leader == it.unit() }.buildLineBar(it.unit().data.unitCap, it.unit().data.unitCap))
                })
        }
        delay(500)
    }
    loop(Dispatchers.game) {
        //哦天哪我在写什么屎山算了不优化了开摆哦天哪我在写什么屎山算了不优化了开摆哦天哪我在写什么屎山算了不优化了开摆哦天哪我在写什么屎山算了不优化了开摆哦天哪我在写什么屎山算了不优化了开摆哦天哪我在写什么屎山算了不优化了开摆哦天哪我在写什么屎山算了不优化了开摆哦天哪我在写什么屎山算了不优化了开摆哦天哪我在写什么屎山算了不优化了开摆哦天哪我在写什么屎山算了不优化了开摆哦天哪我在写什么屎山算了不优化了开摆
        Groups.unit.forEach {
            if (it.type.mineTier < 0 && !(it.spawnedByCore && it.type.flying)) return@forEach
            val tiles = buildList<Tile> { Vars.world.tiles.eachTile { t -> if (t.drop() == when (it.type.mineTier) {
                    0 -> Items.copper
                    1 -> Items.lead
                    2 -> Items.titanium
                    3 -> Items.thorium
                    else -> Items.copper
                }) add(t) } }
            val ore = tiles.minByOrNull { t -> t.dst(it) } ?: return@forEach
            if (!ore.within(it, 8 * 8f)) return@forEach
            if (Random.nextFloat() >= 0.8f) {
                it.team().core()?.items?.add(ore.drop(), 1)
                val backup = ore.overlay()
                ore.setOverlayNet(Blocks.oreScrap)
                Call.transferItemEffect(Items.copper, ore.getX(), ore.getY(), it)
                launch {
                    delay(Random.nextLong(30_000, 60_000))
                    ore.setOverlayNet(backup)
                }
            }
        }
        delay(1_000)
    }
    loop(Dispatchers.game) {
        Groups.unit.forEach {
            if (it.isCommandable)
                it.command().command = UnitCommand.moveCommand
            if (it.data.leader?.isValid == true)
                it.data.mode.action.invoke(it)
        }
        delay(500L)
    }
}

listen<EventType.TapEvent> {
    if (!it.player.dead() && !playerInMenu.getOrDefault(it.player, false)) {
        if (it.tile.block() is CoreBlock && it.tile.team() == it.player.team()) {
            launch(Dispatchers.game) { ShopMenu(it.player, it.tile.build as CoreBuild).sendTo() }
            return@listen
        }
        if (it.player.checkView(0.9f)) {
            it.player.data.shootingTime = 0
            launch(Dispatchers.game) { CommandMenu(it.player).sendTo() }
        }
        if (!it.player.checkView(-0.9f)) {
            if (it.player.checkView(-1f, 1f / 3f - 0.5f)) {
                Groups.unit.forEach { u ->
                    if (u.data.leader == it.player.unit())
                        u.data.mode = attackModes[0]
                }
                it.player.sendMessage("[yellow]所有单位战术模式切换为推进")
            } else if (it.player.checkView(-1f, 2f / 3f - 0.5f)) {
                Groups.unit.forEach { u ->
                    if (u.data.leader == it.player.unit())
                        u.data.mode = attackModes[1]
                }
                it.player.sendMessage("[yellow]所有单位战术模式切换为突击")
            } else {
                Groups.unit.forEach { u ->
                    if (u.data.leader == it.player.unit())
                        u.data.mode = attackModes[2]
                }
                it.player.sendMessage("[yellow]所有单位战术模式切换为固守")
            }
        }
    }
}

listenPacket2Server<CommandUnitsCallPacket> { con, packet ->
    con.player.sendMessage("[red]不支持rts控制单位!")
    false
}

listenPacket2Server<UnitControlCallPacket> { con, packet ->
    if (packet.unit.type.mineTier >= 0) {
        true
    } else {
        con.player.sendMessage("[red]不支持手操非采矿单位!")
        false
    }
}
