package inscription

import arc.util.Log
import mindustry.gen.Player
import kotlin.random.Random

open class BaseEffect(
    val name: String,
    val desc: String,
    val id: Int,

    val type: String = "",
    val weight: Int = 100,
) {
    open suspend fun active(player: Player, level: Int, rate: Float = 1f) { }

    open suspend fun pvpActive(player: Player, level: Int, rate: Float = 1f) { active(player, level, rate) }
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