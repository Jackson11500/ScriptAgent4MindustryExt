@file:Depends("wayzer/user/userService")
@file:Depends("inscription/special")
@file:Depends("coreLibrary/DBApi", "数据库服务")

package xkldklp.user

import coreLibrary.DBApi.DB.registerTable
import coreLibrary.lib.event.RequestPermissionEvent
import coreLibrary.lib.with
import coreMindustry.lib.*
import mindustry.gen.Player
import org.jetbrains.exposed.sql.transactions.transaction
import wayzer.lib.dao.PlayerData
import wayzer.lib.dao.PlayerProfile
import wayzer.user.UserService
import inscription.Special
import mindustry.gen.Call
import mindustry.gen.Groups

val userService = contextScript<UserService>()
val special = contextScript<Special>()
registerTable(SpInscriptionEntity.T)

/** Should call in [Dispatchers.IO] */
fun newSpInscription(profile: PlayerProfile, id: Int): SpInscriptionEntity {
    return transaction {
        val enable = SpInscriptionEntity.wrapRows(SpInscriptionEntity.playerEnable(profile.id))
        val b = enable.any {
            it.iId == id
        }
        if (b) {
            enable.first { it.iId == id }
        } else {
            SpInscriptionEntity.new(profile.id, id)
        }
    }
}

export(::newSpInscription)

fun special(id: Int): Special.SpecialInscription {
    return special.specials[id]!!
}

command("spinscription", "管理指令: 铭刻特殊星铭") {
    usage = "<account> <ID>"
    permission = "xkldklp.user.newInscription"
    body {
        if (arg.size < 2) replyUsage()
        if (arg[0] == "all") {
            val id = special.specials.getOrDefault(arg[1].toInt(), null)?.id ?: returnReply("[red]未知ID".with())
            Groups.player.forEach {
                val profile = PlayerData[it.uuid()].profile ?: return@forEach
                withContext(Dispatchers.IO) {
                    newSpInscription(profile, id)
                }
            }
            reply("[green]添加成功".with())
        } else {
            val profile = arg[0].toLongOrNull()?.let {
                PlayerProfile.findByAccount(it)
            } ?: returnReply("[red]找不到该用户".with())
            val id = special.specials.getOrDefault(arg[1].toInt(), null)?.id ?: returnReply("[red]未知ID".with())
            withContext(Dispatchers.IO) {
                newSpInscription(profile, id)
            }
            reply("[green]添加成功".with())
        }
    }
}

command("sptest", "测试") {
    permission = "fun"
    body {
        val profile = PlayerData[player!!.uuid()].profile
        launch(Dispatchers.IO) {
            if (player!!.hasPermission("xkldklp.12f")) {
                Call.sendMessage("f")
            }
            if (player!!.hasPermission("xkldklp.12t")) {
                Call.sendMessage("t")
            }
        }
    }
}

listenTo<RequestPermissionEvent> {
    val profile = when (val p = subject) {
        is PlayerProfile -> p
        is Player -> PlayerData[p.uuid()].secureProfile(p) ?: return@listenTo
        else -> return@listenTo
    }
    val index = group.indexOfLast { !it.startsWith("@") }
    val newGroup = group.toMutableList()
    val sp = buildMap {
        transaction {
            val enable = SpInscriptionEntity.wrapRows(SpInscriptionEntity.playerEnable(profile.id))
            enable.forEach {
                put(it.iId, it.reversed)
            }
        }
    }
    newGroup.addAll(index + 1, (sp).map { "@sp${it.key}${it.value}" })
    group = newGroup
}

