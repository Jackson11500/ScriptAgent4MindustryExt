package wayzer.ext

import coreLibrary.lib.config
import coreLibrary.lib.with
import coreMindustry.lib.MsgType
import coreMindustry.lib.broadcast
import coreMindustry.lib.game
import java.time.Duration

val type by config.key(MsgType.Message, "发送方式")
val time by config.key(Duration.ofMinutes(10)!!,"公告间隔")
val list = buildList {
    add("[yellow]你知道吗?学术端可以点击快捷设置中的[cyan]技[yellow]快速释放铭能")
    add("[yellow]尽管你只能携带3枚星铭进场,但是终焉/始源铭却不计入这个数量")
    add("[yellow]有些季度战役模式可不只有表面的几关哦..")
    add("[yellow]等级图标其实是罗马数字哦！")
    add("[yellow]klp是个大鸽子,咕咕咕")
    //add("[yellow]为了完美通关而牺牲自己..我会记得他的[red] FOREVER")
    add("[yellow]喵！")
    add("[yellow]虽然服务器不用qq绑定,但还是加一下qq群吧！878118248")
    add("[yellow]alphaaaaaa")
    add("[yellow]服务器内有非常多的成就,快去各种玩法地图发掘它们吧！")
    add("[yellow]pvp模式中获胜将会获得三倍经验！MVP则在此基础上再三倍！")
}

var i = 0
fun broadcast(){
    if(list.isEmpty())return
    i %= list.size
    broadcast(list[i].with(),type,15f)
    i++
}

onEnable{
    launch(Dispatchers.game) {
        while (true) {
            delay(time.toMillis())
            broadcast()
        }
    }
}