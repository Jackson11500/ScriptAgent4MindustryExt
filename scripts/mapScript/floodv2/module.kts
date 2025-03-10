@file:Suppress("unused")
@file:Depends("wayzer/map/mapInfo", "显示地图信息", soft = true)
@file:Depends("coreMindustry/utilMapRule", "修改方块flag")
@file:Depends("coreMindustry/contentsTweaker", "CT属性修改")
@file:Import("mapScript.floodV2.lib.*", defaultImport = true)

package mapScript.floodV2

import cf.wayzer.placehold.DynamicVar
import coreLibrary.lib.config
import coreLibrary.lib.registerVar
import coreMindustry.lib.gamePost
import coreMindustry.lib.listen
import mindustry.Vars.state
import mindustry.content.Blocks
import mindustry.content.UnitTypes
import mindustry.game.EventType
import mindustry.game.GameStats
import mindustry.type.UnitType

//config
val dmgPerFlood by config.key(0.2f, "每单元flood所需伤害（参考）")
val creepDowngrade by config.key(true, "洪水渲染主动降级")
val transferRate by config.key(0.25f, "洪水传播速率，必须小于1/4")
val airHinter by config.key(1f, "洪水在空地传播阻力", "小数值能提高水的传播速度，但是容易造成隐藏水问题")
val creepDamage by config.key(20f, "洪水对建筑伤害,per creep*seconds")
val creepUnitDamage by config.key(10f, "洪水对单位伤害,per creep^2*seconds")
val damageEvaporationRate by config.key(0.95f, "建筑阻挡水时,对水的衰减系数")

val creeperBlocks = arrayOf(
    Blocks.air, Blocks.scrapWall, Blocks.copperWall, Blocks.titaniumWall, Blocks.berylliumWall,
    Blocks.thoriumWall, Blocks.tungstenWall, Blocks.phaseWall, Blocks.plastaniumWall, Blocks.surgeWall,
    Blocks.reinforcedSurgeWall, Blocks.carbideWall
)
val creeperResistanceMap: Map<UnitType, Float> = mapOf(
    UnitTypes.flare to 0.1f,
    UnitTypes.horizon to 0.9f,
    UnitTypes.zenith to 0.1f,
    UnitTypes.quad to 0.4f,
    UnitTypes.vanquish to 0.2f,
    UnitTypes.conquer to 0.5f
).withDefault { 0f }

//listen
val mapRule = contextScript<coreMindustry.UtilMapRule>()

listen<EventType.PlayEvent> {
    state.rules.bannedBlocks.addAll(*creeperBlocks)
    FloodUtil.initWorld()

    // 作者及赞助名单禁止修改
    depends("wayzer/map/mapInfo")?.import<(String, String) -> Unit>("addModeIntroduce")
        ?.invoke(
            "洪水模式 Flood CW ver", """
        进行大幅改动，添加、重做大量机制
        [scarlet]插件开发 WayZer 数值平衡 LuckyClover,WayZer[]
        具体更新可以参考FLOODV2指导地图（/vote map 14487）
        
        1.欢迎来到洪水模式，敌人会如同洪水一般淹没这个世界，你的任务是击退洪水并消除水源
        2.洪水的高度和墙体种类挂钩。洪水会扩散，并会对触碰建筑和单位持续造成伤害，攻击可以阻止扩散。
        3.悬崖、地形墙和空具有一定的高度和粘稠性，洪水需要更高的水位才能越过这些地形。
        3.敌方核心、发射台、容器等是[scarlet]泉眼[],他们会源源不断的输出水
          每类泉眼有着不同的[orange]压制[white]和[orange]摧毁[white]方式
          可以在泉眼的信息界面查看如何压制或是清除目标泉眼
        4.小心对面的[scarlet]孢子发射器(钍反应堆)[]，会发射富含水的孢子导弹，落地后会释放其中的水。(可以使用[scarlet]裂解[]防御)
        5.当摧毁所有间歇泉并压制所有泉眼时，为游戏结束
        
        *本模式采用Flood属性包，请安装[scarlet]ContentsTweaker[]Mod，获取和服务器相同的属性设置。
        *感谢[gold]小撒,萝卜,树根,苦力怕,小K,PCX,小汤圆,小屑猫,sono,Ipecac,天幻,神域,机械师,同行,啊这怪,猫神撅,梦回,548[]为插件开发提供支持
    """.trimIndent()
        )
}
onEnable {
    launch(Dispatchers.gamePost) {
        FloodUtil.initWorld()
    }
}

listen(EventType.Trigger.update) { if (!state.isPaused) FloodUtil.update() }
listen<EventType.BlockDestroyEvent> { e ->
    if (!FloodUtil.enable) return@listen
    if (e.tile.team() == FloodUtil.creepTeam) {
        FloodUtil.creepMap[e.tile] = 0f
    }
}

listen<EventType.ResetEvent> { FloodUtil.reset() }
listen<EventType.GameOverEvent> { FloodUtil.reset() }
onDisable { FloodUtil.reset() }

Emitter.tracker.listenChange(this)
FloodUtil.otherBuildsTracker.listenChange(this)


registerVar("scoreBroad.ext.floodEmitter", "flood发射台显示", DynamicVar.v {
    if (FloodUtil.enable.not()) return@v null
    val all = Emitter.emitters.size
    val nullified = Emitter.emitters.count { it.targetFinish }
    if (all == 0) return@v "[violet]洪水[orange]: 泉眼已清空"
    val color = trafficLightColor(nullified.toFloat() / all)
    "[violet]洪水-泉眼状态[orange]: \uE88B [$color] $nullified/$all []"
})