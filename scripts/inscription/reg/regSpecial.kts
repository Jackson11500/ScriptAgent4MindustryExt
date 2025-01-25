@file:Depends("inscription/special")

package inscription.reg

import arc.util.Log
import coreMindustry.lib.command
import coreMindustry.lib.listen
import inscription.Special
import mindustry.game.EventType

fun build() {
    val special = buildList {
        add(Special.SpecialInscription(
                "律令",
                "畸变",
                "[lightgray]始源第十二铭，拥有创造与改变规则的力量\n[yellow]解锁改变地图规则的投票",
                "[lightgray]终焉第十二铭，产生混乱，打破一切\n[yellow]解锁生成单位的投票",
                12
        ))
    }

    val target = contextScript<Special>().specials
    target.clear()
    special.forEach {
        target[it.id] = it
    }
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