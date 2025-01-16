@file:Depends("wayzer/user/achievement", "成就")

package mapScript

import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Items
import mindustry.content.UnitTypes
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.WorldLabel
import mindustry.type.ItemStack
import mindustry.type.UnitType
import mindustry.world.Block
import mindustry.world.blocks.campaign.Accelerator
import wayzer.lib.dao.PlayerData
import kotlin.random.Random

name = "bingo"

val achievement = contextScript<wayzer.user.Achievement>()

fun Player.achievement(name: String, exp: Int, broadcast: Boolean = false) {
    val profile = PlayerData[uuid()].profile
    if (profile != null)
        achievement.finishAchievement(profile, name, exp, broadcast)
}


open class Bingo(
    val desc: String
) {
    open fun finish(team: Team): Boolean {
        return true
    }
}

class ResourceBingo(
    desc: String,
    val items: List<ItemStack>
): Bingo(
    desc
) {
    override fun finish(team: Team): Boolean {
        return items.all { team.core().items.get(it.item) >= it.amount }
    }
}

class UnitBingo(
    desc: String,
    val units: Map<UnitType, Int>
): Bingo(
    desc
) {
    override fun finish(team: Team): Boolean {
        return units.all { u -> team.data().units.count { it.type == u.key }  >= u.value }
    }
}

class BuildingBingo(
    desc: String,
    val buildings: Map<Block, Int>
): Bingo(
    desc
) {
    override fun finish(team: Team): Boolean {
        return buildings.all { b -> team.data().getBuildings(b.key).size >= b.value }
    }
}

class LaunchBingo(
    desc: String,
    val amount: Int
): Bingo(
    desc
) {
    override fun finish(team: Team): Boolean {
        return team.data().getBuildings(Blocks.interplanetaryAccelerator).count { b ->
            (b.block as Accelerator).launching.requirements.all {
                b.items.get(it.item) >= it.amount
            }
        } >= amount
    }
}

val lv1Bingos by autoInit { listOf(
    mutableListOf(
        ResourceBingo("铜超过5k", listOf(ItemStack(Items.copper, 5000))),
        ResourceBingo("铅超过5k", listOf(ItemStack(Items.lead, 5000))),
        ResourceBingo("钛超过3k", listOf(ItemStack(Items.titanium, 3000))),
        ResourceBingo("钍超过3k", listOf(ItemStack(Items.thorium, 3000))),
        ResourceBingo("硅超过5k", listOf(ItemStack(Items.silicon, 5000))),
        ResourceBingo("铍超过6k", listOf(ItemStack(Items.beryllium, 6000))),
        ResourceBingo("钨超过4k", listOf(ItemStack(Items.tungsten, 4000))),
        ResourceBingo("合金超过4k", listOf(ItemStack(Items.surgeAlloy, 4000))),
        ResourceBingo("氧化物超过2k", listOf(ItemStack(Items.oxide, 2000))),
    ),
    mutableListOf(
        UnitBingo("建造80只尖刀", mapOf(UnitTypes.dagger to 80)),
        UnitBingo("建造80只爬虫", mapOf(UnitTypes.crawler to 80)),
        UnitBingo("建造80只新星", mapOf(UnitTypes.nova to 80)),
        UnitBingo("建造60只恒星", mapOf(UnitTypes.pulsar to 60)),
        UnitBingo("建造40只毒蛛", mapOf(UnitTypes.atrax to 40)),
        UnitBingo("建造40只战锤", mapOf(UnitTypes.mace to 40)),
        UnitBingo("建造60只围护", mapOf(UnitTypes.stell to 60)),
        UnitBingo("建造40只循迹", mapOf(UnitTypes.locus to 40)),
        UnitBingo("建造60只天守", mapOf(UnitTypes.merui to 60)),
        UnitBingo("建造40只恩赐", mapOf(UnitTypes.cleroi to 40)),
        UnitBingo("建造60只挣脱", mapOf(UnitTypes.elude to 60)),
        UnitBingo("建造40只遮蔽", mapOf(UnitTypes.avert to 40)),
    ),
    mutableListOf(
        BuildingBingo("建造一个行星际发射器", mapOf(Blocks.interplanetaryAccelerator to 1)),
        BuildingBingo("建造一个行星际发射器", mapOf(Blocks.interplanetaryAccelerator to 1)),
        BuildingBingo("建造一个行星际发射器", mapOf(Blocks.interplanetaryAccelerator to 1)),
        BuildingBingo("建造二个堡垒核心", mapOf(Blocks.coreCitadel to 2))
    )
)  }

val lv2Bingos by autoInit { listOf(
    mutableListOf(
        ResourceBingo("铜超过10k, 铅超过5k", listOf(ItemStack(Items.copper, 10000), ItemStack(Items.lead, 5000))),
        ResourceBingo("钛超过8k, 钍超过4k", listOf(ItemStack(Items.titanium, 8000), ItemStack(Items.thorium, 4000))),
        ResourceBingo("硅超过10k, 石墨超过15k", listOf(ItemStack(Items.silicon, 10000), ItemStack(Items.graphite, 15000))),
        ResourceBingo("铍超过20k", listOf(ItemStack(Items.beryllium, 20000))),
        ResourceBingo("钨超过10k", listOf(ItemStack(Items.tungsten, 10000))),
        ResourceBingo("合金超过8k", listOf(ItemStack(Items.surgeAlloy, 8000))),
        ResourceBingo("硫超过3k, 爆炸混合物超过3k", listOf(ItemStack(Items.pyratite, 3000), ItemStack(Items.blastCompound, 3000))),
        ResourceBingo("裂变产物超过1k", listOf(ItemStack(Items.fissileMatter, 1000))),
        ResourceBingo("塑钢超过4k, 玻璃超过8k", listOf(ItemStack(Items.metaglass, 8000), ItemStack(Items.plastanium, 4000))),
        ResourceBingo("氧化物超过5k, 碳化物超过4k", listOf(ItemStack(Items.oxide, 5000), ItemStack(Items.carbide, 4000))),
    ),
    mutableListOf(
        UnitBingo("建造80只耀星", mapOf(UnitTypes.quasar to 80)),
        UnitBingo("建造80只堡垒", mapOf(UnitTypes.fortress to 80)),
        UnitBingo("建造80只血蛭", mapOf(UnitTypes.spiroct to 80)),
        UnitBingo("建造80只苍穹", mapOf(UnitTypes.zenith to 80)),
        UnitBingo("建造80只巨像", mapOf(UnitTypes.mega to 80)),
        UnitBingo("建造80只准绳", mapOf(UnitTypes.precept to 80)),
        UnitBingo("建造80只影逝", mapOf(UnitTypes.obviate to 80)),
        UnitBingo("建造80只灾祸", mapOf(UnitTypes.anthicus to 80)),
        UnitBingo("建造20只权杖", mapOf(UnitTypes.scepter to 20)),
        UnitBingo("建造20只灾星", mapOf(UnitTypes.vela to 20)),
        UnitBingo("建造20只终结", mapOf(UnitTypes.quell to 20)),
        UnitBingo("建造20只征服", mapOf(UnitTypes.vanquish to 20)),
    ),
    mutableListOf(
        BuildingBingo("建造4个无量级单位重构厂", mapOf(Blocks.tetrativeReconstructor to 4)),
        BuildingBingo("建造8个多幂级单位重构厂", mapOf(Blocks.exponentialReconstructor to 8)),
        BuildingBingo("建造8个幽灵与融毁", mapOf(Blocks.spectre to 8, Blocks.meltdown to 8)),
        BuildingBingo("建造8个瘤变与4个通量", mapOf(Blocks.neoplasiaReactor to 8, Blocks.fluxReactor to 4)),
        BuildingBingo("建造4个天谴与魔灵", mapOf(Blocks.smite to 4, Blocks.malign to 4)),
        BuildingBingo("建造100个爆破钻头", mapOf(Blocks.blastDrill to 100)),
        BuildingBingo("建造40个爆裂钻头", mapOf(Blocks.eruptionDrill to 40)),
        BuildingBingo("建造3个卫城核心", mapOf(Blocks.coreAcropolis to 3)),
    ),
    mutableListOf(
        LaunchBingo("建造并启动1座行星发射器", 1)
    )
)  }

val lv3Bingos by autoInit { listOf(
    mutableListOf(
        ResourceBingo("铜超过50k, 铅超过50k", listOf(ItemStack(Items.copper, 50000), ItemStack(Items.lead, 50000))),
        ResourceBingo("钛超过40k, 钍超过40k", listOf(ItemStack(Items.titanium, 40000), ItemStack(Items.thorium, 40000))),
        ResourceBingo("硅超过50k, 石墨超过50k", listOf(ItemStack(Items.silicon, 50000), ItemStack(Items.graphite, 50000))),
        ResourceBingo("铍超过60k, 钨超过40k", listOf(ItemStack(Items.beryllium, 60000), ItemStack(Items.tungsten, 40000))),
        ResourceBingo("硫超过20k, 爆炸混合物超过20k", listOf(ItemStack(Items.pyratite, 20000), ItemStack(Items.blastCompound, 20000))),
        ResourceBingo("裂变产物超过10k", listOf(ItemStack(Items.fissileMatter, 10000))),
        ResourceBingo("塑钢超过40k, 玻璃超过40k", listOf(ItemStack(Items.metaglass, 40000), ItemStack(Items.plastanium, 40000))),
        ResourceBingo("合金超过20k, 氧化物超过40k, 碳化物超过20k", listOf(ItemStack(Items.surgeAlloy, 20000), ItemStack(Items.oxide, 40000), ItemStack(Items.carbide, 20000))),
    ),
    mutableListOf(
        UnitBingo("建造80只耀星, 堡垒, 血蛭", mapOf(UnitTypes.quasar to 80, UnitTypes.fortress to 80, UnitTypes.spiroct to 80)),
        UnitBingo("建造80只苍穹, 巨像, 影逝", mapOf(UnitTypes.zenith to 80, UnitTypes.mega to 80, UnitTypes.obviate to 80)),
        UnitBingo("建造80只江豚, 戟鲸", mapOf(UnitTypes.bryde to 80, UnitTypes.cyerce to 80)),
        UnitBingo("建造40只权杖, 灾星", mapOf(UnitTypes.scepter to 40, UnitTypes.vela to 40)),
        UnitBingo("建造20只终结, 征服, 天理", mapOf(UnitTypes.quell to 20, UnitTypes.vanquish to 20, UnitTypes.tecta to 20)),
        UnitBingo("建造10只王座, 天蝎, 死星", mapOf(UnitTypes.reign to 10, UnitTypes.toxopid to 10, UnitTypes.corvus to 10)),
        UnitBingo("建造10只日蚀, 要塞", mapOf(UnitTypes.eclipse to 10, UnitTypes.oct to 10)),
        UnitBingo("建造10只龙王, 海神", mapOf(UnitTypes.eclipse to 10, UnitTypes.oct to 10)),
        UnitBingo("建造10只瓦解, 领主, 帝君", mapOf(UnitTypes.disrupt to 10, UnitTypes.conquer to 10, UnitTypes.collaris to 10)),
    ),
    mutableListOf(
        BuildingBingo("建造40个无量级单位重构厂", mapOf(Blocks.tetrativeReconstructor to 40)),
        BuildingBingo("建造80个多幂级单位重构厂", mapOf(Blocks.exponentialReconstructor to 80)),
        BuildingBingo("建造80个幽灵与融毁", mapOf(Blocks.spectre to 80, Blocks.meltdown to 80)),
        BuildingBingo("建造40个天谴与魔灵", mapOf(Blocks.smite to 40, Blocks.malign to 40)),
        BuildingBingo("建造20个创伤", mapOf(Blocks.scathe to 20)),
        BuildingBingo("建造80个爆裂钻头", mapOf(Blocks.eruptionDrill to 80)),
        BuildingBingo("建造5个卫城核心", mapOf(Blocks.coreAcropolis to 5)),
    ),
    mutableListOf(
        LaunchBingo("建造并启动5座行星发射器", 5),
        LaunchBingo("建造并启动5座行星发射器", 5),
        LaunchBingo("建造并启动5座行星发射器", 5)
    )
)  }

val bingos by autoInit { mutableListOf<List<Bingo>>() }

val teamBingos by autoInit { mutableMapOf<Team, MutableList<Bingo>>() }
val bingo2World by autoInit { mutableMapOf<Bingo, WorldLabel>() }

var bingoLevel = 0

onEnable {
    launch(Dispatchers.game) {
        delay(10_000)
        val level = Random.nextFloat()
        if (level <= 0.5) {
            bingoLevel = 1
        } else if (level >= 0.7) {
            bingoLevel = 2
        } else {
            bingoLevel = 3
        }

        val randomBingos = when (bingoLevel) {
            1 -> lv1Bingos
            2 -> lv2Bingos
            3 -> lv3Bingos
            else -> lv1Bingos
        }

        Call.sendMessage("[yellow]本局Bingo等级为:${bingoLevel}, 快去中间查看bingo目标吧！")
        repeat(5) { i ->
            bingos.add(buildList {
                repeat(5) { j->
                    var randomBingo: Bingo
                    var randomClass = randomBingos.random()
                    if (randomClass.isEmpty()) {
                        randomClass = randomBingos.random()
                        if (randomClass.isEmpty()) {
                            randomClass = randomBingos.random()
                        }
                    }
                    if (randomClass.isEmpty()) {
                        randomBingo = Bingo("空")
                    } else {
                        randomBingo = randomClass.random()
                        randomClass.remove(randomBingo)
                    }
                    add(randomBingo)
                }
            })
        }
        //263 327
        repeat(5) { i ->
            val wy = (327 - i * 16) * 8f
            repeat(5) { j ->
                val wx = (263 + j * 16) * 8f
                WorldLabel.create().apply {
                    x = wx + 4.5f * 8f
                    y = wy + 4f * 8f
                    fontSize = 2f
                    snapInterpolation()
                    val bingo = bingos[i][j]
                    text = bingo.desc
                    bingo2World[bingo] = this
                    add()
                }
            }
        }

        loop(Dispatchers.game) {
            bingos.forEach { it.forEach { b ->
                Vars.state.teams.getActive().forEach {
                    if (b.finish(it.team) && b !in teamBingos.getOrPut(it.team) { mutableListOf() }) {
                        teamBingos.getOrPut(it.team) { mutableListOf() }.add(b)
                        if (b.desc != "空") {
                            Call.sendMessage("[#${it.team.color}]${it.team}完成了 [white]${b.desc}！")
                        }
                        bingo2World[b]?.text += "\n[#${it.team.color}]${it.team}已完成"
                    }
                }
            } }
            //懒得动脑 我是穷举大王
            Vars.state.teams.getActive().forEach {
                var bingo = false
                repeat(5) { i ->
                    if (bingos[i].all { b -> b in teamBingos.getOrPut(it.team) { mutableListOf() } }) {
                        bingo = true
                    }
                }
                repeat(5) { i ->
                    var rowBingo = true
                    repeat(5) { j ->
                        if (bingos[j][i] !in teamBingos.getOrPut(it.team) { mutableListOf() } ) {
                            rowBingo = false
                        }
                    }
                    if (rowBingo) {
                        bingo = true
                    }
                }
                if (buildList {
                        repeat(5) { i ->
                            add(bingos[i][i])
                        }
                    }.all { b -> b in teamBingos.getOrPut(it.team) { mutableListOf() } }) {
                    bingo = true
                }
                if (buildList {
                        repeat(5) { i ->
                            add(bingos[4 - i][i])
                        }
                    }.all { b -> b in teamBingos.getOrPut(it.team) { mutableListOf() } }) {
                    bingo = true
                }
                if (bingo) {
                    Call.sendMessage("\n\n\n\n\n\n\n[#${it.team.color}]${it.team}Bingo！")
                    Vars.state.teams.getActive().filterNot { t -> t.team == it.team }.forEach {
                        it.destroyToDerelict()
                    }
                    val team = it.team
                    Groups.player.filter { it.team() == team }.forEach {
                        it.achievement("[green][Bingo!]", 100)
                        if (bingoLevel == 3) {
                            it.achievement("[purple][Bingo!]", 200)
                        }
                    }
                }
            }
            delay(200)
        }
    }
}