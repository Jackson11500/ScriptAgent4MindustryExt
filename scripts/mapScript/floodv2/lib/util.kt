package mapScript.floodV2.lib

import arc.Core
import arc.math.geom.Geometry
import mindustry.Vars
import mindustry.core.World
import mindustry.world.Tile
import mapScript.floodV2.lib.FloodUtil.creepMap
import mapScript.floodV2.lib.FloodUtil.maxCreep
import mapScript.floodV2.lib.FloodUtil.terrainMap
import java.awt.Color

fun depositCreeper(tile: Tile, radius: Int, amount: Float) {
    if (!FloodUtil.enable) return
    val validTile = buildList {
        Geometry.circle(tile.x.toInt(), tile.y.toInt(), radius) { cx: Int, cy: Int ->
            val ct = Vars.world.tile(cx, cy) ?: return@circle
            if (terrainMap[ct] < maxCreep && ct.breakable()) add(ct)
        }
    }
    if (validTile.isEmpty()) return
    validTile.forEach {
        creepMap[it] = (creepMap[it] + amount / validTile.size).coerceAtMost(maxCreep - terrainMap[it])
        Core.app.post { FloodUtil.creeperDraw(it) }
    }
}

fun trafficLightColor(v: Float): String {
    val rate = v.coerceIn(0f, 1f)
    return "#" + Integer.toHexString(Color.HSBtoRGB(rate / 3f, 1f, 1f)).substring(2)
}

@JvmInline
value class TileFloatMap(private val data: FloatArray) {
    constructor(length: Int) : this(FloatArray(length))
    constructor(world: World) : this(world.width() * world.height())

    operator fun get(t: Tile) = data[t.array()]
    operator fun set(t: Tile, v: Float) {
        data[t.array()] = v
    }

    companion object {
        val EMPTY = TileFloatMap(0)
    }
}

/**for [FloodUtil.creepMap]*/
operator fun Array<FloatArray>.get(t: Tile) =
    if (FloodUtil.enable) this[t.x.toInt()][t.y.toInt()] else -1f

operator fun Array<FloatArray>.set(t: Tile, v: Float) {
    this[t.x.toInt()][t.y.toInt()] = v
}