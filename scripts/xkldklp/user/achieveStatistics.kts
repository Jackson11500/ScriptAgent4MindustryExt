@file:Depends("wayzer/user/achievement", "成就")

package xkldklp.user

import coreMindustry.lib.listen
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.EventType
import mindustry.gen.Iconc
import mindustry.gen.Player
import mindustry.world.Block
import wayzer.lib.dao.PlayerData
import wayzer.lib.dao.PlayerProfile

val achievement = contextScript<wayzer.user.Achievement>()

fun Player.achievement(name: String, exp: Int, broadcast: Boolean = false) {
    val profile = PlayerData[uuid()].profile
    if (profile != null)
        achievement.finishAchievement(profile, name, exp, broadcast)
}

val name2desc = mapOf(
    "[green][绑定账号]" to "绑定账号",
    "[purple][+90!]" to "在一次签到中获得90+的经验奖励",
    "[green][观星]" to "在观星台控制一台死星",
    "[green][放个屁都能被打断]" to "LordOfWar中,被打断一次领主降临",
    "[green][放了个屁]" to "LordOfWar中,成功降临一次领主\n[lightgray]什么B动静?[]",
    "[purple][全能！]" to "LordOfWar中,将一个城市转型为全能城",
    "[purple][科教兴国]" to "LordOfWar中,将科研等级提升至31级",
    "[green][落日 - 启动成功]" to "LordOfWar中,见证落日计划成功",
    "[green][落日 - 启动失败]" to "LordOfWar中,见证落日计划失败",
    "[purple][轨道巨构发射成功！]" to "LordOfWar中,成功发射轨道巨构",
    "[green][轰击！]" to "LordOfWar中,对敌方进行一次高效的打击",
    "[green][虚空闪烁！]" to "LordOfWar中,对敌方进行一次致命的突击",
    "[green][虚空全知！]" to "LordOfWar中,对敌方进行并没有什么卵用的侦察",
    "[green][虚空放逐！]" to "LordOfWar中,混乱敌方单位与他们的领主",
    "[green][挪移！]" to "LordOfWar中,强行定身敌方单位",
    "[green][虚空领域！]" to "LordOfWar中,释放一种致命的领域",
    "[green][封锁！]" to "LordOfWar中,使用超武拖慢敌方的进攻步伐",
    "[green][虚空转化！]" to "LordOfWar中,使敌方单位倒戈",
    "[green][回生！]" to "LordOfWar中,将己方单位死亡逆转",
    "[green][虚空劫掠！]" to "LordOfWar中,对敌方进行严重的经济打击",
    "[green][偷盗！]" to "LordOfWar中,将富哥们攒的钱全部清空",
    "[purple][神偷！]" to "LordOfWar中,使用偷盗超武清空一个坐拥88888金币玩家的钱包",
    "[green][影鬼！]" to "LordOfWar中,给敌人来点小小的翻倍震撼",
    "[green][虚空之灵！]" to "LordOfWar中,启动紧急防御手段",
    "[green][百科全书！]" to "LordOfWar中,使用一次等级查找科技",
    "[green][新星铭！]" to "铭刻一枚新的星铭",
    "[green][StarBlast!]" to "在观星台建造50个爆破钻头",
    "[purple][终极坦克！]" to "坦克大战中拔得头筹",
    "[green][灵魂，很多的灵魂！]" to "坦克大战中结算后有500以上的灵魂",
    "[green][辅助武器？真不熟！]" to "坦克大战中不使用辅助武器获得胜利",
    "[green][征服者！]" to "坦克大战中在20人以上获胜",
    "[green][铜区战神！]" to "坦克大战中在铜墙区中获胜",
    "[green][孤勇者!]" to "MagicFight夺旗中成功放回旗帜",
    "[green][尸变之人]" to "尸潮模式中,操控的单位尸变为红队",
    "[green][Bingo!]" to "生产力Bingo中,率先完成Bingo",
    "[purple][Bingo!]" to "生产力Bingo中,率先完成三级Bingo",
    "[purple][膀胱局]" to "完成一局90min以上的pvp且结算时仍还有10人",
    "[green][我是画画大师]" to "一局pvp游戏中,建造100个画板",
    "[green][史诗工程]" to "一局pvp游戏中,建造50个超核处理器",
    "[green][科技之光]" to "一局pvp游戏中,建造20个大超速(超速穹顶)",
    "[green][异世-零地区]" to "通关异世天灾-零号地区",
    "[green][异世-冰冻森]" to "通关异世天灾-冰冻森林",
    "[green][异世-陨石坑]" to "通关异世天灾-陨石坑",
    "[green][异世-风群岛]" to "通关异世天灾-风吹群岛",
    "[purple][异世-焦油田]" to "通关异世天灾-焦油田",
    "[purple][异世-冲击区]" to "通关异世天灾-冲击区0078",
    "[purple][异世-裂谷道]" to "通关异世天灾-荒芜裂谷",
    "[purple][异世-发射区]" to "通关异世天灾-行星际发射终端",
    "[yellow][始与终-异世天灾]" to "通关异世天灾",
    "[purple][跃迁逃脱]" to "在末日启示录中,成功研究出最终科技",
    "[green][逆天改命]" to "在升级模式中升级一只obviate${Iconc.unitObviate}"
)

val buildBlocks by autoInit { mutableMapOf<PlayerProfile, MutableMap<Block, Int>>() }

listen<EventType.BlockBuildEndEvent> {
    val player = it.unit.player ?: return@listen
    val profile = PlayerData[player.uuid()].profile ?: return@listen
    if (!it.breaking) {
        val block = it.tile.block()
        val blocks = buildBlocks.getOrPut(profile) { mutableMapOf() }
        if (block == Blocks.canvas && Vars.state.rules.pvp) {
            blocks[Blocks.canvas] = blocks.getOrPut(Blocks.canvas) { 0 } + 1
            if (blocks[Blocks.canvas]!! >= 100) {
                player.achievement("[green][我是画画大师]", 100)
            }
        }
        if (block == Blocks.hyperProcessor && Vars.state.rules.pvp) {
            blocks[Blocks.hyperProcessor] = blocks.getOrPut(Blocks.hyperProcessor) { 0 } + 1
            if (blocks[Blocks.hyperProcessor]!! >= 50) {
                player.achievement("[green][史诗工程]", 100)
            }
        }
        if (block == Blocks.overdriveDome && Vars.state.rules.pvp) {
            blocks[Blocks.overdriveDome] = blocks.getOrPut(Blocks.overdriveDome) { 0 } + 1
            if (blocks[Blocks.overdriveDome]!! >= 20) {
                player.achievement("[green][科技之光]", 100)
            }
        }
    }
}

listen<EventType.PlayEvent> {
    buildBlocks.clear()
}