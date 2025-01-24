@file:Depends("coreLibrary/DBApi", "数据库服务")

package inscription

import mindustry.gen.Player
import org.jetbrains.exposed.dao.id.EntityID

open class SpecialInscription(
        var name: String,
        var nameR: String,
        var desc: String,
        var descR: String,
        val id: Int,

) {
    open suspend fun active(player: Player, level: Int, rate: Float = 1f, iid: EntityID<Int>) {

    }

    open suspend fun pvpActive(player: Player, level: Int, rate: Float = 1f, iid: EntityID<Int>) {
        active(player, level, rate, iid)
    }
}

val specials = mutableMapOf<Int, SpecialInscription>()