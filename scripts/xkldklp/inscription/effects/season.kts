package xkldklp.inscription.effects

import mindustry.gen.Player
import inscription.Effect

class SeasonEffect(
    name: String,
    desc: String,
    id: Int,

    weight: Int = 0,
    val levelRate: Float = 0f,
    val effect: Pair<Player, Float>.() -> Unit,
): Effect.BaseEffect(name, desc, id, "节日", weight) {

    override suspend fun active(player: Player, level: Int, rate: Float) {
        effect.invoke(player to rate * (1 + level * levelRate))
    }
}
