@file:Depends("wayzer/voteService", "投票实现")

package xkldklp.user

import coreLibrary.lib.with
import coreMindustry.lib.command
import coreMindustry.lib.player
import mindustry.net.Administration
import wayzer.VoteService

val voteService = contextScript<VoteService>()

command("ssi", "查询当前ssi") {
    body {
        returnReply("[yellow]当前SSI:[white]{ssi}".with("ssi" to Administration.Config.snapshotInterval.get()))
    }
}

fun VoteService.reg() {
    addSubVote("更改实体快照间隔", "<间隔(ms)>(100-800)", "ssi") {
        if (arg.isEmpty()) returnReply("[red]请输入间隔".with())
        if (arg.getOrNull(0)?.toIntOrNull() !in 100..800) {
            returnReply("[red]数值不合法".with())
        }
        start(player!!, "更改实体快照间隔至 {i}".with("i" to arg[0])) {
            Administration.Config.snapshotInterval.set(arg[0].toInt())
        }
    }
}

onEnable {
    voteService.reg()
}