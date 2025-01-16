@file:Depends("coreMindustry/menu", "调用菜单")

package wayzer.user

import arc.util.Time
import coreLibrary.DBApi.DB.registerTable
import coreLibrary.lib.PermissionApi
import coreLibrary.lib.with
import coreMindustry.lib.*
import mindustry.Vars.netServer
import mindustry.game.EventType
import mindustry.gen.Groups
import mindustry.gen.Player
import org.jetbrains.exposed.sql.transactions.transaction
import wayzer.lib.dao.PlayerData
import wayzer.lib.dao.PlayerProfile
import java.text.DateFormat
import java.time.Duration
import java.time.Instant
import java.util.*
import mindustry.net.Packets


registerTable(PlayerBan.T)

fun Player.kick(profile: PlayerProfile, ban: PlayerBan) {
    fun format(instant: Instant) = DateFormat.getDateTimeInstance().format(Date.from(instant))
    kick(
        """
        [red]你已在该服被禁封[]
        [yellow]名字: ${name()} [yellow]绑定账号: ${profile.account}
        [green]原因: ${ban.reason}
        [green]禁封时间: ${format(ban.createTime)}
        [green]解禁时间: ${format(ban.endTime)}
        [yellow]如有问题,请截图此页咨询管理员
        """.trimIndent(), 0
    )
}

listen<EventType.PlayerConnect> {
    val profile = PlayerData.findById(it.player.uuid())?.profile ?: return@listen
    launch(Dispatchers.IO) {
        val ban = transaction { PlayerBan.findNotEnd(profile.id) } ?: return@launch
        withContext(Dispatchers.game) {
            it.player.kick(profile, ban)
        }
    }
}

suspend fun ban(uuid: String, time: Int, reason: String, operate: PlayerProfile?, admin: String): PlayerProfile? {
    val profile = withContext(Dispatchers.IO) {
        transaction { PlayerData.findByIdWithTransaction(uuid) }?.profile
    }
    if (profile == null) {
        netServer.admins.banPlayerID(uuid)
        Groups.player.filter { it.uuid() == uuid }.forEach { it.kick(Packets.KickReason.banned) }
        netServer.admins.getInfoOptional(uuid)?.let {
            broadcast("[red] 管理员 {admin} 禁封了{target.name},原因: [yellow]{reason}".with("admin" to admin, "target" to it, "reason" to reason))
        }
        val target = Groups.player.find { it.uuid() == uuid }
        target.kick(
            """
                [red]因为未绑定禁封,你已在该服被永久禁封[]
                [yellow]名字: ${target.name}
                [green]原因: $reason
                [yellow]管理员禁封: $admin
                [yellow]如有问题,请截图此页咨询管理员,请勿关闭,关闭后无法找回
                """.trimIndent(), 0
        )

    } else {
        val ban = withContext(Dispatchers.IO) {
            transaction {
                PlayerBan.create(profile, Duration.ofMinutes(time.toLong()), reason, operate)
            }
        }
        profile.players.toTypedArray().forEach {
            it.kick(profile, ban)
            broadcast("[red] 管理员禁封了{target.name},原因: [yellow]{reason}".with("target" to it, "reason" to reason))
        }
    }
    return profile
}

export(::ban)

command("banX", "管理指令: 禁封") {
    usage = "<3位id> <时间|分钟> <原因>"
    permission = "wayzer.admin.ban"
    body {
        if (arg.size < 3) replyUsage()
        val uuid = netServer.admins.getInfoOptional(arg[0])?.id
            ?: depends("wayzer/user/shortID")?.import<(String) -> String?>("getUUIDbyShort")?.invoke(arg[0])
            ?: returnReply("[red]请输入目标3位ID,不清楚可通过/list查询".with())
        if (netServer.admins.getInfoOptional(uuid)!!.admin && player != null) { returnReply("[red]无法封禁管理,请联系xem/klp".with()) }
        val time = arg[1].toIntOrNull()?.takeIf { it > 0 } ?: replyUsage()
        val reason = arg.slice(2 until arg.size).joinToString(" ")
        val operate = player?.let { PlayerData[it.uuid()].profile }
        val qq = ban(uuid, time, reason, operate, player?.name ?: "[red][SERVER]")?.account
        reply("[green]已禁封{qq}".with("qq" to (qq ?: uuid)))
    }
}

command("unbanX", "管理指令: 解封") {
    usage = "<3位id/uuid>"
    permission = "wayzer.admin.ban"
    body {
        val uuid = netServer.admins.getInfoOptional(arg[0])?.id
            ?: depends("wayzer/user/shortID")?.import<(String) -> String?>("getUUIDbyShort")?.invoke(arg[0])
            ?: returnReply("[red]请输入目标3位ID,不清楚可通过/list查询".with())
        netServer.admins.getInfo(uuid).apply {
            if (lastKicked > Time.millis()) {
                lastKicked = Time.millis()
                ips.forEach {
                    netServer.admins.kickedIPs.put(it, 0L)
                }
                returnReply("[green]已解除{uuid}的踢出状态".with("uuid" to uuid))
            }
        }
        val profile = PlayerData.findById(uuid)?.profile ?: if (netServer.admins.getInfoOptional(uuid).banned) {
            netServer.admins.unbanPlayerID(uuid);returnReply("[green]已解封{uuid}".with("uuid" to uuid))
        } else returnReply("[red]未处于封禁状态/如果处于封禁请让他加入一次服务器再unban".with())
        val ban = transaction { PlayerBan.findNotEnd(profile.id) } ?: returnReply("[red]未处于封禁状态".with())
        transaction { ban.endTime = Instant.now() }
        reply("[green]已解封{qq}".with("qq" to profile.account))
    }
}

command("unbanDDOS", "管理指令: 解封ddosban") {
    usage = "<ip地址>"
    permission = "wayzer.admin.ban"
    body {
        if (arg.isEmpty()) replyUsage()
        if (netServer.admins.dosBlacklist.remove(arg.first()))
            reply("[green]已解封{ip}".with("ip" to arg.first()))
        else
            reply("[red]目标未封禁！".with())
    }
}

command("DDOSlist", "查询目前被封dos玩家") {
    permission = "wayzer.admin.ban"
    body {
        //if (netServer.admins.dosBlacklist.remove(arg.first()))
        reply("${netServer.admins.dosBlacklist}".with())
    }
}
command("kick", "管理指令: 踢出") {
    usage = "<3位id> <原因>"
    permission = "wayzer.admin.ban"
    body {
        if (arg.size < 2) replyUsage()
        val uuid = netServer.admins.getInfoOptional(arg[0])?.id
            ?: depends("wayzer/user/shortID")?.import<(String) -> String?>("getUUIDbyShort")?.invoke(arg[0])
            ?: returnReply("[red]请输入目标3位ID,不清楚可通过/list查询".with())
        if (netServer.admins.getInfoOptional(uuid)!!.admin && player != null) { returnReply("[red]无法踢出管理,请联系xem/klp".with()) }
        val reason = arg.slice(1 until arg.size).joinToString(" ")
        val target = Groups.player.find { it.uuid() == uuid }
        target.kick("""
                [red]你已在该服被踢出[]
                [yellow]名字: ${target.name}
                [green]原因: $reason
                [yellow]管理员踢出: ${player?.name ?: "[red][SERVER]"}
                [yellow]如有问题,请截图此页咨询管理员,请勿关闭,关闭后无法找回
                [green]这只是踢出而已 你可以直接重进的
                """.trimIndent(), 0
        )
        broadcast("[red] 管理员 {admin} 踢出了{name},原因: [yellow]{reason}".with("admin" to (player?.name ?: "[red][SERVER]"), "name" to target.name, "reason" to reason))
        reply("[green]已踢出{player}".with("player" to target.name))
    }
}

PermissionApi.registerDefault("wayzer.admin.ban", group = "@admin")