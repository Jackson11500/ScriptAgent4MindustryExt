@file:Depends("wayzer/maps", "地图管理")
@file:Depends("wayzer/voteService", "投票实现")

package wayzer.ext

import arc.Core
import arc.Events
import arc.util.Time
import coreLibrary.lib.PermissionApi
import coreLibrary.lib.PlaceHold
import coreLibrary.lib.with
import coreMindustry.lib.broadcast
import coreMindustry.lib.game
import coreMindustry.lib.hasPermission
import coreMindustry.lib.player
import mindustry.Vars
import mindustry.Vars.*
import mindustry.ctype.ContentType
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.type.UnitType
import wayzer.MapManager
import wayzer.VoteService
import wayzer.lib.dao.PlayerData
import java.time.Instant
import kotlin.math.min
import kotlin.random.Random

val voteService = contextScript<VoteService>()

fun VoteService.register() {
    /*
    addSubVote("换图投票", "<地图ID> [网络换图类型参数]", "map", "换图") {
        if (arg.isEmpty())
            returnReply("[red]请输入地图序号".with())
        launch(Dispatchers.game) {
            val map = arg[0].toIntOrNull()?.let { MapRegistry.findById(it, reply) }
                ?: return@launch reply("[red]地图序号错误,可以通过/maps查询".with())
            start(
                player!!,
                "换图([green]{nextMap.id}[]: [green]{nextMap.map.name}[yellow]|[green]{nextMap.mode}[])".with("nextMap" to map),
                supportSingle = true
            ) {
                if (map.map.file.exists() && !SaveIO.isSaveValid(map.map.file))
                    return@start broadcast("[red]换图失败,地图[yellow]{nextMap.name}[green](id: {nextMap.id})[red]已损坏".with("nextMap" to map))
                MapManager.loadMap(map)
                Core.app.post { // 推后,确保地图成功加载
                    broadcast("[green]换图成功,当前地图[yellow]{map.name}[green](id: {map.id})".with())
                }
            }
        }
    }

     */
    addSubVote("投降或结束该局游戏，进行结算", "", "gameOver", "投降", "结算") {
        if (state.rules.pvp) {
            val team = player!!.team()
            if (!state.teams.isActive(team) || state.teams.get(team)!!.cores.isEmpty)
                returnReply("[red]队伍已输,无需投降".with())

            canVote = canVote.let { default -> { default(it) && it.team() == team } }
            //requireNum = { allCanVote().size }
            start(player!!, "投降({team.colorizeName}[yellow]队|需要全队同意)".with("player" to player!!, "team" to team)) {
                launch(Dispatchers.game) {
                    state.teams.get(team).destroyToDerelict()
                }
            }
        }
        start(player!!, "投降".with(), supportSingle = true) {
            state.gameOver = true
            Events.fire(EventType.GameOverEvent(state.rules.waveTeam))
        }
    }
    addSubVote("强制结束该局游戏并返回观星台", "", "back") {
        start(player!!, "[red]结束游戏并返回观星台[]".with(), supportSingle = true) {
            state.gameOver = true
            Events.fire(EventType.GameOverEvent(Team.derelict))
        }
    }
    addSubVote("快速出波(默认10波,最高50)", "[波数]", "skipWave", "跳波") {
        val lastResetTime by PlaceHold.reference<Instant>("state.startTime")
        val t = min(arg.firstOrNull()?.toIntOrNull() ?: 10, 50)
        start(player!!, "跳波({t}波)".with("t" to t), supportSingle = true) {
            launch {
                val startTime = Instant.now()
                var waitTime = 3
                repeat(t) {
                    while (state.enemies > 300) {//延长等待时间
                        if (waitTime > 60) return@launch //等待超时
                        delay(waitTime * 1000L)
                        waitTime *= 2
                    }
                    if (lastResetTime > startTime) return@launch //Have change map
                    Core.app.post { logic.runWave() }
                    delay(waitTime * 1000L)
                }
            }
        }
    }
    /*
    addSubVote("回滚到某个存档(使用/slots查看)", "<存档ID>", "rollback", "load", "回档") {
        if (arg.firstOrNull()?.toIntOrNull() == null)
            returnReply("[red]请输入正确的存档编号".with())
        val map = MapManager.getSlot(arg[0].toInt())
            ?: returnReply("[red]存档不存在或存档损坏".with())
        start(player!!, "回档".with(), supportSingle = true) {
            MapManager.loadSave(map)
            broadcast("[green]回档成功".with(), quite = true)
        }
    }

     */
    addSubVote("踢出某人15分钟", "<玩家名>", "kick", "踢出") {
        fun getTarget(arg: List<String>): Player {
            if (arg.isEmpty()) returnReply("[red]请输入玩家三位id".with())
            if (arg[0].startsWith("#"))
                Groups.player.getByID(arg[0].substring(1).toIntOrNull() ?: 0)?.let { return it }
            return Groups.player.find { it.name.replace(" ", "") == arg.joinToString("") }
                ?: thisContextScript().depends("wayzer/user/shortID")?.import<(String) -> String?>("getUUIDbyShort")
                    ?.invoke(arg[0])?.let { uuid -> Groups.player.find { it.uuid() == uuid } }
                ?: returnReply("[red]请输入正确的玩家名".with())
        }
        val target = getTarget(arg)
        start(player!!, "踢人(踢出[red]{target.name}[yellow])".with("target" to target)) {
            if (target.admin || target.hasPermission("wayzer.admin.skipKick"))
                return@start broadcast("[red]错误: {target.name}[red]拥有防踢权限, 如有问题请与服主联系".with("target" to target))
            target.info.lastKicked = Time.millis() + (15 * 60 * 1000) //Kick for 15 Minutes
            target.con?.kick("[yellow]你被投票踢出15分钟")
            val secureLog = depends("wayzer/admin")?.import<(String, String) -> Unit>("secureLog") ?: return@start
            secureLog(
                "Kick",
                "${target.name}(${target.uuid()},${target.con.address}) is kicked By ${player!!.name}(${player!!.uuid()})"
            )
        }
    }
    addSubVote("清理本队建筑记录", "", "clear", "清理", "清理记录") {
        val team = player!!.team()

        canVote = canVote.let { default -> { default(it) && it.team() == team } }
        //requireNum = { ceil(allCanVote().size * 2.0 / 5).toInt() }
        start(player!!, "清理建筑记录({team.colorizeName}[yellow]队|需要2/5同意)".with("team" to team)) {
            team.data().plans.clear()
        }
    }
    addSubVote("自定义投票", "<内容>", "text", "文本", "t") {
        if (arg.isEmpty()) returnReply("[red]请输入投票内容".with())
        start(player!!, "自定义([green]{text}[])".with("text" to arg.joinToString(" "))) {}
    }
    addSubVote("生成单位(畸变)", "[类型ID=列出] [数量=1]", "spawn", "生成", "畸变") {
        if (MapManager.current.id <= 1000 && MapManager.current.id != 1) returnReply("[red]你无法在这里使用<畸变>的权能".with())
        if (!player!!.hasPermission("xkldklp.12t")) {
            returnReply("[red]未拥有权限，请检查是否启用终焉铭<畸变>".with())
        }
        val list = content.getBy<UnitType>(ContentType.unit).filterNot { it.internal }
        val type = arg.getOrNull(0)?.toIntOrNull()?.let { list.getOrNull(it) } ?: returnReply(
                "[red]请输入类型ID: {list}"
                        .with("list" to list.mapIndexed { i, type -> "[yellow]$i[green]($type)" }.joinToString())
        )
        val team = player!!.team()
        val unit = player!!.unit()
        val num = arg.getOrNull(1)?.toIntOrNull() ?: 1
        start(player!!, "为{team}[]生成([green]{type}[] * {amount})".with(
                "team" to "[#${team.color}]${team.name}",
                "type" to type,
                "amount" to num
        )) {
            repeat(num) {
                type.create(team).apply {
                    if (unit != null) set(unit.x, unit.y)
                    else team.data().core()?.let {
                        set(it.x, it.y)
                    }
                    add()
                }
            }
            broadcast("已为{team}[]生成([green]{type}[] * {amount})".with(
                    "team" to "[#${team.color}]${team.name}",
                    "type" to type,
                    "amount" to num
            ))
        }
    }
    addSubVote("改变规则(律令)", "[类型=列出] [数值]", "rule", "规则", "律令") {
        if (MapManager.current.id <= 1000 && MapManager.current.id != 1) returnReply("[red]你无法在这里使用<律令>的权能".with())
        if (!player!!.hasPermission("xkldklp.12f")) {
            returnReply("[red]未拥有权限，请检查是否启用始源铭<律令>".with())
        }
        val ruleList = listOf(
                "unitBuildSpeed",
                "unitCost",
                "unitCrashDamage",
                "unitDamage",
                "unitHealth",
                "buildCost",
                "blockDamage",
                "blockHealth",
                "buildSpeed"
        )
        if (!ruleList.contains(arg.firstOrNull())) {
            returnReply("请输入类型: \n{list}".with("list" to ruleList.joinToString("\n")))
        } else {
            val num = arg.getOrNull(1)?.toFloatOrNull() ?: returnReply("[red]请输入数值".with())
            start(player!!, "修改({rule})为({num})".with("rule" to arg[0], "num" to arg[1])){
                when(arg[0]) {
                    "unitBuildSpeed" -> state.rules.unitBuildSpeedMultiplier = num
                    "unitCost" -> state.rules.unitCostMultiplier = num
                    "unitCrashDamage" -> state.rules.unitCrashDamageMultiplier = num
                    "unitDamage" -> state.rules.unitDamageMultiplier = num
                    "unitHealth" -> state.rules.unitHealthMultiplier = num
                    "buildCost" -> state.rules.buildCostMultiplier = num
                    "blockDamage" -> state.rules.blockDamageMultiplier = num
                    "blockHealth" -> state.rules.blockHealthMultiplier = num
                    "buildSpeed"  -> state.rules.buildSpeedMultiplier = num
                }
                Call.setRules(state.rules)
            }
        }
    }
}

onEnable {
    voteService.register()
}

PermissionApi.registerDefault("wayzer.vote.*")