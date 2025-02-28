package mapScript.floodV2

import arc.Core
import arc.math.geom.Geometry
import arc.math.geom.Intersector
import arc.struct.IntSet
import arc.util.Interval
import arc.util.io.Writes
import coreLibrary.lib.config
import coreMindustry.lib.listen
import mapScript.floodV2.Module
import mapScript.floodV2.lib.*
import mindustry.Vars.tilesize
import mindustry.Vars.world
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.game.EventType
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.WorldLabel
import mindustry.world.blocks.defense.ForceProjector
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

val creepDamageRate by config.key(0.2f, "洪水对盾的伤害倍率")
val creepDamage get() = FloodUtil.creepDamage * creepDamageRate

inner class FloodForceProjector(override val build: ForceProjector.ForceBuild) :
    BuildingBinder<ForceProjector.ForceBuild> {

    private val label = WorldLabel.create()!!.apply {
        set(build)
        add()
    }

    private val firstIgnore = IntSet()//tile pos when first absorb
    override fun ForceProjector.ForceBuild.selfUpdate() {
        if (realRadius() > 0 && !broken) {
            val tileSize = tilesize.toFloat()
            Geometry.circle(tileX(), tileY(), (realRadius() / tileSize).toInt() * 3) { cx, cy ->
                if (Intersector.isInsideHexagon(x, y, realRadius() * 2, cx * tileSize, cy * tileSize)) {
                    val tile = world.tile(cx, cy) ?: return@circle
                    val creep = FloodUtil.creepMap[tile].coerceAtLeast(0f)
                    if (creep > 0) {
                        FloodUtil.creepMap[tile] = 0f
                        if (!firstIgnore.add(tile.pos()) || creep > 1) {
                            hit = 1f
                            buildup += creep * creepDamage * (if (this.items.any()) 0.85f else 1f)
                        }
                    }
                    if (tile.block() != Blocks.air && tile.block() in FloodUtil.creeperBlocksSet) {
                        Call.effect(Fx.absorb, tile.worldx(), tile.worldy(), 1f, FloodUtil.creepTeam.color)
                        tile.build.kill()
                    }
                }
            }
        }

        val percentage = buildup / ((block as ForceProjector).shieldHealth +
                (block as ForceProjector).phaseShieldBoost * phaseHeat)
        label.text = "[${trafficLightColor(1 - percentage)}]${(percentage * 100).toInt()}%"
        if (this.items.any()) label.text += "\n[yellow]\uE84D[] [stat]+15%"
        if (percentage >= 1) {
            val buildup = buildup
            Core.app.post {
                this.buildup = buildup
                this.kill()
            }
        }
    }

    override fun onRemove(resetEvent: Boolean) {
        label.hide()
        if (resetEvent) return
        val size = build.block.size
        depositCreeper(build.tile, size, build.buildup / creepDamage)

    }
}

val tracker = BuildingTracker.new(false, FloodUtil::enable, ::FloodForceProjector) {
    it.team != FloodUtil.creepTeam
}
    .listenChange(this)
    .listenLifecycle(this, { true })

fun syncTile(builds: List<Building>) {
    val outStream = ByteArrayOutputStream()
    val write = DataOutputStream(outStream)
    builds.forEach {
        write.writeInt(it.pos())
        write.writeShort(it.block.id.toInt())
        it.writeAll(Writes.get(write))
    }
    Call.blockSnapshot(builds.size.toShort(), outStream.toByteArray())
}

val timer = Interval()
listen(EventType.Trigger.update) {
    if (FloodUtil.enable && timer[60f])
        syncTile(tracker.map.keys.filter { it.buildup > 0 })
}