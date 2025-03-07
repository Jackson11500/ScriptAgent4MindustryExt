package mapScript.floodV2

import arc.math.Mathf
import arc.math.geom.Geometry
import arc.math.geom.Intersector
import coreLibrary.lib.config
import coreLibrary.lib.util.reflectDelegate
import coreMindustry.lib.listen
import mapScript.floodV2.lib.FloodUtil
import mindustry.Vars.*
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.entities.abilities.ForceFieldAbility
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Unit

val damageScale by config.key(0.5f, "洪水对单位立场的伤害倍率")
val ForceFieldAbility.radiusScale: Float by reflectDelegate()

fun Unit.creepAbsorb(ability: ForceFieldAbility) {
    val realRange = ability.radiusScale * ability.radius
    if (realRange <= 1f) return
    val tileSize = tilesize.toFloat()
    Geometry.circle(tileX(), tileY(), (realRange / tileSize).toInt() * 3) { cx, cy ->
        if (Intersector.isInsideHexagon(x, y, realRange * 2, cx * tileSize, cy * tileSize)) {
            val tile = world.tile(cx, cy) ?: return@circle
            val creep = FloodUtil.creepMap[tile].coerceAtLeast(0f)
            if (creep > 0) {
                FloodUtil.creepMap[tile] = 0f
                damagePierce(
                    creep * FloodUtil.creepUnitDamage * damageScale /
                            Mathf.sqrt(damageMultiplier / reloadMultiplier / state.rules.unitDamage(team))
                )
                if (shield == 0f)
                    shield = -ability.regen * ability.cooldown
            }
            if (tile.block() != Blocks.air && tile.block() in FloodUtil.creeperBlocksSet) {
                Call.effect(Fx.absorb, tile.worldx(), tile.worldy(), 1f, FloodUtil.creepTeam.color)
                tile.build.kill()
            }
        }
    }
}


listen(EventType.Trigger.update) {
    if (FloodUtil.enable) {
        Groups.unit.forEach {
            if (it.team == FloodUtil.creepTeam || (it.team == state.rules.waveTeam && it.team != state.rules.defaultTeam)) return@forEach
            val ability = it.abilities.filterIsInstance<ForceFieldAbility>().firstOrNull() ?: return@forEach
            it.creepAbsorb(ability)
        }
    }
}