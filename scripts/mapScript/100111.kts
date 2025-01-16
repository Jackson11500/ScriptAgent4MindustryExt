package mapScript

import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import coreMindustry.lib.listen
import mapScript.lib.modeIntroduce
import mindustry.Vars
import mindustry.content.Fx
import mindustry.content.Liquids
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Iconc
import mindustry.type.Item
import mindustry.world.blocks.defense.OverdriveProjector
import mindustry.world.blocks.defense.OverdriveProjector.OverdriveBuild
import mindustry.world.blocks.distribution.DirectionalUnloader.DirectionalUnloaderBuild
import mindustry.world.blocks.distribution.Duct.DuctBuild
import mindustry.world.blocks.payloads.Constructor.ConstructorBuild
import mindustry.world.blocks.payloads.PayloadDeconstructor.PayloadDeconstructorBuild
import mindustry.world.blocks.power.ConsumeGenerator
import mindustry.world.blocks.power.ConsumeGenerator.ConsumeGeneratorBuild
import mindustry.world.blocks.power.ImpactReactor.ImpactReactorBuild
import mindustry.world.blocks.power.NuclearReactor.NuclearReactorBuild
import mindustry.world.blocks.power.PowerGenerator
import mindustry.world.blocks.power.PowerGenerator.GeneratorBuild
import mindustry.world.blocks.production.BeamDrill.BeamDrillBuild
import mindustry.world.blocks.production.Drill.DrillBuild
import mindustry.world.blocks.production.Fracker.FrackerBuild
import mindustry.world.blocks.production.GenericCrafter
import mindustry.world.blocks.production.GenericCrafter.GenericCrafterBuild
import mindustry.world.blocks.production.Separator
import mindustry.world.blocks.production.Separator.SeparatorBuild
import mindustry.world.blocks.production.WallCrafter.WallCrafterBuild
import mindustry.world.blocks.units.Reconstructor.ReconstructorBuild
import mindustry.world.blocks.units.UnitFactory
import mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild
import mindustry.world.consumers.ConsumeItemExplosive
import mindustry.world.consumers.ConsumeItemFilter
import mindustry.world.consumers.ConsumeItemFlammable
import mindustry.world.consumers.ConsumeItemRadioactive
import mindustry.world.consumers.ConsumeItems
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
    Call.setItem(this, item, amount - rightAmount)
}

fun Building.transFromCore(item: Item, targetAmount: Int, coreLeast: Int = targetAmount * 2) {
    val core = team().core() ?: return
    val amountC = core.items[item]
    if (items == null) return
    val amountB = items[item]
    val rightAmount = (amountC - coreLeast).coerceAtMost(targetAmount - amountB).coerceAtLeast(0)
    if (rightAmount == 0) return
    core.items.remove(item, rightAmount)
    Call.setItem(this, item, rightAmount + amountB)
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

onEnable {
    loop(Dispatchers.game) {
        Groups.build.forEach {
            if (it.team().core() != null) {
                if (it is DrillBuild) {
                    it.items.each { item, amount ->
                        it.trans2Core(item)
                    }
                }
                if (it is WallCrafterBuild) {
                    it.items.each { item, amount ->
                        it.trans2Core(item)
                    }
                }
                if (it is BeamDrillBuild) {
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
                }
                if (it is NuclearReactorBuild) {
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
                    (it.block as GenericCrafter).outputItems?.forEach { i ->
                        it.trans2Core(i.item)
                    }
                    (it.block as GenericCrafter).consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, i.amount * 5)
                        }
                    }
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
                if (it is ReconstructorBuild) {
                    it.block.consumers?.filter { it is ConsumeItems }?.forEach { c ->
                        (c as ConsumeItems).items.forEach { i ->
                            it.transFromCore(i.item, i.amount)
                        }
                    }
                }
            }
        }
        delay(500)
    }
}