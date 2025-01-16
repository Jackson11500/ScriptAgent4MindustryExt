@file:Depends("wayzer/user/userService")
@file:Depends("coreMindustry/menu", "调用菜单")
@file:Depends("inscription/effect")
@file:Depends("inscription/prefix")
@file:Depends("wayzer/maps", "获取地图信息")

package xkldklp.user

import coreLibrary.DBApi.DB.registerTable
import coreLibrary.lib.with
import coreMindustry.MenuBuilder
import coreMindustry.lib.*
import inscription.Effect
import inscription.Prefix
import mindustry.Vars
import mindustry.game.EventType
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import wayzer.MapManager
import wayzer.lib.dao.PlayerData
import wayzer.lib.dao.PlayerProfile
import wayzer.user.UserService
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

val userService = contextScript<UserService>()
val effects = contextScript<Effect>().effects
val prefixs = contextScript<Prefix>().prefixes

registerTable(InscriptionEntity.T)

/** Should call in [Dispatchers.IO] */
fun createNewInscription(profile: PlayerProfile, effect: Effect.BaseEffect, prefix: Prefix.BasePrefix): InscriptionEntity {
    return transaction {
        InscriptionEntity.new(profile.id, effect.id, prefix.id)
    }
}
export(::createNewInscription)

command("inscription", "管理指令: 铭刻") {
    usage = "<account> <effectID> <prefixID>"
    permission = "xkldklp.user.newInscription"
    body {
        if (arg.size < 3) replyUsage()
        val profile = arg[0].toLongOrNull()?.let {
            PlayerProfile.findByAccount(it)
        } ?: returnReply("[red]找不到该用户".with())
        val effect = effects.getOrDefault(arg[1].toInt(), null) ?: returnReply("[red]未知effectID".with())
        val prefix = prefixs.getOrDefault(arg[2].toInt(), null) ?: returnReply("[red]未知prefixID".with())
        withContext(Dispatchers.IO) {
            createNewInscription(profile, effect, prefix)
        }
        reply("[green]添加成功".with())
    }
}
command("cd", "管理指令: 刷新铭能使用状态") {
    permission = "xkldklp.user.cooldownInscription"
    body {
        used.clear()
        reply("[green]刷新成功".with())
    }
}

fun effect(inscriptionEntity: InscriptionEntity): Effect.BaseEffect? {
    return effects[inscriptionEntity.effectId]
}
fun prefix(inscriptionEntity: InscriptionEntity): Prefix.BasePrefix? {
    return prefixs[inscriptionEntity.prefixId]
}
fun name(inscriptionEntity: InscriptionEntity): String {
    return "[white]${prefix(inscriptionEntity)?.prefix}[yellow] 的 [white]${effect(inscriptionEntity)?.name}"
}

val used = mutableMapOf<EntityID<Int>, Boolean>()
listen<EventType.ResetEvent> { used.clear() }

fun level(exp: Int) = floor(sqrt(max(exp, 0).toDouble()) / 10).toInt()

command("inscriptionMenu", "释放铭能") {
    aliases = listOf("skill")
    type = CommandType.Client
    body {
        val profile = PlayerData[player!!.uuid()].profile ?: returnReply("[red]未绑定账号！".with())
        if ((Vars.state.rules.pvp && !Vars.state.rules.tags.getBool("@enableSkills")) || Vars.state.rules.tags.getBool("@disableSkills")) returnReply("[red]模式禁用,若需启用需在描述添加[@enableSkills]".with())
        if (player!!.dead()) returnReply("[red]你已死亡".with())
        launch(Dispatchers.game) {
            MenuBuilder {
                title = "释放铭能"
                msg = "[cyan]通过在观星台装备的星铭来释放铭能"
                transaction {
                    val enable = InscriptionEntity.wrapRows(InscriptionEntity.playerEnable(profile.id))
                    if (enable.empty()) {
                        option("[red]未装配星铭！") { refresh() }
                        newRow()
                    } else {
                        enable.forEach {
                            option(name(it)) {
                                launch(Dispatchers.game) {
                                    if ((Vars.state.rules.pvp && !Vars.state.rules.tags.getBool("@enableSkills")) || Vars.state.rules.tags.getBool("@disableSkills")) returnReply("[red]模式禁用,若需启用需在描述添加[@enableSkills]".with())
                                    if (player!!.dead()) returnReply("[red]你已死亡".with())
                                    if (!used.getOrDefault(it.id, false)) {
                                        transaction {
                                            broadcast("{player} 使用了 {name}".with("player" to player!!.name,
                                                "name" to name(it)), type = MsgType.InfoToast)
                                            it.refresh()
                                            used[it.id] = true
                                        }
                                        val map = MapManager.current.randomId
                                        if (Vars.state.rules.pvp) {
                                            prefix(it)?.pvpActive(player!!,
                                                level(profile.totalExp),
                                                prefix(it)?.rate ?: 1f)
                                            if (map == MapManager.current.randomId)
                                                effect(it)?.pvpActive(player!!,
                                                    level(profile.totalExp),
                                                    prefix(it)?.rate ?: 1f)
                                        } else {
                                            prefix(it)?.active(player!!,
                                                level(profile.totalExp),
                                                prefix(it)?.rate ?: 1f)
                                            if (map == MapManager.current.randomId)
                                                effect(it)?.active(player!!,
                                                    level(profile.totalExp),
                                                    prefix(it)?.rate ?: 1f)
                                        }
                                    } else {
                                        player!!.sendMessage("[red]已使用过了！")
                                    }
                                }
                            }
                            newRow()
                        }
                    }
                    option("退出菜单") {  }
                }
            }.sendTo(player!!)
        }
    }
}