@file:Import("@coreMindustry/util/tools.kt", sourceFile = true)

package main

import coreLibrary.lib.with
import coreMindustry.lib.command
import coreMindustry.lib.player
import coreMindustry.util.polyBuild
import mindustry.Vars
import mindustry.Vars.content
import mindustry.content.StatusEffects
import mindustry.ctype.ContentType
import mindustry.entities.units.StatusEntry
import mindustry.type.StatusEffect


command("imFire", "爷是火!!") {
    permission = "fun"
    body {
        val player = player!!
        if (player.dead()) returnReply("[red]你已死亡".with())

        repeat(10) {
            player.unit().statuses.add(StatusEntry().set(StatusEffects.burning, Float.POSITIVE_INFINITY))
        }
        repeat(6) {
            player.unit().statuses.add(StatusEntry().set(StatusEffects.overdrive, Float.POSITIVE_INFINITY))
        }
        player.unit().spawnedByCore = true
    }
}
command("suicide", "自杀") {
    body {
        val player = player!!
        if (player.dead()) returnReply("[red]你已死亡".with())

        player.unit().kill()
    }
}

command("infApply", "应用效果") {
    permission = "fun"
    usage = "[id=列出] [秒=INF]"
    body {
        val player = player!!
        if (player.dead()) returnReply("[red]你已死亡".with())

        val list = content.getBy<StatusEffect>(ContentType.status)
        val type = arg.getOrNull(0)?.toIntOrNull()?.let { list.get(it) } ?: returnReply(
            "[red]请输入类型ID: {list}"
                .with("list" to list.mapIndexed { i, type -> "[yellow]$i[green]($type)" }.joinToString())
        )
        player.unit().statuses.add(StatusEntry().set(type, (arg.getOrNull(1)?.toFloatOrNull() ?: Float.POSITIVE_INFINITY) * 60f))
    }
}

command("fly", "飞") {
    permission = "fun"
    body {
        val player = player!!
        if (player.dead()) returnReply("[red]你已死亡".with())

        player.unit().elevation = 1f
    }
}

command("polyBuild", "poly!") {
    permission = "fun"
    usage = "[amount=1]"
    body {
        val player = player!!
        if (player.dead()) returnReply("[red]你已死亡".with())

        val base64 = Vars.dataDirectory.child("scripts").child("main").child("schematic.txt").file().readText()
        polyBuild(player.x, player.y, base64, player.team(), arg.getOrNull(0)?.toIntOrNull() ?: 1)
    }
}
