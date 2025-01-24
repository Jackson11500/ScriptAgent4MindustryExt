@file:Depends("coreLibrary/DBApi", "数据库服务")

package inscription

import mindustry.gen.Player
import org.jetbrains.exposed.dao.id.EntityID
import kotlin.random.Random

open class BaseEffect(
    val name: String,
    val desc: String,
    val id: Int,

    val type: String = "",
    val weight: Int = 100,
) {
    open suspend fun active(player: Player, level: Int, rate: Float = 1f,iid: EntityID<Int>) { }

    open suspend fun pvpActive(player: Player, level: Int, rate: Float = 1f, iid: EntityID<Int>) { active(player, level, rate, iid) }
}

val effects = mutableMapOf<Int, BaseEffect>()

fun randomEffect(): BaseEffect {
    val leftEffects = effects.values.toMutableList()
    var num = Random.nextInt(effects.values.sumOf { it.weight }) + 1
    while (num > leftEffects.first().weight) {
        num -= leftEffects.first().weight
        leftEffects.removeFirst()
    }
    return leftEffects.first()
}

fun getEffectChance(effect: BaseEffect): Float {
    val weights = effects.values.sumOf { it.weight } + 1
    return effect.weight / weights.toFloat() * 100f
}