@file:Depends("coreLibrary/DBApi", "数据库服务")

package xkldklp.user

import cf.wayzer.scriptAgent.define.annotations.Depends
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import wayzer.lib.dao.PlayerProfile
import wayzer.lib.dao.util.NeedTransaction

class SpInscriptionEntity(id: EntityID<Int>) : IntEntity(id) {
    var userId by T.profile
    var iId by T.iId
    var enable by T.enable
    var reversed by T.reversed

    object T : IntIdTable("SpInscription") {
        val profile = reference("profile", PlayerProfile.T)
        val iId = integer("iId")
        val enable = bool("enable").default(false)
        val reversed = bool("reversed").default(false)
    }

    companion object : IntEntityClass<SpInscriptionEntity>(T) {
        @NeedTransaction
        fun new(profile: EntityID<Int>, iId: Int): SpInscriptionEntity {
            return SpInscriptionEntity.new {
                userId = profile
                this.iId = iId
            }
        }

        @NeedTransaction
        fun player(profile: EntityID<Int>): Query {
            return T.select { (T.profile eq profile) }
        }

        @NeedTransaction
        fun playerReversed(profile: EntityID<Int>): Query {
            return T.select { (T.profile eq profile) and (T.reversed eq true)}
        }

        @NeedTransaction
        fun playerEnable(profile: EntityID<Int>): Query {
            return T.select { (T.profile eq profile) and (T.enable eq true)}
        }
    }
}