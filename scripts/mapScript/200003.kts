@file:Depends("coreMindustry/menu", "调用菜单")

package mapScript.tags

import coreMindustry.MenuBuilder
import mindustry.Vars.content
import mindustry.Vars.state
import mindustry.content.Blocks
import mindustry.ctype.UnlockableContent
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.type.Category
import mindustry.type.ItemStack
import mindustry.type.UnitType
import mindustry.world.Block
import mindustry.world.blocks.storage.CoreBlock
import kotlin.math.roundToInt

/**@author Lucky Clover
 * WayZer 整理并规范化代码*/
name = "战役模式"
// registerMapTag("@liteCampaign")
modeIntroduce(
    "战役模式", "玩法介绍 \n[acid]Made By [violet]Lucky Clover[]" +
            "\n类似于战役，你的绝大部分建筑和兵种需要使用[cyan]资源[white]解锁\n点击核心进行解锁\n\n[cyan]作图说明[white]\n参考16774简介打上地图标签和设置科技消耗即可\n默认倍率(1倍花费)与原版科技相同"
)
val techCostMul by config.key(100, "科技价格倍率/100")
val techCostMultiplier = (techCostMul / 100f) * state.rules.tags.getFloat("@techCost", 1f)

val allUnlock by autoInit {
    mapOf<String, List<UnlockableContent>>("兵种" to content.units().asIterable().filter { !it.hidden }) +
            content.blocks().asIterable()
                .filter { it.canBeBuilt() && it.environmentBuildable() }
                .groupBy {
                    when (it.category) {
                        Category.crafting -> "加工"
                        Category.defense -> "防御"
                        Category.distribution -> "物流"
                        Category.effect -> "其他"
                        Category.liquid -> "流体"
                        Category.logic -> "逻辑"
                        Category.power -> "电力"
                        Category.production -> "生产"
                        Category.turret -> "炮台"
                        Category.units -> "单位"
                        else -> "x"
                    }
                }
}

onEnable{
    if (!state.rules.tags.containsKey("@liteCampaign")) {
        state.rules.bannedBlocks.clear()
        state.rules.bannedUnits.clear()
        content.blocks().toList().filter { !it.isHidden && it !in state.rules.revealedBlocks }.forEach {
            state.rules.bannedBlocks.add(it)
        }
        content.units().toList().filter { !it.isHidden }.forEach {
            state.rules.bannedUnits.add(it)
        }
        state.rules.bannedBlocks.remove(Blocks.coreBastion)
        state.rules.bannedBlocks.remove(Blocks.coreShard)
    }
    state.rules.hideBannedBlocks = false
    Call.setRules(state.rules)
}

class CoreMenu(private val player: Player) : MenuBuilder<Unit>() {
    private val UnlockableContent.unlocked
        get() = state.rules.let { !it.bannedBlocks.contains(this) && !it.bannedUnits.contains(this) }
    private val ItemStack.coreHas
        get() = state.rules.defaultTeam.core().items[item] >= amount

    @MenuBuilderDsl
    private suspend fun unlockOption(content: UnlockableContent) = lazyOption {
        if (content.unlocked) refreshOption("${content.emoji()}[cyan]已解锁")
        val c = content.techNode?.parent?.content
        if (c != null && !c.unlocked)
            refreshOption("${content.emoji()} \n[gray]前置科技 ${c.emoji()}")

        val cost = content.researchRequirements().map {
            it.copy().apply { amount = (amount * techCostMultiplier).roundToInt() }
        }
        val costName = if (cost.isEmpty()) "[green]0元购[]"
        else cost.joinToString(" ") {
            "${it.item.emoji()}${if (it.coreHas) "[green]" else "[red]"}${it.amount}[]"
        }
        if (cost.any { !it.coreHas }) refreshOption("${content.emoji()} $costName")
        option("${content.emoji()} $costName")

        state.rules.defaultTeam.core().items.remove(cost)
        if (content is UnitType) state.rules.bannedUnits.remove(content)
        else if (content is Block) state.rules.bannedBlocks.remove(content)
        Call.setRules(state.rules)
        broadcast(
            "[acid][科技][] {player.name} [white] 解锁了 {content}".with("player" to player, "content" to content)
        )
        refresh()
    }

    private var category: String? = null
    override suspend fun build() {
        title = "[cyan]科技解锁器"
        allUnlock[category]?.let { list ->
            msg = "当前科技类型：[cyan]${category}"
            var count = 0
            list.forEach {
                if (count > 0 && count % 2 == 0) newRow()
                count += 1
                unlockOption(it)
            }
            newRow()
            option("返回") { category = null;refresh() }
        } ?: let {
            msg = "[cyan]科技类别"
            var count = 0
            allUnlock.forEach { (it, list) ->
                if (count > 0 && count % 2 == 0) newRow()
                count += 1
                val unlocked = list.count { it.unlocked }
                option(
                    (if (unlocked == list.size) "[gold]$it[]" else "[cyan]$it[]") +
                            " ~ ${list.first().emoji()}${list[list.size / 2].emoji()}${list.last().emoji()}...\n" +
                            "${unlocked}/${list.size}"
                ) {
                    category = it
                    refresh()
                }
            }
        }
        option("退出菜单") { }
    }
}

listen<EventType.TapEvent> {
    if (it.tile.build is CoreBlock.CoreBuild && it.tile.team() == it.player.team()) {
        launch(Dispatchers.game) {
            CoreMenu(it.player).sendTo(it.player, 60_000)
        }
    }
}