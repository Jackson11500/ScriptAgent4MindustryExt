package xkldklp.inscription.effects

import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.type.ItemStack
import inscription.Effect

class ResourcesEffect(
    name: String,
    desc: String,
    id: Int,

    val items: List<ItemStack>,
    val levelRate: Float = 0f,
    weight: Int = 100
): Effect.BaseEffect(name, desc, id, "资源", weight) {

    override suspend fun active(player: Player, level: Int, rate: Float) {
        items.forEach {
            val core = player.team().core() ?: return@forEach
            if (core.items.has(it.item)) {
                core.items.add(it.item, (it.amount * rate * (1 + level * levelRate)).toInt())
                Call.label("[green]+ ${(it.amount * rate * (1 + level * levelRate)).toInt()}[white]${it.item.emoji()}", 2f, player.x, player.y)
            } else {
                player.sendMessage("[red]核心未拥有[white]${it.item.emoji()}！")
            }
        }
    }
}
