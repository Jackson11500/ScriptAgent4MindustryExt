@file:Depends("wayzer/maps")

package mapScript.floodV2

import mapScript.floodV2.lib.FloodUtil
import wayzer.MapChangeEvent

listenTo<MapChangeEvent>(Event.Priority.Watch) {
    if (rules.tags.containsKey(FloodUtil.TAG)) {
        if (rules.tags.containsKey("@flood"))
            rules.tags.remove("@flood")
        if (!rules.tags.containsKey("@banTeam"))
            rules.tags.put("@banTeam", FloodUtil.creepTeam.id.toString())
    }
}