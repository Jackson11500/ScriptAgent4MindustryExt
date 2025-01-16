package coreMindustry.util

import arc.math.geom.Geometry
import arc.math.geom.Point2
import cf.wayzer.scriptAgent.Config.logger
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Posc
import mindustry.type.UnitType
import mindustry.world.Tile

/**
 * @return 无法找到合适位置，返回null
 */
fun UnitType.spawnAround(pos: Posc, team: Team, radius: Int = 10): mindustry.gen.Unit? {
    return create(team).apply {
        set(pos)
        if (canPass(tileX(), tileY()) && (!canDrown() || floorOn()?.isDeep == false)) {
            add()
        } else {
            val valid = mutableListOf<Point2>()
            Geometry.circle(tileX(), tileY(), Vars.world.width(), Vars.world.height(), radius) { x, y ->
                if (canPass(x, y) && (!canDrown() || floorOn()?.isDeep == false))
                    valid.add(Point2(x, y))
            }
            val r = valid.randomOrNull() ?: return null
            x = r.x * Vars.tilesize.toFloat()
            y = r.y * Vars.tilesize.toFloat()
            add()
        }
    }
}

/**
 * @return 无法找到合适位置，返回null
 */
fun UnitType.spawnAround(tile: Tile, team: Team, radius: Int = 10): mindustry.gen.Unit? {
    return create(team).apply {
        set(tile)
        if (canPass(tileX(), tileY()) && (!canDrown() || floorOn()?.isDeep == false)) {
            add()
        } else {
            val valid = mutableListOf<Point2>()
            Geometry.circle(tileX(), tileY(), Vars.world.width(), Vars.world.height(), radius) { x, y ->
                if (canPass(x, y) && (!canDrown() || floorOn()?.isDeep == false))
                    valid.add(Point2(x, y))
            }
            val r = valid.randomOrNull() ?: return null
            x = r.x * Vars.tilesize.toFloat()
            y = r.y * Vars.tilesize.toFloat()
            add()
        }
    }
}

/**
 * @return 无法找到合适位置，返回null
 */
fun UnitType.spawnAround(sx: Float, sy: Float, team: Team, radius: Int = 10): mindustry.gen.Unit? {
    return create(team).apply {
        set(sx, sy)
        if (canPass(tileX(), tileY()) && (!canDrown() || floorOn()?.isDeep == false)) {
            add()
        } else {
            val valid = mutableListOf<Point2>()
            Geometry.circle(tileX(), tileY(), Vars.world.width(), Vars.world.height(), radius) { x, y ->
                if (canPass(x, y) && (!canDrown() || floorOn()?.isDeep == false))
                    valid.add(Point2(x, y))
            }
            val r = valid.randomOrNull() ?: return null
            x = r.x * Vars.tilesize.toFloat()
            y = r.y * Vars.tilesize.toFloat()
            add()
        }
    }
}