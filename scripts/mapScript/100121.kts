import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import mapScript.lib.modeIntroduce
import mindustry.content.StatusEffects
import mindustry.gen.Groups

name = "加速世界"
modeIntroduce(
    "加速世界", buildString {
        appendLine("[cyan]WRYYYYYYY")
        append("[yellow]单位获得加速")
    }
)

onEnable {
    loop(Dispatchers.game) {
        Groups.unit.forEach {
            it.apply(StatusEffects.fast, Float.POSITIVE_INFINITY)
        }
        yield()
    }
}