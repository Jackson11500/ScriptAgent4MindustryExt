package mapScript

import arc.util.io.ReusableByteOutStream
import arc.util.io.Writes
import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import mapScript.lib.modeIntroduce
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.type.Item
import mindustry.type.Liquid
import mindustry.world.blocks.defense.BuildTurret
import mindustry.world.blocks.defense.BuildTurret.BuildTurretBuild
import mindustry.world.blocks.defense.ForceProjector.ForceBuild
import mindustry.world.blocks.defense.MendProjector.MendBuild
import mindustry.world.blocks.defense.OverdriveProjector.OverdriveBuild
import mindustry.world.blocks.defense.RegenProjector
import mindustry.world.blocks.defense.RegenProjector.RegenProjectorBuild
import mindustry.world.blocks.liquid.LiquidRouter.LiquidRouterBuild
import mindustry.world.blocks.payloads.Constructor.ConstructorBuild
import mindustry.world.blocks.payloads.PayloadDeconstructor.PayloadDeconstructorBuild
import mindustry.world.blocks.power.ConsumeGenerator
import mindustry.world.blocks.power.ConsumeGenerator.ConsumeGeneratorBuild
import mindustry.world.blocks.power.ImpactReactor.ImpactReactorBuild
import mindustry.world.blocks.power.NuclearReactor.NuclearReactorBuild
import mindustry.world.blocks.power.PowerGenerator.GeneratorBuild
import mindustry.world.blocks.power.VariableReactor.VariableReactorBuild
import mindustry.world.blocks.production.BeamDrill.BeamDrillBuild
import mindustry.world.blocks.production.Drill.DrillBuild
import mindustry.world.blocks.production.Fracker.FrackerBuild
import mindustry.world.blocks.production.GenericCrafter
import mindustry.world.blocks.production.GenericCrafter.GenericCrafterBuild
import mindustry.world.blocks.production.Pump
import mindustry.world.blocks.production.Pump.PumpBuild
import mindustry.world.blocks.production.Separator
import mindustry.world.blocks.production.Separator.SeparatorBuild
import mindustry.world.blocks.production.WallCrafter.WallCrafterBuild
import mindustry.world.blocks.units.Reconstructor.ReconstructorBuild
import mindustry.world.blocks.units.UnitAssembler.UnitAssemblerBuild
import mindustry.world.blocks.units.UnitFactory
import mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild
import mindustry.world.consumers.*
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.ceil


/**@author xkldklp */
name = "简单世界"

modeIntroduce(
    "简单世界", buildString {
        appendLine("[cyan]启用简单模式!")
        append("[yellow]大部分工厂都会自动补充/送出材料")
    }
)

fun Building.trans2Core(item: Item) {
    val core = team().core() ?: return
    val amount = items[item]
    val rightAmount = amount.coerceAtMost(core.storageCapacity - core.items[item]).coerceAtLeast(0)
    if (rightAmount == 0) return
    core.items.add(item, rightAmount)
    items[item] = amount - rightAmount
    packetBuildings.add(this)
}

fun Building.transFromCore(item: Item, targetAmount: Int, coreLeast: Int = targetAmount * 2) {
    val core = team().core() ?: return
    val amountC = core.items[item]
    if (items == null) return
    val amountB = items[item]
    val rightAmount = (amountC - coreLeast).coerceAtMost(targetAmount - amountB).coerceAtLeast(0)
    if (rightAmount == 0) return
    core.items.remove(item, rightAmount)
    items[item] = rightAmount + amountB
    packetBuildings.add(this)
}

fun Building.transFromLiquidTank() {
    val conL = block.consumers.filterIsInstance<ConsumeLiquid>()
    val conLs = block.consumers.filterIsInstance<ConsumeLiquids>()
    if (conL.isEmpty() && conLs.isEmpty()) return
    conLs.forEach {
        it.liquids.forEach a@{ con ->
            val l = con.liquid
            val t = liquidTankMax(team, l) ?: return@a
            val max = block.liquidCapacity - liquids[l]
            val a = minOf(max, t.liquids[l] - 0.5f)
            if (a <= 0.5f) return@a
            t.liquids.remove(l, a)
            liquids.add(l, a)
            packetBuildings.add(this)
            packetBuildings.add(t)
        }
    }
    conL.forEach a@{ con ->
        val l = con.liquid
        val t = liquidTankMax(team, l) ?: return@a
        val max = block.liquidCapacity - liquids[l]
        val a = minOf(max, t.liquids[l] - 0.5f)
        if (a <= 0.5f) return@a
        t.liquids.remove(l, a)
        liquids.add(l, a)
        packetBuildings.add(this)
        packetBuildings.add(t)
    }
}

fun Building.getOutPutLiquid(): Set<Liquid> {
    val build = this
    fun MutableSet<Liquid>.addOrNull(liquid: Liquid?) {
        if (liquid != null)
            add(liquid)
    }
    return buildSet {
        val b = block
        when(b) {
            is ConsumeGenerator -> {
                addOrNull(b.outputLiquid?.liquid)
            }
            is GenericCrafter -> {
                addOrNull(b.outputLiquid?.liquid)
                b.outputLiquids?.forEach {
                    addOrNull(it.liquid)
                }
            }
            is Pump -> {
                addOrNull((build as PumpBuild).liquidDrop)
            }
        }
    }
}

fun Building.trans2LiquidTank() {
    val b = block
    getOutPutLiquid().forEach { l ->
        val t = liquidTankMin(team, l) ?: return
        val max = t.block.liquidCapacity - t.liquids.currentAmount()
        if (max <= 0.01f) return
        val a = minOf(max, liquids[l])
        t.liquids.add(l, a)
        liquids.remove(l, a)
        packetBuildings.add(this)
        packetBuildings.add(t)
    }
}

fun liquidTankMax(team: Team, liquid: Liquid): LiquidRouterBuild? {
    val tanks = tankBuildings.filter {
        it.team == team && ((it as? LiquidRouterBuild)?.liquids?.get(liquid) ?: 0f) >= 0.5f
    }
    return tanks.maxByOrNull { (it as LiquidRouterBuild).liquids[liquid] / it.block.liquidCapacity } as? LiquidRouterBuild
}
fun liquidTankMin(team: Team, liquid: Liquid): LiquidRouterBuild? {
    val tanks = tankBuildings.filter {
        it.team == team && ((it as? LiquidRouterBuild)?.liquids?.get(liquid) ?: 0f) >= 0.001f
    }
    return tanks.minByOrNull { (it as LiquidRouterBuild).liquids[liquid] / it.block.liquidCapacity } as? LiquidRouterBuild
}

fun GeneratorBuild.efficiencyMultiplier(item: Item): Float {
    var efficiency = 1f
    if (block.consumers.any { it is ConsumeItemExplosive }) {
        efficiency *= item.explosiveness
    }
    if (block.consumers.any { it is ConsumeItemFlammable }) {
        efficiency *= item.flammability
    }
    if (block.consumers.any { it is ConsumeItemRadioactive }) {
        efficiency *= item.radioactivity
    }
    return efficiency
}

val packetBuildings by autoInit { mutableSetOf<Building>() }

val liquidTanks = listOf(
    Blocks.liquidTank,
    Blocks.liquidContainer,
    Blocks.reinforcedLiquidContainer,
    Blocks.reinforcedLiquidTank
)

var tankBuildings = setOf<Building>()

onEnable {
    loop(Dispatchers.game) {
        packetBuildings.clear()
        tankBuildings = Groups.build.filter { it.block in liquidTanks }.toSet()
        Groups.build.forEach {
            if (it.team().core() != null) {
                if (it is PumpBuild) {
                    it.transFromLiquidTank()
                    it.trans2LiquidTank()
                }
                if (it is DrillBuild) {
                    it.transFromLiquidTank()
                    it.items.each { item, amount ->
                        it.trans2Core(item)
                    }
                }
                if (it is WallCrafterBuild) {
                    it.transFromLiquidTank()
                    it.items.each { item, amount ->
                        it.trans2Core(item)
                    }
                }
                if (it is BeamDrillBuild) {
                    it.transFromLiquidTank()
                    it.items.each { item, amount ->
                        it.trans2Core(item)
                    }
                }
                if (it is OverdriveBuild) {
                    it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, i.amount * 5)
                        }
                    }
                }
                if (it is ConsumeGeneratorBuild) {
                    it.transFromLiquidTank()
                    val items = buildList {
                        it.team.core().items.each { item, amount ->
                            if (amount > 5) {
                                if ((it.block as ConsumeGenerator).filterItem?.filter?.get(item) == true)
                                    add(item)
                            }
                        }
                    }
                    if (items.isNotEmpty()) {
                        it.transFromCore(items.maxBy { i -> it.efficiencyMultiplier(i) }, 4)
                    }
                    it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, 5)
                        }
                    }
                    it.trans2LiquidTank()
                }
                if (it is NuclearReactorBuild) {
                    it.transFromLiquidTank()
                    if (it.liquids.currentAmount() >= 10f) {
                        it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                            (c as ConsumeItems).items.forEach { i ->
                                it.transFromCore(i.item, it.getMaximumAccepted(i.item))
                            }
                        }
                    } else {
                        it.items.each { item, amount ->
                            it.trans2Core(item)
                        }
                    }
                }
                if (it is ImpactReactorBuild) {
                    it.transFromLiquidTank()
                    it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                                it.transFromCore(i.item, it.getMaximumAccepted(i.item))
                        }
                    }
                }
                if (it is FrackerBuild) {
                    it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, i.amount * 5)
                        }
                    }
                }
                if (it is SeparatorBuild) {
                    it.transFromLiquidTank()
                    it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, it.getMaximumAccepted(i.item))
                        }
                    }
                    (it.block as Separator).results.forEach { i ->
                        it.trans2Core(i.item)
                    }
                }
                if (it is GenericCrafterBuild) {
                    it.transFromLiquidTank()
                    (it.block as GenericCrafter).outputItems?.forEach { i ->
                        it.trans2Core(i.item)
                    }
                    (it.block as GenericCrafter).consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, i.amount * 5)
                        }
                    }
                    it.trans2LiquidTank()
                }
                if (it is ConstructorBuild) {
                    if (it.recipe?.requirements != null) {
                        it.recipe.requirements.forEach { i ->
                            it.transFromCore(i.item, ceil(i.amount * Vars.state.rules.buildCostMultiplier).toInt() * 2 )
                        }
                        it.items.each { item, amount ->
                            if (it.recipe.requirements.toList().filter { ceil(it.amount * Vars.state.rules.buildCostMultiplier).toInt() * 2 > 0 }.all { it.item != item })
                                it.trans2Core(item)
                        }
                    } else {
                        it.items.each { item, amount ->
                            it.trans2Core(item)
                        }
                    }
                }
                if (it is PayloadDeconstructorBuild) {
                    it.items.each { item, amount ->
                        it.trans2Core(item)
                    }
                }
                if (it is UnitFactoryBuild) {
                    it.transFromLiquidTank()
                    if (it.currentPlan >= 0 && it.currentPlan <= (it.block as UnitFactory).plans.size) {
                        val plan = (it.block as UnitFactory).plans[it.currentPlan]
                        plan.requirements.forEach { i ->
                            it.transFromCore(i.item, i.amount)
                        }
                        it.items.each { item, amount ->
                            if (plan.requirements.all { it.item != item })
                                it.trans2Core(item)
                        }
                    } else {
                        it.items.each { item, amount ->
                            it.trans2Core(item)
                        }
                    }
                }
                if (it is UnitAssemblerBuild) {
                    it.transFromLiquidTank()
                }
                if (it is ReconstructorBuild) {
                    it.transFromLiquidTank()
                    it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, i.amount)
                        }
                    }
                }
                if (it is MendBuild) {
                    it.transFromLiquidTank()
                    it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, i.amount * 5)
                        }
                    }
                }
                if (it is RegenProjectorBuild) {
                    it.transFromLiquidTank()
                    it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, i.amount * 5)
                        }
                    }
                }
                if (it is BuildTurretBuild) {
                    it.transFromLiquidTank()
                }
                if (it is VariableReactorBuild) {
                    it.transFromLiquidTank()
                    it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, i.amount * 5)
                        }
                    }
                }
            }
        }
        writeBlockSnapshots(packetBuildings.toList())
        delay(200)
    }
}

/** Stream for writing player sync data to.  */
private val syncStream = ReusableByteOutStream()

/** Data stream for writing player sync data to.  */
private val dataStream = DataOutputStream(syncStream)

@Throws(IOException::class)
fun writeBlockSnapshots(builds: List<Building>) {
    syncStream.reset()
    var sent: Short = 0
    for (entity in builds) {
        //if (entity as? Syncc == null) continue
        sent++
        dataStream.writeInt(entity.pos())
        dataStream.writeShort(entity.block.id.toInt())
        entity.writeAll(Writes.get(dataStream))
        if (syncStream.size() > 800) {
            dataStream.close()
            Call.blockSnapshot(sent, syncStream.toByteArray())
            sent = 0
            syncStream.reset()
        }
    }
    if (sent > 0) {
        dataStream.close()
        Call.blockSnapshot(sent, syncStream.toByteArray())
    }
}