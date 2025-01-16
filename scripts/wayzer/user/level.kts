@file:Depends("wayzer/user/userService")

package wayzer.user

import cf.wayzer.placehold.DynamicVar
import coreLibrary.lib.config
import coreLibrary.lib.event.RequestPermissionEvent
import coreLibrary.lib.registerVarForType
import mindustry.gen.Groups
import mindustry.gen.Iconc
import mindustry.gen.Player
import org.jetbrains.exposed.sql.transactions.transaction
import wayzer.lib.dao.PlayerData
import wayzer.lib.dao.PlayerProfile
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

val showIcon by config.key(true, "是否显示等级图标")
val userService = contextScript<UserService>()

val number = arrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
val char = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
val char2Iconc = mapOf(
    "I" to Iconc.unitDagger,
    "V" to Iconc.unitMace,
    "X" to Iconc.unitFortress,
    "L" to Iconc.unitScepter,
    "C" to Iconc.unitReign,
    "D" to Iconc.blockSmite,
    "M" to Iconc.blockMalign
)

fun getIcon(level: Int): String {
    if (level <= 0) return Iconc.statusBoss.toString()
    var text = buildString {
        var num = level
        append(" ")
        repeat(13) { i ->
            while (number[i] <= num) {
                num -= number[i]
                append(char[i])
            }
        }
        append(" ")
    }
    char2Iconc.forEach { t, u ->
        text = text.replace(t, u.toString())
    }
    return text
}

fun level(exp: Int) = floor(sqrt(max(exp, 0).toDouble()) / 10).toInt()
fun expByLevel(level: Int) = level * level * 100

registerVarForType<PlayerProfile>().apply {
    registerChild("level", "当前星辉共鸣等级", DynamicVar.obj { level(it.totalExp) })
    registerChild("levelIcon", "当前等级图标", DynamicVar.obj { getIcon(level(it.totalExp)) })
    registerChild("nextLevel", "下一级的要求经验值", DynamicVar.obj { expByLevel(level(it.totalExp) + 1) })
}

/** Should call in [Dispatchers.IO] */
fun updateExp(p: PlayerProfile, desc: String, dot: Int) {
    if (dot != 0) {
        userService.notify(
            p, "[green]经验+{dotExp}{desc}", mapOf(
                "dotExp" to dot.toString(), "desc" to if (desc.isEmpty()) "" else "([cyan]$desc[])"
            )
        )
        transaction {
            p.refresh()
            p.totalExp += dot
        }
        if (level(p.totalExp) != level(p.totalExp - dot)) {
            userService.notify(p, "[gold]一股强大的铭能...你感到自己的星辉共鸣能力提升至{level}级！", mapOf("level" to level(p.totalExp).toString()))
        }
    }
}
export(::updateExp)

registerVarForType<Player>().apply {
    registerChild("prefix.0lvl", "等级图标显示", DynamicVar.obj {
        if (!showIcon) return@obj ""
        "<${getIcon(level(PlayerData[it.uuid()].secureProfile(it)?.totalExp ?: 0))}>"
    })
}

listenTo<RequestPermissionEvent> {
    val profile = when (val p = subject) {
        is PlayerProfile -> p
        is Player -> PlayerData[p.uuid()].secureProfile(p) ?: return@listenTo
        else -> return@listenTo
    }
    val index = group.indexOfLast { !it.startsWith("@") }
    val newGroup = group.toMutableList()
    newGroup.addAll(index + 1, (level(profile.totalExp) downTo 0).map { "@lvl$it" })
    group = newGroup
}