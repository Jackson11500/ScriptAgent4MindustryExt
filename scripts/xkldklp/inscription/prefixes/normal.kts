package xkldklp.inscription.prefixes

import inscription.Prefix

class NormalPrefix(
    prefix: String,
    desc: String,
    id: Int,

    rate: Float,
    weight: Int = 100
): Prefix.BasePrefix(prefix, desc, id, rate, "增幅", weight) {
    //仅增幅
}
