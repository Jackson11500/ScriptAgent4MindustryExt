@file:Suppress("PropertyName", "unused")

package xkldklp

var S1A9Event = false

class RuleMode(
    val id: Int,
    val ruleLevel: Int,

    var active: Boolean = true
)

var ruleMode: RuleMode? = null
fun ruleMap(id: Int, level: Int) {
    ruleMode = RuleMode(id, level)
}

var enableMapScripts = mutableListOf<Int>()