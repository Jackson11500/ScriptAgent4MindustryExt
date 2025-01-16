package main

import arc.graphics.Colors
import coreLibrary.lib.with
import coreMindustry.lib.broadcast
import coreMindustry.lib.command
import coreMindustry.lib.game
import coreMindustry.lib.player
import mindustry.Vars
import mindustry.game.Schematic
import mindustry.game.Schematics
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.world.blocks.logic.MessageBlock.MessageBuild

command("showColor", "显示所有颜色", {}) {
    reply(Colors.getColors().joinToString("[],") { "[#${it.value}]${it.key}" }.with())
}

command("js", "执行脚本") {
    permission = "main.js"
    body {
        if (arg.isEmpty()) {
            reply("?".with())
        } else {
            launch(Dispatchers.game) {
                val outPut = Vars.mods.scripts.runConsole(buildString { arg.forEach { append("$it ") } })
                reply("> {output}".with("output" to outPut))
                broadcast("[red]管理员[white] {player} [red]执行了一条js命令\n[white]> {output}".with("player" to (player ?: "[red]SERVER"), "output" to arg.joinToString(" ")))
            }
        }

    }
}
command("jsQuiet", "执行脚本(当涉及隐私时)") {
    permission = "main.js"
    body {
        if (arg.isEmpty()) {
            reply("?".with())
        } else {
            launch(Dispatchers.game) {
                val outPut = Vars.mods.scripts.runConsole(buildString { arg.forEach { append("$it ") } })
                reply("> {output}".with("output" to outPut))
                broadcast("[red]管理员[white] {player} [red]执行了一条js命令(涉及隐私，不公开)".with("player" to (player ?: "[red]SERVER")))
            }
        }
    }
}
fun isError(output: String): Boolean {
    return try {
        val errorName = output.substring(0, output.indexOf(' ') - 1)
        Class.forName("org.mozilla.javascript.$errorName")
        true
    } catch (e: Throwable) {
        false
    }
}

fun Building.readText(): String? {
    if (this !is MessageBuild) return null
    return config()
}

fun schematicsPlaceNet(schem: Schematic,x: Int,y: Int,team: Team) {
    val ox = x - schem.width / 2
    val oy = y - schem.height/2;
    schem.tiles.forEach { st ->
        val tile = Vars.world.tile (st.x + ox, st.y + oy) ?: return

        tile.setNet(st.block, team, st.rotation.toInt())

        val config = st.config
        if (tile.build != null) {
            tile.build.configureAny(config)
        }
    }
}

command("placeSchematic", "放置蓝图(单位脚下信息版,可向下读取)") {
    permission = "main.placeSchematic"
    body {
        val player = player!!
        if (player.dead()) returnReply("[red]你已死亡".with())
        if (player.unit().buildOn()?.readText() == null) returnReply("[red]你脚下是什么东西?".with())

        val base64 = buildString {
            var build = player.unit().buildOn()
            while (true) {
                append(build.readText())
                if (Vars.world.tile(build.tileX(), build.tileY() - 1)?.build?.readText() != null) {
                    build = Vars.world.tile(build.tileX(), build.tileY() - 1).build
                } else {
                    break
                }
            }
        }
        schematicsPlaceNet(Schematics.readBase64(base64), player.tileX(), player.tileY(), player.team())
    }
}

command("placeSchematicFile", "放置蓝图(文件读取)") {
    permission = "main.placeSchematic"
    body {
        val player = player!!
        if (player.dead()) returnReply("[red]你已死亡".with())

        val base64 = Vars.dataDirectory.child("scripts").child("main").child("schematic.txt").file().readText()
        schematicsPlaceNet(Schematics.readBase64(base64), player.tileX(), player.tileY(), player.team())
    }
}

