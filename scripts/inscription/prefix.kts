@file:Depends("coreLibrary/DBApi", "数据库服务")

package inscription

import mindustry.gen.Player
import org.jetbrains.exposed.dao.id.EntityID
import kotlin.random.Random

open class BasePrefix(
    var prefix: String,
    var desc: String,
    val id: Int,
    var rate: Float,

    val type: String = "",
    val weight: Int = 100,
) {
    open suspend fun active(player: Player, level: Int, rate: Float = 1f, iid: EntityID<Int>) { }

    open suspend fun pvpActive(player: Player, level: Int, rate: Float = 1f, iid: EntityID<Int>) { active(player, level, rate, iid) }
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

fun getPrefixChance(prefix: BasePrefix): Float {
    val weights = prefixes.values.sumOf { it.weight } + 1
    return prefix.weight / weights.toFloat() * 100f
}