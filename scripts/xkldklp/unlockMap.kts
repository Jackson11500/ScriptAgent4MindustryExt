@file:Depends("wayzer/maps", "获取地图信息")
@file:Depends("wayzer/map/mapInfo", "显示地图信息", soft = true)

package xkldklp

import arc.Core
import coreLibrary.lib.with
import coreMindustry.lib.*
import mindustry.Vars
import mindustry.game.EventType
import wayzer.MapManager

listen<EventType.WinEvent> {
    val mapInfo = MapManager.current
    val id = mapInfo.map.file.name().split(".")[0].substring(1).toIntOrNull()
    if (id != null) {
        Core.settings.put("unlock-${mapInfo.map.file.name().split(".")[0][0]}${id + 1}", true)
    }
}

command("switch", "开关地图解锁状态") {
    permission = "xkldklp.unlockMap"
    body {
        if (arg.isEmpty()) {
            reply("?".with())
        } else {
            Core.settings.put("unlock-${arg.first()}", !Core.settings.getBool("unlock-${arg.first()}", false))
            reply("{bool}".with("bool" to Core.settings.getBool("unlock-${arg.first()}", false)))
        }
    }
}