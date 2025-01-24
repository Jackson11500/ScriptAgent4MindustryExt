package xkldklp.inscription.effects

import mindustry.gen.Player
import inscription.Effect
import org.jetbrains.exposed.dao.id.EntityID

class CoreEffect(
    name: String,
    desc: String,
    id: Int,

    weight: Int = 100,
    val levelRate: Float = 0f,
    val effect: Pair<Player, Float>.() -> Unit,
): Effect.BaseEffect(name, desc, id, "核心", weight) {

    override suspend fun active(player: Player, level: Int, rate: Float, iid: EntityID<Int>) {
        effect.invoke(player to rate * (1 + level * levelRate))
    }
}
