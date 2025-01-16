package inscription

import mindustry.gen.Player
import kotlin.random.Random

open class BasePrefix(
    var prefix: String,
    var desc: String,
    val id: Int,
    var rate: Float,

    val type: String = "",
    val weight: Int = 100,
) {
    open suspend fun active(player: Player, level: Int, rate: Float = 1f) { }

    open suspend fun pvpActive(player: Player, level: Int, rate: Float = 1f) { active(player, level, rate) }
}

val prefixes = mutableMapOf<Int, BasePrefix>()

fun randomPrefix(): BasePrefix {
    val leftPrefixes = prefixes.values.toMutableList()
    var num = Random.nextInt(prefixes.values.sumOf { it.weight }) + 1
    while (num > leftPrefixes.first().weight) {
        num -= leftPrefixes.first().weight
        leftPrefixes.removeFirst()
    }
    return leftPrefixes.first()
}