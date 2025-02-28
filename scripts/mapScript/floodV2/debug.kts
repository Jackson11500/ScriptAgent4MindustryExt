package mapScript.floodV2

import kotlin.system.measureTimeMillis

var enable = false

listen<EventType.TapEvent> {
    if (!FloodUtil.enable || !enable) return@listen
    val tile = it.tile ?: return@listen
    val text = """
        [orange]总[white]：${FloodUtil.creepMap[tile] + FloodUtil.terrainMap[tile]}
        [orange]深[white]：${FloodUtil.creepMap[tile]}
        [orange]高[white]：${FloodUtil.terrainMap[tile]}
        [orange]阻[white]：${FloodUtil.hinderMap[tile] + FloodUtil.dynamicHinderMap[tile]}""".trimIndent()
    Call.label(text, 2f, tile.getX(), tile.getY())
}

command("floodDebug", "获取洪水模式信息") {
    usage = "[toggle]"
    body {
        if (arg.getOrNull(0) == "testShuffle") {
            logger.info("tile count " + world.tiles.run { width * height })
            logger.info("costs " + measureTimeMillis {
                world.tiles.shuffled()
            })
        }
        if (arg.getOrNull(0) == "toggle")
            enable = !enable
        reply("{list:\n}".with("list" to FloodUtil.statistics.map { "${it.key} ${it.value}" }))
    }
}