@file:Depends("wayzer/maps")

package mapScript.floodV2

import coreLibrary.lib.with
import coreMindustry.lib.broadcast
import mapScript.floodV2.lib.FloodUtil
import wayzer.MapChangeEvent
import wayzer.MapManager

listenTo<MapChangeEvent>(Event.Priority.Intercept) {
    if (rules.defaultTeam == FloodUtil.creepTeam) {
        broadcast("[red]禁止玩家队伍作为洪水队伍".with())
        cancelled = true
        launch {
            MapManager.loadMap()
        }
    }
}

listenTo<MapChangeEvent>(Event.Priority.Watch) {
    if (rules.tags.containsKey(FloodUtil.TAG)) {
        if (rules.tags.containsKey("@flood"))
            rules.tags.remove("@flood")
        if (!rules.tags.containsKey("@banTeam"))
            rules.tags.put("@banTeam", FloodUtil.creepTeam.id.toString())
    }
}