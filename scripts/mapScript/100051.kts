package mapScript

import coreMindustry.lib.listen
import mapScript.lib.modeIntroduce
import mindustry.ai.types.MissileAI
import mindustry.content.StatusEffects
import mindustry.game.EventType
import mindustry.gen.Iconc

/**@author xkldklp */
name = "单位感染"

modeIntroduce(
    "俘获强夺之域", "[cyan]单位被摧毁后会被转化为其他队伍单位!\n[yellow]一个单位可被转化2次\n[red]仅弹头直接击杀会被感染"
)


val units by autoInit { mutableMapOf<mindustry.gen.Unit, Int>() }

listen<EventType.UnitBulletDestroyEvent> { e ->
    val u = e.unit
    val team = e.bullet.team
    if (u.controller() is MissileAI) return@listen
    if (u != team && units.getOrDefault(u, 0) <= 1) {
        e.unit.type.spawn(team, e.unit.x, e.unit.y).apply {
            health /= 4
            apply(StatusEffects.electrified, 5 * 60f)
            apply(StatusEffects.slow, 5 * 60f)
            apply(StatusEffects.disarmed, 5 * 60f)
            units[this] = units.getOrDefault(u, 0) + 1
            rotation = u.rotation() + 180f
            vel = u.vel().rotate(180f)
        }
    }
}