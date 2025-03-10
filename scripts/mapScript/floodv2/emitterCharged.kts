package mapScript.floodV2

import arc.math.Mathf
import arc.util.Interval
import mapScript.floodV2.lib.Emitter
import mapScript.floodV2.lib.FloodUtil
import mindustry.gen.Building
import mindustry.world.Block
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.gen.Call

name = "FloodV2 - 间歇泉"

val map = mapOf(
    Blocks.container to Charged(
        5f * (1 + 1 / 0.3f), 2f, protectDps = 500f,
        charge = 0.3f to 900f
    ),
    Blocks.vault to Charged(
        15f * (1 + 1 / 0.3f), 2f, protectDps = 1500f,
        charge = 0.3f to 900f
    ),
    Blocks.launchPad to Charged(
        10f * (1 + 1 / 0.3f), 1f, protectDps = 1000f,
        charge = 0.3f to 900f,
        maxLayer = 10f,
        upgrade = 60 * 30f to Blocks.interplanetaryAccelerator,
    ),
    Blocks.interplanetaryAccelerator to Charged(
        25f * (1 + 1 / 0.5f), 2f, protectDps = 3000f,
        charge = 0.5f to 2400f
    ),
)

Emitter.emitterMap.putAll(map)

/**
 * @param amt water per second
 * @param interval in second
 * @param protectDps min Dps to get damage
 * @param charge (chargeSpeed,chargeCap) in game Time
 * @param upgrade (threshold, block)
 */
data class Charged(
    val amt: Float,
    val interval: Float,
    val protectDps: Float,
    val charge: Pair<Float, Float>,
    val maxLayer: Float? = null,
    val upgrade: Pair<Float, Block>? = null,
) : (Building) -> Emitter {

    override fun invoke(build: Building) = Impl(build)
    inner class Impl(build: Building) : Emitter(build) {
        val chargedEmitterProtectCost get() = protectDps * FloodUtil.dmgPerFlood
        val overFlowOffset get() = chargedEmitterProtectCost * 30 //at least 30s protect
        val maxOverFlow get() = chargedEmitterProtectCost * 180 //at most 180s protect
        var emitting = false
        var dpsOk = false
        var overflow = 0f
        private val timer = Interval(3)

        init {
            build.maxHealth = healthOffset
            build.heal()
            timer.reset(1, 0f)
        }

        override fun update() {
            val (speed, cap) = charge
            if (build.enabled) {
                if (emitting) {
                    if (timer[interval * 60]) {
                        val amt2 = amt * interval / build.block.size / build.block.size
                        build.tile.getLinkedTiles { FloodUtil.creepMap[it] += amt2 }
                    }

                    if (timer[1, cap]) {
                        emitting = false
                        build.tile.getLinkedTiles {
                            if (maxLayer != null && FloodUtil.creepMap[it] > maxLayer) {
                                val delta = (FloodUtil.creepMap[it] - maxLayer).coerceAtLeast(0f)
                                overflow = (overflow + delta).coerceAtMost(maxOverFlow)
                                FloodUtil.creepMap[it] -= delta
                            }
                        }
                    }
                } else {
                    if (timer[1, cap / speed])
                        emitting = true
                }
            }

            val dps = build.maxHealth - build.health
            if (timer[2, 60f]) {
                build.heal()
                dpsOk = dps > protectDps
                val overFlowCost = Mathf.log2(dps / protectDps + 1)
                if (dpsOk) {
                    overflow -= chargedEmitterProtectCost * overFlowCost
                    Call.effect(Fx.healBlock, build.x, build.y, build.block.size + 0f, FloodUtil.creepTeam.color)
                    if (overflow + overFlowOffset < 0) Core.app.post(build::kill)
                }
                label.text = buildString {
                    if (!build.enabled) append("[red]\uE815 已禁用 \uE815[]\n")
                    drawUpgrade(upgrade, overflow.coerceAtLeast(0f))
                    if (emitting && build.enabled) {
                        buildFx(Fx.launch)
                    } else {
                        append("[red]⚠[] - [stat] ${(timer.getTime(1) / (cap / speed) * 100).toInt()}%[]\n")
                    }
                    append(
                        "[stat]DPS[] [%s] %.1f %s %.0f[]\n".format(
                            if (dpsOk) "green" else "red", dps,
                            if (dpsOk) ">" else "<", protectDps
                        )
                    )
                    if (dpsOk) {
                        append(
                            "[yellow]\uE84D[] [stat] %.1fs[] ([stat]-%.1fs[])".format(
                                (overflow + overFlowOffset) / chargedEmitterProtectCost, overFlowCost
                            )
                        )
                    } else append("[stat]\uE809[white] 达到所需DPS")
                }
            }
        }
    }
}