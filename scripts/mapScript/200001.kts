@file:Depends("wayzer/user/achievement", "成就")
@file:Depends("coreMindustry/menu", "调用菜单")
@file:Depends("coreMindustry/utilMapRule", "修改核心单位,单位属性")
@file:Depends("wayzer/map/betterTeam", "更改玩家队伍")
@file:Import("@coreMindustry/util/spawnAround.kt", sourceFile = true)

package mapScript

import arc.Events
import coreLibrary.lib.util.loop
import coreLibrary.lib.with
import coreMindustry.lib.*
import mindustry.ai.types.GroundAI
import mindustry.ai.types.MissileAI
import mindustry.Vars
import mindustry.content.*
import mindustry.content.Blocks.*
import mindustry.entities.Units
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.*
import mindustry.type.UnitType
import mindustry.world.Tile
import wayzer.MapManager
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import coreMindustry.MenuBuilder
import coreMindustry.util.spawnAround
import mindustry.Vars.state
import mindustry.Vars.world
import mindustry.world.blocks.storage.CoreBlock
import wayzer.lib.dao.PlayerData
import wayzer.map.BetterTeam

val ts = contextScript<_200001>()
val betterTeam = contextScript<BetterTeam>()
val achievement = contextScript<wayzer.user.Achievement>()

fun Player.achievement(name: String, exp: Int, broadcast: Boolean = false) {
    val profile = PlayerData[uuid()].profile
    if (profile != null)
        achievement.finishAchievement(profile, name, exp, broadcast)
}


data class PlayerData(
        var coins: Int = 10000, //当前现金 初始10000
        var multiple: Float = 0f, //当前选择倍率
        var team: Team = Team.get(0), //当前选择队伍
        var streak: Int = 0, //连胜次数
        var lastCoin: Int = 10000 //上把现金
) {
    lateinit var player: Player

    fun settle(winTeam: Team){
        val multipleCoins = (coins * multiple).toInt()
        val extraCoins = (multipleCoins * (streak * 0.15f)).toInt()

        if(team == Team.get(0) || multiple == 0f){ //未选择队伍
            lastCoin = coins
            streak = 0
            player.sendMessage("[red]殷紫[grey]:[white]即使是这种赌局你也敢犹豫吗，哈基人，你这家伙...")
        }else if(winTeam == Team.get(255)){ //平局
            lastCoin = coins
            player.sendMessage("[red]殷紫[grey]:[white]呃啊...白白浪费了这么好的局...钱推给你吧...不甘心...")
        }else if(team != winTeam){ //猜错队伍
            lastCoin = coins
            coins = max(2000, coins - multipleCoins)
            streak = 0
            player.sendMessage("[red]殷紫[grey]:[white]Boom！你输了！你的钱就跟你的倒霉运气一样消散了，失去了[gold]${multipleCoins}[]块钱！哈哈哈！")
        }else{ //猜对队伍
            lastCoin = coins
            coins += (multipleCoins + extraCoins)
            coins = min(99999999, coins)  //最大值
            if (coins == 99999999) {
                player.achievement("[purple][999!]", 999, true)
            }
            streak++

            player.sendMessage("[red]殷紫[grey]:[white]啊嘞？你就这么猫对了？拿去吧，你总共得到了[gold]${(multipleCoins + extraCoins)}[]块钱。希望下回合你能输的更惨呀~")
        }
    }
    fun multipleText(): String {
        return when(multiple) {
            0.25f -> "[green]25%"
            0.5f -> "[yellow]50%"
            0.75f -> "[red]75%"
            1f -> "[scarlet]ALL IN"
            else -> "[white]未选择"
        }
    }

}

val playerData by autoInit { mutableMapOf<String, PlayerData>() }
val Player.data
    get() = playerData.getOrPut(uuid()) { PlayerData() }
            .also { it.player = this }

val allUnits = listOf(UnitTypes.dagger,UnitTypes.mace,UnitTypes.fortress,UnitTypes.scepter,UnitTypes.reign,UnitTypes.nova,UnitTypes.pulsar,UnitTypes.quasar,UnitTypes.vela,UnitTypes.corvus,UnitTypes.atrax,UnitTypes.spiroct,UnitTypes.arkyid,UnitTypes.toxopid,UnitTypes.flare,UnitTypes.zenith,UnitTypes.antumbra,UnitTypes.eclipse,UnitTypes.risso,UnitTypes.minke,UnitTypes.bryde,UnitTypes.sei,UnitTypes.omura,UnitTypes.retusa,UnitTypes.oxynoe,UnitTypes.cyerce,UnitTypes.aegires,UnitTypes.navanax,UnitTypes.stell,UnitTypes.locus,UnitTypes.precept,UnitTypes.vanquish,UnitTypes.conquer,UnitTypes.merui,UnitTypes.cleroi,UnitTypes.tecta,UnitTypes.collaris,UnitTypes.elude,UnitTypes.avert,UnitTypes.obviate,UnitTypes.disrupt)

val lowTierUnits = listOf(UnitTypes.dagger,UnitTypes.mace,UnitTypes.fortress,UnitTypes.nova,UnitTypes.pulsar,UnitTypes.quasar,UnitTypes.atrax,UnitTypes.spiroct,UnitTypes.flare,UnitTypes.zenith,UnitTypes.risso,UnitTypes.minke,UnitTypes.bryde,UnitTypes.retusa,UnitTypes.oxynoe,UnitTypes.cyerce,UnitTypes.stell,UnitTypes.locus,UnitTypes.precept,UnitTypes.merui,UnitTypes.cleroi,UnitTypes.anthicus,UnitTypes.elude,UnitTypes.avert,UnitTypes.obviate)

val mediumTierUnits = listOf(UnitTypes.fortress,UnitTypes.scepter,UnitTypes.quasar,UnitTypes.vela,UnitTypes.spiroct,UnitTypes.arkyid,UnitTypes.zenith,UnitTypes.antumbra,UnitTypes.bryde,UnitTypes.sei,UnitTypes.cyerce,UnitTypes.aegires,UnitTypes.precept,UnitTypes.vanquish,UnitTypes.anthicus,UnitTypes.tecta,UnitTypes.obviate)

val highTierUnits = listOf(UnitTypes.scepter,UnitTypes.reign,UnitTypes.vela,UnitTypes.corvus,UnitTypes.arkyid,UnitTypes.toxopid,UnitTypes.antumbra,UnitTypes.eclipse,UnitTypes.sei,UnitTypes.omura,UnitTypes.aegires,UnitTypes.navanax,UnitTypes.vanquish,UnitTypes.conquer,UnitTypes.tecta,UnitTypes.collaris,UnitTypes.disrupt)


val listOfUnit = listOf(lowTierUnits, mediumTierUnits, highTierUnits)

var turnReady = false //正在回合之间的投注时间

var guideList = listOf(
        "[yellow]在单位开始战斗前再双击屏幕才能打开投注界面",
        "[yellow]所有单位都是0护甲，请谨慎投注",
        "[yellow]你可以在某回合不选择来保留自己的金钱，但这样也会清空连胜",
        "[yellow]每一次连胜都能为下次投注成功带来15%加成，并且无限叠加！",
        "[yellow]如果allin失败，殷紫仍然好心的会给你留2000安慰金让你东山再起",
        "[yellow]部分单位的ai与平常有些不同，请谨慎投注"
)

var easterList = listOf(
        "[purple]曾经有那么一只爬虫，重写了所有单位们的行动逻辑，让导弹再也不被仇恨、对地蛐蛐也不会瞄准试图天上，但后来，爬虫在竞技场神秘的消失了",
        "[purple]哦对的对的，哦不对不对，对吗？哦不对，哦对的对的",
        "[purple]是泰拉人来投资了，我们有救了！",
        "[purple]如果十局全部allin并且全部成功，那么金额就会达到99999999的上限",
        "[purple]虽然竞技场是由我一手搭建并设计的，但是在此之前我的好朋友星和辰也帮我解决了很多麻烦事，真是非常感谢呀~"
)

var leaveList = listOf(
        "[red]殷紫[grey]:{player}[white]已经绷不住了并消失的无影无踪了！真是一次精神上的伤害，哈哈哈！！！",
        "[red]殷紫[grey]:{player}[white]从高处摔落了下来，落地过猛。",
        "[red]殷紫[grey]:{player}[white]被神鹰哥标记了，飞起来！！！",
        "[red]殷紫[grey]:{player}[white]在资金管理上遇到了点困难。",
        "[red]殷紫[grey]:[white]笨蛋{player}[white]杂鱼~ 杂鱼~",
        "[red]殷紫[grey]:{player}[white]孤注一掷，但仍然一败涂地。",
        "[red]殷紫[grey]:[white]我亲眼看见了{player}[white]走上了高台",
        "[red]殷紫[grey]:{player}[white]试图逃避，但是什么用都没有！！",
)


//嘲笑策划 理解策划 成为策划
//用脚填数值！！！
val pointMap: Map<UnitType, Int> =
        mapOf(
                UnitTypes.crawler to 53,
                UnitTypes.atrax to 243,
                UnitTypes.spiroct to 325,
                UnitTypes.arkyid to 4760,
                UnitTypes.toxopid to 7106,

                UnitTypes.dagger to 90,
                UnitTypes.mace to 102,
                UnitTypes.fortress to 590,
                UnitTypes.scepter to 3734,
                UnitTypes.reign to 8819,

                UnitTypes.nova to 80,
                UnitTypes.pulsar to 106,
                UnitTypes.quasar to 604,
                UnitTypes.vela to 3217,
                UnitTypes.corvus to 4222,

                UnitTypes.flare to 52,
                //horizon to 110,
                UnitTypes.zenith to 403,
                UnitTypes.antumbra to 3445,
                UnitTypes.eclipse to 5803,

                UnitTypes.risso to 159,
                UnitTypes.minke to 393,
                UnitTypes.bryde to 1071,
                UnitTypes.sei to 5319,
                UnitTypes.omura to 8018,

                UnitTypes.retusa to 181,
                UnitTypes.oxynoe to 443,
                UnitTypes.cyerce to 940,
                UnitTypes.aegires to 5968,
                UnitTypes.navanax to 8530,

                UnitTypes.stell to 167,
                UnitTypes.locus to 704,
                UnitTypes.precept to 2135,
                UnitTypes.vanquish to 3233,
                UnitTypes.conquer to 7852,

                UnitTypes.merui to 172,
                UnitTypes.cleroi to 610,
                UnitTypes.anthicus to 1855,
                UnitTypes.tecta to 3687,
                UnitTypes.collaris to 7651,

                UnitTypes.elude to 125,
                UnitTypes.avert to 512,
                UnitTypes.obviate to 1503,
                UnitTypes.quell to 2997,
                UnitTypes.disrupt to 6216,
        )

var rankMap: Map<Player, Int> = mapOf<Player, Int>()
var sortedRankMap: Map<Player, Int> = mapOf<Player, Int>()

fun label(t: String, Lx: Float, Ly: Float): WorldLabel {
    return label(Lx, Ly).apply {
        text = t
    }
}

fun label(Lx: Float, Ly: Float): WorldLabel {
    return WorldLabel.create().apply {
        set(Lx, Ly)
        snapInterpolation()
        fontSize = 2f
        flags = 2
        add()
    }
}


suspend fun chooseType(lst: List<UnitType>, count: Int = 3): Map<UnitType, Int> {
    var randomType: Map<UnitType, Int> = mapOf<UnitType, Int>()
    lst.shuffled().take(count).forEach{
        randomType += it!! to pointMap[it]!!
    }
    val sortedType = randomType!!.entries.sortedByDescending{it.value}.associate { it.key to it.value }
    return sortedType
}


suspend fun calculate(point: Int, team: Team, unitList: Map<UnitType, Int>){
    var lastPoint = point
    var counts = unitList.size
    var avgPoint = (point / counts).toInt()
    var teamX = 0f

    unitList.forEach{
        var count = max(1, (avgPoint / it.value).toInt())
        spawn(it.key, team, count)
    }

    if(team == Team.crux){teamX = 52f * 8f}else{teamX = 86f * 8f}

    val label = label(teamX, 80f * 8f)
    label.fontSize *= 2
    labelB.add(label)
    ts.launch(Dispatchers.game) {
        while (!label.isNull) {
            label.text = buildString {
                val typeMap = mutableMapOf<UnitType, Int>()
                Groups.unit.forEach {
                    if (it.team == team && it.type.useUnitCap) {
                        typeMap[it.type] = (typeMap[it.type] ?: 0) + 1
                    }
                }
                typeMap.forEach {
                    appendLine("[white]${it.key.emoji()} x [#${team.color}] ${it.value}")
                }
            }
            yield()
        }
    }
}


suspend fun spawn(type: UnitType, team: Team, count: Int, debuff: Boolean = true) {
    var r = 0f
    var x = 0f

    repeat(count){  //答辩

        if(team == Team.crux) {
            r = 0f
            x = ((40..50).random()) * 8f
        }else {
            r = 180f
            x = ((89..99).random()) * 8f
        }

        type.create(team).apply {
            set(x, ((22..72).random()) * 8f)
            rotation = r
            add()
            controller(AreaAI())
            if (debuff) {
                apply(StatusEffects.unmoving, Float.MAX_VALUE)
                apply(StatusEffects.disarmed, Float.MAX_VALUE)
            }
        }
    }

}

fun Player.playerGetTeam(): String{
    val p = this

    return when(p.data.team){
        Team.crux -> "[red]红队[]"
        Team.blue -> "[blue]蓝队[]"
        else -> "[grey]未选择[]"
    }
}

fun Player.playerGetMultiple(): String{
    val p = this

    if(p.data.multiple == 0f){
        return "[grey]未选择[]"
    }else{
        return "[yellow]${p.data.multiple}[]"
    }
}

suspend fun Player.choose() {
    val p = this

    fun getTeam(): String{
        return when(p.data.team){
            Team.crux -> "[red]红队[]"
            Team.blue -> "[blue]蓝队[]"
            else -> "[grey]未选择[]"
        }
    }

    fun getMultiple(): String{
        if(p.data.multiple == 0f){
            return "[grey]未选择[]"
        }else{
            return "[yellow]${p.data.multiple}[]"
        }
    }

    MenuBuilder<Unit>(){
        title = "投注界面"
        msg = """
[yellow]点击下方按钮以选择
[yellow]当前连胜加成：${p.data.streak * 15}%
[yellow]你目前投注的队伍：${getTeam()}
[yellow]你目前投注的倍率：${getMultiple()}
            """.trimMargin()

        option("[red]红队[]"){if(turnReady){p.data.team = Team.crux;refresh()}else{p.sendMessage("[red]已超时")}}
        option("[blue]蓝队[]"){if(turnReady){p.data.team = Team.blue;refresh()}else{p.sendMessage("[red]已超时")}}
        newRow()
        option("[yellow]⇩选择倍率⇩[]"){refresh()}
        newRow()
        option("25%"){if(turnReady){p.data.multiple = 0.25f;refresh()}else{p.sendMessage("[red]已超时")}}
        option("50%"){if(turnReady){p.data.multiple = 0.5f;refresh()}else{p.sendMessage("[red]已超时")}}
        option("75%"){if(turnReady){p.data.multiple = 0.75f;refresh()}else{p.sendMessage("[red]已超时")}}
        newRow()
        option("[red]<<<ALL IN>>>"){if(turnReady){p.data.multiple = 1f;refresh()}else{p.sendMessage("[red]已超时")}}
        newRow()
        option("[yellow]退出"){}
    }.sendTo(p, 20_000)
}

val playerLastTapTile by autoInit { mutableMapOf<String, Pair<Tile, Int>>() }

listen<EventType.TapEvent> {
    val player = it.player
    if (player.team() == Team.green && it.tile.passable()) {
        repeat(6) { i ->
            UnitTypes.crawler.create(Team.green).apply {
                set(it.tile)
                x = x - 4f + 8f * Random.nextFloat()
                y = y - 4f + 8f * Random.nextFloat()
                snapInterpolation()
                controller(AreaAI())
                add()
            }
        }

    }
    val times = if (it.tile == (playerLastTapTile[player.uuid()]?.first ?: false)) (playerLastTapTile[player.uuid()]?.second?.plus(1) ?: 1) else 1
    playerLastTapTile[player.uuid()] = it.tile to times

    if (turnReady) {
        when (times % 2) {
            0 -> {
                launch(Dispatchers.game) {player.choose()}
            }
            1 -> {}
        }
    }
}

//近战单位，试图使敌人进入全部武器射程
val meleeUnits = listOf(
        UnitTypes.scepter,
        UnitTypes.crawler,
        UnitTypes.atrax,
        UnitTypes.spiroct,
        UnitTypes.arkyid,
        UnitTypes.toxopid,
        UnitTypes.navanax,
        UnitTypes.stell,
        UnitTypes.locus,
        UnitTypes.precept,
        UnitTypes.vanquish,
        UnitTypes.conquer
)
//远程单位，试图使敌人只进入最远武器射程
val rangedUnits = listOf(
        UnitTypes.fortress,
        UnitTypes.corvus,
        UnitTypes.omura,
        UnitTypes.anthicus,
        UnitTypes.collaris,
        UnitTypes.quell,
        UnitTypes.disrupt
)
//其他单位，使用接近原版的ai索敌方式
val otherUnits = pointMap.keys.filter { it !in meleeUnits && it !in rangedUnits }

val tile = world.tile(70, 47)!!

fun UnitType.weaponRange(): Float {
    return when {
        (this in meleeUnits) -> {
            weapons.minOf { it.range() - 4f }
        }
        (this in rangedUnits) -> {
            weapons.maxOf { it.range() - 4f}
        }
        else -> range
    }.coerceAtLeast(8f)
}

class AreaAI(): GroundAI() {
    override fun updateMovement() {
        if (target == null) {
            if (winner == unit.team) {
                unit.lookAt(unit.rotation + if (Random(unit.id).nextBoolean()) 120f else -120f)
            } else {
                moveTo(tile, 16 * 8f, 20f, false, null)
            }
        } else {
            val range = unit.type.weaponRange() * when {
                (unit.type in meleeUnits) -> 0.4f
                (unit.type in rangedUnits) -> 0.9f
                else -> 0.75f
            }
            moveTo(target, range, 20f, false, null)
            unit.lookAt(target)
        }
        faceTarget()
    }

    override fun target(x: Float, y: Float, range: Float, air: Boolean, ground: Boolean): Teamc? {
        return Units.closestTarget(unit.team, x, y, 9999f, {
            it.checkTarget(air, ground) && it.controller() !is MissileAI
        },{
            ground
        })
    }
}

var turns = 0 //回合数
var winner = Team.get(0) //本局胜利方 用于结算

val labelA = mutableListOf<WorldLabel>()//A型label,准备阶段后重置
val labelB = mutableListOf<WorldLabel>()//B型label,战斗阶段后重置

onEnable {
    var point = 0 //单位生成点数
    var typeCount = 2 //单位种类数 初始为2
    winner = Team.get(0)
    var unitsPool = allUnits //选择生成的单位池
    var time = 100 //最长回合时间
    var pickedPlayers = mutableListOf<Player>()
    var cruxText = "" //红队玩家名单
    var blueText = "" //蓝队玩家名单
    var rankText = "" //财富榜名单
    turns = 0 //回合数
    turnReady = false
    rankMap = mapOf<Player, Int>()
    sortedRankMap = mapOf<Player, Int>()
    labelB.clear()
    labelA.clear()

    contextScript<coreMindustry.UtilMapRule>().apply {
        registerMapRule((Blocks.coreShard as CoreBlock)::unitType) { UnitTypes.evoke }
        registerMapRule(UnitTypes.evoke::physics) { false }
        Vars.content.units().forEach {
            registerMapRule(it::armor) { 0f }
        }
    }

    Vars.state.rules.apply {
        bannedBlocks.clear()
        blockWhitelist = true
        hideBannedBlocks = true
        canGameOver = false
        unitCap = 999
        possessionAllowed = false
    }

    Call.setRules(Vars.state.rules)

    loop(Dispatchers.game) {
        Groups.player.forEach {
            if (it.dead()) {
                UnitTypes.evoke.create(Team.sharded).apply {
                    set(tile)
                    snapInterpolation()
                    spawnedByCore = true
                    it.unit(this)
                    add()
                }
            }
        }
        yield()
    }

    launch(Dispatchers.game) {
        var startTime = 30
        var turnTime = 30
        while(startTime > 0){
            when(startTime){
                25 -> broadcast("[red]殷紫[grey]:[white]嘻~又是你们这群笨蛋呀！超——开心！".with())
                20 -> broadcast("[red]殷紫[grey]:[white]欢迎光临本大人新搭的舞台哦~叫斗蛐蛐场也行啦！".with())
                15 -> broadcast("[red]殷紫[grey]:[white]这次不用亲自打架啦~乖乖看场上的单位们自相残杀就行咯！".with())
                10 -> broadcast("[red]殷紫[grey]:[white]每次开战前押注哪边会赢！喏~这堆破铜烂铁就是你们的赌资啦".with())
                5 -> broadcast("[red]殷紫[grey]:[white]最后金币超过[gold]200000[]的家伙才能滚出这块地方哦~嘻嘻嘻！".with())
                1 -> broadcast("[red]殷紫[grey]:[white]游戏要开始啦~可别哭着求我放你出去哟！杂鱼~".with())
            }
            delay(1_000)
            startTime -= 1
        }

        Groups.player.filter{!it.dead()}.forEach {
            it.unit().kill()
        }

        repeat(99){Team.sharded.cores().forEach { it.kill()}}
        delay(1_000)

        ts.loop(Dispatchers.game) {
            turns++ //以免出现11/10的bug

            broadcast("[red]殷紫[grey]:[white]叮咚~蛐蛐们又要开掐啦！赶紧双击屏幕下注吧，手快有手慢无哦笨蛋们~".with())

            //新一回合开始生成新单位
            point = (((50..200).random() * 100 + turns * 3000) * 1.75f).toInt()

            if(turns == 3){typeCount = 3}
            if(turns == 7){typeCount = 4}
            if(turns == 10){typeCount = 5}

            unitsPool = listOfUnit.shuffled()[0]

            calculate(point, Team.crux, chooseType(unitsPool, typeCount))
            calculate(point, Team.blue, chooseType(unitsPool, typeCount))

            turnReady = true

            while(turnTime > 0){

                when(turnTime){
                    15 -> broadcast("[red]殷紫[grey]:[white]你们觉得谁会赢呢？赶紧去打开下注界面吧！".with())
                    10 -> broadcast("[red]殷紫[grey]:[white]嗯哼，看来这局应该没什么大问题啦~".with())
                    5 -> broadcast("[red]殷紫[grey]:[white]快点快点，马上就要超时了！".with())
                    1 -> broadcast("[red]殷紫[grey]:[white]哦，战斗要开始了！".with())
                }

                Groups.unit.filter{ it.team() != Team.get(1) }.forEach{
                    it.apply(StatusEffects.unmoving, Float.MAX_VALUE)
                    it.apply(StatusEffects.disarmed, Float.MAX_VALUE)
                }
                delay(1_000L)
                turnTime--
            }

            labelA.forEach {
                it.hide()
            }
            labelA.clear()

            turnReady = false

            Groups.player.forEach{
                pickedPlayers.add(it)
                if(it.data.team == Team.crux){
                    cruxText += "[#${it.color}]${it.name}[] ${it.data.multipleText()}\n"
                }else if(it.data.team == Team.blue){
                    blueText += "[#${it.color}]${it.name}[] ${it.data.multipleText()}\n"
                }
            }

            labelB.add(label(36f * 8f, 18f * 8f).apply {
                text = cruxText + "[red]下注红队"
            })
            labelB.add(label(102f * 8f, 18f * 8f).apply {
                text = blueText + "[blue]下注蓝队"
            })

            Groups.unit.forEach{
                it.unapply(StatusEffects.unmoving)
                it.unapply(StatusEffects.disarmed)
            }


            while(true){
                time--
                delay(1_000L)
                Groups.unit.forEach {
                    if (it.type == UnitTypes.flare && it.controller() !is AreaAI) {
                        it.controller(AreaAI())
                    }
                }
                if(Team.crux.data().unitCount <= 0) {
                    broadcast("[red]殷紫[grey]:[white]锵锵锵~胜者出炉啦~！是[blue]蓝队[]！哪个倒霉蛋被输得哇哇叫呢~？".with())
                    winner = Team.blue
                    break
                }else if(Team.blue.data().unitCount <= 0) {
                    broadcast("[red]殷紫[grey]:[white]锵锵锵~胜者出炉啦~！是[red]红队[]！哪个倒霉蛋被输得哇哇叫呢~？".with())
                    winner = Team.crux
                    break
                }else if(time <= 0){
                    broadcast("[red]殷紫[grey]:[white]哦不！蛐蛐们陷入僵局了！那只好平局了...唉".with())
                    repeat(99){
                        Groups.unit.forEach {  //清场 全部上市
                            if (!it.isPlayer)
                                it.kill()
                        }
                    }

                    winner = Team.get(255)
                    break
                }
            }

            delay(10_000L)

            labelB.forEach {
                it.hide()
            }
            labelB.clear()

            repeat(99){
                Groups.unit.forEach {  //清场 全部上市
                    if (!it.isPlayer)
                        it.kill()
                }
            }


            pickedPlayers.forEach {
                it.data.settle(winner)
                it.data.multiple = 0f
                it.data.team = Team.get(0)
                //并非选择
            }


            Groups.player.forEach {
                rankMap += it to it.data.coins
            }

            sortedRankMap = rankMap!!.entries.sortedByDescending{ it.value }.associate { it.key to it.value }
            sortedRankMap.forEach {
                val p = it.key
                val change = p.data.coins - p.data.lastCoin
                val t = when {
                    change > 0 -> "[green]+$change"
                    change < 0 -> "[red]$change"
                    else -> ""
                }
                rankText += "[#${p.color}]${p.name} [yellow]${it.value} $t\n"
            }

            labelA.add(label(rankText, 69.5f * 8f, 84f * 8f))

            if((1..100).random() > 5){
                labelB.add(label(guideList.shuffled()[0], 69.5f * 8f, 88f * 8f))
            }else{
                labelB.add(label(easterList.shuffled()[0], 69.5f * 8f, 88f * 8f))
            }

            pickedPlayers.clear()
            cruxText = ""
            blueText = ""
            rankText = ""
            rankMap = mapOf<Player, Int>()
            sortedRankMap = mapOf<Player, Int>()
            turnTime = 20
            time = 100
            winner = Team.get(0)

            delay(2000L)

            if(turns >= 10){
                broadcast("[red]殷紫[grey]:[white]哦！游戏要结束了！让我看看排行怎么样！".with())
                delay(5_000L)
                Groups.player.forEach {
                    rankMap += it to it.data.coins
                }
                sortedRankMap = rankMap!!.entries.sortedByDescending{ it.value }.associate { it.key to it.value }
                var rank = 1 //排名
                var text = "[red]殷紫[grey]:[white]接下来，排行榜出炉了！看看结果吧！\n"
                sortedRankMap.forEach {
                    text += "${rank}. ${(it.key).name} [yellow]${it.value}[]\n"
                    rank++
                }
                broadcast(text.with())
                val players = mutableListOf<Player>()
                rankMap.forEach { //个人胜负判断
                    if (it.value >= 200000) {
                        (it.key).sendMessage("[red]殷紫[grey]:[white]你怎么真的到达了我的指标了啊...不甘心...你赢了...")
                        (it.key).sendMessage("[red]殷紫[grey]:[white]在走之前，我赋予了你能控制爬虫的能力，控制他们炸飞那些一败涂地的Loser吧，哈哈哈！")
                        it.key.achievement("[green][成功的赌徒]", 100)
                        betterTeam.changeTeam(it.key, Team.green)
                    } else {
                        (it.key).sendMessage("[red]殷紫[grey]:[white]哈哈！你并没达成我的目标！现在，留在这里吧！永远永远！成为竞技场上互相厮杀的单位吧~")
                        players.add(it.key)
                    }
                }

                delay(10_000L)
                //战败CG
                repeat(99){
                    Groups.unit.forEach {
                        it.kill()
                    }
                }


                Vars.state.rules.apply {
                    possessionAllowed = true
                    unitHealthMultiplier = 0.25f
                }

                Call.setRules(Vars.state.rules)

                players.forEach {
                    if (Team.crux.data().players.size > Team.blue.data().players.size) {
                        Team.blue.data().players.add(it)
                        betterTeam.changeTeam(it, Team.blue)
                    } else {
                        Team.crux.data().players.add(it)
                        betterTeam.changeTeam(it, Team.crux)
                    }
                }

                ts.loop(Dispatchers.game) {
                    typeCount = 3
                    Groups.unit.forEach {
                        it.health *= 0.25f
                    }
                    calculate(30000, Team.crux, chooseType(listOf(highTierUnits, mediumTierUnits).random(), typeCount))
                    calculate(30000, Team.blue, chooseType(listOf(highTierUnits, mediumTierUnits).random(), typeCount))
                    Groups.unit.forEach {
                        it.apply(StatusEffects.fast, Float.POSITIVE_INFINITY)
                        it.unapply(StatusEffects.unmoving)
                        it.unapply(StatusEffects.disarmed)
                    }
                    delay(4_000)
                }
                delay(60_000)

                state.gameOver = true
                Events.fire(EventType.GameOverEvent(Team.green))
                delay(99_000L)  //不明代码 可能出bug
                cancel()
            } //游戏结束 终局结算
        }

        ts.loop(Dispatchers.game){ //显示ui信息
            delay(1000)
            Groups.player.forEach {
                val text = "[green]博弈竞技场\n" + "[]当前回合：" + turns + "/10" + " []剩余时间：" + time + "\n[]当前金额：[yellow]" + it.data.coins + " []当前连胜：[green]" + it.data.streak + "\n[]当前选择队伍：" + it.playerGetTeam() + " []当前选择倍率：" + it.playerGetMultiple()
                Call.setHudText(it.con,text)
            }
        }
    }
}

listen<EventType.PlayerLeave> {  //退出游戏时嘲讽玩家
    val player = it.player!!
    broadcast((leaveList.shuffled()[0]).with("player" to player.name))
}