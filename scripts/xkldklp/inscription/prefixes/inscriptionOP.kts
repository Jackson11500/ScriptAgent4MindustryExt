@file:Depends("coreLibrary/DBApi", "数据库服务")

package xkldklp.inscription.prefixes

import inscription.Prefix
import mindustry.gen.Player
import org.jetbrains.exposed.dao.id.EntityID

class InscriptionOPPrefix(
    prefix: String,
    desc: String,
    id: Int,

    rate: Float,
    weight: Int = 100,

    val effect: suspend Pair<Player, Pair<Float, EntityID<Int>>>.() -> Unit,
): Prefix.BasePrefix(prefix, desc, id, rate, "铭能操作", weight) {

    override suspend fun active(player: Player, level: Int, rate: Float, iid: EntityID<Int>) {
        effect.invoke(player to (level * rate to iid))
    }
}
