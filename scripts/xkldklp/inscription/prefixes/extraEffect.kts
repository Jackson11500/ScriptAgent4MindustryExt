package xkldklp.inscription.prefixes

import inscription.Prefix
import mindustry.gen.Player

class ExtraEffectPrefix(
    prefix: String,
    desc: String,
    id: Int,

    rate: Float,
    weight: Int = 100,

    val effect: suspend Pair<Player, Float>.() -> Unit,
): Prefix.BasePrefix(prefix, desc, id, rate, "增幅", weight) {

    override suspend fun active(player: Player, level: Int, rate: Float) {
        effect.invoke(player to level * rate)
    }
}
