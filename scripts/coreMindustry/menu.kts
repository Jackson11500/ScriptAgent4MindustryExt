package coreMindustry

import coreLibrary.lib.Commands
import coreLibrary.lib.util.nextEvent
import coreLibrary.lib.with
import coreMindustry.lib.listen
import coreMindustry.lib.listenPacket2ServerAsync
import coreMindustry.lib.player
import coreMindustry.lib.toPlayer
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.MenuChooseCallPacket
import mindustry.gen.Player
import kotlin.random.Random

suspend fun <T : Any> sendMenuBuilder(
    player: Player,
    timeoutMillis: Int,
    title: String,
    msg: String,
    builder: suspend MutableList<List<Pair<String, suspend () -> T>>>.() -> Unit
): T? {
    return MenuBuilder<T>(title) {
        this.msg = msg
        buildList { builder() }.forEachIndexed { i, l ->
            if (i != 0) newRow()
            l.forEach { option(it.first, it.second) }
        }
    }.sendTo(player, timeoutMillis)
}

fun <T : Any> menuBuilder(
    title: String,
    block: MenuBuilder<T>.() -> Unit
): MenuBuilder<T> = MenuBuilder(title, block)

listenPacket2ServerAsync<MenuChooseCallPacket> { con, packet ->
    con.player?.let { p ->
        MenuChooseEvent(p, packet.menuId, packet.option).emitAsync().cancelled.not()
    } ?: true
}

/**
 * 发送菜单并接收回复选项
 * 低级接口, 内部不带Timeout, 外部通常需要进行Timeout处理(否则可能无限期等待)
 * @return 原版返回值，代表选中n个选项，可能 -1 代表主动关闭
 */
suspend fun menuAsync(player: Player, title: String, msg: String, menu: Array<Array<String>>): Int {
    val id = Random.nextInt()
    Call.followUpMenu(player.con, id, title, msg, menu)
    return nextEvent<MenuChooseEvent> { it.player == player && it.menuId == id }.value
}

//TODO 接管 Commands.defaultHelpImpl

data class MenuChooseEvent(
    val player: Player, val menuId: Int, val value: Int
) : Event, Event.Cancellable {
    override var cancelled: Boolean = false

    companion object : Event.Handler()
}

listen<EventType.MenuOptionChooseEvent> {
    MenuChooseEvent(it.player, it.menuId, it.option).launchEmit(coroutineContext) { e ->
        if (!e.cancelled && it.menuId < 0)
            Call.hideFollowUpMenu(e.player.con, e.menuId)
    }
}

onEnable {
    val bak = Commands.defaultHelpImpl
    onDisable { Commands.defaultHelpImpl = bak }
    Commands.defaultHelpImpl = impl@{ context, explicit ->
        val player = context.player ?: return@impl bak(context, explicit)
        if (context.arg.isNotEmpty() && !explicit) return@impl context.reply("[red]无效指令,请使用/help查询".with())
        val showDetail = context.checkArg("-v")
        if (showDetail && !context.hasPermission("command.detail"))
            return@impl context.reply("[red]必须拥有command.detail权限才能查看完整help".with())

        val commands = getSubCommands(context).values.toSet().filter {
            showDetail || it.permission.isBlank() || context.hasPermission(it.permission)
        }
        PagedMenuBuilder(commands, selectedPage = context.arg.firstOrNull()?.toIntOrNull() ?: 1) { command ->
            option(buildString {
                append("[gold]${context.prefix}${command.name}")
                if (command.aliases.isNotEmpty())
                    append("[scarlet](${command.aliases.joinToString()})")
                appendLine(" [white]${command.usage}")
                append("[cyan]${command.description.toPlayer(player)}")
                if (showDetail) {
                    command.script?.let { append(" | ${it.id}") }
                    command.permission.takeUnless { it == "" }?.let { append(" | $it") }
                }
            }) {
                context.arg = listOf(command.name)
                context.reply("[yellow][快捷输入指令][] {command}".with("command" to (context.prefix + command.name)))
                invoke(context)
            }
        }.apply {
            msg = "点击选项将直接执行指令"
        }.sendTo(player, 60_000)
    }
}