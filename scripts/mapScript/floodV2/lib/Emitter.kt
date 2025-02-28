package mapScript.floodV2.lib

import arc.Core.app
import arc.math.geom.Geometry
import arc.math.geom.Position
import arc.util.Interval
import mindustry.entities.Effect
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.WorldLabel
import mindustry.world.Block
import mindustry.world.Tile
import mapScript.floodV2.lib.FloodUtil.creepMap
import mapScript.floodV2.lib.FloodUtil.creepTeam
import mapScript.floodV2.lib.FloodUtil.maxCreep

@Suppress("MemberVisibilityCanBePrivate")
abstract class Emitter(override val build: Building) : Position by build,
    BuildingBinder<Building> {
    protected val label = WorldLabel.create()!!.apply {
        set(build)
        add()
    }
    open val targetFinish: Boolean get() = false // must destroy

    override fun onRemove(resetEvent: Boolean) {
        if (FloodUtil.enable)
            build.tile.getLinkedTilesAs(build.block) {
                creepMap[it] = creepMap[it].coerceAtMost(maxCreep)
            }
        label.hide()
    }

    private val fxTimer = Interval()
    fun buildFx(fx: Effect) {
        if (fxTimer[60f])
            Call.effect(fx, build.x, build.y, build.block.size.toFloat(), creepTeam.color)
    }

    /**@param info (threshold, block),*/
    fun StringBuilder.drawUpgrade(info: Pair<Float, Block>?, value: Float) {
        val (threshold, block) = info ?: return
        append("[green]\uE804[] - [stat]${(value / threshold * 100).toInt()}%[]\n")
        if (value > threshold) app.post {
            build.tile.setNet(block, creepTeam, 0)
        }
    }

    companion object {
        const val healthOffset = 1e6f //融汇dps 1000
        val emitterMap: MutableMap<Block, (Building) -> Emitter> = mutableMapOf()
        internal val tracker = BuildingTracker.new(true, FloodUtil::enable, { build: Building ->
            emitterMap[build.block]?.invoke(build) ?: error("invalid emitter block")
        }) {
            it.team == creepTeam && it.block in emitterMap
        }

        val emitters
            get() = tracker.map.values as Collection<Emitter>

        val Tile.closestEmitter: Emitter? get() = Geometry.findClosest(getX(), getY(), emitters)

        fun initWorld() {
            tracker.load()
            FloodUtil.script.logger.info("Emitter count: ${emitters.size}")
        }

        fun update() {
            if (emitters.all { it.targetFinish }) {
                emitters.toList().forEach { it.build.kill() }
                FloodUtil.reset()
                return
            }
            tracker.update()
        }

        fun reset() {
            tracker.reset()
        }
    }
}