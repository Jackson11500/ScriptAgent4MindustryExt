package mapScript

import coreLibrary.lib.with
import coreMindustry.lib.command
import coreMindustry.lib.player
import mapScript.lib.modeIntroduce
import mindustry.Vars
import mindustry.gen.Iconc


/**@author xkldklp */
name = "投敌"

modeIntroduce(
    "GRW招生之域", "[cyan]在非pvp模式下输入\n" +
            "[yellow]/还是pvp大佬\n" +
            "[cyan]即可在波次队伍与玩家队伍间切换\n" +
            "[lightgray]还是pvp大佬"
)

command("还是pvp大佬", "在波次队伍或玩家队伍间切换,pvp禁用") {
    body {
        if (Vars.state.rules.pvp) returnReply("[red]pvp模式禁用".with())
        val player = player!!
        if (player.team() != Vars.state.rules.defaultTeam) {
            player.clearUnit()
            player.team(Vars.state.rules.defaultTeam)
            player.clearUnit()
        } else {
            player.clearUnit()
            player.team(Vars.state.rules.waveTeam)
            player.clearUnit()
        }
    }
}