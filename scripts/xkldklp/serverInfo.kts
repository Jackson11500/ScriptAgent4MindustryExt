package xkldklp

import coreLibrary.lib.config
import coreLibrary.lib.util.loop
import coreMindustry.lib.listen
import coreMindustry.util.randomColor
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Groups
import mindustry.net.Administration
import mindustry.net.Packet
import mindustry.net.Packets
import mindustryX.events.SendPacketEvent
import kotlin.math.max

val playerMax by config.key(30 ,"服务器最大人数")

fun refreshPlayerLimit() {
    Vars.netServer.admins.playerLimit = max(playerMax, Groups.player.size() + 1)
}

onEnable {
    loop {
        refreshPlayerLimit()
        delay(1_000)
    }
    loop {
        Administration.Config.serverName.set("${randomColor()}Star${randomColor()}Legends [white]~ [cyan]星辰传说")
        delay(1_000)
    }
}

fun checkMax(amount: Int = 1): Boolean{
    return Groups.player.size() + amount > playerMax
}

listen<EventType.PlayerConnect> {
    if (checkMax() && !it.player.admin) {
        it.player.kick("但是，服务器已满", 0L)
    } else {
        refreshPlayerLimit()
    }
}

