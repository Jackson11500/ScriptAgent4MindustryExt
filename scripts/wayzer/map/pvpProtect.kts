package wayzer.map

import arc.func.Cons
import arc.math.Mathf
import arc.util.Time
import arc.util.Tmp
import coreLibrary.lib.config
import coreLibrary.lib.util.loop
import coreLibrary.lib.with
import coreMindustry.lib.broadcast
import coreMindustry.lib.game
import coreMindustry.lib.listen
import coreMindustry.lib.sendMessage
import mindustry.Vars.state
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.content.StatusEffects
import mindustry.game.EventType
import mindustry.game.Gamemode
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Unit
import mindustry.world.blocks.defense.BaseShield
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import java.time.Duration
import kotlin.math.ceil

val time by config.key(600, "pvp保护时间(单位秒,小于等于0关闭)")

val Unit.inEnemyArea: Boolean
    get() {
        val closestCore = state.teams.active
            .mapNotNull { it.cores.minByOrNull(this::dst2) }
            .minByOrNull(this::dst2) ?: return false
        return closestCore.team != team() && (state.rules.polygonCoreProtection || dst(closestCore) < state.rules.enemyCoreBuildRadius)
    }


listen<EventType.PlayEvent> {
    launch {
        var leftTime = state.rules.tags.getInt("@pvpProtect", time)
        if (state.rules.mode() != Gamemode.pvp || time <= 0) return@launch
        loop(Dispatchers.game) {
            delay(1000)
            Groups.unit.forEach {
                if (it.inEnemyArea) {
                    it.health -= (it.health / 2).coerceAtLeast(it.maxHealth / 2)
                    if (it.health <= 0) {
                        it.health -= 999999
                        return@forEach
                    }
                    it.set(it.closestCore())
                    it.snapInterpolation()
                    it.apply(StatusEffects.unmoving, 2 * 60f)
                    if (it.player != null) {
                        Call.setPosition(it.player.con, it.closestCore().x, it.closestCore().y)
                        it.player.sendMessage("[red]PVP保护时间,禁止进入敌方区域".with())
                    }
                }
            }
        }
        broadcast(
            "[yellow]PVP保护时间,禁止在其他基地攻击(持续{time:分钟})".with("time" to Duration.ofSeconds(leftTime.toLong())),
            quite = true
        )
        repeat(leftTime / 60) {
            delay(60_000)
            leftTime -= 60
            broadcast("[yellow]PVP保护时间还剩 {time}分钟".with("time" to ceil(leftTime / 60f)), quite = true)
        }
        delay(leftTime * 1000L)
        broadcast("[yellow]PVP保护时间已结束, 全力进攻吧".with())
        cancel()
    }
}

listen<EventType.ResetEvent> {
    coroutineContext[Job]?.cancelChildren()
}

