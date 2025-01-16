@file:Depends("wayzer/user/userService")

package wayzer.user

import coreLibrary.DBApi.DB.registerTable
import coreLibrary.lib.with
import coreMindustry.lib.command
import mindustry.gen.Groups
import org.jetbrains.exposed.sql.transactions.transaction
import wayzer.lib.dao.PlayerData
import wayzer.lib.dao.PlayerProfile

val userService = contextScript<UserService>()
registerTable(AchievementEntity.T)

/** Should call in [Dispatchers.IO] */
fun finishAchievement(profile: PlayerProfile, name: String, exp: Int, broadcast: Boolean = false) {
    transaction {
        if (!AchievementEntity.newWithCheck(profile.id, name, exp)) return@transaction
        userService.updateExp(profile, exp, "完成成就")
        userService.notify(
            profile,
            "[gold][成就]{player.name}[gold]完成成就[white] {name}！",
            mapOf("name" to name, "exp" to exp.toString(), if (broadcast) ("_" to "") else "player.name" to ""),
            broadcast
        )
    }
}
export(::finishAchievement)

command("achieve", "管理指令: 添加成就") {
    this.usage = "<account> <name> <exp>"
    permission = "wayzer.user.achieve"
    body {
        if (arg.size < 3) replyUsage()
        val profile = arg[0].toLongOrNull()?.let {
            PlayerProfile.findByAccount(it)
        } ?: returnReply("[red]找不到该用户".with())
        val name = arg[1]
        val exp = arg[2].toIntOrNull() ?: returnReply("[red]请输入正确的数字".with())
        withContext(Dispatchers.IO) {
            finishAchievement(profile, name, exp, false)
        }
        reply("[green]添加成功".with())
    }
}


command("achieveAll", "管理指令: 添加成就") {
    this.usage = "<name> <exp>"
    permission = "wayzer.user.achieve"
    body {
        if (arg.size < 2) replyUsage()
		Groups.player.forEach {
			val profile = PlayerData[it.uuid()].profile ?: return@forEach
			val name = arg[0]
			val exp = arg[1].toIntOrNull() ?: returnReply("[red]请输入正确的数字".with())
			withContext(Dispatchers.IO) {
				finishAchievement(profile, name, exp, false)
			}
		}
    }
}