@file:Depends("inscription/prefix")
@file:Depends("xkldklp/inscription/prefixes/normal")
@file:Depends("xkldklp/inscription/prefixes/extraEffect")
@file:Depends("xkldklp/inscription/prefixes/dynamic")
@file:Depends("xkldklp/inscription/prefixes/inscriptionOP")
@file:Depends("coreLibrary/DBApi", "数据库服务")
@file:Depends("wayzer")
@file:Depends("wayzer/user/ban")
@file:Depends("xkldklp/user/inscription")

package inscription.reg

import arc.util.Log
import coreMindustry.lib.broadcast
import coreMindustry.lib.command
import coreMindustry.lib.listen
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import org.jetbrains.exposed.sql.transactions.transaction
import wayzer.lib.dao.PlayerData
import wayzer.user.PlayerBan
import xkldklp.inscription.prefixes.Dynamic.*
import xkldklp.inscription.prefixes.ExtraEffect.*
import xkldklp.inscription.prefixes.Normal.*
import xkldklp.inscription.prefixes.InscriptionOP.*
import java.time.Duration
import java.util.*
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random
import xkldklp.user.Inscription

val inscription = contextScript<Inscription>()

fun build() {
    val prefix = buildList {
        add(NormalPrefix(
            "普通",
            "属性无增幅",
            1,
            1f,
            400
        ))
        add(NormalPrefix(
            "增幅",
            "属性提升20%",
            2,
            1.2f,
            50
        ))
        add(NormalPrefix(
            "弱化",
            "属性降低20%",
            3,
            1.2f,
            50
        ))
        add(NormalPrefix(
            "闪耀",
            "属性提升40%",
            4,
            1.4f,
            25
        ))
        add(NormalPrefix(
            "璀璨",
            "属性提升60%",
            5,
            1.6f,
            10
        ))
        add(NormalPrefix(
            "耀眼",
            "属性提升80%",
            6,
            1.8f,
            5
        ))
        add(NormalPrefix(
            "星辰",
            "属性提升100%",
            7,
            2f,
            1
        ))
        add(ExtraEffectPrefix(
            "牺牲",
            "封禁自己12小时, 属性提升300%",
            8,
            4f,
            5
        ) {
            launch(Dispatchers.IO) {
                val profile = withContext(Dispatchers.IO) {
                    transaction { PlayerData.findByIdWithTransaction(first.uuid()) }?.profile
                } ?: return@launch
                withContext(Dispatchers.IO) {
                    transaction {
                        PlayerBan.create(profile, Duration.ofHours(12L),"牺牲自己..提升了星铭威力", null)
                    }
                }
                Groups.player.filter { PlayerData[it.uuid()].profile == profile }.forEach {
                    it.kick("Ouch!", 0)
                }
            }
        })
        add(ExtraEffectPrefix(
            "落辉",
            "[red]永久封禁自己, 属性提升99900%",
            9,
            1000f,
            1
        ) {
            launch(Dispatchers.IO) {
                val profile = withContext(Dispatchers.IO) {
                    transaction { PlayerData.findByIdWithTransaction(first.uuid()) }?.profile
                } ?: return@launch
                withContext(Dispatchers.IO) {
                    transaction {
                        PlayerBan.create(profile, Duration.ofHours(1145141919810L),"牺牲自己..提升了星铭威力\n[red]但真的值得吗[]", null)
                    }
                }
                Groups.player.filter { PlayerData[it.uuid()].profile == profile }.forEach {
                    it.kick("Ouch!", 0)
                }
            }
        })
        add(DynamicPrefix(
            "愚者",
            "属性提升-50% ~ 200%",
            10,
            20
        ) {
            rate = Random.nextInt(-50, 200) / 100f
        })
        add(ExtraEffectPrefix(
            "专注",
            "属性提升40%，操控的单位无法移动20秒",
            11,
            1.4f,
            20
        ) {
           first.unit().apply(StatusEffects.unmoving, 20 * 60f)
        })
        add(ExtraEffectPrefix(
            "融毁",
            "属性提升40%，操控的单位融化30秒",
            12,
            1.4f,
            20
        ) {
            first.unit().apply(StatusEffects.melting, 20 * 60f)
        })
        add(ExtraEffectPrefix(
            "星芒",
            "属性降低20%,生成1-3只独影采矿机",
            13,
            0.8f,
            30
        ) {
            repeat(Random.nextInt(1, 4)) {
                UnitTypes.mono.create(first.team()).apply {
                    set(first.unit())
                    add()
                }
            }
        })
        add(ExtraEffectPrefix(
            "磁场",
            "属性降低20%,发射一枚延迟爆炸的龙王炮弹",
            14,
            0.8f,
            30
        ) {
            Call.createBullet(UnitTypes.navanax.weapons[4].bullet, first.team(), first.x, first.y, 360 * Random.nextFloat(), UnitTypes.navanax.weapons[4].bullet.damage, 0f, 2f)
        })
        add(DynamicPrefix(
            "独狼",
            "属性提升400%,每有一名其他玩家降低50%,最低-50%",
            15,
            30
        ) {
            rate = (4 - (Groups.player.size() - 1) * 0.5f).coerceAtLeast(0.5f)
        })
        add(DynamicPrefix(
            "狼群",
            "属性降低50%,每有一名其他玩家增加20%,最高200%",
            16,
            10
        ) {
            rate = (0.5f + (Groups.player.size() - 1) * 0.2f).coerceAtMost(3f)
        })
        add(ExtraEffectPrefix(
            "蓄力",
            "属性提升50%,技能效果延迟20s",
            17,
            1.5f,
            50
        ) {
            delay(20_000)
        })
        add(ExtraEffectPrefix(
            "延时",
            "属性提升20%,技能效果延迟5s",
            18,
            1.2f,
            50
        ) {
            delay(5_000)
        })
        add(ExtraEffectPrefix(
            "急速",
            "获得10s加速效果",
            19,
            1f,
            40
        ) {
            first.unit().apply(StatusEffects.fast, 10f * 60f)
        })
        add(InscriptionOPPrefix(
            "无效",
            "属性降低90%，但有50%概率能再次释放",
            20,
            0.1f,
            20
        ) {
            if (Random.nextBoolean()) {
                inscription.used[second.second]=false
                Call.label("[green]无效化！", 2f, first.x, first.y)
            }
        })
    }

    val target = contextScript<inscription.Prefix>().prefixes
    target.clear()
    prefix.forEach {
        target[it.id] = it
    }
}
var builded = false
listen(EventType.Trigger.update) {
    if (!builded) {
        builded = true
        build()
        Log.info("[green]prefix生成完毕")
    }
}

command("buildPrefix", "构建Skill前缀") {
    permission = id.replace("/", ".")
    body {
        build()
    }
}



