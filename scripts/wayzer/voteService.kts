@file:Depends("wayzer/user/ext/activeCheck", "玩家活跃判定", soft = true)
@file:Depends("coreMindustry/menu", "调用菜单")

package wayzer

import cf.wayzer.placehold.DynamicVar
import cf.wayzer.placehold.PlaceHoldContext
import coreLibrary.lib.*
import coreMindustry.MenuBuilder
import coreMindustry.lib.*
import mindustry.Vars.netServer
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Iconc
import mindustry.gen.Player
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

name = "投票服务"

val voteTime by config.key(Duration.ofSeconds(40)!!, "投票时间")

protected val Player.active
    get() = textFadeTime >= 0 || depends("wayzer/user/ext/activeCheck")
        ?.import<(Player) -> Int>("inactiveTime")
        ?.let { it(this) < 30_000 } ?: true

//public
val voteCommands: Commands = VoteCommands()
lateinit var canVote: (Player) -> Boolean

var lastTime = 0
//private
val voting = AtomicBoolean(false)
protected val agreed: MutableSet<String> = ConcurrentHashMap.newKeySet()
protected val disagreed: MutableSet<String> = ConcurrentHashMap.newKeySet()
protected var lastAction = 0L //最后一次玩家退出或投票成功时间,用于处理单人投票
protected lateinit var voteDesc: PlaceHoldContext
protected lateinit var votePlayer: Player

fun allCanVote() = Groups.player.filter(canVote)
fun abstained() = allCanVote().count(canVote) - agreed.size - disagreed.size
fun check():Boolean = agreed.size >= disagreed.size * 2
//agreed.size + abstained() * 0.5 > allCanVote().count(canVote) * 0.5

registerVar("scoreBroad.ext.vote", "空占位", DynamicVar.v {
    buildString {
        append("[cyan]输入/vote查看投票指令\n换图仅能通过观星台菜单打开\n使用/vote back来强制返回观星台")
        if (lastTime > 0) {
            appendLine()
            appendLine("[cyan]${voteDesc}[yellow]投票中... [lightgray]${lastTime}s/${voteTime.seconds}s")
            var i = 0
            repeat(agreed.size) {
                i++
                if (i == 11) {
                    appendLine()
                    i = 1
                }
                append("[green]${Iconc.star}")
            }
            repeat(disagreed.size) {
                i++
                if (i == 11) {
                    appendLine()
                    i = 1
                }
                append("[red]${Iconc.star}")
            }
            repeat(abstained()) {
                i++
                if (i == 11) {
                    appendLine()
                    i = 1
                }
                append("[yellow]${Iconc.star}")
            }
        }
    }
})


fun start(player: Player, voteDesc: PlaceHoldContext, supportSingle: Boolean = false, onSuccess: suspend () -> Unit) {
    if (voting.get()) return
    voting.set(true)
    this.voteDesc = voteDesc
    this.votePlayer = player
    launch(Dispatchers.game) {
        try {
            if (supportSingle && allCanVote().run { count(canVote) == 0 || singleOrNull() == player }) {
                if (System.currentTimeMillis() - lastAction > 60_000) {
                    broadcast("[cyan][投票系统][yellow]单人快速投票{type}成功".with("type" to voteDesc))
                    lastAction = System.currentTimeMillis()
                    withContext(Dispatchers.gamePost) {
                        onSuccess()
                    }
                    return@launch
                } else
                    broadcast("[cyan][投票系统][red]距离上一玩家离开或上一投票成功不足1分钟,快速投票失败".with())
            }
            lastTime = voteTime.seconds.toInt()
            allCanVote().forEach{launch{sendVoteMenu(it)}}
            broadcast(
                "[cyan][投票系统][yellow]{player.name}[yellow]发起{type}[yellow]投票"
                    .with("player" to player, "type" to voteDesc)
            )
            repeat(voteTime.seconds.toInt()) {
                delay(1000L)
                lastTime -= 1
                if (check() && (voteTime.seconds.toInt() - lastTime >= 15 || agreed.size >= allCanVote().size)) {//提前结束
                    broadcast(
                        "[cyan][投票系统][yellow]{type}[yellow]投票结束,投票成功.[white] [green]{agreed}[]/[red]{disagreed}[]/[yellow]{abstained}[]"
                            .with(
                                "type" to voteDesc,
                                "agreed" to agreed.size,
                                "disagreed" to disagreed.size,
                                "abstained" to abstained(),
                            )
                    )
                    withContext(Dispatchers.gamePost) {
                        onSuccess()
                    }
                    lastTime = 0
                    return@launch
                }
            }
            //TimeOut
            lastTime = 0
            broadcast(
                "[cyan][投票系统][yellow]{type}[yellow]投票结束,投票失败.[white] [green]{agreed}[]/[red]{disagreed}[]/[yellow]{abstained}[]"
                    .with(
                        "type" to voteDesc,
                        "agreed" to agreed.size,
                        "disagreed" to disagreed.size,
                        "abstained" to abstained(),
                    )
            )
        } finally {
            reset()
        }
    }
}

fun Script.addSubVote(
    desc: String,
    usage: String,
    vararg aliases: String,
    body: suspend CommandContext.() -> Unit
) {
    val voteCommands = contextScript<VoteService>().voteCommands
    voteCommands += CommandInfo(this, aliases.first(), desc) {
        this.usage = usage
        this.aliases = aliases.toList()
        body(body)
        if (permission.isEmpty())
            permission = "wayzer.vote." + aliases.first().lowercase()
    }
    voteCommands.autoRemove(this)
}

suspend fun sendVoteMenu(player: Player) {
    MenuBuilder<Unit>(true){
        title = "投票系统"
        msg = """
            {votePlayer}[yellow]发起了[]{type}[yellow]投票
            [yellow]请点击以下选项投票
        """.with(
            "type" to voteDesc,
            "votePlayer" to votePlayer.name,
        ).toString().trimIndent()
        option ("[acid]赞成"){onAgree(player)}
        option ("[scarlet]反对"){onDisAgree(player)}
        newRow ()
        option ("[grey]稍后再投"){}
    }.sendTo(player, lastTime * 1000)
}

//private
fun reset() {
    canVote = { !it.dead() && it.active }
    agreed.clear()
    disagreed.clear()
    voting.set(false)
    lastTime = 0
}

fun onAgree(p: Player) {
    if (!voting.get()) return
    if (p.uuid() in agreed) return p.sendMessage("[red]你已经赞成 无法再次赞成".with())
    if (p.uuid() in disagreed) disagreed.remove(p.uuid())
    if (!canVote(p)) return p.sendMessage("[red]你不能对此投票".with())
    agreed.add(p.uuid())
    broadcast("[cyan][投票系统][green]{name}[acid]已赞成[white] [green]{agreed}[]/[red]{disagreed}[]/[yellow]{abstained}[]".with(
        "name" to (p.name()),
        "agreed" to agreed.size,
        "disagreed" to disagreed.size,
        "abstained" to abstained(),
        "all" to allCanVote().count(canVote)
    ))
}
fun onDisAgree(p: Player) {
    if (!voting.get()) return
    if (p.uuid() in disagreed) return p.sendMessage("[red]你已经反对 无法再次反对".with())
    if (p.uuid() in agreed) agreed.remove(p.uuid())
    if (!canVote(p)) return p.sendMessage("[red]你不能对此投票".with())
    disagreed.add(p.uuid())
    broadcast("[cyan][投票系统][]{name}[scarlet]已反对[white] [green]{agreed}[]/[red]{disagreed}[]/[yellow]{abstained}[]".with(
        "name" to (p.name()),
        "agreed" to agreed.size,
        "disagreed" to disagreed.size,
        "abstained" to abstained(),
        "all" to allCanVote().count(canVote)
    ))
}
listen<EventType.PlayerChatEvent> { it ->
    it.player.textFadeTime = 0f //防止因为不说话判定为挂机
    if (it.message.equals("y", true) || it.message == "1") {
        onAgree(it.player)
    }
    if (it.message.equals("n", true) || it.message == "0") {
        onDisAgree(it.player)
    }
}

listen<EventType.PlayerJoin> {
    if (!voting.get()) return@listen
    it.player.sendMessage("[cyan][投票系统][yellow]当前正在进行{type}[yellow]投票".with("type" to voteDesc))
    launch{sendVoteMenu(it.player)}
}

listen<EventType.PlayerLeave> {
    lastAction = System.currentTimeMillis()
    if ( it.player.uuid() in disagreed) disagreed.remove(it.player.uuid())
    if ( it.player.uuid() in agreed) agreed.remove(it.player.uuid())
}

inner class VoteCommands : Commands() {
    override suspend fun invoke(context: CommandContext) {
        if (voting.get()) return context.reply("[red]投票进行中".with())
        super.invoke(context)
        if (voting.get()) {//success
            val raw = context.prefix + context.arg.joinToString(" ")
            val msg = netServer.chatFormatter.format(context.player!!, raw)
            Call.sendMessage(msg, raw, context.player!!)
        }
    }

    override suspend fun onHelp(context: CommandContext, explicit: Boolean) {
        if (!explicit) context.reply("[red]错误投票类型,请检查输入是否正确".with())
        super.onHelp(context, explicit)
    }
}

command("vote", "发起投票") {
    type = CommandType.Client
    aliases = listOf("投票")
    body(voteCommands)
}
command("votekick", "(弃用)投票踢人") {
    this.usage = "<player...>";this.type = CommandType.Client
    body {
        //Redirect
        arg = listOf("kick", *arg.toTypedArray())
        voteCommands.invoke(this)
    }
}