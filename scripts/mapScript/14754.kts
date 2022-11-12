@file:Depends("coreMindustry/utilNext", "调用菜单")
@file:Depends("wayzer/map/betterTeam", "更改玩家队伍")
@file:Depends("coreMindustry/contentsTweaker", "修改核心单位,单位属性")

package mapScript

import arc.Events
import arc.graphics.Color
import arc.util.Align
import arc.util.Interval
import arc.util.Time
import coreLibrary.lib.util.loop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import mindustry.Vars
import mindustry.content.*
import mindustry.content.UnitTypes.*
import mindustry.core.World
import mindustry.entities.Units
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Iconc
import mindustry.gen.Iconc.*
import mindustry.gen.Player
import mindustry.type.UnitType
import mindustry.world.Tile
import wayzer.map.BetterTeam
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

/**@author xkldklp
 * https://mdt.wayzer.top/v2/map/14754/latest
 */
name = "LifePoint Champions"
//人生赢家...?

val menu = contextScript<coreMindustry.UtilNext>()
val betterTeam = contextScript<BetterTeam>()

var gameStart = false
var finalBattle = false
var gameEnd = false
val readyAreaRange: Int by lazy { Vars.state.rules.tags.getInt("@readyAreaRange", 30) }
val finalBattleTime: Int by lazy { Vars.state.rules.tags.getInt("@finalBattleTime", 600) }

var teams: MutableList<Team> = mutableListOf()

data class TeamData(
    var lifePoint: Int = 0,
    var players: MutableList<Player> = mutableListOf(),
    var leader: Player? = null,
    var spawnTile: Tile = Vars.emptyTile
)
val teamData by lazy { mutableMapOf<Team, TeamData?>() }
fun Team.teamData(): TeamData {
    if(teamData[this] == null) {
        val data = TeamData()
        teamData[this] = data
    }
    return teamData[this]!!
}

data class PlayerData(
    var unitType: UnitType? = null,
    var unit: mindustry.gen.Unit? = null,
    var respawnCost: Int = 1,
    var respawnTime: Long = 0L
){
    fun checkRespawn(): Boolean{
        return respawnTime - Time.millis() < 0
    }
}
val playerData by lazy { mutableMapOf<Player, PlayerData?>() }
fun Player.playerData(): PlayerData {
    if(playerData[this] == null) {
        val data = PlayerData()
        playerData[this] = data
    }
    return playerData[this]!!
}

data class UnitData(
    var onwer: Player? = null
)
val unitData by lazy { mutableMapOf<mindustry.gen.Unit, UnitData?>() }
fun mindustry.gen.Unit.unitData(): UnitData {
    if(unitData[this] == null) {
        val data = UnitData()
        unitData[this] = data
    }
    return unitData[this]!!
}

val unitsWithKit = arrayOf(
    arrayOf(nova, pulsar),//heal
    arrayOf(stell, locus, precept),//tank
    arrayOf(dagger, mace),//attack
    arrayOf(fortress, quasar),//rangeAttack
    arrayOf(elude),//scout
)

fun LPDrop(amount: Int, x: Float, y: Float, IgnoreFog: Boolean = false, duration: Int = 30_000, visibleRange: Float = 54 * 8f, maxRange: Float = 16f){
    val dropX = x - maxRange / 2 + Random.nextFloat() * maxRange
    val dropY = y - maxRange / 2 + Random.nextFloat() * maxRange
    val dorpTime = Time.millis()
    launch(Dispatchers.game){
        while(true){
            delay(200)
            if (Time.millis() - dorpTime >= duration) break
            Groups.player.forEach{ p ->
                if (p.within(dropX, dropY, visibleRange) && (Vars.fogControl.isVisible(p.team(), dropX, dropY) || IgnoreFog || !Vars.state.rules.fog))
                    Call.label(
                        p.con , "${Iconc.liquidNeoplasm} * $amount\n[lightgray]${((Time.millis() - dorpTime ) / 1000)}s/${duration / 1000}s", 0.21f,
                        dropX, dropY
                    )
                val u = p.unit()
                if (u.within(dropX, dropY, u.hitSize + 4) && u.team in teams){
                    u.team.teamData().lifePoint += amount
                    u.team.teamData().players.forEach {
                        it.sendMessage("${p.name}[yellow]拾取了[white]${amount}[yellow]点LP")
                    }
                    return@launch
                }
            }
        }
    }
}

suspend fun Player.kitMenu() {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]职业选择",
        """
            [yellow]选择你的职业！
        """.trimIndent()
    ) {
        add(listOf("[red]此模式需要CT模组支持！\n点击下载" to {
            Call.openURI(con, "https://github.com/way-zer/ContentsTweaker/releases/latest")
        }))

        unitsWithKit.forEach { units ->
            add(buildList {
                units.forEach { unit ->
                    this += "${unit.emoji()}${unit.localizedName}${unit.emoji()}" to suspend {
                        if (!gameStart){
                            playerData().unitType = unit
                            sendMessage("[green]选择成功！目前职业：[white]${unit.emoji()}${unit.localizedName}${unit.emoji()}")
                        }else {
                            sendMessage("[red]游戏已经开始！")
                        }
                    }
                }
            }.toList())
        }
    }
}

suspend fun Player.skillsMenu() {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]发动技能",
        """
            [yellow]发动一次需要 ${playerData().respawnCost} LP！
            [red]同时复活与下一次技能所需点数上涨
        """.trimIndent()
    ) {
        fun checkPoint(): Boolean{
            return team().teamData().lifePoint >= playerData().respawnCost
        }
        /**
         * heal 拥有治疗队友的能力
        LPSkill-瞬间治疗所有队友至满血
         * tank 拥有展开立场盾的能力
        LPSkill-立场能量翻倍 最多3倍于最大生命值
         * attack 拥有粘性电立场(泥泞)
        LPSkill-获得超频超时boss三件套10s
         * rangeAttack 可以展开tecta同款立场护盾
        LPSkill-射程内所有单位unmoving5s
         * scout 拥有较高视野与移速 拥有自回血
        LPSkill-原地召唤哨兵塔(冰雹)
         */
       when(playerData().unitType) {
           in unitsWithKit[0] -> {
               add(listOf(
                   "LPSkill-瞬间治疗所有队友至满血" to {
                       if (checkPoint()){
                           team().teamData().players.forEach {
                               if (!it.dead()) it.unit().health = it.unit().maxHealth
                               Call.announce(it.con, "${name}使用了LPSkill-瞬间治疗所有队友至满血")
                           }
                           team().teamData().lifePoint -= playerData().respawnCost
                           playerData().respawnCost += 1
                       }
                   }))
           }
           in unitsWithKit[1] -> {
               add(listOf(
                   "LPSkill-立场能量翻倍 最多3倍于最大生命值" to {
                       if (checkPoint()) {
                           unit().shield = min((unit().shield * 2), unit().maxHealth * 3)
                           team().teamData().players.forEach {
                               Call.announce(it.con, "${name}使用了LPSkill-立场能量翻倍 最多3倍于最大生命值")
                           }
                           team().teamData().lifePoint -= playerData().respawnCost
                           playerData().respawnCost += 1
                       }
                   }))

           }
           in unitsWithKit[2] -> {
               add(listOf(
                   "LPSkill-获得超频超时boss三件套10s" to {
                       if (checkPoint()) {
                           unit().apply {
                               apply(StatusEffects.overdrive, 10f * 60f)
                               apply(StatusEffects.overclock, 10f * 60f)
                               apply(StatusEffects.boss, 10f * 60f)
                           }
                           launch(Dispatchers.game) {
                               delay(10_000)
                               unit().apply {
                                   unapply(StatusEffects.overdrive)
                                   unapply(StatusEffects.overclock)
                                   unapply(StatusEffects.boss)
                               }
                           }
                           team().teamData().players.forEach {
                               Call.announce(it.con, "${name}使用了LPSkill-获得超频超时boss三件套10s")
                           }
                           team().teamData().lifePoint -= playerData().respawnCost
                           playerData().respawnCost += 1
                       }
                   }))
           }
           in unitsWithKit[3] -> {
               add(listOf(
                   "LPSkill-获得缓慢超频超时boss保护20s" to {
                       if (checkPoint()) {
                           unit().apply {
                               apply(StatusEffects.overdrive, 20f * 60f)
                               apply(StatusEffects.overclock, 20f * 60f)
                               apply(StatusEffects.boss, 20f * 60f)
                               apply(StatusEffects.slow, 20f * 60f)
                               apply(StatusEffects.shielded, 20f * 60f)
                           }
                           launch(Dispatchers.game) {
                               delay(20_000)
                               unit().apply {
                                   unapply(StatusEffects.overdrive)
                                   unapply(StatusEffects.overclock)
                                   unapply(StatusEffects.boss)
                               }
                           }
                           team().teamData().players.forEach {
                               Call.announce(it.con, "${name}使用了LPSkill-获得缓慢超频超时boss保护20s")
                           }
                           team().teamData().lifePoint -= playerData().respawnCost
                           playerData().respawnCost += 1
                       }
                   }))
           }
           in unitsWithKit[4] -> {
               add(listOf(
                   "LPSkill-原地召唤哨兵塔(冰雹)" to {
                       if (checkPoint()) {
                           Vars.world.tile(unit().tileX(), unit().tileY()).setNet(Blocks.hail, team(), 0)
                           team().teamData().players.forEach {
                               Call.announce(it.con, "${name}使用了LPSkill-原地召唤哨兵塔(冰雹)")
                           }
                           team().teamData().lifePoint -= playerData().respawnCost
                           playerData().respawnCost += 1
                       }
                   }))
           }
       }
        if (team().teamData().leader == this@skillsMenu)
            this += listOf(
                "LPSkill-召集队友" to {
                    if (checkPoint()) {
                        val tx = unit().x
                        val ty = unit().y
                        team().teamData().players.forEach {
                            Call.announce(it.con, "${name}使用了LPSkill-召集队友 所有队友如没有受伤将在10s后传送！")
                            val unit = it.playerData().unit ?: return@forEach
                            val lastHealth = unit.health
                            launch(Dispatchers.game) {
                                delay(10_000)
                                if (lastHealth <= unit.health && !it.dead()){
                                    unit.kill()
                                    it.createUnit(World.toTile(tx), World.toTile(ty))
                                    it.sendMessage("[green]传送成功！")
                                }else {
                                    it.sendMessage("[red]传送失败！")
                                }
                            }
                        }
                        team().teamData().lifePoint -= playerData().respawnCost
                        playerData().respawnCost += 1
                    }
                }
            )
        this += listOf(
            "取消" to {}
        )
    }
}

fun findTileS(fx: Int, fy: Int, tx: Int, ty: Int, cx: Int = Vars.world.width() / 2 , cy: Int = Vars.world.height() / 2, min: Int = 0): Tile{
    var times = 0
    while (true) {
        val sx = Random.nextInt(fx, tx)
        val sy = Random.nextInt(fy, ty)

        val tile = Vars.world.tile(sx, sy)

        if (++times > 80) {
            findFail()
            return tile
        }

        if ((sx <= cx + min && sx >= cx - min) || (sy <= cy + min && sy >= cy - min)) continue

        if (tile.passable()) {
            return tile
        }
    }
}

fun findFail(){
    Call.sendMessage("[red]发生了一个错误！游戏强制结束")
    Vars.state.gameOver = true
    Events.fire(EventType.GameOverEvent(Team.derelict))
}

fun Player.createUnit(x: Int, y: Int){
    clearUnit()
    val unit = playerData().unitType!!.create(team())
    unit.apply {
        unit.set(x * 8f, y * 8f)
        playerData().unit = unit
        unitData().onwer = this@createUnit
        add()
        unit(unit)
    }
}

onEnable {
    contextScript<coreMindustry.ContentsTweaker>().addPatch("LPC",
        "{\n" +
                "  \"block\": {\n" +
                "    \"core-acropolis\": {\n" +
                "      \"fogRadius\": 1000,\n" +
                "      \"unitType.buildSpeed\": 0\n" +
                "    },\n" +
                "    \"hail\": {\n" +
                "      \"fogRadius\": 51,\n" +
                "      \"solid\": false\n" +
                "    }\n" +
                "  },\n" +
                "  \"unit\": {\n" +
                "    \"nova\": {\n" +
                "      \"health\": 120,\n" +
                "      \"armor\": 0,\n" +
                "      \"buildSpeed\": 0,\n" +
                "      \"canBoost\": false\n" +
                "    },\n" +
                "    \"pulsar\": {\n" +
                "      \"health\": 120,\n" +
                "      \"armor\": 0,\n" +
                "      \"buildSpeed\": 0,\n" +
                "      \"canBoost\": false,\n" +
                "      \"abilities.0\": {\n" +
                "        \"amount\": 0\n" +
                "      },\n" +
                "      \"abilities.+=\": [\n" +
                "        {\n" +
                "          \"type\": \"RepairFieldAbility\",\n" +
                "          \"amount\": 8,\n" +
                "          \"reload\": 240,\n" +
                "          \"range\": 60\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"stell\": {\n" +
                "      \"health\": 180,\n" +
                "      \"armor\": 0,\n" +
                "      \"abilities.+=\": [\n" +
                "        {\n" +
                "          \"type\": \"ForceFieldAbility\",\n" +
                "          \"radius\": 55,\n" +
                "          \"regen\": 0.5,\n" +
                "          \"max\": 180,\n" +
                "          \"cooldown\": 960\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"locus\": {\n" +
                "      \"health\": 220,\n" +
                "      \"armor\": 0,\n" +
                "      \"weapons.0.bullet.damage\": 14,\n" +
                "      \"abilities.+=\": [\n" +
                "        {\n" +
                "          \"type\": \"ForceFieldAbility\",\n" +
                "          \"radius\": 45,\n" +
                "          \"regen\": 0.4,\n" +
                "          \"max\": 120,\n" +
                "          \"cooldown\": 960\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"precept\": {\n" +
                "      \"health\": 260,\n" +
                "      \"armor\": 0,\n" +
                "      \"weapons.0.bullet.damage\": 12,\n" +
                "      \"weapons.0.bullet.splashDamage\": 12,\n" +
                "      \"weapons.0.bullet.fragBullet.damage\": 6,\n" +
                "      \"abilities.+=\": [\n" +
                "        {\n" +
                "          \"type\": \"ForceFieldAbility\",\n" +
                "          \"radius\": 35,\n" +
                "          \"regen\": 0.3,\n" +
                "          \"max\": 80,\n" +
                "          \"cooldown\": 960\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"dagger\": {\n" +
                "      \"health\": 180,\n" +
                "      \"armor\": 0,\n" +
                "      \"abilities.+=\": [\n" +
                "        {\n" +
                "          \"type\": \"EnergyFieldAbility\",\n" +
                "          \"hitBuildings\": false,\n" +
                "          \"damage\": 10,\n" +
                "          \"maxTargets\": 2,\n" +
                "          \"healPercent\": 1,\n" +
                "          \"status\": \"muddy\"\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"mace\": {\n" +
                "      \"health\": 200,\n" +
                "      \"armor\": 0,\n" +
                "      \"abilities.+=\": [\n" +
                "        {\n" +
                "          \"type\": \"EnergyFieldAbility\",\n" +
                "          \"hitBuildings\": false,\n" +
                "          \"damage\": 10,\n" +
                "          \"maxTargets\": 3,\n" +
                "          \"healPercent\": 1,\n" +
                "          \"status\": \"muddy\"\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"fortress\": {\n" +
                "      \"health\": 160,\n" +
                "      \"armor\": 0,\n" +
                "      \"weapons.0.bullet.splashDamage\": 30,\n" +
                "      \"abilities.+=\": [\n" +
                "        {\n" +
                "          \"type\": \"ShieldArcAbility\",\n" +
                "          \"region\": \"tecta-shield\",\n" +
                "          \"radius\": 34,\n" +
                "          \"angle\": 82,\n" +
                "          \"regen\": 0.6,\n" +
                "          \"cooldown\": 480,\n" +
                "          \"max\": 80,\n" +
                "          \"y\": -20,\n" +
                "          \"width\": 6\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"quasar\": {\n" +
                "      \"health\": 150,\n" +
                "      \"armor\": 0,\n" +
                "      \"weapons.0.bullet.damage\": 17,\n" +
                "      \"canBoost\": false,\n" +
                "      \"buildSpeed\": 0,\n" +
                "      \"abilities.0\": {\n" +
                "        \"max\": 0,\n" +
                "        \"radius\": 0\n" +
                "      },\n" +
                "      \"abilities.+=\": [\n" +
                "        {\n" +
                "          \"type\": \"ShieldArcAbility\",\n" +
                "          \"region\": \"tecta-shield\",\n" +
                "          \"radius\": 34,\n" +
                "          \"angle\": 82,\n" +
                "          \"regen\": 0.3,\n" +
                "          \"cooldown\": 480,\n" +
                "          \"max\": 80,\n" +
                "          \"y\": -20,\n" +
                "          \"width\": 6\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"elude\": {\n" +
                "      \"health\": 160,\n" +
                "      \"armor\": 0,\n" +
                "      \"weapons.0.bullet.damage\": 6,\n" +
                "      \"fogRadius\": 51\n" +
                "    }\n" +
                "  }\n" +
                "}"
    )
    Vars.state.rules.apply{
        unitCap = 999999
        defaultTeam = Team.get(255)
        waveTeam = Team.get(255)
        modeName = "LifePoint Champions"
        canGameOver = false
        pvp = false
    }
    launch(Dispatchers.game){
        val centerX = Vars.world.width() / 2
        val centerY = Vars.world.height() / 2
        Call.setMapArea(centerX - readyAreaRange, centerY - readyAreaRange, readyAreaRange * 2, readyAreaRange * 2)
        Call.setRules(Vars.state.rules)
        val startTime = Time.millis()
        val interval = Interval(2)
        while (Time.timeSinceMillis(startTime) <= 120 * 1000) {
            if (interval[0, 30f * 60f]) broadcast("[yellow]还有{time}秒开始".with("time" to (120 - ceil(Time.timeSinceMillis(startTime) / 1000f))), quite = true)
            if (gameStart) break
            yield()
        }
        val count = Groups.player.size()
        val teamAmount = min(Random.nextInt(2,3), count)
        val teamPlayerAmount = count / teamAmount
        gameStart = true
        broadcast("[yellow]游戏开始！本局有{num}个队伍\n每队{pnum}个玩家".with("num" to teamAmount, "pnum" to teamPlayerAmount), quite = true)
        Call.setMapArea(0, 0, Vars.world.width(), Vars.world.height())
        Call.setRules(Vars.state.rules)
        repeat(teamAmount){ it ->
            val team = Team.get(it + 10)
            team.teamData().apply {
                players = Groups.player.toMutableList().subList(it * teamPlayerAmount, (it + 1) * teamPlayerAmount)
                leader = players.random()
                players.forEach { p ->
                    betterTeam.changeTeam(p,team)
                    p.sendMessage(buildString {
                        appendLine("[#${p.team().color}]队伍${p.team().name}")
                        players.forEach { appendLine(if (it != leader) "[blue]成员:${it.name}" else "[cyan]队长:${it.name}") }
                    })
                }
                spawnTile = findTileS(0, 0, Vars.world.width(), Vars.world.height(), min = readyAreaRange)
                teams.add(team)
            }
        }
        teams.forEach { team ->
            team.teamData().apply {
                players.forEach { p ->
                    if (p.playerData().unitType == null){
                        p.playerData().unitType = unitsWithKit.random().random()
                        p.sendMessage("[green]未选择单位！自动选择职业：[white]${p.playerData().unitType!!.emoji()}${p.playerData().unitType!!.localizedName}${p.playerData().unitType!!.emoji()}")
                    }
                    p.createUnit(spawnTile.x.toInt(), spawnTile.y.toInt())
                }
                Call.effect(Fx.impactReactorExplosion, spawnTile.x * 8f, spawnTile.y * 8f, 0f, Color.red)
            }
        }
        val startStartTime = Time.millis()
        val warningInterval = finalBattleTime / 20 * 60f
        while (Time.timeSinceMillis(startStartTime) / 1000f <= finalBattleTime) {
            if (interval[1, warningInterval]) {
                broadcast("[yellow]还有{time}秒决战开始".with("time" to (finalBattleTime - Time.timeSinceMillis(startStartTime) / 1000f).toInt()), quite = true)
                if (Random.nextFloat() >= 0.8){
                    launch(Dispatchers.game){
                        val startDropTime = Time.millis()
                        val amount = if(Random.nextFloat() >= 0.7) Random.nextInt(5, 20) else Random.nextInt(25, 40)
                        val dropTime = min((amount * 2 * Random.nextFloat() + 1).toInt(), ((finalBattleTime - Time.timeSinceMillis(startStartTime) / 1000f) / 2).toInt())
                        val tile = findTileS(0, 0, Vars.world.width(), Vars.world.height())
                        var text = buildString {
                            appendLine("[yellow]在(${tile.x.toInt()},${tile.y.toInt()})检测到空投！")
                            appendLine("[white]${Iconc.liquidNeoplasm}$amount")
                            append("[yellow]预计在${dropTime}后空投降落！")
                        }
                        Call.announce(text)
                        Call.sendMessage(text)
                        while(Time.timeSinceMillis(startDropTime) / 1000 <= dropTime){
                            Call.label("[red]空投降落！\n[white]${Iconc.liquidNeoplasm}$amount [lightgray]${dropTime - Time.timeSinceMillis(startDropTime) / 1000}", 0.2026f, tile.x * 8f, tile.y * 8f)
                            Call.effect(Fx.spawnShockwave, tile.x * 8f, tile.y * 8f, 0f, Color.red)
                            delay(200)
                        }
                        text = "位于(${tile.x.toInt()},${tile.y.toInt()})的空投已经降落！"
                        Call.announce(text)
                        Call.sendMessage(text)
                        Call.effect(Fx.impactReactorExplosion, tile.x * 8f, tile.y * 8f, 0f, Color.red)
                        LPDrop(amount, tile.x * 8f, tile.y * 8f, true, 120_000, Float.NEGATIVE_INFINITY, 0f)
                    }
                }
            }
            if (finalBattle) break
            yield()
        }
        finalBattle = true
        teams.forEach { team ->
            team.teamData().apply {
                spawnTile = findTileS(centerX - readyAreaRange, centerY - readyAreaRange, centerX + readyAreaRange, centerY + readyAreaRange)
                players.forEach { p ->
                    if (!p.dead()) {
                        p.unit().kill()
                        p.createUnit(spawnTile.x.toInt(), spawnTile.y.toInt())
                    }else if(p.playerData().respawnTime != 0L){
                        p.createUnit(spawnTile.x.toInt(), spawnTile.y.toInt())
                        p.playerData().respawnTime = 0L
                    }

                }
                Call.effect(Fx.reactorExplosion, spawnTile.x * 8f, spawnTile.y * 8f, 0f, Color.red)
                Call.setRules(Vars.state.rules)
            }
        }
        var pnum = 0
        teams.forEach { pnum += it.teamData().players.size }
        broadcast("[yellow]最终决战开始！复活冷却大幅降低！\n剩余队伍：{tnum}\n剩余人数：{pnum}".with("tnum" to teams.size, "pnum" to pnum), quite = true)
        Call.setMapArea(centerX - readyAreaRange, centerY - readyAreaRange, readyAreaRange * 2, readyAreaRange * 2)
    }
    loop(Dispatchers.game){
            if (gameStart) {
                Vars.state.rules.modeName = "LPC|${teams.size}Teams Left"
                teams.removeIf { it.teamData().players.size == 0 || it.teamData().players.all { it.playerData().unit == null || it.playerData().unit!!.dead()} }
                if (teams.size == 0){
                    Vars.state.gameOver = true
                    Events.fire(EventType.GameOverEvent(Team.derelict))
                    gameStart = false
                    gameEnd = true
                    return@loop
                }
                teams.forEach { team ->
                    team.teamData().apply {
                        if (teams.size == 1) {
                            Vars.state.gameOver = true
                            Events.fire(EventType.GameOverEvent(team))
                            gameStart = false
                            gameEnd = true
                            return@loop
                        }
                        players.forEach playerIndex@{ p ->
                            if (p.con.hasDisconnected || p.team() != team) {
                                p.playerData().unit?.unitData()?.onwer = null
                                players.remove(p)
                                if (players.size == 0) {
                                    teams.remove(team)
                                    return@forEach
                                }
                                return@playerIndex
                            }
                            if (p == leader)
                                p.unit().apply(StatusEffects.boss, 1.5f * 60f)
                            if (p.dead() && p.playerData().respawnTime == 0L && lifePoint >= p.playerData().respawnCost) {
                                p.playerData().respawnTime = Time.millis() + if (finalBattle) 5_000 else 30_000
                                lifePoint -= p.playerData().respawnCost
                                p.playerData().respawnCost += 1
                            }
                            if (p.dead() && p.playerData().checkRespawn() && p.playerData().respawnTime != 0L) {
                                p.playerData().respawnTime = 0L
                                val list = players.toMutableList()
                                list.remove(p)
                                val player = list.random()
                                p.createUnit(player.tileX(), player.tileY())
                            }
                            Call.infoPopup(
                                p.con,
                                buildString {
                                    appendLine("[#${p.team().color}]队伍${p.team().name}")
                                    appendLine("Life Point:${p.team().teamData().lifePoint}")
                                    if (p.team().teamData().lifePoint >= p.playerData().respawnCost) {
                                        appendLine("[green]LPSkills可使用！|点击自己单位花费[white]${p.playerData().respawnCost}[green]使用LPSkills")
                                    }
                                    players.forEach {
                                        append(if (it != leader) "[blue]成员:${it.name}" else "[cyan]队长:${it.name}")
                                        appendLine("[white]${it.playerData().unitType!!.emoji()}${it.playerData().unitType!!.localizedName}${it.playerData().unitType!!.emoji()}")
                                        if (it.playerData().respawnTime != 0L) {
                                            val respawnTime =
                                                (if (finalBattle) 5 else 30) - ((it.playerData().respawnTime - Time.millis()) / 1000).toInt()
                                            append("[green]${Iconc.refresh}")
                                            repeat((respawnTime / if (finalBattle) 0.5f else 3f).toInt()) {
                                                append("[green]|")
                                            }
                                            repeat(10 - (respawnTime / if (finalBattle) 0.5f else 3f).toInt()) {
                                                append("[red]|")
                                            }
                                            appendLine("[white]${respawnTime}/${if (finalBattle) 5 else 30}")
                                        } else if (!it.dead()) {
                                            append("[green]${Iconc.add}")
                                            repeat((it.unit().health / it.unit().maxHealth * 10).toInt()) {
                                                append("[green]|")
                                            }
                                            repeat(10 - (it.unit().health / it.unit().maxHealth * 10).toInt()) {
                                                append("[red]|")
                                            }
                                            appendLine("[white]${it.unit().health}/${it.unit().maxHealth}")
                                        } else {
                                            appendLine("[red]已死亡 需要[white]${it.playerData().respawnCost}[red]点LP复活")
                                        }
                                    }
                                }, 1.013f,
                                Align.topRight, 350, 0, 0, 0
                            )
                        }
                    }
                }
            } else {
                if(!gameEnd)
                    Vars.state.rules.modeName = "LPC|准备中"
            }
        delay(1000)
    }
}

listen<EventType.UnitControlEvent> {
    val unit: mindustry.gen.Unit = it.unit ?: return@listen
    val owner = unit.unitData().onwer
    if (it.player != owner){
        it.player.clearUnit()
        Call.announce(it.player.con(),"[red]你不该控制此单位！无法控制")
    }
}

command("start", "立刻开始游戏") {
    permission = id.replace("/", ".")
    body {
        val name = if (player == null) "[red]Server" else player!!.name
        broadcast("[yellow]玩家[white]{player}[yellow]强制开启了游戏".with("player" to name), quite = true)
        if (gameStart)
            finalBattle = true
        gameStart = true
    }
}

listen<EventType.TapEvent> {
    val player = it.player
    if (!gameStart && !gameEnd) {
        launch(Dispatchers.game) { player.kitMenu() }
    }else {
        if (player.dead()) return@listen
        if (it.tile.team() == player.team() ||
            (player.unit().within(it.tile.worldx(), it.tile.worldy(), player.unit().hitSize))) {
            launch(Dispatchers.game) { player.skillsMenu() }
        }
    }
}

listen<EventType.UnitDestroyEvent> { u ->
    if (!u.unit.spawnedByCore)
        LPDrop(1, u.unit.x, u.unit.y)
}
