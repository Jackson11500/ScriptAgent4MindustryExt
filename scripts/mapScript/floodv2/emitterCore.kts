package mapScript.floodV2

import arc.math.geom.Geometry
import arc.util.Interval
import arc.util.Time
import arc.util.Timer
import mindustry.gen.Building
import mindustry.graphics.Pal
import mindustry.type.Item
import mindustry.content.Blocks
import mindustry.world.Block
import mindustry.world.blocks.power.ImpactReactor
import mindustry.world.blocks.storage.CoreBlock.CoreBuild

val range by config.key(16f * tilesize, "冲击反应堆Nullify距离")

val mainScript = contextScript<Module>()

val map = mapOf(
    Blocks.coreShard to Core(
        5f, 0.5f,
        maxLayer = 5f,
        upgrade = 20 * 30f to Blocks.coreFoundation,
        nullifyAble = 10000f to 3 * 60f,
    ),
    Blocks.coreFoundation to Core(
        12f, 0.4f,
        maxLayer = 10f,
        upgrade = 100 * 30f to Blocks.coreNucleus,
        nullifyAble = 20000f to 5 * 60f,
    ),
    Blocks.coreNucleus to Core(
        20f, 0.2f,
        nullifyAble = 100000f to 6 * 60f,
    ),
    Blocks.reinforcedContainer to Core(
        2f, 1f,
        nullifyAble = 2500f to 3 * 60f
    ),
    Blocks.reinforcedVault to Core(
        8f, 1f,
        nullifyAble = 5000f to 3 * 60f
    ),
    Blocks.coreBastion to Core(
        20f, 0.4f,
        nullifyAble = 15000f to 3 * 60f,
        canClear = false,
    ),
    Blocks.coreCitadel to Core(
        25f, 0.2f,
        nullifyAble = 100000f to 5 * 60f,
        canClear = false,
    ),
    Blocks.coreAcropolis to Core(
        50f, 0.2f,
        nullifyAble = 250000f to 6 * 60f,
        canClear = false,
    )
)

/**
 * @param amt water per second
 * @param interval in second
 * @param nullifyAble (damage, timeout) , timeout in game time
 * @param upgrade (threshold, block)
 */
data class Core(
    val amt: Float,
    val interval: Float,
    val nullifyAble: Pair<Float, Float>,
    val canClear: Boolean = true,
    val maxLayer: Float? = null,
    // the maximum layer of creep a coreBlock can hold, overCreep would be turned into overflow and use for upgrade
    // only block can be upgraded should have maxCreep
    val upgrade: Pair<Float, Block>? = null,
) : (Building) -> Emitter {
    override fun invoke(build: Building) = Impl(build)
    inner class Impl(build: Building) : Emitter(build) {
        val type get() = this@Core
        override val targetFinish: Boolean
            get() = nullified
        val nullified get() = nullifyTimeout > 0
        var overflow = 0f   //Core stack process

        private val timer = Interval()
        private var nullifyDamage = 0f
        private var nullifyTimeout = 0f
        private var floodDam = 0f  //Extra flood fbsord(>0)|Emit(<0) rate per sec

        init {
            build.maxHealth = healthOffset
            build.heal()
        }

        override fun update() {
            val (nullDamage, nullTimeout) = nullifyAble
            nullifyTimeout -= Time.delta
            if (build.damaged()) {
                nullifyDamage += build.maxHealth - build.health
                build.heal()
                if (nullifyDamage > nullDamage) {
                    nullifyDamage = 0f
                    nullifyTimeout = nullTimeout
                    overflow = (overflow - nullDamage / 300).coerceAtLeast(0f)
                }
            }
            if (timer[interval * 60f]) {
                val amt2 = amt * interval / build.block.size / build.block.size
                build.tile.getLinkedTiles {
                    if (nullified) {
                        FloodUtil.creepMap[it] = 0f
                    } else if (build.enabled) {
                        floodDam = calFloodDam()
                        FloodUtil.creepMap[it] = (FloodUtil.creepMap[it] + amt2 + floodDam).coerceAtLeast(0f)
                        if (maxLayer != null && FloodUtil.creepMap[it] > maxLayer) {
                            overflow += FloodUtil.creepMap[it] - maxLayer
                            FloodUtil.creepMap[it] = maxLayer
                        }
                    }
                }

            }
            label.text = buildString {
                if (!build.enabled) append("[red]\uE815 已禁用 \uE815[]\n")
                if (floodDam != 0f) {
                    append("额外${if (floodDam > 0) "[red]出水" else "[green]吸水"} ${floodDam.toInt()}[]\n")
                }
                append("[stat]伤害[white] ${nullifyDamage.toInt()}/${nullDamage.toInt()}\n")
                if (nullified) {
                    append("[red]**[yellow] 压制中 [red]**[]\n")
                    buildFx(Fx.placeBlock)
                }
                drawUpgrade(upgrade, overflow)
                if (canClear)
                    append("[stat]\uE809[white] 启动" + Blocks.impactReactor.emoji())
                else
                    append("[stat]\uE809[white] [white]持续压制")
            }
        }

        private fun calFloodDam(): Float {
            if (build is CoreBuild || build.items.empty()) return 0f
            fun ifConsume(item: Item, v: Float): Float? {
                if (!build.items.has(item)) return null
                build.items.remove(item, 1)
                return v
            }
            return ifConsume(Items.sporePod, 100f)
                ?: ifConsume(Items.blastCompound, 10f)
                ?: ifConsume(Items.pyratite, 1f)
                ?: ifConsume(Items.coal, -1f)
                ?: ifConsume(Items.sand, -10f)
                ?: 0f
        }
    }
}
Emitter.emitterMap.putAll(map)

inner class FloodImpactReactor(override val build: ImpactReactor.ImpactReactorBuild) :
    BuildingBinder<ImpactReactor.ImpactReactorBuild> {
    private val myTimer = Interval(2)
    override fun ImpactReactor.ImpactReactorBuild.selfUpdate() {
        val targets = Emitter.emitters.filterIsInstance<Core.Impl>()
            .filter { it.type.canClear && within(it, range) }
        if (targets.isEmpty()) return

        if (myTimer[0, (2f - warmup) * 60]) targets.forEach { target ->
            Geometry.iterateLine(0f, x, y, target.x, target.y, ((1.2f - warmup) * 16f).coerceIn(2f, 16f)) { x, y ->
                Timer.schedule({
                    Call.effect(Fx.lancerLaserChargeBegin, x, y, 1f, Pal.accent)
                }, dst(x, y) / range)
            }
        }
        if (canConsume() || power.status >= 0.99) {//work
            if (myTimer[1, 60f]) targets.forEach {
                it.build.damage(team, 1f)
            }

            if (warmup == 1f) arc.Core.app.post {
                targets.forEach { target ->
                    target.build.damage(team, 1f)
                    target.build.kill()
                }

                tile.setNet(Blocks.air)
            }
        }
    }
}

BuildingTracker.new(false, FloodUtil::enable, ::FloodImpactReactor) {
    it.team != FloodUtil.creepTeam
}
    .listenChange(this)
    .listenLifecycle(this, { true })