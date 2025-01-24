package inscription.reg

import arc.util.Log
import coreMindustry.lib.command
import coreMindustry.lib.listen
import mindustry.game.EventType

fun build() {

}

var builded = false
listen(EventType.Trigger.update) {
    if (!builded) {
        builded = true
        build()
        Log.info("[green]special生成完毕")
    }
}

command("buildPrefix", "构建special星铭") {
    permission = id.replace("/", ".")
    body {
        build()
    }
}