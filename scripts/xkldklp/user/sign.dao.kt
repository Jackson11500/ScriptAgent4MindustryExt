@file:Depends("coreLibrary/DBApi", "数据库服务")
@file:Depends("wayzer/user/userService", "玩家绑定")
@file:Depends("wayzer", "玩家绑定")

package xkldklp.user

import arc.math.Mathf.floor
import arc.util.Time
import cf.wayzer.scriptAgent.contextScript
import cf.wayzer.scriptAgent.define.annotations.Depends
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.select
import wayzer.lib.dao.PlayerProfile
import wayzer.lib.dao.util.NeedTransaction
import wayzer.user.UserService
import kotlin.random.Random


class SignEntity(id: EntityID<Int>) : IntEntity(id) {
    var userId by T.profile
    var latestSign by T.latestSign

    object T : IntIdTable("SignEntity") {
        val profile = reference("profile", PlayerProfile.T)
        val latestSign  = long("latestSign")
    }

    companion object : IntEntityClass<SignEntity>(T) {
        private val userService = contextScript<UserService>()

        @NeedTransaction
        fun sign(profile: EntityID<Int>): Int {
            val rows = T.select { T.profile eq profile }
            val exp = Random.nextInt(1, 100)

            if (!rows.empty()) {
                val time = SignEntity.wrapRows(T.select { T.profile eq profile }).toList().maxOf { it.latestSign }
                if (floor(Time.millis() / 1000f / 60f / 60f / 24f) <= floor(time / 1000f / 60f / 60f / 24f)) {
                    return -1
                }
            }

            SignEntity.new {
                userId = profile
                latestSign = Time.millis()
            }
            sign(profile, exp)
            return exp
        }

        @NeedTransaction
        fun sign(profile: EntityID<Int>, exp: Int) {
            userService.updateExp(PlayerProfile[profile], exp, "签到")
        }
    }
}