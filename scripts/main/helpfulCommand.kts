package main

import arc.graphics.Colors
import arc.util.serialization.Base64Coder
import mindustry.Vars
import java.security.MessageDigest


val md5Digest = MessageDigest.getInstance("md5")!!
fun shortStr(str: String): String {
    fun md5Md5(bs: ByteArray) = synchronized(md5Digest) {
        // md5(md5(bs)+bs)
        md5Digest.update(md5Digest.digest(bs))
        md5Digest.digest(bs)
    }

    val bs = md5Md5(str.toByteArray())
    //return Base64Coder.encode(bs).sliceArray(0..2).concatToString()
    return Base64Coder.encode(bs).concatToString()
}


command("showColor", "显示所有颜色", {}) {
    reply(Colors.getColors().joinToString("[],") { "[#${it.value}]${it.key}" }.with())
}

command("uuid", "你自己的uuid") {
    type = CommandType.Client
    body {
        val p : Player = player!!

        reply("uuid:{uuid}:{short}".with("uuid" to p.uuid(), "short" to shortStr(p.uuid())))
        reply("usid:{usid}".with("usid" to p.usid()))
    }
}


