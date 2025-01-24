@file:Depends("wayzer/user/userService")
@file:Depends("coreLibrary/DBApi", "数据库服务")

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

registerTable(SpInscriptionEntity.T)

/** Should call in [Dispatchers.IO] */
fun newSpInscription(profile: PlayerProfile, id: Int): SpInscriptionEntity {
    return transaction {
        SpInscriptionEntity.new(profile.id, id)
    }
}
export(::newSpInscription)

command("spinscription", "管理指令: 铭刻特殊星铭") {
    usage = "<account> <ID>"
    permission = "xkldklp.user.newInscription"
    body {
        if (arg.size < 2) replyUsage()
        val profile = arg[0].toLongOrNull()?.let {
            PlayerProfile.findByAccount(it)
        } ?: returnReply("[red]找不到该用户".with())
        val id = effects.getOrDefault(arg[1].toInt(), null) ?: returnReply("[red]未知ID".with())
        withContext(Dispatchers.IO) {
            newSpInscription(profile, id)
        }
        reply("[green]添加成功".with())
    }
}
