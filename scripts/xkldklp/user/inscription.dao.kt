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

class InscriptionEntity(id: EntityID<Int>) : IntEntity(id) {
    var userId by T.profile
    var effectId by T.effectId
    var prefixId by T.prefixId
    var enable by T.enable
    var hidden by T.hidden

    object T : IntIdTable("Inscription") {
        val profile = reference("profile", PlayerProfile.T)
        val effectId = integer("effectId")
        val prefixId = integer("prefixId")
        val enable = bool("enable").default(false)
        val hidden = bool("hidden").default(false)
    }

    companion object : IntEntityClass<InscriptionEntity>(T) {
        @NeedTransaction
        fun new(profile: EntityID<Int>, effectId: Int, prefixId: Int): InscriptionEntity {
            return InscriptionEntity.new {
                userId = profile
                this.effectId = effectId
                this.prefixId = prefixId
            }
        }

        @NeedTransaction
        fun player(profile: EntityID<Int>): Query {
            return T.select { (T.profile eq profile) and ((T.hidden eq false) or (T.enable eq true))}
        }

        @NeedTransaction
        fun playerHidden(profile: EntityID<Int>): Query {
            return T.select { (T.profile eq profile) and (T.hidden eq true) and (T.enable eq false)}
        }

        @NeedTransaction
        fun playerEnable(profile: EntityID<Int>): Query {
            return T.select { (T.profile eq profile) and (T.enable eq true)}
        }
    }
}