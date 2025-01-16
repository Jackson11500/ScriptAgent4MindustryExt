@file:Depends("inscription/effect")
@file:Depends("xkldklp/inscription/effects/resources")
@file:Depends("xkldklp/inscription/effects/fun")
@file:Depends("xkldklp/inscription/effects/normal")
@file:Depends("xkldklp/inscription/effects/core")
@file:Depends("xkldklp/inscription/effects/unitEffect")
@file:Depends("xkldklp/inscription/effects/unitSpawn")
@file:Depends("xkldklp/inscription/effects/season")
@file:Depends("coreLibrary/DBApi", "数据库服务")
@file:Depends("wayzer/maps", "获取地图信息")
@file:Depends("wayzer")
@file:Depends("wayzer/user/ban")
@file:Import("@coreMindustry/util/spawnAround.kt", sourceFile = true)


package inscription.reg

import arc.graphics.Color
import arc.graphics.Colors
import arc.math.geom.Vec2
import arc.struct.IntSeq
import arc.util.Log
import arc.util.Time
import coreMindustry.lib.command
import coreMindustry.lib.game
import coreMindustry.lib.listen
import coreMindustry.util.spawnAround
import mindustry.Vars
import mindustry.content.*
import mindustry.ctype.ContentType
import mindustry.entities.Units
import mindustry.entities.bullet.BulletType
import mindustry.entities.units.StatusEntry
import mindustry.game.EventType
import mindustry.gen.Bullet
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.PayloadUnit
import mindustry.gen.Payloadc
import mindustry.type.ItemStack
import mindustry.type.StatusEffect
import mindustry.world.Block
import mindustry.world.blocks.defense.turrets.ItemTurret
import mindustry.world.blocks.payloads.BuildPayload
import org.jetbrains.exposed.sql.transactions.transaction
import wayzer.MapManager
import wayzer.lib.dao.PlayerData
import wayzer.user.PlayerBan
import xkldklp.inscription.effects.Core.*
import xkldklp.inscription.effects.Fun.*
import xkldklp.inscription.effects.Normal.*
import xkldklp.inscription.effects.Resources.*
import xkldklp.inscription.effects.Season
import xkldklp.inscription.effects.UnitEffect.*
import xkldklp.inscription.effects.UnitSpawn.*
import java.time.Duration
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

fun mindustry.gen.Unit.infApply(statusEffect: StatusEffect, duration: Float) {
    statuses.add(StatusEntry().set(statusEffect, duration))
}

fun build() {
    val effects = buildList{
        add(ResourcesEffect(
            "生铜",
            "[cyan]对这块像铜的星铭输入铭能,刻印出大量铜\n[yellow]+100铜(+25%/共鸣等级)",
            1,
            listOf(ItemStack(Items.copper, 100)),
            0.25f,
            50
        ))
        add(ResourcesEffect(
            "生铅",
            "[cyan]对这块像铅的星铭输入铭能,刻印出大量铅\n[yellow]+100铅(+25%/共鸣等级)",
            2,
            listOf(ItemStack(Items.lead, 100)),
            0.25f,
            50
        ))
        add(ResourcesEffect(
            "生硅",
            "[cyan]对这块像硅的星铭输入铭能,刻印出大量硅\n[yellow]+50硅(+25%/共鸣等级)\n[lightgray]拯救硅0",
            3,
            listOf(ItemStack(Items.silicon, 100)),
            0.25f,
            50
        ))
        add(ResourcesEffect(
            "生钛",
            "[cyan]对这块像钛的星铭输入铭能,刻印出大量钛\n[yellow]+75钛(+25%/共鸣等级)",
            4,
            listOf(ItemStack(Items.titanium, 100)),
            0.25f,
            50
        ))
        add(ResourcesEffect(
            "生钍",
            "[cyan]对这块充满辐射的星铭输入铭能,刻印出大量钍\n[yellow]+75钍(+25%/共鸣等级)",
            5,
            listOf(ItemStack(Items.thorium, 100)),
            0.25f,
            50
        ))
        add(ResourcesEffect(
            "生金",
            "[cyan]电光涌动..向着块星铭输入铭能就会出现一些金色合金\n[yellow]+25巨浪合金(+25%/共鸣等级)",
            6,
            listOf(ItemStack(Items.surgeAlloy, 100)),
            0.25f,
            50
        ))
        add(FunEffect(
            "喵！",
            "[cyan]喵！\n[yellow]喵喵！",
            7,
            5,
            1f
        ) {
            Groups.player.forEach {
                it.sendMessage("喵".repeat(second.toInt().coerceAtLeast(1).coerceAtMost(100)), first)
            }
        })
        add(CoreEffect(
          "坚石",
          "[cyan]一股强大的铭能支撑你的核心[yellow]所有己方核心增加20%已损生命值(+10%/共鸣等级)",
            8,
            levelRate = 0.1f
        ) {
            val list = buildList {
                first.team().cores().forEach {
                    it.health += ((it.maxHealth - it.health) * 0.2f * second).coerceAtLeast(1f)
                    add(it.id)
                    add(it.health.toInt())
                }
            }
            Call.buildHealthUpdate(IntSeq(list.toIntArray()))
        })
        add(CoreEffect(
            "希冀",
            "[cyan]他的希望。[yellow]所有己方核心增加200%已损生命值(+10%/共鸣等级)\n对核心周围进行导弹打击",
            9,
            10,
            0.1f
        ) {
            val list = buildList {
                first.team().cores().forEach {
                    it.health += ((it.maxHealth - it.health) * 2f * second).coerceAtLeast(1f)
                    add(it.id)
                    add(it.health.toInt())
                    launch(Dispatchers.game) {
                        repeat(12) { i ->
                            (Blocks.scathe as ItemTurret).ammoTypes[Items.carbide].spawnUnit.spawn(it, it.team())
                                .apply {
                                    rotation(360f * i / 12)
                                    add()
                                }
                        }
                    }
                }
            }
            Call.buildHealthUpdate(IntSeq(list.toIntArray()))
        })
        add(UnitEffect(
            "超频",
            "[cyan]使用铭能扰乱机械构造\n[yellow]控制的单位获得60s超频与15s加速(+10%/共鸣等级)",
            10,
            levelRate = 0.1f,
        ) {
            first.unit().infApply(StatusEffects.overclock, 60f * second * 60f)
            first.unit().infApply(StatusEffects.fast, 15f * second * 60f)
        })
        add(UnitEffect(
            "过载",
            "[cyan]使用铭能扰乱机械构造\n[yellow]控制的单位获得过载\n受到50%最大生命值的真实伤害",
            11,
            levelRate = 0.1f,
        ) {
            first.unit().infApply(StatusEffects.overdrive, 60f * second * 60f)
            first.unit().health -=  first.unit().maxHealth * 0.5f
        })
        add(UnitEffect(
            "御守",
            "[cyan]使用铭能加强机械结构\n[yellow]控制的单位获得30s保护(+10%/共鸣等级)",
            12,
            levelRate = 0.1f,
        ) {
            first.unit().infApply(StatusEffects.shielded, 30f * second * 60f)
        })
        add(UnitEffect(
            "燃尽",
            "[cyan]对机械强行注入大量铭能\n[yellow]控制的单位获得30s无敌\n无敌结束后抹杀该单位并封禁使用者15min",
            13,
            5,
        ) {
            first.unit().infApply(StatusEffects.invincible, 30f * 60f)
            launch(Dispatchers.game) {
                val unit = first.unit()
                delay(1_000)
                while (unit.hasEffect(StatusEffects.invincible)) {
                    yield()
                }
                unit.destroy()
                launch(Dispatchers.IO) a@{
                    val profile = withContext(Dispatchers.IO) {
                        transaction { PlayerData.findByIdWithTransaction(first.uuid()) }?.profile
                    } ?: return@a
                    withContext(Dispatchers.IO) {
                        transaction {
                            PlayerBan.create(profile, Duration.ofMinutes(15),"燃尽生命..\n但真的值得吗", null)
                        }
                    }
                    Groups.player.filter { PlayerData[it.uuid()].profile == profile }.forEach {
                        it.kick("Ouch!", 0)
                    }
                }
            }
        })
        add(ResourcesEffect(
            "生物",
            "[cyan]与矿物星球共鸣,使其献出体内矿物\n[yellow]+100大部分原材料(+25%/共鸣等级)",
            14,
            listOf(ItemStack(Items.copper, 100),
                ItemStack(Items.lead, 100),
                ItemStack(Items.titanium, 100),
                ItemStack(Items.thorium, 100),
                ItemStack(Items.beryllium, 100),
                ItemStack(Items.tungsten, 100)
            ),
            0.25f,
            2
        ))
        add(ResourcesEffect(
            "创物",
            "[cyan]与文明星球共鸣,使其偷走文明的一部分工业成品\n[yellow]+100大部分生产成品(+25%/共鸣等级)",
            15,
            listOf(ItemStack(Items.silicon, 100),
                ItemStack(Items.surgeAlloy, 100),
                ItemStack(Items.plastanium, 100),
                ItemStack(Items.phaseFabric, 100),
                ItemStack(Items.carbide, 100),
                ItemStack(Items.oxide, 100),
                ItemStack(Items.graphite, 100),
                ItemStack(Items.metaglass, 100)
            ),
            0.25f,
            2
        ))
        add(CoreEffect(
            "回溯",
            "[cyan]改变时间的力量..真的能拯救一切吗[yellow]释放时对己方核心资源进行记录\n60s过后若有资源数量低于记录数量则将其数量复原\n若有记录的资源已被用完则将核心设置为1血",
            16,
            20,
            0.1f
        ) {
            launch(Dispatchers.game) {
                val map = MapManager.current.randomId
                val core = first.team()?.core() ?: return@launch
                core.items.each { item, amount ->
                    if (amount == 0) return@each
                    launch a@{
                        delay(60_000)
                        if (MapManager.current.randomId != map) return@a
                        if (core.items[item] < amount) {
                            if (core.items[item] > 0) {
                                core.items.set(item, amount)
                                Call.sendMessage("${item.emoji()} [yellow]已经回溯至 $amount")
                            } else {
                                val list = buildList {
                                    core.team().cores().forEach {
                                        it.health = 1f
                                        add(it.id)
                                        add(1)
                                    }
                                }
                                Call.buildHealthUpdate(IntSeq(list.toIntArray()))
                                Call.sendMessage("${item.emoji()} [red]已经用光..回溯反噬")
                            }
                        }
                    }
                }
            }
        })
        add(UnitEffect(
            "扰乱",
            "[cyan]使用铭能干扰机械结构\n[yellow]对24格内单位给予30s麻痹(+10%/共鸣等级)",
            17,
            levelRate = 0.1f,
        ) {
            Units.nearby(null, first.x, first.y, 24 * second * 8f) {
                it.infApply(StatusEffects.electrified, 30f * 60f * second)
            }
        })
        add(UnitSpawnEffect(
            "众影",
            "[cyan]刻印指定单位\n生成1只独影采矿机(+20%/共鸣等级, 向下取整)",
        18,
            80,
            0.2f
        ) {
            repeat(floor(second).toInt().coerceAtLeast(1).coerceAtMost(100)) {
                UnitTypes.mono.create(first.team()).apply {
                    team = first.team()
                    set(first.unit())
                    add()
                }
            }
        })
        add(UnitEffect(
            "瘟疫",
            "[cyan]使用铭能破坏机械结构\n[yellow]对24格内敌方单位给予30s腐蚀(+10%/共鸣等级)\n且在死亡时传播腐蚀效果",
            19,
            40,
            0.1f,
        ) {
            Units.nearby(null, first.x, first.y, 24 * second * 8f) {
                if (it.team != first.team())
                    it.infApply(StatusEffects.corroded, 30f * 60f * second)
                launch(Dispatchers.game) {
                    while (!it.dead) {
                        yield()
                    }
                    Units.nearby(null, it.x, it.y, 6 * 8f * second) {
                        it.infApply(StatusEffects.corroded, 10f * 60f  * second)
                    }
                }
            }
        })
        add(UnitEffect(
            "星护",
            "[cyan]刻印大量微缩行星守护单位\n[yellow]生成一面单位最大生命值50%(+5%/共鸣等级)的护盾\n护盾持续时间内获得保护buff\n盾碎之后对24格内单位(包括友方)给予10s缴械(+5%/共鸣等级)",
            20,
            5,
            0.05f,
        ) {
            launch(Dispatchers.game) {
                val unit = first.unit()
                unit.shield += unit.maxHealth * 0.5f * second
                delay(100)
                while (unit.shield > 0) {
                    unit.apply(StatusEffects.shielded, 5 * 60f)
                    yield()
                }
                Units.nearby(null, unit.x, unit.y, 24 * second * 8f) {
                    it.infApply(StatusEffects.disarmed, 10f * 60f * second)
                }
            }
        })
        add(UnitEffect(
            "触点",
            "[cyan]牵一发而动全身\n[yellow]控制的单位获得boss效果\n死亡时,所有己方单位-50%生命值",
            21,
            20,
        ) {
            launch(Dispatchers.game) {
                val unit = first.unit()
                unit.infApply(StatusEffects.boss, 5 * 60f)
                delay(100)
                while (!unit.dead) {
                    yield()
                }
                unit.team.data().units.forEach {
                    it.health /= 2
                }
            }
        })
        add(NormalEffect(
            "[red]??[]",
            "[lightgray]伪典·焉龙啸\n[red]ERROR 无法检索数据库",
            22,
            1,
        ) {
            launch(Dispatchers.game) {
                val unit = first.unit()
                if (unit.spawnedByCore) {
                    first.sendMessage("[red]此单位无法施展..")
                    return@launch
                }
                if (unit.team().data().units.filterNot { it.spawnedByCore }.maxOf { it.maxHealth } > unit.maxHealth) {
                    first.sendMessage("[red]此单位过于弱小..")
                    return@launch
                }
                //25.1s
                unit.apply(StatusEffects.slow, 999f * 60f)
                repeat(10) {
                    repeat(20) {
                        repeat(5) {
                            Call.effect(Fx.itemTransfer, Vars.world.width() * Random.nextFloat() * 8f, Vars.world.height() * Random.nextFloat() * 8f, 0f, Colors.getColors().values().toList().random(), unit)
                        }
                        delay(50)
                    }
                }
                //15.1s
                repeat(10) { i ->
                    repeat(20) {
                        Call.effect(Fx.lightBlock, unit.x + Random.nextInt(it * -32 - 1, it * 32), unit.y + Random.nextInt(it * -32 - 1, it * 32), 1 + i * 2f, Colors.getColors().values().toList().random())
                        delay(50)
                    }
                }
                //5.1s
                val vec = Vec2(unit.x, unit.y)

                repeat(51) {
                    if (unit.dead) return@launch
                    repeat(2) {
                        Call.createBullet(UnitTypes.corvus.weapons[0].bullet, unit.team, unit.x, unit.y, unit.rotation + Random.nextInt(-30, 30),
                            UnitTypes.corvus.weapons[0].bullet.damage, 1f, 1f)
                        Call.createBullet(UnitTypes.conquer.weapons[0].bullet, unit.team, unit.x, unit.y, Random.nextInt(360) * 1f,
                            UnitTypes.conquer.weapons[0].bullet.damage, 2f, 1f)
                    }
                    unit.set(vec.x + Random.nextInt((-1 * it -1) * 8, it * 8), vec.y + Random.nextInt((-1 * it -1) * 8, it * 8))
                    repeat(8) {
                        Call.createBullet(UnitTypes.navanax.weapons[4].bullet, first.team(), Vars.world.width() * 8f * Random.nextFloat(), Vars.world.height()* 8f  * Random.nextFloat(), 360 * Random.nextFloat(), UnitTypes.navanax.weapons[4].bullet.damage, 0f, 2f)
                    }
                    unit.player?.let {
                        Call.setPosition(it.con, unit.x, unit.y)
                    }
                    delay(100)
                }
                unit.kill()
                Call.effect(Fx.unitEnvKill, unit.x, unit.y, 0f, Color.red)
            }
        })
        add(UnitEffect(
            "双子",
            "使用铭能刻印出指定机械克隆体[lightgray]那闪耀却暗淡无光的双子星..\n[yellow]控制的单位将会分裂出50%最大生命的同类单位\n若双方距离大于32格(-1%/共鸣等级,词缀反向增幅,最小6格)将会获得debuff\n若任何一个死亡,另一个会有90%概率一同死亡,10%概率获得全正面buff并翻倍最大生命",
            23,
            15,
            0.01f
        ) {
            launch(Dispatchers.game) {
                val unit = first.unit()
                if (unit.spawnedByCore) {
                    first.sendMessage("[red]此单位无法施展..")
                    return@launch
                }
                val other = unit.type.create(unit.team).apply {
                    maxHealth = unit.maxHealth * 0.5f
                    clampHealth()
                    set(unit)
                    add()
                }
                delay(100)
                while (!unit.dead && !other.dead) {
                    if (unit.dst(other) > (32f / second).coerceAtLeast(6f) * 8f) {
                        listOf(unit, other).forEach {
                            it.apply(StatusEffects.tarred, 5 * 60f)
                            it.apply(StatusEffects.freezing, 5 * 60f)
                            it.apply(StatusEffects.wet, 5 * 60f)
                        }
                    }
                    yield()
                }
                val left = if (unit.dead) other else unit
                if (Random.nextFloat() >= 0.1f) {
                    left.kill()
                } else {
                    left.apply {
                        apply(StatusEffects.overclock, Float.POSITIVE_INFINITY)
                        apply(StatusEffects.overdrive, Float.POSITIVE_INFINITY)
                        apply(StatusEffects.boss, Float.POSITIVE_INFINITY)
                        apply(StatusEffects.shielded, Float.POSITIVE_INFINITY)
                        maxHealth *= 2
                    }
                }
            }
        })
        add(UnitSpawnEffect(
            "幻影",
            "[cyan]刻印指定单位\n[yellow]生成3只幻型建造机(+20%/共鸣等级, 向下取整)\n在生成后60s死亡",
            24,
            80,
           0.2f
        ) {
            repeat(floor(3 * second).toInt().coerceAtLeast(1).coerceAtMost(100)) {
                UnitTypes.poly.create(first.team()).apply {
                    set(first.unit())
                    add()
                    launch(Dispatchers.game) {
                        delay(60_000)
                        kill()
                    }
                }
            }
        })
        add(NormalEffect(
            "破晓",
        "[cyan]向星空发射大量铭能请求支援\n[lightgray]带来希望..亦或是,绝望?\n[yellow]在300~600s(词缀反向增幅)后触发随机效果\n[lightgray]但不保证能解决当前的困境",
        25,
            20,
            0f
        ) {
            launch(Dispatchers.game) {
                val team = first.team()
                val map = MapManager.current.randomId
                delay((Random.nextLong(300_000, 600_000) / second).toLong().coerceAtLeast(10_000))
                if (MapManager.current.randomId != map) return@launch
                Call.sendMessage("[#${team.color}]破晓之光抵达！")
                val rand = Random.nextFloat()
                when {
                    rand > 0.75f -> {
                        Call.sendMessage(buildString {
                            appendLine("[white]-------------------")
                            appendLine("[yellow]破晓·横财")
                            appendLine("资源翻1.5倍！")
                            append("[white]-------------------")
                        })
                        team.core()?.let {
                            it.items.each { item, amount ->
                                it.items.add(item, amount / 2)
                            }
                        }
                    }
                    rand > 0.5f -> {
                        Call.sendMessage(buildString {
                            appendLine("[white]-------------------")
                            appendLine("[yellow]破晓·兵援")
                            appendLine("获得一队st3小队支援！")
                            append("[white]-------------------")
                        })
                        team.core()?.let {
                            listOf(UnitTypes.quasar, UnitTypes.fortress, UnitTypes.mega, UnitTypes.zenith, UnitTypes.spiroct).random().apply {
                                repeat(8) { i ->
                                    spawnAround(it, team)
                                }
                            }
                        }
                    }
                    rand > 0.25f -> {
                        Call.sendMessage(buildString {
                            appendLine("[white]-------------------")
                            appendLine("[yellow]破晓·厄兆")
                            appendLine("全部单位减半生命值！")
                            append("[white]-------------------")
                        })
                        Groups.unit.forEach {
                            it.health /= 2
                        }
                    }
                    else -> {
                        Call.sendMessage(buildString {
                            appendLine("[white]-------------------")
                            appendLine("[yellow]破晓·无声")
                            appendLine("无事发生！")
                            append("[white]-------------------")
                        })
                    }
                }
            }
        })
        add(UnitEffect(
            "献祭",
            "[cyan]使用大量铭能干扰机械构成\n[yellow]控制的单位死亡,但有30%(+1%/共鸣等级)概率生成5个相同单位",
            26,
            50,
            0.01f,
        ) {
            launch(Dispatchers.game) {
                val unit = first.unit()
                unit.kill()
                if (Random.nextFloat() < 0.3 * second) {
                    repeat(5) {
                        unit.type.create(unit.team()).apply {
                            set(unit)
                            add()
                        }
                    }
                }
            }
        })
        add(UnitSpawnEffect(
            "陨星",
            "[cyan]刻印指定单位\n[yellow]生成1只恒星战斗修复机(+20%/共鸣等级, 向下取整)",
            27,
            50,
            0.2f,
        ) {
            repeat(floor(second).toInt().coerceAtLeast(1).coerceAtMost(100)) {
                UnitTypes.pulsar.create(first.team()).apply {
                    set(first.unit())
                    add()
                }
            }
        })
        add(UnitSpawnEffect(
            "替身",
            "[cyan]使用铭能包裹核心机使其成为新单位\n[yellow]将你的核心机替换为巨像,拥有1000点护盾(+10%/共鸣等级),仅生效一次",
            28,
            25,
            0.1f,
        ) {
            UnitTypes.mega.create(first.team()).apply {
                set(first.unit())
                shield += 1000 * second
                add()
                first.unit(this)
                spawnedByCore = true
            }
        })
        add(NormalEffect(
            "君临",
            "[cyan]将铭能化为帝君之息\n向前方发射三枚帝君炮弹,伤害150%(+10%/共鸣等级)",
            29,
            45,
            0.1f
        ) {
            Call.createBullet(UnitTypes.collaris.weapons[0].bullet, first.team(), first.x, first.y, first.unit().rotation, UnitTypes.collaris.weapons[0].bullet.damage * 1.5f * second, 1f, 1f)
            Call.createBullet(UnitTypes.collaris.weapons[0].bullet, first.team(), first.x, first.y, first.unit().rotation + 15, UnitTypes.collaris.weapons[0].bullet.damage * 1.5f * second, 1f, 1f)
            Call.createBullet(UnitTypes.collaris.weapons[0].bullet, first.team(), first.x, first.y, first.unit().rotation - 15, UnitTypes.collaris.weapons[0].bullet.damage * 1.5f * second, 1f, 1f)
        })
        add(NormalEffect(
            "重整",
            "[cyan]播撒大量铭能协助修复虚影建筑\n[yellow]拥有10000点修复能量(+10%/共鸣等级)\n每2s随机修复场上被破坏的一个虚影,每次消耗其最大生命值的修复能量\n每2s固定消耗100点能量",
            30,
            10,
            0.1f
        ) {
            launch(Dispatchers.game) {
                var energy = 10000 * second
                val team = first.team()
                val map = MapManager.current.randomId
                while (energy > 0) {
                    if (MapManager.current.randomId != map) return@launch
                    if (!team.data().plans.isEmpty) {
                        team.data().plans.toList().random().apply {
                            val block = Vars.content.getByID<Block>(ContentType.block, block.toInt())
                            if (energy >= block.health) {
                                energy -= block.health
                                val tile = Vars.world.tile(x.toInt(), y.toInt())
                                Vars.world.tile(x.toInt(), y.toInt()).setNet(block, team, rotation.toInt())
                                Call.tileConfig(null, tile.build, config)
                            }
                            team.data().plans.remove(this)
                        }
                    }
                    energy -= 100
                    delay(2000)
                }
                Call.sendMessage("[yellow]${first.name} 的重整效果已经结束")
            }
        })
        add(UnitEffect(
            "斩杀",
            "[cyan]使用大量铭能引发机械体自爆\n[yellow]若波次队伍仅剩1(+10%/共鸣等级)个及以下单位,秒杀它(们)",
            31,
            50,
            0.1f,
        ) {
            val team = Vars.state.rules.waveTeam
            if (team.data().units.size <= 1 * second) {
                team.data().units.forEach {
                    it.kill()
                }
            }
        })
        add(UnitEffect(
            "幻移",
            "[cyan]标记当前地点并且在稍后将单位重刻至此\n[yellow]标记当前地点与单位\n60s后或单位血量<10%将其传送回标记点",
            32,
            50,
            0.1f,
        ) {
            launch(Dispatchers.game) {
                val unit = first.unit()
                val tile = unit.tileOn()
                val time = Time.millis()
                while (Time.timeSinceMillis(time) <= 60_000) {
                    if (unit.health < unit.maxHealth * 0.1f) break
                    yield()
                }
                unit.set(tile)
            }
        })
        add(UnitEffect(
            "幻殇",
            "[cyan]标记当前单位血量并在稍后修复单位\n[yellow]标记当前血量与单位\n60s后或单位血量<10%将其血量回复至标记",
            33,
            50,
            0.1f,
        ) {
            launch(Dispatchers.game) {
                val unit = first.unit()
                val health = unit.health
                val time = Time.millis()
                while (Time.timeSinceMillis(time) <= 60_000) {
                    if (unit.health < unit.maxHealth * 0.1f) break
                    yield()
                }
                if (health > unit.health) unit.health = health
            }
        })
        add(UnitSpawnEffect(
            "神堡",
            "[cyan]刻印指定单位\n[yellow]生成一只堡垒,其拥有4000点护盾(+10%/共鸣等级)与永久的缓慢麻痹",
            34,
            40,
            0.1f,
        ) {
            launch(Dispatchers.game) {
                UnitTypes.fortress.spawn(first.unit()).apply {
                    team = first.team()
                    shield += 4000 * second
                    apply(StatusEffects.slow, 9999999f)
                    apply(StatusEffects.electrified, 9999999f)
                }
            }
        })
        add(NormalEffect(
            "熄火",
            "[cyan]投放大量铭能熄灭火焰\n[yellow]熄灭全场所有火焰并在下5s(+20%/共鸣等级)持续熄灭火焰",
            35,
            60,
            0.2f,
        ) {
            launch(Dispatchers.game) {
                Groups.fire.forEach {
                    it.remove()
                }
                val startTime = Time.millis()
                while (Time.millis() <= startTime + (5_000 * second).toLong()) {
                    Groups.fire.forEach {
                        it.remove()
                    }
                    delay(100)
                }
            }
        })
        add(NormalEffect(
            "磁暴",
            "[cyan]用铭能扰乱磁场引发磁暴\n[yellow]原地释放5(+10%/共鸣等级)次龙王主炮弹,间隔3s",
            36,
            45,
            0.1f
        ) {
            launch(Dispatchers.game) {
                val map = MapManager.current.randomId
                val x = first.x
                val y = first.y
                val t = first.team()
                repeat((5 * second).toInt()) {
                    if (MapManager.current.randomId != map) return@launch
                    Call.createBullet(UnitTypes.navanax.weapons[4].bullet,
                        t,
                        x,
                        y,
                        360 * Random.nextFloat(),
                        UnitTypes.navanax.weapons[4].bullet.damage,
                        0f,
                        0.1f)
                    delay(3000)
                }
            }
        })
        add(UnitSpawnEffect(
            "战车",
            "[cyan]刻印指定单位\n[yellow]生成一只围护,其拥有1(+5%/共鸣等级,向下取整)个bossbuff",
            37,
            40,
            0.05f,
        ) {
            launch(Dispatchers.game) {
                UnitTypes.stell.spawn(first.unit()).apply {
                    team = first.team()
                    repeat(floor(1f * second).toInt()) {
                        infApply(StatusEffects.boss, Float.POSITIVE_INFINITY)
                    }
                }
            }
        })
        add(NormalEffect(
            "弹幕",
            "[cyan]将铭能凝聚为大量子弹\n[yellow]向控制的单位前方发射20(+10%/共鸣等级)发随机弹幕",
            38,
            20,
            0.2f,
        ) {
            launch(Dispatchers.game) {
                val map = MapManager.current.randomId
                val unit = first.unit()
                val bullets = listOf(
                    UnitTypes.fortress.weapons[0].bullet,
                    UnitTypes.scepter.weapons[0].bullet,
                    UnitTypes.reign.weapons[0].bullet,
                    UnitTypes.quasar.weapons[0].bullet,
                    UnitTypes.corvus.weapons[0].bullet,
                    UnitTypes.toxopid.weapons[2].bullet,
                    UnitTypes.antumbra.weapons[0].bullet,
                    UnitTypes.zenith.weapons[0].bullet,
                    UnitTypes.eclipse.weapons[0].bullet,
                    UnitTypes.eclipse.weapons[2].bullet,
                    UnitTypes.poly.weapons[0].bullet,
                    UnitTypes.mega.weapons[0].bullet,
                    UnitTypes.bryde.weapons[0].bullet,
                    UnitTypes.cyerce.weapons[0].bullet,
                    UnitTypes.minke.weapons[2].bullet,
                    UnitTypes.sei.weapons[0].bullet,
                    UnitTypes.sei.weapons[2].bullet,
                    UnitTypes.omura.weapons[0].bullet,
                    UnitTypes.navanax.weapons[4].bullet,
                    UnitTypes.stell.weapons[0].bullet,
                    UnitTypes.locus.weapons[0].bullet,
                    UnitTypes.precept.weapons[0].bullet,
                    UnitTypes.vanquish.weapons[0].bullet,
                    UnitTypes.conquer.weapons[0].bullet,
                    UnitTypes.tecta.weapons[0].bullet,
                    UnitTypes.collaris.weapons[0].bullet,
                )
                repeat((20 * second).toInt()) {
                    unit.apply {
                        if (dead || !isValid) return@launch
                        val bullet = bullets.random()
                        Call.createBullet(bullet, team, x + (sin(rotation) * 2f * Random.nextFloat() - sin(rotation)) * 2f * hitSize, y + (cos(rotation) * 2f * Random.nextFloat() - cos(rotation)) * 2f * hitSize, rotation, bullet.damage, 1f, 1f)
                    }
                    delay(50)
                }
            }
        })
        add(UnitEffect(
            "净化",
            "[cyan]使用铭能清洗单位结构\n[yellow]清除当前单位的1(+50%/共鸣等级)个buff",
            39,
            40,
            0.5f,
        ) {
            val unit = first.unit()
            val statuses = unit.statuses
            repeat((1 * second).toInt()) {
                if (!statuses.isEmpty) {
                    val status = statuses[0]
                    statuses.remove(0)
                    Call.label(status.effect.emoji(), 3f, unit.x + Random.nextInt(-32, 32), unit.y + Random.nextInt(-32, 32))
                }
            }
        })
        add(UnitEffect(
            "凛冬",
            "[cyan]使用铭能干扰机械结构\n[yellow]对24格内单位给予40s冰冻(+10%/共鸣等级)",
            40,
            levelRate = 0.1f,
        ) {
            Units.nearby(null, first.x, first.y, 24 * second * 8f) {
                it.infApply(StatusEffects.freezing, 40f * 60f * second)
            }
        })
        add(FunEffect(
            "卤罐",
            "[cyan]我是卤罐大王\n[yellow]如果可能,给当前单位添加一个卤罐",
            41,
            10,
            0.2f,
        ) {
            val unit = first.unit()
            if (unit is Payloadc) {
                val pay = BuildPayload(Blocks.reinforcedLiquidContainer, unit.team)
                unit.addPayload(pay)
                pay.build.apply {
                    liquids.add(Liquids.ozone, 999f * Random.nextFloat() * second)
                }
            }
        })
    }

    val target = contextScript<inscription.Effect>().effects
    target.clear()
    effects.forEach {
        target[it.id] = it
    }
}
var builded = false
listen(EventType.Trigger.update) {
    if (!builded) {
        builded = true
        build()
        Log.info("[green]effect生成完毕")
    }
}

command("buildEffect", "构建Skill效果") {
    permission = id.replace("/", ".")
    body {
        build()
    }
}

