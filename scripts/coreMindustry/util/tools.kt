package coreMindustry.util

import arc.graphics.Colors
import arc.struct.Queue
import kotlinx.coroutines.delay
import mindustry.content.UnitTypes
import mindustry.entities.units.BuildPlan
import mindustry.game.Schematics
import mindustry.game.Team
import mindustry.gen.WorldLabel

fun randomColor(): String{
    return "[${Colors.getColors().keys().toList().random()}]"
}

fun polyBuild(x: Float, y: Float, schematic: String, team: Team, amount: Int = 1) {
    val msch = Schematics.readBase64(schematic)
    val uplans = mutableListOf<BuildPlan>()

    msch.tiles.forEach {
        if (it.block.canBeBuilt()) {
            uplans.add(BuildPlan().apply {
                set((x / 8 + it.x).toInt(), (y / 8 + it.y).toInt(), it.rotation.toInt(), it.block)
                block = it.block
                config = it.config
            })
        }
    }

    val queue = Queue<BuildPlan>(uplans.size).apply {
        uplans.sortBy { it.dst(x, y) }
        uplans.forEach {
            add(it)
        }
    }

    repeat(amount) {
        UnitTypes.poly.create(team).apply {
            set(x, y)
            add()
            plans = queue
        }
    }
}

val number = arrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
val char = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")

fun getRomanNum(level: Int): String {
    if (level <= 0) return ""
    return buildString {
        var num = level
        repeat(13) { i ->
            while (number[i] <= num) {
                num -= number[i]
                append(char[i])
            }
        }
    }
}

fun Float.format(i: Int = 2): String {
    return "%.${i}f".format(this)
}

fun Float.buildLineBar(length: Int = 20, max: Float = 20f, color: Pair<Pair<String, String>, String> = Pair(Pair("[yellow]","[green]"), "[red]")): String {
    val num = this
    return buildString {
        repeat(length) {
            append("${
                when {
                    num > it * (max / length) + max -> color.first.first
                    num > it * (max / length) -> color.first.second
                    else -> color.second
                }
            }|")
        }
    }
}

fun Int.buildLineBar(length: Int = 20, max: Int = 20, color: Pair<Pair<String, String>, String> = Pair(Pair("[yellow]","[green]"), "[red]")): String {
    return toFloat().buildLineBar(length, max.toFloat(), color)
}

suspend fun worldLabelMessage(x: Float, y: Float, msg: String, time: Long, color: String = "") {
    val label = WorldLabel.create().apply {
        set(x, y)
        fontSize = 6f
        flags = 2
        text = buildString {
            append(color)
        }
        snapInterpolation()
    }
    label.add()
    val perCharTime = time / msg.length
    msg.forEach {
        label.text += it
        delay(perCharTime)
    }
    delay(time)
    label.hide()
}