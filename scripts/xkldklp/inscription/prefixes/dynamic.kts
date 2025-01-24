package xkldklp.inscription.prefixes

import inscription.Prefix
import mindustry.gen.Player
import org.jetbrains.exposed.dao.id.EntityID

class DynamicPrefix(
    prefix: String,
    desc: String,
    id: Int,

    weight: Int = 100,
    private val dynamic: Prefix.BasePrefix.() -> Unit,
): Prefix.BasePrefix(prefix, desc, id, 1f, "动态", weight) {

    override suspend fun active(player: Player, level: Int, rate: Float, iid: EntityID<Int>) {
        super.active(player, level, rate, iid)
        dynamic.invoke(this)
    }

}
