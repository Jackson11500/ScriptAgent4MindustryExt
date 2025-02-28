package wayzer.map

import coreLibrary.lib.with
import coreMindustry.lib.broadcast
import coreMindustry.lib.listen
import coreMindustry.lib.sendMessage
import mindustry.Vars.content
import mindustry.Vars.state
import mindustry.ctype.ContentType
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.type.UnitType
import mindustry.world.blocks.units.Reconstructor
import kotlin.random.Random

var list = emptySet<UnitType>()
var oldList = emptySet<UnitType>()

listen<EventType.PlayEvent> {
    val old = state.rules.tags["@banUnit"].orEmpty()
        .split(';')
        .filterNot { it.isEmpty() }
        .mapNotNull { content.getByName<UnitType>(ContentType.unit, it) }
    oldList = old.toSet()
    list = state.rules.bannedUnits.toSet() + old
    if (list.isNotEmpty()) {
        state.rules.bannedUnits.addAll(*list.toTypedArray())
        broadcast("[red]本地图禁用单位:[yellow]{list}".with("list" to list.map { "${it.emoji()}${it.localizedName}" }))
    }
}

listen<EventType.PlayerJoin> { e ->
    if (list.isNotEmpty()) {
        list = state.rules.bannedUnits.toSet() + oldList
        e.player.sendMessage("[red]本地图禁用单位:[yellow]{list}".with("list" to list.map { "${it.emoji()}${it.localizedName}" }))
    }
}

val specialFlag = Random.nextDouble()

listen<EventType.UnitCreateEvent> {
    list = state.rules.bannedUnits.toSet() + oldList
    if (it.unit.type in list && it.spawner is Reconstructor.ReconstructorBuild)
        it.unit.flag = specialFlag
}

listen<EventType.UnitUnloadEvent> {
    val type = it.unit.type
    if (type in list && it.unit.flag == specialFlag) {
        Call.label("[red]本地图已禁用单位${type.emoji()}${type.localizedName}}", 10f, it.unit.x, it.unit.y)
        it.unit.set(0f, 0f)
        it.unit.kill()
    }
}
