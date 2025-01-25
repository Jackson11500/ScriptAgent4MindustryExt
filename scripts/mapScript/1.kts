@file:Depends("coreMindustry/menu", "调用菜单")
@file:Depends("coreMindustry/utilTextInput", "调用菜单")
@file:Depends("wayzer/voteService", "投票实现")
@file:Depends("wayzer/user/userService", "玩家绑定")
@file:Depends("wayzer/user/achievement", "成就")
@file:Depends("xkldklp/KVars")
@file:Depends("xkldklp/user/inscription")
@file:Depends("xkldklp/user/spinscription")
@file:Depends("xkldklp/user/sign")
@file:Depends("xkldklp/user/achieveStatistics")
@file:Depends("inscription/effect")
@file:Depends("inscription/prefix")

package mapScript

import arc.Core
import arc.graphics.Color
import arc.graphics.Colors
import arc.math.Mathf.pow
import coreLibrary.lib.with
import coreMindustry.MenuBuilder
import coreMindustry.lib.broadcast
import coreMindustry.lib.game
import coreMindustry.lib.listen
import coreMindustry.lib.sendMessage
import inscription.Effect
import inscription.Prefix
import kotlinx.coroutines.Dispatchers
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.entities.units.StatusEntry
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.WorldLabel
import mindustry.io.SaveIO
import mindustry.ui.Menus
import mindustry.world.blocks.campaign.Accelerator
import mindustry.world.blocks.campaign.LaunchPad
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.transactions.transaction
import wayzer.MapInfo
import wayzer.MapManager
import wayzer.MapRegistry
import wayzer.VoteService
import wayzer.lib.dao.PlayerData
import wayzer.lib.dao.PlayerProfile
import wayzer.user.AchievementEntity
import wayzer.user.UserService
import xkldklp.user.InscriptionEntity
import xkldklp.user.Spinscription
import xkldklp.user.SignEntity
import xkldklp.user.SpInscriptionEntity
import kotlin.math.*
import kotlin.random.Random

val voteService = contextScript<VoteService>()
val userService = contextScript<UserService>()
val prefix = contextScript<Prefix>()
val effect = contextScript<Effect>()
val inscription = contextScript<xkldklp.user.Inscription>()
val spinscription = contextScript<Spinscription>()
val achieveStatistics = contextScript<xkldklp.user.AchieveStatistics>()
val textInput = contextScript<coreMindustry.UtilTextInput>()
val achievement = contextScript<wayzer.user.Achievement>()
val kVars = contextScript<xkldklp.KVars>()

val localhost = "h1.getmc.cn:33455"

fun Player.achievement(name: String, exp: Int, broadcast: Boolean = false) {
    val profile = PlayerData[uuid()].profile
    if (profile != null)
        achievement.finishAchievement(profile, name, exp, broadcast)
}

val mapTile = Vars.world.tile(113,113)!!
val signTile = Vars.world.tile(86,86)!!
val inscriptionTile = Vars.world.tile(86,113)!!
val bindTile = Vars.world.tile(113,86)!!
val achievementTile = Vars.world.tile(44,154)!!
val ruleTile = Vars.world.tile(154,154)!!

onEnable {
    launch(Dispatchers.game) {
        delay(2_000)
        drawing.clear()

        Groups.label.forEach { it.remove() }
        val mapLabel = WorldLabel.create().apply {
            set(mapTile)
            y += 8f
            fontSize = 2f
            text = buildString {
                appendLine("[cyan]打开星图")
                append("[lightgray]选择地图")
            }
            snapInterpolation()
        }.add()
        val signLabel = WorldLabel.create().apply {
            set(signTile)
            y += 8f
            fontSize = 2f
            text = buildString {
                appendLine("[cyan]星辉共鸣")
                append("[lightgray]每日签到")
            }
            snapInterpolation()
        }.add()
        val inscriptionLabel = WorldLabel.create().apply {
            set(inscriptionTile)
            y += 8f
            fontSize = 2f
            text = buildString {
                appendLine("[cyan]铭刻星铭")
                append("[lightgray]技能抽取")
            }
            snapInterpolation()
        }.add()
        val bindLabel = WorldLabel.create().apply {
            set(bindTile)
            y += 8f
            fontSize = 2f
            text = buildString {
                appendLine("[cyan]登记新星")
                append("[lightgray]绑定账号")
            }
            snapInterpolation()
        }.add()
        val achievementLabel = WorldLabel.create().apply {
            set(achievementTile)
            y += 12f
            x += 4f
            fontSize = 2f
            text = buildString {
                appendLine("[cyan]光辉事迹")
                append("[lightgray]成就列表")
            }
            snapInterpolation()
        }.add()
        val ruleLabel = WorldLabel.create().apply {
            set(ruleTile)
            y += 12f
            x += 4f
            fontSize = 2f
            text = buildString {
                appendLine("[cyan]世界拉入")
                append("[lightgray]规则突变")
            }
            snapInterpolation()
        }.add()
    }
    if (kVars.S1A9Event) {
        launch(Dispatchers.game) {
            Call.sendMessage("[yellow]星图发生了什么改变...")
            kVars.S1A9Event = false
            while (true) {
                Call.effect(Fx.spawn, mapTile.x * 8f, mapTile.y * 8f, 1f, Color.red)
                delay(500)
            }
        }
    }
}

fun Player.returnUnbind() {
    sendMessage("[red]未绑定")
}

listen<EventType.TapEvent> {
    val tile = it.tile
    val player = it.player
    when {
        mapTile.within(tile, 2 * 8f) -> {
            MapMenu(player).sendTo()
        }
        bindTile.within(tile, 2 * 8f) -> {
            AccountMenu(player).sendTo()
        }
        inscriptionTile.within(tile, 2 * 8f) -> {
            InscriptionMenu(player).sendTo()
        }
        signTile.within(tile, 2 * 8f) -> {
            val profile = PlayerData[player.uuid()].profile ?: return@listen
            launch(Dispatchers.IO) {
                transaction {
                    val exp = SignEntity.sign(profile.id)
                    if (exp == -1) {
                        player.sendMessage("[red]你今天已经签到过了！")
                    } else {
                        Call.label("[green]经验+${exp}[]\n${player.name}", 12f, player.x, player.y)
                        if(exp > 90) {
                            player.achievement("[purple][+90!]", 200)
                            launch(Dispatchers.game) {
                                repeat(60) {
                                    repeat(4) {
                                        Call.effect(Fx.pointBeam,
                                            Random.nextFloat() * 8f * Vars.world.width(),
                                            Random.nextFloat() * 8f * Vars.world.height(),
                                            5f,
                                            Colors.getColors().values().toList().random(),
                                            player.unit())
                                    }
                                    delay(200)
                                }
                            }
                        }
                        if(exp > 80) {
                            Call.effect(Fx.impactReactorExplosion, player.x, player.y, 0f, Color.red)
                        }
                    }
                }
            }
        }
        achievementTile.within(tile, 2 * 8f) -> {
            val profile = PlayerData[player.uuid()].profile ?: return@listen
            AchievementMenu(player).sendTo()
        }
        ruleTile.within(tile, 2 * 8f) -> {
            RuleMenu(player).sendTo()
        }
    }
    if(tile.block() is LaunchPad) {
        player.team(if (player.team() == Team.sharded) Team.crux else Team.sharded)
    }
    if(tile.block() is Accelerator) {
        AcceleratorMenu(player).sendTo()
    }
}

class RuleMenu(private val player: Player): MenuBuilder<Unit>() {
    var tab: Int = 0
    var page: Int = 1
    lateinit var world: Pair<Pair<String, String>, Int>
    lateinit var blackIDList: List<Int>

    fun sendTo() {
        launch(Dispatchers.game) {
            sendTo(player, 60_000)
        }
    }

    fun worldActive(id: Int = world.second): Boolean {
        return id in kVars.enableMapScripts
    }

    fun buildWorld(name: String, desc: String, id: Int, blackList: List<Int>? = null) {
        if (blackList?.any { it in kVars.enableMapScripts } == true) {
            option("[red]--DISABLE--") {
                refresh()
            }
        } else {
            option("${if (worldActive(id)) "[green]" else "[red]"}${name}") {
                tab = 1
                world = (name to desc) to id
                blackIDList = blackList ?: listOf()
                refresh()
            }
        }

    }

    private suspend fun mainMenu() {
        title = "[cyan]世界拉入"
        msg = "[yellow]选择你要拉入的世界!(仅对网络地图生效)\n可拉入世界数随等级提升" +
                (if (kVars.enableMapScripts.size >= 2) "\n[red]拉入多世界可能导致未知的bug" else "") +
                        (if (kVars.enableMapScripts.size >= 4) "\n[red]你拉入那么多世界干什么?" else "") +
                                (if (kVars.enableMapScripts.size >= 6) "\n[red]发起全面战争。" else "")

        buildWorld("领主战争之域", "[cyan]LordOfWar模式!", 14668, listOf(13545, 100061, 100062))
        newRow()
        buildWorld("俘获强夺之域", "[cyan]单位被摧毁后会被转化为其他队伍单位!", 100051)
        newRow()
        buildWorld("历战之域", "[cyan]单位击杀单位或建筑可升级!", 100061, listOf(14668, 100062))
        newRow()
        buildWorld("精英之域", "[cyan]弱化版的历战之域,升级更简单但是有上限!", 100062, listOf(14668, 100061))
        newRow()
        buildWorld("GRW招生之域", "[cyan]在非pvp模式下输入\n[yellow]/还是pvp大佬\n[cyan]即可在波次队伍与玩家队伍间切换\n[lightgray]还是pvp大佬", 100071)
        newRow()
        buildWorld("美人一小块地", "[cyan]CoreWar模式!", 13545, listOf(14668))
        newRow()
        buildWorld("英灵神殿", "[cyan]召唤英雄单位!", 100081)
        newRow()
        buildWorld("幽默之域", "[cyan]幽默描述", 100091)
        newRow()
        buildWorld("重构之域", "[cyan]单位死亡将会掉落一部分物品至最近核心!", 100101)
        newRow()
        buildWorld("简单世界", "[cyan]启用简单模式!\n[red]人多慎重启用此kts!!!", 100111)
        newRow()
        buildWorld("加速世界", "[cyan]WRYYYYY", 100121)
    }
    private suspend fun worldMenu() {
        title = "[cyan]世界拉入-${world.first.first}"
        msg = world.first.second
        if (blackIDList.any { it in kVars.enableMapScripts }) {
            option("[red]--DISABLE--") {
                refresh()
            }
        } else {
            option(if (worldActive()) "[green]目前状态:预备" else "[red]目前状态:未激活") {
                if (worldActive()) {
                    kVars.enableMapScripts.remove(world.second)
                    Call.sendMessage("${player.name}[yellow]取消了世界拉入-${world.first.first}")
                } else {
                    if (kVars.enableMapScripts.size * 2 <= floor(sqrt(max(PlayerData[player.uuid()].profile?.totalExp ?: 100, 0).toDouble()) / 10).toInt()) {
                        kVars.enableMapScripts.add(world.second)
                        Call.sendMessage("${player.name}[yellow]开启了世界拉入-${world.first.first}")
                    } else {
                        player.sendMessage("[red]当前等级不足以拉入这么多世界")
                    }
                }
                refresh()
            }
        }
        newRow()
        option("返回") {
            tab = 0
            refresh()
        }
    }

    override suspend fun build() {
        when(tab) {
            0 -> mainMenu()
            1 -> worldMenu()
        }
        newRow()
        option("[white]退出菜单") { }
    }
}

class AcceleratorMenu(private val player: Player): MenuBuilder<Unit>() {
    var tab: Int = 0
    var page: Int = 1

    fun sendTo() {
        launch(Dispatchers.game) {
            sendTo(player, 60_000)
        }
    }

    private suspend fun mainMenu() {
        title = "[cyan]奇怪的东西！"
        msg = ""

        option("[green]输入一串文字。。。") {
            val text = textInput.textInput(player, "aaa", "bbb", "", 60, false)
            if (text != null && text.length <= 60 && !player.dead()) {
                player.sendMessage("[yellow]你生成了一只 ${text} [yellow]!")
                Call.label(text, 3f, player.x, player.y)
                val unitScore = Random(text.hashCode()).nextInt(1, 100)
                val unitStage = when {
                    unitScore >= 90 -> 3
                    unitScore >= 70 -> 2
                    unitScore >= 55 -> 1
                    else -> 0
                }
                val unit = when(unitStage) {
                    3 -> UnitTypes.reign
                    2 -> UnitTypes.scepter
                    1 -> UnitTypes.fortress
                    else -> UnitTypes.dagger
                }
                val p = player
                unit.create(p.team()).apply {
                    spawnedByCore = true
                    set(p)
                    snapInterpolation()
                    p.unit(this)
                    add()
                    val buffScoreA = 100 - unitScore + 5f * Random(text.hashCode()).nextFloat()
                    var buffScore = buffScoreA
                    if (buffScore >= 40) {
                        statuses.add(StatusEntry().set(StatusEffects.boss, Float.POSITIVE_INFINITY))
                    }
                    if (buffScore >= 65) {
                        statuses.add(StatusEntry().set(StatusEffects.shielded, Float.POSITIVE_INFINITY))
                    }
                    if (buffScore >= 85) {
                        statuses.add(StatusEntry().set(StatusEffects.fast, Float.POSITIVE_INFINITY))
                        statuses.add(StatusEntry().set(StatusEffects.shielded, Float.POSITIVE_INFINITY))
                    }

                    if (buffScore <= 15) {
                        apply(StatusEffects.freezing, Float.POSITIVE_INFINITY)
                    }
                    if (buffScore <= 20) {
                        apply(StatusEffects.electrified, Float.POSITIVE_INFINITY)
                    }
                    var i = 0
                    while (true) {
                        buffScore -= 15
                        i++
                        if (buffScore <= 0) {
                            break
                        }
                        if (Random(text.hashCode() + i).nextFloat() >= 0.8f) {
                            statuses.add(StatusEntry().set(StatusEffects.overclock, Float.POSITIVE_INFINITY))
                        } else {
                            statuses.add(StatusEntry().set(StatusEffects.overdrive, Float.POSITIVE_INFINITY))
                        }
                    }
                    val score = (pow((buffScoreA + buffScore * -2 - 10f) / 10f, 2f) + pow(unitStage + 1f, 4f)).toInt()
                    player.sendMessage("[yellow]单位得分：${unitScore}\nbuff得分：${buffScoreA.toInt()}\n综合：${score}")
                }
            } else {
                player.sendMessage("????")
            }
        }
    }

    override suspend fun build() {
        when(tab) {
            0 -> mainMenu()
        }
        newRow()
        option("[white]退出菜单") { }
    }
}

class AccountMenu(private val player: Player): MenuBuilder<Unit>() {

    var tab: Int = 0

    fun sendTo() {
        launch(Dispatchers.game) {
            sendTo(player, 60_000)
        }
    }

    fun bind(account: Long = Core.settings.getLong("latestAccount", 0) + 1, password: String) {
        val data = PlayerData[player.uuid()]
        launch(Dispatchers.IO) {
            transaction {
                data.bind(player, PlayerProfile.findOrCreate(account, password).apply {
                    this.onJoin(player)
                })
            }
            userService.finishAchievement(data.profile!!, "[green][绑定账号]", 100, false)
            userService.updateExp(data.profile!!, 0)
            Call.connect(player.con, localhost, 8840)
            if (Core.settings.getLong("latestAccount", 0) + 1 == account) {
                Core.settings.put("latestAccount", account)
            }
        }
    }

    suspend fun accountMenu() {
        val profile = PlayerData.findById(player.uuid())?.profile
        title = "登记新星"
        if (profile == null) {
            msg = "[red]未绑定账号！"
            option("[cyan]创建新账号") {
                val text = textInput.textInput(player, "输入密码", "密码", "", 20, false)
                if (text != null && text.length <= 20)
                    bind(password = text)
            }
            option("[cyan]绑定旧账号") {
                val text = textInput.textInput(player, "输入账号", "账号", "", 20, true)
                if (text != null && text.length <= 20){
                    val account = PlayerProfile.findByAccount(text.toLongOrNull() ?: 1145141919810)
                    if (account != null)
                        launch(Dispatchers.IO) {
                            val password = textInput.textInput(player, "输入密码", "密码", "", 20, false)
                            if (password != null && password.length <= 20)
                                launch(Dispatchers.IO) {
                                    transaction {
                                        if (account.password == password) {
                                            bind(account.account, password)
                                        } else {
                                            player.sendMessage("[red]密码不正确")
                                        }
                                    }
                                }
                        }
                    else
                        player.sendMessage("[red]未发现账号")
                }
            }
        } else {
            msg = "[green]已绑定：${profile.account}"
            option("[blue]更改密码") {
                val text = textInput.textInput(player, "输入旧密码", "密码", "", 20, false)
                if (text != null && text.length <= 20){
                    if (profile.password == text) {
                        val new = textInput.textInput(player, "输入新密码", "密码", "", 20, false)
                        if (new  != null && new .length <= 20) {
                            transaction {
                                profile.password = new
                            }
                            player.sendMessage("[green]更改完成")
                        }
                    }
                    else
                        player.sendMessage("[red]密码错误")
                }
            }
            option("[red]取消绑定") {
                val text = textInput.textInput(player, "输入取消绑定", "", "", 20, false)
                if (text != null && text.length <= 20){
                    if ("取消绑定" == text) {
                        val data = PlayerData[player.uuid()]
                        launch(Dispatchers.IO) {
                            transaction {
                                data.unbind()
                            }
                            Call.connect(player.con, localhost, 8840)
                        }
                    }
                    else
                        player.sendMessage("[red]错误")
                }
            }
        }
    }


    override suspend fun build() {
        when(tab) {
            0 -> accountMenu()
        }
        newRow()
        option("[white]退出菜单") { }
    }
}

class MapMenu(private val player: Player): MenuBuilder<Unit>() {

    var tab: Int = 0
    lateinit var info: MapInfo
    lateinit var mapPrefix: String
    var canReturn: Boolean = false
    var ruleMode: Boolean = false
    var ruleLevel: Int = 0

    fun sendTo() {
        launch(Dispatchers.game) {
            sendTo(player, 60_000)
        }
    }

    private suspend fun mapInfoMenu() {
        title = "[cyan]星图-已锁定目标"
        msg = buildString {
            appendLine("${info.map.name()}[white](${info.id})")
            appendLine("[crimson]By [white]${info.map.author()}")
            val name = if (info.map.tag("modeName") != "Unknown") info.map.tag("modeName") else info.mode.name
            appendLine("[cyan]地图模式 [white]$name")
            appendLine("[yellow]----------")
            append("[white]${info.map.description()}")
        }
        option( "${ if(voteService.voting.get()) "[red]" else "[green]"} 发起换图${if (ruleMode) "\n[red]畸变等级 ${ruleLevel}" else ""}") {
            voteService.canVote = { true }
            voteService.start(player, "换图([green]{nextMap.id}[]: [green]{nextMap.map.name}[yellow]|[green]{nextMap.mode}[])".with("nextMap" to info), supportSingle = true) {
                if (info.map.file.exists() && !SaveIO.isSaveValid(info.map.file))
                    return@start broadcast("[red]换图失败,地图[yellow]{nextMap.name}[green](id: {nextMap.id})[red]已损坏".with("nextMap" to info))
                if (ruleMode) {
                    kVars.ruleMap(info.id, ruleLevel)
                }
                MapManager.loadMap(info)
                Core.app.post { // 推后,确保地图成功加载
                    broadcast("[green]换图成功,当前地图[yellow]{map.name}[green](id: {map.id})".with())
                }
            }
        }
        if (canReturn) {
            newRow()
            option("返回地图选择") {
                tab = 2
                ruleMode = false
                refresh()
            }
        }
        newRow()
        option("返回星图") {
            tab = 0
            ruleMode = false
            refresh()
        }
    }

    private suspend fun webMapChoose() {
        val text = textInput.textInput(player, "[cyan]定位目标(网络地图)", "WZ资源站地图号", "", 5, true)
        if (text != null)
            launch {
                val id = text.toInt()
                val mapInfo = MapRegistry.findById(id)
                if (mapInfo != null && id > 1000) {
                    tab = 1
                    info = mapInfo
                    sendTo()
                } else {
                    player.sendMessage("[red]请检查地图号是否有效")
                }
            }
    }

    private suspend fun mapMenu() {
        title = "[cyan]星图-未选择星系"
        msg = ""
        option("[cyan]定位目标(网络地图)") {
            canReturn = false
            webMapChoose()
        }
        newRow()
        option("[blue]Season I - 异世天灾") {
            tab = 2
            mapPrefix = "A"
            refresh()
        }
        newRow()
        option("[gray]星云\n[lightgray]玩法影响等级：稀薄尘埃") {
            tab = 2
            mapPrefix = "B"
            refresh()
        }
        newRow()
        option("[white]星团\n[lightgray]玩法影响等级：星火燎原") {
            tab = 2
            mapPrefix = "C"
            refresh()
        }
        newRow()
        option("[cyan]星系\n[lightgray]玩法影响等级：灿若繁星") {
            tab = 2
            mapPrefix = "D"
            refresh()
        }
    }

    private suspend fun mapChooseMenu() {
        title = "[cyan]星图-$mapPrefix"
        msg = listOf(
            "战役系列地图需要打通上一关解锁\n进度全服共享"
                ).random()

        if (mapPrefix == "A") {
            option("[red]启用畸变模式\n(网络换图)") {
                //player.sendMessage("coming soooon")

                val level = textInput.textInput(player, "输入畸变等级", "影响畸变速率, 数值1-10", "1", 2, true)?.toIntOrNull()
                if (level == null) {
                    player.sendMessage("[red]数值不合法")
                } else {
                    ruleLevel = level.coerceAtLeast(1).coerceAtMost(10)
                    ruleMode = true
                    canReturn = true
                    webMapChoose()
                }


            }
            newRow()
        }


        MapRegistry.getMaps("Name|$mapPrefix").sortedBy { it.map.file.name().split(".")[0].replace(mapPrefix, "").toIntOrNull() ?: 9999 }.forEach {
            val id = it.map.file.name().split(".")[0].replace(mapPrefix, "").toIntOrNull() ?: 1
            if (id != 1 && !Core.settings.getBool("unlock-$mapPrefix$id", false)) {
                if (!it.map.file.name().split(".")[1].contains("hidden")){
                    option("[lightgray]--未解锁--") {
                        refresh()
                    }
                    newRow()
                }
            } else {
                option(it.map.name()) {
                    tab = 1
                    info = it
                    canReturn = true
                    refresh()
                }
                newRow()
            }

        }
        option("[white]返回星图") { tab = 0; refresh() }
    }

    override suspend fun build() {
        when(tab) {
            0 -> mapMenu()
            1 -> mapInfoMenu()
            2 -> mapChooseMenu()
        }
        newRow()
        option("[white]退出菜单") { }
    }
}

val drawing by autoInit { mutableMapOf<PlayerProfile, Boolean>() }

class InscriptionMenu(private val player: Player): MenuBuilder<Unit>() {
    var tab: Int = 0
    var frame: Int = 0
    var targetFrame: Int = 0
    var canReturn: Boolean = false
    val profile = PlayerData[player.uuid()].profile
    private val effectQueue: MutableList<Effect.BaseEffect> = mutableListOf()
    private val prefixQueue: MutableList<Prefix.BasePrefix> = mutableListOf()

    private lateinit var typeName: String
    private lateinit var inscriptionInIt: List<InscriptionEntity>
    private lateinit var inscriptions: SizedIterable<InscriptionEntity>
    private lateinit var showInscriptionEntity: InscriptionEntity
    private lateinit var spinscriptions: SizedIterable<SpInscriptionEntity>
    private lateinit var showSpInscriptionEntity: SpInscriptionEntity

    fun sendTo() {
        launch(Dispatchers.IO) {
            sendTo(player, 60_000)
        }
    }

    private suspend fun inscriptionMenu() {
        title = "[cyan]铭刻"
        msg = "铭刻星铭..或者管理你的星铭"
        option("铭刻星铭") {
            tab = 1; refresh()
        }
        newRow()
        option("管理星铭") {
            if (profile == null) {
                refresh()
            } else {
                tab = 4; refresh()
            }
        }
        option("管理特殊星铭") {
            if (profile == null) {
                refresh()
            } else {
                transaction {
                    spinscriptions = SpInscriptionEntity.wrapRows(SpInscriptionEntity.player(profile.id))
                    if (spinscriptions.empty()) {
                        player.sendMessage("[red]你还没有特殊星铭!"); refresh()
                    } else {
                        tab = 7; refresh()
                    }
                }
            }
        }
    }

    fun level(exp: Int) = floor(sqrt(max(exp, 0).toDouble()) / 10).toInt()

    private suspend fun drawMenu() {
        title = "[cyan]铭刻-新星铭"
        msg = "铭刻一枚全新的星铭..由效果与前缀组成"
        lazyOption {
            if (profile == null) {
                refreshOption("[red]还没有注册..")
            }
            if (profile.cardDraws > level(profile.totalExp)) {
                refreshOption("[red]铭刻次数已达等级上限..")
            }
            if (drawing[profile] == true) {
                refreshOption("[red]正在铭刻")
            }
            option("[green]开刻！")
            repeat(5) {
                effectQueue.add(effect.randomEffect())
            }
            repeat(5) {
                prefixQueue.add(prefix.randomPrefix())
            }
            frame = 0
            targetFrame = Random.nextInt(10, 20)
            drawing[profile] = true
            tab = 2; followup = true; refresh()
        }
        newRow()
        option("返回") {
            tab = 0; refresh()
        }
    }
    private suspend fun drawingMenu() {
        title = "[cyan]铭刻-新星铭"
        msg = "铭刻一枚全新的星铭..由效果与前缀组成"

        repeat(5) {
            option("${if (it == 2) "[green]" else "[yellow]"}${prefixQueue[it].prefix}") { }
        }
        newRow()
        repeat(5) {
            if (it == 2) {
                option("[green]|") { }
            } else option("[yellow]-") { }
        }
        newRow()
        repeat(5) {
            option("${if (it == 2) "[green]" else "[yellow]"}${effectQueue[it].name}") { }
        }
    }
    private suspend fun drewMenu() {
        title = "[cyan]铭刻-新星铭"
        msg = "铭刻一枚全新的星铭..由效果与前缀组成"
        showInscriptionEntity =  inscription.createNewInscription(profile!!, effectQueue[1], prefixQueue[1])
        Call.label("${prefixQueue[1].prefix}[yellow] 的 [white]${effectQueue[1].name}\n[white]${player.name}", 8f, player.x, player.y)
        transaction {
            profile.cardDraws++
        }
        player.achievement("[green][新星铭！]", 50)
        option("${prefixQueue[1].prefix}[yellow] 的 [white]${effectQueue[1].name}") {
            canReturn = false
            tab = 6; refresh()
        }
        newRow()
        option("返回") {
            tab = 0; refresh()
        }
    }

    fun managerMsg(): String {
        return buildString {
            appendLine("[cyan]选择三块星辰之铭作为铭能具象的途径")
            appendLine("[cyan]目前已经启用的星辰铭：")
            inscriptions.filter { it.enable }.forEach {
                appendLine(inscription.name(it))
            }
            append("[cyan]更高的星辉共鸣等级能使铭能利用率提升")
        }
    }

    private suspend fun managerMenu() {
        transaction {
            title = "[cyan]铭刻-星铭管理"
            inscriptions = InscriptionEntity.wrapRows(InscriptionEntity.player(profile!!.id))
            val inscriptionsHidden = InscriptionEntity.wrapRows(InscriptionEntity.playerHidden(profile.id))
            msg = managerMsg()
            val types = buildList {
                inscriptions.forEach {
                    val type = inscription.effect(it)?.type ?: ""
                    if (!contains(type)) {
                        add(type)
                    }
                }
            }

            types.forEach { type ->
                inscriptionInIt = inscriptions.filter { (inscription.effect(it)?.type ?: "") == type }
                if (inscriptionInIt.isNotEmpty()) {
                    option(type) {
                        transaction {
                            inscriptionInIt = inscriptions.filter { (inscription.effect(it)?.type ?: "") == type }
                            tab = 5; refresh()
                        }
                    }
                    newRow()
                }
            }
            if (!inscriptionsHidden.empty()) {
                option("[lightgray]${inscriptionsHidden.count()}个隐藏的星铭") {
                    transaction {
                        inscriptionInIt = inscriptionsHidden.toList()
                        tab = 5; refresh()
                    }
                }
                newRow()
            }
            option("返回") {
                tab = 0; refresh()
            }
        }
    }

    fun InscriptionEntity.switch() {
        val it = this
        transaction {
            it.refresh()
            if (it.enable) {
                it.refresh()
                it.enable = false
            } else {
                if (InscriptionEntity.wrapRows(InscriptionEntity.playerEnable(profile!!.id)).toList().size < 3){
                    it.refresh()
                    it.enable = true
                }
            }
        }
    }
    fun InscriptionEntity.switchHidden() {
        val it = this
        transaction {
            it.refresh()
            it.hidden = !it.hidden
        }
    }
    private suspend fun typeMenu() {
        transaction {
            title = "[cyan]铭刻-星铭管理"
            msg = managerMsg()

            inscriptionInIt.forEach {
                option(inscription.name(it)) {
                    canReturn = true
                    showInscriptionEntity = it
                    tab = 6; refresh()
                }

                option(if (it.enable) "[green]启用" else "[red]未启用") {
                    it.switch()
                    refresh()
                }

                newRow()
            }

            option("返回") {
                tab = 4; refresh()
            }
        }
    }

    private suspend fun infoMenu() {
        transaction {
            title = inscription.name(showInscriptionEntity)
            msg = buildString {
                append(inscription.name(showInscriptionEntity))
                appendLine("[white]")
                append(inscription.effect(showInscriptionEntity)!!.name)
                append(" ("+"%.${3}f".format(effect.getEffectChance(inscription.effect(showInscriptionEntity)!!))+"%)")
                appendLine("[white]")
                append(inscription.effect(showInscriptionEntity)?.desc)
                appendLine("[white]")
                append(inscription.prefix(showInscriptionEntity)?.prefix)
                append(" ("+"%.${3}f".format(prefix.getPrefixChance(inscription.prefix(showInscriptionEntity)!!))+"%)")
                appendLine("[white]")
                append(inscription.prefix(showInscriptionEntity)?.desc)
            }

            option(if (showInscriptionEntity.enable) "[green]启用" else "[red]未启用") {
                showInscriptionEntity.switch()
                refresh()
            }
            option(if (showInscriptionEntity.hidden) "[lightgray]已经隐藏${if (showInscriptionEntity.enable) ",但仍在启用" else ""}" else "[green]未隐藏") {
                showInscriptionEntity.switchHidden()
                refresh()
            }
            newRow()
            option("返回") {
                if (canReturn) {
                    tab = 5
                } else {
                    tab = 1
                }
                refresh()
            }
        }
    }

    private suspend fun spmanagerMenu() {
        title = "[cyan]铭刻-特殊星铭"
        msg = ""
        transaction {
            spinscriptions = SpInscriptionEntity.wrapRows(SpInscriptionEntity.player(profile!!.id))
            spinscriptions.forEach {
                val sp = spinscription.special(it.iId)
                option(if (it.reversed) "[yellow]${sp.nameR}" else "[cyan]${sp.name}") {
                    showSpInscriptionEntity = it
                    tab = 8; refresh()
                }

                option(if (it.enable) "[green]开启" else "[red]关闭") {
                    transaction {
                        it.enable = !it.enable;
                    }
                    refresh()
                }
                option("[blue]翻转") {
                    transaction {
                        it.reversed = !it.reversed
                    }
                    refresh()
                }
                newRow()
            }
            option("返回") {
                tab = 0; refresh()
            }
        }
    }
    private suspend fun spinfoMenu() {
        transaction {
            val spe = showSpInscriptionEntity
            val sp = spinscription.special(spe.iId)
            val r = spe.reversed
            title = if (r) sp.nameR else sp.name
            msg = buildString {
                appendLine(if (r) sp.nameR else sp.name)
                append(if (r) sp.descR else sp.desc)
            }
            option("返回") {
                tab = 7; refresh()
            }
        }
    }
    override suspend fun build() {
        if (followup && frame >= targetFrame) {
            Call.hideFollowUpMenu(_menuId)
            tab = 3
            followup = false
            drawing[profile!!] = false
        }
        when(tab) {
            0 -> inscriptionMenu()
            1 -> drawMenu()
            2 -> drawingMenu()
            3 -> drewMenu()
            4 -> managerMenu()
            5 -> typeMenu()
            6 -> infoMenu()
            7 -> spmanagerMenu()
            8 -> spinfoMenu()
        }
        if (!followup) {
            newRow()
            option("[white]退出菜单") { }
        } else {
            launch {
                delay(500)
                prefixQueue.removeFirst()
                prefixQueue.add(prefix.randomPrefix())
                effectQueue.removeFirst()
                effectQueue.add(effect.randomEffect())
                frame++
                sendTo(player, 60_000)
            }
        }
    }
}

fun AchievementEntity.getDesc(): String {
    return name2desc.getOrDefault(name, "[lightgray]无描述")
}

val name2desc = achieveStatistics.name2desc

class AchievementMenu(private val player: Player): MenuBuilder<Unit>() {
    var tab: Int = 0
    var page: Int = 1
    lateinit var achievements: List<AchievementEntity>
    lateinit var showAchievement: AchievementEntity

    val profile = PlayerData[player.uuid()].profile!!

    fun sendTo() {
        launch(Dispatchers.game) {
            sendTo(player, 60_000)
        }
    }

    private suspend fun achievementMenu() {
        title = "[cyan]成就"
        msg = ""

        option("已取得的成就") {
            tab = 1; refresh()
        }
        newRow()
        option("成就奖励") {
            tab = 2; refresh()
        }
    }

    private suspend fun allAchievementsMenu() {
        title = "[cyan]成就"
        transaction {
            achievements = AchievementEntity.wrapRows(AchievementEntity.player(profile.id)).toList().sortedBy { it.time.epochSecond }.reversed()
        }
        msg = "[yellow]你一共完成了${achievements.size}个成就"
        if (achievements.isEmpty()) {
            lazyOption {
                refreshOption("[red]你还没有完成任何成就！")
            }
        } else {
            achievements.subList((page - 1) * 6, min(page * 6, achievements.size)).forEach {
                transaction {
                    option("${it.name}[white]\n${it.time.toString().replace("T", "|").split(".")[0]}") {
                        showAchievement = it
                        tab = 3; refresh()
                    }
                }
                newRow()
            }
        }
        val max = ceil(achievements.size / 6f).toInt()
        val min = 1
        option("<<") {
            page = min; refresh()
        }
        option("<") {
            page = (page - 1).coerceAtLeast(min).coerceAtMost(max); refresh()
        }
        option("${page}/${max}") {
            val text = textInput.textInput(player, title, "输入查询页数", page.toString(), isNumeric = true)
            if (text != null) {
                page = (text.toIntOrNull() ?: page).coerceAtLeast(min).coerceAtMost(max)
            }
            refresh()
        }
        option(">") {
            page = (page + 1).coerceAtLeast(min).coerceAtMost(max); refresh()
        }
        option(">>") {
            page = max; refresh()
        }
        newRow()
        option("返回") {
            tab = 0; refresh()
        }
    }

    private suspend fun infoMenu() {
        transaction {
            title = "[cyan]成就-${showAchievement.name}"
            msg = buildString {
                appendLine(showAchievement.getDesc())
                appendLine()
                appendLine("[green]获取时间：${showAchievement.time.toString().replace("T", "|").split(".")[0]}")
                appendLine("[yellow]奖励经验：${showAchievement.exp}")
            }
            option("返回") {
                tab = 1; refresh()
            }
        }
    }

    private suspend fun rewardMenu() {
        title = "[cyan]成就"
        msg = ""

        option("[red]我还没想好！") {
            tab = 0; refresh()
        }
    }


    override suspend fun build() {
        when(tab) {
            0 -> achievementMenu()
            1 -> allAchievementsMenu()
            2 -> rewardMenu()
            3 -> infoMenu()
        }
        newRow()
        option("[white]退出菜单") { }
    }
}

listen<EventType.UnitControlEvent> {
    if (it.unit.type == UnitTypes.corvus) {
        it.player.achievement("[green][观星]", 100, false)
    }
}

val buildBlastDrills by autoInit { mutableMapOf<PlayerProfile, Int>() }

listen<EventType.BlockBuildEndEvent> {
    val player = it.unit.player ?: return@listen
    val profile = PlayerData[player.uuid()].profile ?: return@listen
    if (!it.breaking && it.tile.block() == Blocks.blastDrill) {
        buildBlastDrills[profile] = buildBlastDrills.getOrPut(profile) { 0 } + 1
        if (buildBlastDrills[profile]!! >= 50) {
            player.achievement("[green][StarBlast!]", 100)
        }
    }
}

