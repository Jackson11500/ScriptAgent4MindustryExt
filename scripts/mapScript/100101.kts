package mapScript

import arc.graphics.Color
import arc.math.geom.Vec2
import arc.struct.Seq
import coreMindustry.lib.game
import coreMindustry.lib.listen
import mapScript.lib.modeIntroduce
import mindustry.content.Fx
import mindustry.game.EventType
import mindustry.gen.Call
import kotlin.math.log
import kotlin.random.Random

/**@author xkldklp */
name = "掉落"

modeIntroduce(
    "重构之域", "[cyan]单位死亡将会掉落一部分物品至最近核心!\n[yellow]掉落数量为单位建造所需材料/4"
)

listen<EventType.UnitDestroyEvent> {
    val unit = it.unit
    val core = unit.closestEnemyCore() ?: return@listen

    core.items().apply {
        if (unit.type.totalRequirements.isEmpty()) return@listen
        unit.type.totalRequirements.forEach {
            add(it.item, (it.amount / 4).coerceAtMost(core.storageCapacity - get(it.item).coerceAtLeast(0)))
        }
        Call.label(unit.type.totalRequirements.joinToString(" ") {"[#${core.team.color}]${it.amount / 4}[white]${it.item.emoji()}"}, 1.5f, unit.x, unit.y)
    }
}