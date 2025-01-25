@file:Depends("coreLibrary/DBApi", "数据库服务")

package inscription

open class SpecialInscription(
        var name: String,
        var nameR: String,
        var desc: String,
        var descR: String,
        val id: Int,

) {

}

val specials = mutableMapOf<Int, SpecialInscription>()