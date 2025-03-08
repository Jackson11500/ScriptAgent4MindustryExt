@file:Import("@coreMindustry/util/tools.kt", sourceFile = true)
@file:Depends("xkldklp/user/inscription")
@file:Depends("xkldklp/user/spinscription")
@file:Depends("wayzer/user/achievement", "成就")
package mapScript

import arc.Events
import arc.graphics.Color
import arc.math.geom.Vec2
import arc.util.Time
import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import coreMindustry.lib.listen
import coreMindustry.util.buildLineBar
import coreMindustry.util.getRomanNum
import coreMindustry.util.polyBuild
import coreMindustry.util.worldLabelMessage
import mindustry.Vars
import mindustry.ai.types.FlyingAI
import mindustry.content.*
import mindustry.entities.units.StatusEntry
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.*
import mindustry.type.UnitType
import mindustry.world.Block
import mindustry.world.Build
import mindustry.world.Tile
import mindustry.world.blocks.defense.turrets.ItemTurret
import mindustry.world.blocks.defense.turrets.ItemTurret.ItemEntry
import mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild
import mindustry.world.blocks.defense.turrets.Turret.AmmoEntry
import mindustry.world.blocks.payloads.BuildPayload
import mindustry.world.blocks.payloads.UnitPayload
import mindustry.world.blocks.storage.CoreBlock
import wayzer.MapManager
import wayzer.lib.dao.PlayerData
import xkldklp.user.Inscription
import xkldklp.user.Spinscription
import kotlin.random.Random

val achievement = contextScript<wayzer.user.Achievement>()
val inscription = contextScript<Inscription>()
val spinscription = contextScript<Spinscription>()
val ts = contextScript<_100002>()

val msch_coreMiner = "bXNjaAF4nDWV2XIbRRiFWyPNIsnSSKN9Hy9JvMkmgTfgMSguFEsQVcmSka1QvB0U17wKFBdcUCkQ/+kvlDL+Mn3O6f67p6fHfeVeFV1pu3hcucJbV12unh/266eX9W7rnIs2i/erzbMLvvm24tKH3X41/2532C4XXg8/Lg6bF9d62iyeXxbb9eFx/rDbflz9tNu76ns1zpf79Wbjapv1D4f1cr7fHV5Wexeba3lYv7jK0+7H1X6+3S1XLn1cPXywTh4Wm/nT4fHJ1d/v18vvV/P/zdHzbq+0K3xtl/3sX+BCZ39KdgVCEZRAKIRoIVqIFqJFaBFahBahxWgxWowWoyVoCVqClqCV0cpoZbQyWgWtglZBq6BV0apoVbSqtKI7sZ81VF3qrKHqGq4YWJMPnBA4IXCiQGhogKacNZw1nDWcNZw1s3hkctZx1nHWcdbljAxpUvCrE9r/M6XqrqVUSiollZJKmVaq1RVikIAyqIAqOAE1YEUUbRoNzTy1gay/1LWlNRivwXgNxmtoQUJDG3TkbOJs4mzibOJsmsWjK2eGM8OZ4cxwZmbx6MnZwtnC2cLZkrNoY7dVbsucVm7L9RVoE2gTaBNo03XbLB4DOTs4Ozg7ODs4O2bxGMrZxdnF2cXZlTMy9BIN3zVrIIyU6JHokeiR6CkhdJ219+1n7T0LWLznxsr1yfXJ9cn1yfWVCw1jDdR3EwUGBAYEBgQGBAYEBuYMhKkCQwJDAkMCQwJDAkNzBsJMgRGBEYERgRGBkQUKQg/0FR9ZLhByxcfEx8THxMfExwTG5gyEUwUmBCYEJgQmBCYEJuYMhDMFpgSmBKYEpgSmBKbmDIRzBWYEZgRmBGYEZrwQM70QQgoaoAky0AJt0AFd0AP2ICPDIAmOfx+POj/tmlkZQWy40HGaU01ONTnV5DqDBF9NTjU51eRUk1NNTjU51eRUk1NNTjU51eSqJjacq+tThj1l2FOGPbXDwWv+cDjVdgy0dKAISqAi5xmnyRnOc5znOM9xnutrIEQgBgkogwrwvVzQywW9XOhDI4QgAjFIQBn09UJeaLVd8fjp+MmpxFfqLjFk8fH47192/epb/VivJRbdG50kuvP7+DXaG2mRIbOTuHT8x6nJK5d0eem7NMH5gS4RrxCvXKLx/rTrN5vClT0mJ9F7rpnitU3Rjt0b7YvI7pKk6ELrT1/Rgv8Y3miZBf8tutbBqZgv85q+btRXyZDJcqN9INg+SAwdFfGHXT87Wf3reqPjKXC3FHrrC/08C922Ffndrl+cTB1FbnUGSuzFsnl/bLd9TWpOBXNL25OYu5qfjtoz5La6mOvAjQ09Nd6xAneswL1WILS7RE/jnr11Z5u/INRBChqgCTLQAm0Veecn/XlCsd36Q+1efceGju6+0F3JvXX+7H5nV0GIQAwSnRnvtLmECqjqHfpS9QoRiEECyqAC7I36D17VrSQ="
val msch_coalMiner = "bXNjaAF4nF1Va2/aQBBc7owN2OC3DZhHpKaPL3zo76n6gQJVqXiVQKv+9TRSunMjNSWKktGtZ3budvcu8kHurXiH5X4jrY8SrjcPq/P2dNkeDyLi75ZfNrsHMZ8+BxKvjufN4uvxelgv3ff0sr0sD9vrfrE6Hn5ufh/PEn7ZLR8ui/V5u9tJvNv+uG7Xi+/Xw8oJAuWtr9uL9E7HX5vz4nBcbyTeb1bfNM1quVucrvuT2v4RaYmHPy0JxAI6hC6hRwgJEaFPGBBiQkJICRkhJxSEklARaoChraGtoa2hraGtoa2hraGtoa2hraGtoa2hrVEH0xJLB0sHSwdLB0sHSwdLB0sHSweryVwWl8zTHwdtgk+ICTnBMdtktslsk9kms01mG0yjX0ZogE+BT4FPgU+BT0FASkBKQEpASkBKh5QOKR1SOqR0SOmS0iWlS0qXlC4oKH8prlRDUXJXN2gxCk7Xo65HXQ86T2EoBkUdQdCTMQQhBSEFIQUhBaEyDco/hiCUBoKIgoiCiIKIgkiZBo1qIIhkIratMO+Y58fnR9cYDKZL0GeCPhP0kQBjk4prcYEEfQwjoEHWvqZzwSm2MWCWAbMMkMVTKMAcoDJYVZAPVOeCUwZnkMeUx5THlMeqMxipSty81JDHqnPBmbhOz8XdIydPKE8oT1SnzER1gtUQp0+k+e/0vq41D8JvNPz0/CSe/uJGuoQpE6YsSspbkfJWpLwVKW9FiluBu9RHwVJNqBlSXDFAjY2knIyUk5HRIYODp5CAkqHeWGXQZRyQjAOScUBy6nLqchUYXLdM3F3Kocs5JznmBLegEfecOF1BXaECpRT6VbAqUJtC6pvaFEiA8LxjWZl/9SmZrWR9SiVbQIAaVFoYrUGpk6asEg8EIIdhyUkq+axVyOIpRPhWoXZYDSCoODsVZ6fiA1hTUCvT4HUZiHs6YghqTkuNafEVapx6SMFQmfptqEzBKsFxh5LfHHcIJcINjvtyYH0UxxrXI42087oeofOAGElH7PWIvR4rRYNj1ACrHoMJgymDrruNUjTYKEWwChlMGcxwiIbtmShFgxOluFWE3U8kvtn9BBKE61fN8vTChNBP2ZApGzJj0Wcs+oxFn7OwcxTWV4iR8k7CG6c7fEU4f1UnT+7136Pu7d7t7eXDWw0bQAsm7xQMwGD1XsEArNi/EoeVHg=="
val msch_coreIndustry = "bXNjaAF4nE1R21aDMBCcEgJpAtQf4cFf8Dc8PiCNitKkJ4D9ebXuZnuOlofJXmZ2d4o73CmUYTh57O7hjn4Z03RepxgAVPPw7OcFxePTAc05Xnzql7il0aN7TcP5bVp9f05+WVCluK0+odvC0aeXOV7612H1MO9bGLNcGz//Fw7LNE9jDP1y8jMzreiHePQoP6Y5oFpi4sphCkRd/bG/JcwW5jjQHOjPYZtX7EloHabAzWNMvn+JtMdwO+MB/DPA7g+sQAMU9O0BRUDJksAJUE0TtOQDVQ0nFbdw5JiguEbuCa/kqCaKNgU4UUHivVH0Kq4/JIbrF3eIjBYZzURDsRGWFaDknviWd63hNPKgGo3mvRS9Wn4YXt4SUKMjcAKNJFs+0XKtpb6Gj7ZsBm9QIN+jRLiU6DamkqhGJufFHBMqAiWgueZQ53VcHoqORrQ8pEG2oGGOJai1SDTcxT0W2b4C2TclUYlshpZkxQa0skMr/1vHsgVBXqVjXk1Ajl+/ydtv8thQXLFOx5Rfd7FaIg=="
val msch_aaDef1 = "bXNjaAF4nEWOzQ6CMBCEp0AU8fcJjKJRo0D0eYyHWppIUsAUYuLby2YO9vJ1ZjrdxRXXEFGjawt1w7S0nfHVu6/aBsDI6ad1HYL7I8Kqtualm8pol5e+cg6xaZuP/bYe487ovrceiWm9zbuX9uXQv+B/AiIiRkRMJMSMWBArQA0tBSUIiBBK7hGzMc2YmDBLmM1pLogls+HPcBizk3XWyGTMGoUUNshlxQ3VVpRCSjMVM8CehT2fHJgdqI4snGieWDizcOaTjFlGlbNQ0CzE/AFVFSMa"
val msch_aaDef2 = "bXNjaAF4nCWNUQrCMBBEp2mosZZaBI/hh+cRP2K6YCBNSxoEb2/ihA3svN3H4gLdQke7CJo7TrPsLvkt+zUC6IJ9SdihHs8Wh93ZnCVhWsS9bfTOhtucfAgwbo0f+a6pOFfwNeWrWqq0qvk3pdqaFDShrrDcodBxxXBmmI4UesKewkBh4MrI2ch0pjARThX+AKoQF7E="
val msch_coreWall = "bXNjaAF4nDWOWw6CMBBFh9KHA6twAXy4HuNHhSaSVCDFxO1bPKH9OJmTe6eVTrpW7BLfSZqb9FPaxzJvn3ldRMTn+Ex5F3N/GOnHddtSGb4xZ+nGtaRhf8Uy1dxVztMAA9oTBrTAAgf8AXtEmqos01866o66Q3qkR3rmgAzIwEOBZYE9geSFpPJTpaAUlIKSVLYqFa1XflMgE4E="

var name = "[white][[#00ffff][cyan]"
var nameR = "[white][[#ffff00][yellow]"

fun Player.achievement(name: String, exp: Int, b: Boolean = false) {
    val profile = PlayerData[uuid()].profile
    if (profile != null)
        achievement.finishAchievement(profile, name, exp, b)
}

fun achieveAll(name: String, exp: Int) {
    Groups.player.forEach { it.achievement(name, exp) }
}

fun Float.format(i: Int = 2): String {
    return "%.${i}f".format(this)
}

fun polyBuild(x: Int, y: Int, msch: String, amount: Int) {
    polyBuild(x * 8f, y * 8f, msch, Team.sharded, amount)
}

fun getSameSizeBuild(block: Block): Block {
    return when (block.size) {
        1 -> Blocks.arc
        2 -> Blocks.lancer
        3 -> Blocks.cyclone
        4 -> Blocks.spectre
        5 -> Blocks.multiplicativeReconstructor
        6 -> Blocks.largeLogicDisplay
        else -> Blocks.tetrativeReconstructor
    }
}

val tiles by autoInit { Vars.world.tiles.filter { it.passable() && !it.floor().isLiquid } }
fun getCoreTile(core: CoreBlock): Tile? {
    return tiles.filter {
        Build.validPlace(getSameSizeBuild(core), Team.crux, it.centerX(), it.centerY(), 0,false)
    }.randomOrNull()
}

val waveUnits by autoInit { mutableMapOf<UnitType, Int>() }
val preWaveUnits by autoInit { mutableMapOf<UnitType, Int>() }
var waveTime = 120_000L

fun spawnFromSide(unit: UnitType): mindustry.gen.Unit {
    var x = 0f
    var y = 0f
    var dx = 0f
    var dy = 0f
    listOf(
            {
                x = 0f * 8f
                y = -10f * 8f
                dx = 600f * 8f
                dy = 0f * 8f
            },
            {
                x = -10f * 8f
                y = 0f * 8f
                dx = 0f * 8f
                dy = 600f * 8f
            },
            {
                x = 610f * 8f
                y = 0f * 8f
                dx = 0f * 8f
                dy = 600f * 8f
            },
            {
                x = 0f * 8f
                y = 610f * 8f
                dx = 600f * 8f
                dy = 0f * 8f
            }
    ).random().invoke()
    val ux = x + Random.nextFloat() * dx
    val uy = y + Random.nextFloat() * dy
    val u = unit.create(Team.crux)
    u.apply {
        elevation = 1f
        //set(ux, uy)
        set(ux, uy)
        snapInterpolation()
        add()
    }
    ts.launch(Dispatchers.game) {
        if (u.controller() is FlyingAI) return@launch
        while (true) {
            if (u.tileOn() != null && u.tileOn().passable() && u.tileOn()?.floor()?.isDeep != true) {
                u.elevation = 0f
                break
            } else {
                val angle = u.angleTo(300 * 8f, 300 * 8f)
                u.elevation = 1f
                u.rotation(angle)
                u.velAddNet(Vec2(8f, 8f).setAngle(angle).setLength(((u.dst(300 * 8f, 300 * 8f) - 180 * 8f) / 50f).coerceAtLeast(0.5f)))
                u.apply(StatusEffects.slow, 5 * 60f)
                u.apply(StatusEffects.shielded, 5 * 60f)
            }
            delay(100)
        }
    }
    return u
}

var ruleLevel = 1
var ruleExp = 0f

class Rule(
    val name: String,
    val desc: String,
    val effect: () -> Unit
)

val appliedRules by autoInit { mutableListOf<Rule>() }

val lv1Rules = listOf(
        Rule("[green][阻塞干扰]", "[red]友方单位获得永久麻痹") {
            ts.loop(Dispatchers.game) {
                Team.sharded.data().units.forEach {
                    it.apply(StatusEffects.electrified, Float.POSITIVE_INFINITY)
                }
                yield()
            }
        },
        Rule("[green][孢子寄生]", "[red]友方单位获得永久孢子减速") {
            ts.loop(Dispatchers.game) {
                Team.sharded.data().units.forEach {
                    it.apply(StatusEffects.sporeSlowed, Float.POSITIVE_INFINITY)
                }
                yield()
            }
        },
        Rule("[green][末日预兆]", "[red]友方单位获得永久弱化") {
            ts.loop(Dispatchers.game) {
                Team.sharded.data().units.forEach {
                    it.apply(StatusEffects.sapped, Float.POSITIVE_INFINITY)
                }
                yield()
            }
        },
        Rule("[green][构造干扰]", "[red]建筑花费*1.25") {
            Vars.state.rules.buildCostMultiplier *= 1.25f
            Call.setRules(Vars.state.rules)
        },
        Rule("[green][惊喜快递]", "[red]随机核心爆炸!") {
            val core = Team.sharded.cores().random()
            Call.logicExplosion(Team.crux, core.x, core.y, 12 * 8f, 80f, true, true, true)
        },
        Rule("[green][直破苍穹]", "[red]敌方苍穹出厂获得两层迅捷10s") {
            val applyMap = mutableMapOf<mindustry.gen.Unit, Boolean>()
            ts.loop(Dispatchers.game) {
                Team.crux.data().units.filter { it.type == UnitTypes.zenith && !applyMap.getOrDefault(it, false) }.forEach {
                    repeat(2) { i ->
                        it.statuses.add(StatusEntry().set(StatusEffects.fast, 10 * 60f))
                        applyMap[it] = true
                    }
                }
                yield()
            }
        }
)
val lv2Rules = listOf(
        Rule("[purple][转换封锁]", "[red]120s后，电力源被摧毁") {
            ts.launch(Dispatchers.game) {
                delay(120_000L)
                while (true) {
                    Groups.build.forEach {
                        if (it.block == Blocks.powerSource) {
                            it.kill()
                        }
                    }
                    yield()
                }
            }
        },
        Rule("[purple][升阳之时]", "[red]敌方苍穹出厂获得保护10s，苍穹袭击数量+1") {
            zenithStrike += 1
            val applyMap = mutableMapOf<mindustry.gen.Unit, Boolean>()
            ts.loop(Dispatchers.game) {
                Team.crux.data().units.filter { it.type == UnitTypes.zenith && !applyMap.getOrDefault(it, false) }.forEach {
                    repeat(2) { i ->
                        it.statuses.add(StatusEntry().set(StatusEffects.shielded, 10 * 60f))
                        applyMap[it] = true
                    }
                }
                yield()
            }
        },
        Rule("[purple][神出鬼没]", "[red]核心资源随机减少") {
            Team.sharded.core().items.each { item, amount ->
                Team.sharded.core().items.remove(item, (amount * Random.nextFloat()).toInt())
            }
        },
        Rule("[purple][铭能重压]", "[red]友方单位获得永久减速") {
            ts.loop(Dispatchers.game) {
                Team.sharded.data().units.forEach {
                    it.apply(StatusEffects.slow, Float.POSITIVE_INFINITY)
                }
                yield()
            }
        },
        Rule("[green][炮塔限制]", "[red]s炮塔随机被禁用") {
            arrayOf(Blocks.duo,
                    Blocks.scatter,
                    Blocks.scorch,
                    Blocks.hail,
                    Blocks.arc,
                    Blocks.wave,
                    Blocks.lancer,
                    Blocks.swarmer,
                    Blocks.salvo,
                    Blocks.fuse,
                    Blocks.ripple,
                    Blocks.cyclone,
                    Blocks.foreshadow,
                    Blocks.spectre,
                    Blocks.meltdown,
                    Blocks.segment,
                    Blocks.parallax,
                    Blocks.tsunami).forEach {
                if (Random.nextFloat() >= 0.6f) {
                    Vars.state.rules.bannedBlocks.add(it)
                }
            }
            Call.setRules(Vars.state.rules)
        },
        Rule("[purple][希望之光]", "[red]敌方最后一个单位获得断断续续的无敌Buff") {
            ts.loop(Dispatchers.game) {
                Team.crux.data().units.singleOrNull()?.apply(StatusEffects.invincible,9 * 60f)
                delay(10_000)
            }
        }
)

fun applyRule(rule: Rule) {
    appliedRules.add(rule)
    Call.sendMessage("[red]检测到律令 - ${rule.name}")
    Call.sendMessage(rule.desc)
    rule.effect.invoke()
}

var coreRebuild = false
var coreRebuildTime = 120_000L
var zenithStrike = 1
var zenithStrikeD = 60_000L
var quietMode = false//shut up
var defStartTime = -1L
onEnable {
    defStartTime = -1L
    quietMode = false
    coreRebuild = false
    coreRebuildTime = 120_000L
    ruleExp = 0f
    ruleLevel = 1
    waveTime = 120_000L
    zenithStrike = 1
    zenithStrikeD = 60_000L
    name = "[white][[#00ffff][cyan]"
    nameR = "[white][[#ffff00][yellow]"
    launch(Dispatchers.game) {
        Vars.state.rules.apply {
            limitMapArea = true
            limitX = 280
            limitY = 280
            limitHeight = 39
            limitWidth = 39
            modeName = "决战"
            blockWhitelist = true
            enemyCoreBuildRadius = 24f * 8f
            unitCrashDamageMultiplier = 0.1f
        }
        Team.sharded.rules().blockHealthMultiplier = 5f
        Team.sharded.rules().buildSpeedMultiplier = 4f
        Call.setRules(Vars.state.rules)
        delay(5000)
        Call.sendMessage(name + "诸位，观星台出现了紧急情况---")
        delay(2000)
        Call.sendMessage(name + "我们不久前收集的<律令>突然失控了")
        delay(3000)
        Call.sendMessage(name + "不仅如此，<律令>还在虚空中打通了观星台和外界的通道")
        delay(3000)
        Call.sendMessage(name + "这意味着我们要与<律令>和外界来敌同时作战")
        delay(3000)
        Call.sendMessage(name + "不过嘛..")
        delay(3000)
        Call.sendMessage(name + "尽管我已丧失大部分的力量，但压制一枚星铭不是什么难事，<律令>的力量将会极大削弱")
        delay(3000)
        Call.sendMessage(name + "我需要你们在我重新控制之前<律令>抵抗外界来敌")
        //Core.settings.put("unlock-A9", false)
        delay(2000)
        Call.announce("[cyan]观星台展开!")
        Call.sendMessage(name + "作为这里的主人,我可以在压制<律令>的同时展开观星台,这已经是我最多能做的了")
        launch(Dispatchers.game) {
            repeat(15) {
                Vars.state.rules.apply {
                    Call.setMapArea(limitX - it * 2, limitY  - it * 2, limitHeight + it * 4, limitHeight + it * 4)
                }
                Call.setRules(Vars.state.rules)
                delay(500)
            }
            Vars.state.rules.apply {
                limitMapArea = false
            }
            Call.setMapArea(0, 0, 600, 600)
            Call.setRules(Vars.state.rules)
            listOf(387f to 299.5f,
                    299.5f to 387f,
                    212f to 299.5f,
                    299.5f to 212f)
                    .sortedBy { Random.nextFloat() }
                    .forEach {
                        val x = it.first * 8f
                        val y = it.second * 8f
                        UnitTypes.sei.create(Team.sharded).apply {
                            set(x, y)
                            snapInterpolation()
                            rotation = angleTo(300 * 8f, 300 * 8f) + 180f
                            apply(StatusEffects.unmoving, Float.POSITIVE_INFINITY)
                            val u = this
                            repeat(3) {
                                Call.effect(
                                        Fx.launch,
                                        x,
                                        y,
                                        rotation,
                                        Color.yellow
                                )
                                delay(500)
                            }
                            Call.effect(
                                    Fx.shieldBreak,
                                    x,
                                    y,
                                    39f,
                                    Color.yellow
                            )
                            Call.effect(
                                    Fx.unitSpawn,
                                    x,
                                    y,
                                    rotation,
                                    Color.yellow,
                                    UnitTypes.sei
                            )
                            delay(500)
                            add()
                        }
                        delay(1000)
                    }
        }
        delay(8000)
        Call.sendMessage(name + "部分资源正在刻印,稍后我将开启观星台的全部的刻印权限,这次我们是主场作战!")
        repeat(10) {
            Team.sharded.core().items.add(Items.copper, Random.nextInt(1000, 2000))
            delay(100)
        }
        repeat(10) {
            Team.sharded.core().items.add(Items.lead, Random.nextInt(1000, 2000))
            delay(100)
        }
        repeat(10) {
            Team.sharded.core().items.add(Items.silicon, Random.nextInt(500, 1000))
            delay(100)
        }
        repeat(10) {
            Team.sharded.core().items.add(Items.metaglass, Random.nextInt(500, 1000))
            delay(100)
        }
        repeat(10) {
            Team.sharded.core().items.add(Items.graphite, Random.nextInt(500, 1000))
            delay(100)
        }
        repeat(10) {
            Team.sharded.core().items.add(Items.titanium, Random.nextInt(500, 1000))
            delay(100)
        }
        repeat(10) {
            Team.sharded.core().items.add(Items.thorium, Random.nextInt(500, 1000))
            delay(100)
        }
        repeat(10) {
            Team.sharded.core().items.add(Items.plastanium, Random.nextInt(200, 300))
            delay(100)
        }
        repeat(10) {
            Team.sharded.core().items.add(Items.phaseFabric, Random.nextInt(50, 100))
            delay(100)
        }
        repeat(10) {
            Team.sharded.core().items.add(Items.surgeAlloy, Random.nextInt(50, 100))
            delay(100)
        }
        Call.sendMessage(name + "刻印权限已经对你们开放了 --- 相信你们会喜欢一些基础生产设施的..尽管它们可能会有一些问题")
        coreRebuild = true
        Vars.state.rules.apply {
            blockWhitelist = false
        }
        Call.setRules(Vars.state.rules)
        polyBuild(229, 298, msch_coreMiner, 9)
        delay(8000)
        polyBuild(219, 329, msch_coalMiner, 5)
        delay(6000)
        polyBuild(208, 320, msch_coreIndustry, 2)
        delay(20_000)
        Call.sendMessage(name + "注意,他们来了!")
        launch(Dispatchers.game) {
            delay(2_000)
            Call.sendMessage(name + "观星台的自主建造系统并不总是可靠,尽快手动建立防线！")
            Team.sharded.rules().blockHealthMultiplier = 1f
            Call.setRules(Vars.state.rules)
            polyBuild(364, 189, msch_aaDef1, 2)
            delay(2_000)
            polyBuild(393, 234, msch_aaDef2, 2)
            delay(2_000)
            polyBuild(370, 220, msch_coreWall, 3)
            delay(8_000)
            repeat(5) {
                UnitTypes.zenith.spawn(Team.crux, 625 * 8f, -25 * 8f)
                delay(200)
            }
            delay(2_000)
            repeat(3) {
                UnitTypes.zenith.spawn(Team.crux, 620 * 8f, -25 * 8f)
                delay(200)
            }
            delay(1_000)
            repeat(3) {
                UnitTypes.zenith.spawn(Team.crux, 625 * 8f, -20 * 8f)
            }
            delay(10_000)
            Call.sendMessage(name + "正在印刻辅助星铭<天穹>...它能帮我们更好的建立防线")
            val ux = 299.5f * 8f
            val uy = 299.5f * 8f
            UnitTypes.oct.create(Team.sharded).apply {
                set(ux, uy)
                snapInterpolation()
                rotation = angleTo(300 * 8f, 300 * 8f) + 180f
                apply(StatusEffects.fast, Float.POSITIVE_INFINITY)
                apply(StatusEffects.shielded, Float.POSITIVE_INFINITY)
                (this as Payloadc).addPayload(BuildPayload(Blocks.powerSource, Team.sharded))
                repeat(8) {
                    Call.effect(
                            Fx.launch,
                            x,
                            y,
                            rotation,
                            Color.yellow
                    )
                    delay(500)
                }
                Call.effect(
                        Fx.shieldBreak,
                        x,
                        y,
                        66f,
                        Color.yellow
                )
                Call.effect(
                        Fx.unitSpawn,
                        x,
                        y,
                        rotation,
                        Color.yellow,
                        UnitTypes.oct
                )
                delay(500)
                add()
                Call.sendMessage(name + "<天穹>已经印刻完毕，试着让它拾取一些修复器！")
                ts.launch(Dispatchers.game) {
                    while (!dead()) {
                        val vec = Vec2(8f, 8f)
                        vec.setAngle(rotation + 180f).setLength(hitSize * 0.7f)
                        Call.effect(Fx.instBomb, x + vec.x, y + vec.y, 0f, Color.cyan)
                        if (health >= maxHealth * 0.1f && shield <= 6000) {
                            val max = health - maxHealth * 0.1f
                            val num = (6000 - shield).coerceAtMost(max)
                            if (num >= 0) {
                                health -= num
                                shield += num
                            }
                        }
                        delay(100)
                    }
                }
            }
        }
        launch(Dispatchers.game) {
            delay(60_000)
            Team.sharded.rules().blockHealthMultiplier = 2f
            Team.sharded.rules().buildSpeedMultiplier = 2f
            Vars.state.rules.enemyCoreBuildRadius = 32 * 8f
            Call.setRules(Vars.state.rules)
            Call.sendMessage(name + "源源不断的敌方空军正在赶往这里...做好全面防御准备")
            delay(10_000)
            while (zenithStrike >= 1) {
                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, 625 * 8f, -25 * 8f)
                    delay(200)
                }
                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, 620 * 8f, -25 * 8f)
                    delay(200)
                }
                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, 625 * 8f, -20 * 8f)
                    delay(200)
                }

                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, 625 * 8f, 625 * 8f)
                    delay(200)
                }
                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, 620 * 8f, 625 * 8f)
                    delay(200)
                }
                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, 625 * 8f, 620 * 8f)
                    delay(200)
                }

                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, -25 * 8f, -25 * 8f)
                    delay(200)
                }
                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, -20 * 8f, -25 * 8f)
                    delay(200)
                }
                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, -25 * 8f, -20 * 8f)
                    delay(200)
                }

                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, -25 * 8f, 625 * 8f)
                    delay(200)
                }
                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, -20 * 8f, 625 * 8f)
                    delay(200)
                }
                repeat(zenithStrike) {
                    UnitTypes.zenith.spawn(Team.crux, -25 * 8f, 620 * 8f)
                    delay(200)
                }
                delay(zenithStrikeD)
            }
        }
        delay(60_000)
        Call.sendMessage(name + "<律令>准备在场上放置了核心来收集铭能")
        delay(4000)
        var ruleSuspend = true
        var exInfo = false
        loop(Dispatchers.game) {
            Call.setHudText(buildString {
                append("[yellow]律令等级${if (ruleSuspend) "[green]--被压制--" else getRomanNum(ruleLevel)} ")
                if (ruleSuspend) {
                    appendLine()
                } else {
                    appendLine(ruleExp.buildLineBar(10, 100f))
                }
                append("[yellow]律令充能  ${Team.crux.cores().size.buildLineBar(10, 10)}")
                if (exInfo) {
                    appendLine()
                    append("[yellow]距离律令能量耗尽还有:  ${((defStartTime + 1800_000L - Time.millis()) / 1000f).format(2)}s")
                }
            })
            delay(500)
        }
        var spawnDelay = 60_000L
        loop(Dispatchers.game) {
            if (getCoreTile(Blocks.coreNucleus as CoreBlock) != null) {
                if (!quietMode) {
                    Call.sendMessage(name + listOf(
                            "律令刻印了一处新的律令核心",
                            "一处新的律令核心生成完毕",
                            "新的律令核心正在给<律令>充能",
                    ).random())
                }
                getCoreTile(Blocks.coreNucleus as CoreBlock)!!.setNet(Blocks.coreNucleus, Team.crux, 0)
            } else {
                Call.sendMessage(name + "<律令>找不到适合刻印律令核心的地方..祂现在专注于进攻了")
                Team.crux.rules().unitHealthMultiplier += 0.2f
                Team.crux.rules().unitDamageMultiplier += 0.2f
                Call.setRules(Vars.state.rules)
            }
            spawnDelay = (spawnDelay - 2_000L).coerceAtLeast(20_000L)
            delay(spawnDelay)
        }
        delay(2000)
        Call.sendMessage(name + "这些核心会给<律令>提供反抗压制的铭能，尽快摧毁它们!")
        var zenithStrikeAdded = false
        launch(Dispatchers.game) {
            while (true) {
                if (Team.crux.cores().size >= 3 && !zenithStrikeAdded) {
                    zenithStrikeAdded = true
                    zenithStrike += 1
                    Call.sendMessage(name + "<律令>吸引了更多前来袭击的空军...")
                    break
                }
                yield()
            }
        }
        while (true) {
            if (Team.crux.cores().size >= 5 || spawnDelay <= 30_000L) {
                break
            }
            yield()
        }

        //STAGE II
        quietMode = true
        Call.sendMessage(name + "<律令>暂时突破了压制，全员戒备!")
        delay(8_000)
        applyRule(Rule("[cyan]<始源第十二铭-律令>","[lightgray]从现在开始，一切为我所掌控。") {
            ruleSuspend = false
            spawnDelay = 30_000L
            Vars.state.rules.apply {
                lighting = true
                ambientLight = Color(0f, 1f, 1f, 0.4f)
            }
            Vars.state.rules.enemyCoreBuildRadius = 48 * 8f
            Call.setRules(Vars.state.rules)
            zenithStrikeD = 40_000L
            zenithStrike += 1
            coreRebuildTime = 240_000L
            Groups.unit.forEach {
                if (it.team == Team.sharded) {
                    it.apply(StatusEffects.unmoving, 12 * 60f)
                }
            }
        })
        delay(3_000)
        Call.sendMessage(name + "!?怎么在这时候------")
        delay(3_000)
        Call.sendMessage(nameR + "场面失控了呢，哥·哥~")
        delay(4_000)
        Call.sendMessage(name + "..辰?")
        nameR = "[[[yellow]辰[white]][yellow]"
        delay(3_000)
        Call.sendMessage(nameR + "Bingo~")
        delay(3_000)
        Call.sendMessage(name + "这颗始源铭是你放的吧?")
        delay(3_000)
        Call.sendMessage(nameR + "..哥哥的目标是拿回它们，不是吗?")
        delay(3_000)
        Call.sendMessage(nameR + "既然如此，那作妹妹的辰当然要帮助哥哥了~")
        delay(4_000)
        Call.sendMessage(nameR + "星哥哥啊，这便是你赎罪的考验")
        name = "[[[cyan]星[white]][cyan]"
        delay(4_000)
        Call.sendMessage(nameR + "现在你的力量，能否战胜自己的过去的权柄?")
        delay(3_000)
        applyRule(Rule("[green][始源压制]", "[red](友 建造速度*0.05 建筑血量*0.05)?") {
            val tile = Vars.world.tiles.shuffled().first()
            ts.launch(Dispatchers.game) {
                worldLabelMessage(tile.worldx(), tile.worldy(), "[yellow]这个就不用了啦", 3L)
            }
            //因为辰放水了，所以这个律令完全没有生效
            /*
            Team.sharded.rules().apply {
                buildSpeedMultiplier *= 0.05f
                blockHealthMultiplier *= 0.05f
            }
            Call.setRules(Vars.state.rules)
             */
        })
        delay(4_000)
        val leftLv1Rules = lv1Rules.toMutableList()
        val leftLv2Rules = lv2Rules.toMutableList()
        var randomRule = leftLv1Rules.random()
        leftLv1Rules.remove(randomRule)
        applyRule(randomRule)
        delay(2_000)
        Call.sendMessage(name + "说来话长..没想到现在辰就激活了属于始源铭的力量")
        delay(4_000)
        Call.sendMessage(name + "这不是普通的铭能可以应对的")
        delay(4_000)
        Call.sendMessage(name + "好在，我们可以通过观星台来再次压制<律令>")
        delay(4_000)
        Call.sendMessage(name + "但这需要我们建造并启动2个[行星际发射器]...")
        delay(6_000)
        Call.sendMessage(name + "好了，相应的刻印权限已经开放，尽快将其启动!")
        Vars.state.rules.revealedBlocks.add(Blocks.interplanetaryAccelerator)
        Call.setRules(Vars.state.rules)
        delay(4_000)
        Call.sendMessage(name + "在此期间，<律令>的铭能增长将不受我的压制...请务必重视律令核心的摧毁工作!")
        quietMode = false
        //律令充能
        var chargeScl = 1f
        var passiveCharge = true
        launch(Dispatchers.game) {
            while (true) {
                ruleExp += (Team.crux.cores().size.coerceAtMost(20) * 0.5f + 0.5f) * chargeScl
                if (passiveCharge) ruleExp += chargeScl
                delay(10_000L)
            }
        }
        loop(Dispatchers.game) {
            delay(600_000)
            Call.sendMessage(name + "<律令>已经熟悉了观星台的环境..它的铭能增长加快了")
            chargeScl += 1f
        }
        //律令升级
        launch(Dispatchers.game) {
            while (true) {
                if (ruleExp >= 100f) {
                    ruleExp = 0f
                    when (ruleLevel) {
                        in 1..4 -> {
                            randomRule = leftLv1Rules.random()
                            leftLv1Rules.remove(randomRule)
                            applyRule(randomRule)
                        }
                        in 5..8 -> {
                            randomRule = leftLv2Rules.random()
                            leftLv2Rules.remove(randomRule)
                            applyRule(randomRule)
                        }
                        else -> {
                            applyRule(Rule("[yellow][终焉之时]", "[red]敌方单位属性增长..") {
                                Team.crux.rules().unitDamageMultiplier *= 1.2f
                                Team.crux.rules().unitHealthMultiplier *= 1.2f
                                Call.setRules(Vars.state.rules)
                            })
                        }
                    }
                    ruleLevel += 1
                }
                yield()
            }
        }
        launch(Dispatchers.game) {
            delay(60_000L)
            Call.sendMessage(name + "检测到敌方陆军准备登陆，做好准备！")
            delay(20_000L)
            waveUnits[UnitTypes.mace] = 32
            waveUnits[UnitTypes.fortress] = 16
            waveUnits[UnitTypes.quasar] = 12
            waveUnits[UnitTypes.scepter] = 4
            waveUnits[UnitTypes.reign] = 1
            while (true) {
                waveUnits.putAll(preWaveUnits)
                preWaveUnits.clear()
                waveUnits.forEach {
                    repeat(it.value) { i ->
                        ts.launch(Dispatchers.game) { spawnFromSide(it.key) }
                        delay(50L)
                    }
                }

                delay(waveTime)
            }
        }
        var trigger = false
        delay(6_000)
        Call.sendMessage(name + "我现在没法压制<律令>，作为替代，我将会持续刻印物资来提供支援")
        while (true) {
            val item = listOf(
                    Items.copper to 5,
                    Items.lead to 5,
                    Items.silicon to 3,
                    Items.titanium to 4,
                    Items.thorium to 3,
                    Items.graphite to 4,
                    Items.metaglass to 2,
                    Items.plastanium to 2,
                    Items.phaseFabric to 1,
                    Items.surgeAlloy to 1
            ).random()
            Team.sharded.core().handleStack(item.first, item.second, null)
            val ias = Groups.build.filter { it.block == Blocks.interplanetaryAccelerator }.filter { it.power.status >= 0.9f && it.items.sum { item, amount -> amount.toFloat() } >= 24999 }
            if (ias.isNotEmpty() && !trigger) {
                Call.sendMessage(name + "第一台行星际发射器已就位，借助它的力量，你们使用过的星铭可以再次使用一次了！")
                inscription.used.clear()
                trigger = true
            }
            if (ias.size >= 2) {
                ias.forEach {
                    it.kill()
                }
                break
            }
            yield()
        }

        //STAGE III
        passiveCharge = false
        quietMode = true
        var killUnit = true
        coreRebuildTime = 5_000
        launch(Dispatchers.game) {
            while (killUnit) {
                Groups.unit.forEach {
                    if (it.team == Team.crux) {
                        it.apply(StatusEffects.disarmed, 5 * 60f)
                        it.apply(StatusEffects.melting, 5 * 60f)
                        it.apply(StatusEffects.corroded, 5 * 60f)
                    }
                }
                yield()
            }
        }
        Call.sendMessage(name + "第二台行星际发射器已就位，干得好!")
        delay(4_000L)
        Call.sendMessage(name + "观星台已经拥有足够的铭能重新控制<律令>了!")
        delay(4_000L)
        Call.sendMessage("[#ff00ff]已锁定封印目标<律令>....")
        delay(4_000L)
        Call.sendMessage("[#ff00ff]准备进行..?充能强化?......")
        delay(4_000L)
        Call.sendMessage(name + "...什么玩意?")
        delay(4_000L)
        Call.sendMessage(nameR + "啊，是那个呢~")
        delay(4_000L)
        Call.sendMessage("[#ff00ff]对目标的充能强化任务已完成!")
        coreRebuildTime = 45_000
        killUnit = false
        chargeScl += 1f
        ruleExp += 100
        zenithStrike += 1
        zenithStrikeD = 20_000L
        waveTime -= 30_000L
        delay(2_000L)
        applyRule(Rule("[双生召唤]", "[lightgray]你的目标何时开始偏离你的初心？") {
            ts.launch(Dispatchers.game) {
                delay(2_000L)
                Call.sendMessage("[red]检测到终焉铭能出现...确认来自<终焉第十二铭-畸变>")
                ts.launch(Dispatchers.game) {
                    val tile = Vars.world.tiles.filter { it.floor() == Blocks.cryofluid }.sortedBy { Random.nextFloat() }
                    tile.forEach {
                        it.setFloorNet(Blocks.slag)
                        delay(50)
                    }
                }
                preWaveUnits[UnitTypes.latum] = 1
                Vars.state.rules.apply {
                    ambientLight = Color(1f, 0f, 0f, 0.4f)
                }
                Vars.state.rules.enemyCoreBuildRadius = 48 * 8f
                Call.setRules(Vars.state.rules)
            }
        })
        randomRule = leftLv1Rules.random()
        leftLv1Rules.remove(randomRule)
        applyRule(randomRule)
        randomRule = leftLv2Rules.random()
        leftLv2Rules.remove(randomRule)
        applyRule(randomRule)
        delay(4_000L)
        Call.sendMessage(name + "终焉铭...我真的不愿意想起过去的事")
        repeat(8) {
            spawnFromSide(UnitTypes.conquer).apply {
                apply(StatusEffects.shielded, Float.POSITIVE_INFINITY)
                apply(StatusEffects.slow, Float.POSITIVE_INFINITY)
            }
        }
        preWaveUnits[UnitTypes.conquer] = 2
        delay(5_000L)
        Call.sendMessage(name + "总之，事态已经超出控制了，我们必须全力以赴!")
        delay(5_000L)
        Call.sendMessage(name + "我在中心核心刻印了一个铭能具象，使用他摧毁敌军!")
        UnitTypes.omura.create(Team.sharded).apply {
            set(300 * 8f, 300 * 8f)
            snapInterpolation()
            statuses.apply {
                repeat(2) {
                    add(StatusEntry().set(StatusEffects.boss, Float.POSITIVE_INFINITY))
                }
                repeat(3) {
                    add(StatusEntry().set(StatusEffects.overclock, Float.POSITIVE_INFINITY))
                }
                repeat(3) {
                    add(StatusEntry().set(StatusEffects.overdrive, Float.POSITIVE_INFINITY))
                }
                repeat(2) {
                    add(StatusEntry().set(StatusEffects.electrified, Float.POSITIVE_INFINITY))
                }
            }
            elevation = 1f
            Call.effect(
                    Fx.unitSpawn,
                    x,
                    y,
                    rotation,
                    Color.yellow,
                    UnitTypes.omura
            )
            ts.launch {
                while (!dead()) {
                    val vec = Vec2(8f, 8f)
                    vec.setAngle(rotation).setLength(hitSize * 0.7f)
                    Call.effect(Fx.instBomb, x + vec.x, y + vec.y, 0f, Color.cyan)
                    delay(100)
                }
            }
            add()
        }
        delay(5_000L)
        Call.sendMessage(nameR + "我也要和哥哥一块~")//你妈的，我自己都要绷不住了，为什么我要写这种东西
        val nameRU = UnitTypes.navanax.create(Team.sharded)
        nameRU.apply {
            set(300 * 8f, 300 * 8f)
            snapInterpolation()
            statuses.apply {
                apply(StatusEffects.invincible, Float.POSITIVE_INFINITY)
            }
            elevation = 1f
            Call.effect(
                    Fx.unitSpawn,
                    x,
                    y,
                    rotation,
                    Color.yellow,
                    UnitTypes.navanax
            )
            ts.launch {
                while (!dead()) {
                    val vec = Vec2(8f, 8f)
                    vec.setAngle(rotation).setLength(hitSize * 0.7f)
                    Call.effect(Fx.instBomb, x + vec.x, y + vec.y, 0f, Color.cyan)
                    delay(100)
                }
            }
            add()
        }
        delay(5_000L)
        Call.sendMessage(name + "你还是站我这边的吗...")
        delay(5_000L)
        Call.sendMessage(nameR + "嗯呐，我可是哥哥的妹妹~")
        delay(5_000L)
        Call.sendMessage(name + "咳咳，回到我们的主要问题上")
        delay(5_000L)
        Call.sendMessage(name + "有辰的帮助，我已经封锁了观星台与外界的通道")
        delay(5_000L)
        Call.sendMessage(name + "现在，这次循环中近乎全部的铭能已经都在观星台及其附近")
        delay(5_000L)
        Call.sendMessage(name + "由于<星>的能力，始源铭与终焉铭不可能从我与辰处抽取铭能")
        delay(5_000L)
        Call.sendMessage(name + "即使它们同时在场....我们只要耗光他们所有的铭能就可轻易打败他们!")
        worldLabelMessage(nameRU.x, nameRU.y, "[yellow]落日理论...", 2)
        delay(5_000L)
        Call.sendMessage(name + "好了，观星台正在计算我们需要守住的时间，我也将刻印一些目前观星台不存在的资源")
        defStartTime = Time.millis() // 守30分钟
        exInfo = true
        repeat(6) {
            Team.sharded.core().items.add(Items.beryllium, Random.nextInt(500, 1000))
            delay(100)
        }
        repeat(6) {
            Team.sharded.core().items.add(Items.tungsten, Random.nextInt(250, 500))
            delay(100)
        }
        repeat(6) {
            Team.sharded.core().items.add(Items.oxide, Random.nextInt(100, 500))
            delay(100)
        }
        repeat(6) {
            Team.sharded.core().items.add(Items.carbide, Random.nextInt(100, 1000))
            delay(100)
        }
        Call.sendMessage(name + "我感受到大量铭能在观星台外聚集..进攻要开始激烈了")
        delay(3_000L)
        Call.sendMessage(name + "你们的使用过的星铭已经再次重置,好好利用他们!")
        inscription.used.clear()
        quietMode = false
        var triggerNum = 0
        while (true) {
            if (defStartTime + 300_000L <= Time.millis() && triggerNum <= 0) {
                triggerNum += 1
                Call.sendMessage(name + "侦测到更多单位准备登陆!")
                preWaveUnits[UnitTypes.vela] = 3
                preWaveUnits[UnitTypes.vanquish] = 6
                preWaveUnits[UnitTypes.antumbra] = 3
                preWaveUnits[UnitTypes.eclipse] = 1
                waveTime -= 15_000L
            }
            if (defStartTime + 600_000L <= Time.millis() && triggerNum <= 1) {
                triggerNum += 1
                Call.sendMessage(name + "侦测到更多单位准备登陆!")
                preWaveUnits[UnitTypes.vela] = 6
                preWaveUnits[UnitTypes.reign] = 4
                preWaveUnits[UnitTypes.mace] = 0
                preWaveUnits[UnitTypes.fortress] = 32
                preWaveUnits[UnitTypes.quasar] = 24
                preWaveUnits[UnitTypes.quell] = 4
                preWaveUnits[UnitTypes.disrupt] = 2
                waveTime -= 15_000L
            }
            if (defStartTime + 900_000L <= Time.millis() && triggerNum <= 2) {
                triggerNum += 1
                Call.sendMessage(name + "检测到终焉铭能，是<畸变>开始行动了！")
                preWaveUnits[UnitTypes.quad] = 4
                preWaveUnits[UnitTypes.oct] = 1
                ts.launch(Dispatchers.game) {
                    delay(20_000)
                    Call.sendMessage(name + "现在，我们血量较低的单位将会被<畸变>控制，保持住他们的血量！")
                    while (true) {
                        Groups.unit.forEach {
                            if (it.team == Team.sharded && it.health <= it.maxHealth * 0.5f && !it.isPlayer)
                                it.team = Team.crux
                        }
                        delay (500)
                    }
                }
                waveTime -= 15_000L
            }
            if (defStartTime + 1200_000L <= Time.millis() && triggerNum <= 3) {
                triggerNum += 1
                applyRule(Rule("[最终决战]", "[red]无法进行核心重刻") {
                    coreRebuild = false
                })
                delay(2_000)
                Call.sendMessage(nameR + "希望你们的刻印核心还好~")
                delay(3_000)
                Call.sendMessage(name + "还有10分钟，坚持住！")
                waveTime -= 15_000L
            }
            if (defStartTime + 1500_000L <= Time.millis() && triggerNum <= 4) {
                triggerNum += 1
                applyRule(Rule("[孤注一掷]", "[red]登录无冷却时间") {
                    waveTime = 0L
                })
                delay(2_000)
                Call.sendMessage(name + "这将是最后一波攻势...来吧！")
                ts.launch(Dispatchers.game) {
                    while(true) {
                        if (Team.sharded.cores().size <= 8 && triggerNum <= 5) {
                            Team.sharded.rules().blockHealthMultiplier += 0.2f
                            Team.sharded.rules().blockDamageMultiplier += 0.2f
                            Team.sharded.rules().unitHealthMultiplier += 0.2f
                            Team.sharded.rules().unitDamageMultiplier += 0.2f
                            Call.setRules(Vars.state.rules)
                            triggerNum += 1
                        }
                        if (Team.sharded.cores().size <= 4 && triggerNum <= 5) {
                            Team.sharded.rules().blockHealthMultiplier += 0.2f
                            Team.sharded.rules().blockDamageMultiplier += 0.2f
                            Team.sharded.rules().unitHealthMultiplier += 0.2f
                            Team.sharded.rules().unitDamageMultiplier += 0.2f
                            Call.setRules(Vars.state.rules)
                            triggerNum += 1
                        }
                        if (Team.sharded.cores().size <= 2 && triggerNum <= 6) {
                            Team.sharded.rules().blockHealthMultiplier += 0.2f
                            Team.sharded.rules().blockDamageMultiplier += 0.2f
                            Team.sharded.rules().unitHealthMultiplier += 0.2f
                            Team.sharded.rules().unitDamageMultiplier += 0.2f
                            Call.setRules(Vars.state.rules)
                            triggerNum += 1
                        }
                        if (Team.sharded.cores().size <= 1 && triggerNum <= 7) {
                            Team.sharded.rules().blockHealthMultiplier += 0.2f
                            Team.sharded.rules().blockDamageMultiplier += 0.2f
                            Team.sharded.rules().unitHealthMultiplier += 0.2f
                            Team.sharded.rules().unitDamageMultiplier += 0.2f
                            Call.setRules(Vars.state.rules)
                            triggerNum += 1
                            break
                        }
                        yield()
                    }
                }
            }
            if (defStartTime + 1800_000L <= Time.millis()) {
                break
            }
            yield()
        }
        //FINAL
        loop(Dispatchers.game) {
            Groups.unit.forEach {
                if (it.team() == Team.crux) {
                    it.kill()
                }
            }
            yield()
        }
        quietMode = true
        Vars.state.rules.apply {
            canGameOver = false
        }
        Call.setRules(Vars.state.rules)
        Call.sendMessage(name + "结束了...<律令>和<畸变>再也没有足够的铭能刻印单位了..")
        delay(4_000L)
        if (MapManager.current.id <= 1000) {
            Groups.player.forEach {
                ts.launch(Dispatchers.IO) a@{
                    val profile = PlayerData[it.uuid()].profile ?: return@a
                    achievement.finishAchievement(profile, "[yellow][始与终-异世天灾]", 8000)
                    spinscription.newSpInscription(profile, 12)
                }
            }
            delay(20_000L)
            Events.fire(EventType.GameOverEvent(Team.sharded))
            Vars.state.gameOver = true
        } else {
            Call.sendMessage("...你说得对，但是为什么MapManager.current.id是大于1000的？奖励没收！")
        }
    }
}

listen<EventType.BlockDestroyEvent> {
    val core = it.tile.block() as? CoreBlock ?: return@listen
    if (it.tile.team() == Team.sharded && coreRebuild) {
        launch(Dispatchers.game) {
            if (!quietMode) {
                Call.sendMessage(name + listOf(
                        "友方核心已被摧毁，不用担心，重刻一枚<刻印>是很简单的",
                        "友方核心被摧毁了，但稍后一枚新的<刻印>将会回到战场",
                        "友方核心遭到重创，重刻工作正在进行中"
                ).random())
            }
            Call.effect(Fx.upgradeCore, it.tile.worldx() + if (core.size % 2 == 0) 4f else 0f, it.tile.worldy() + if (core.size % 2 == 0) 4f else 0f, 0f, Color.yellow, core)
            var timeStamp = Time.millis()
            val label = WorldLabel.create().apply {
                set(it.tile)
                y += 4f
                fontSize = 2f
                text = buildString {
                    appendLine("[cyan]核心重刻..")
                    append("[lightgray]"+ ((timeStamp + coreRebuildTime - Time.millis()) / 1000f).format(2) +"s")
                }
                snapInterpolation()
            }
            label.add()
            while (timeStamp + coreRebuildTime >= Time.millis()) {
                if (Build.validPlace(getSameSizeBuild(core), Team.sharded, it.tile.centerX() , it.tile.centerY(), 0, false)) {
                    label.text = buildString {
                        appendLine("[cyan]核心重刻..")
                        append("[lightgray]"+ ((timeStamp + coreRebuildTime - Time.millis()) / 1000f).format(2) +"s")
                    }
                } else {
                    timeStamp = Time.millis()
                    label.text = buildString {
                        appendLine("[cyan]核心重刻..")
                        append("[red]无法在此重刻")
                    }
                }
                yield()
                if (!coreRebuild) return@launch
            }
            label.hide()
            if (!quietMode) {
                Call.sendMessage(name + listOf(
                        "一个友方核心已重返战场!",
                        "新的友方核心已被部署!",
                        "一个损坏友方的核心已被重刻!"
                ).random())
            }
            Call.effect(Fx.upgradeCore, it.tile.worldx() + if (core.size % 2 == 0) 4f else 0f, it.tile.worldy() + if (core.size % 2 == 0) 4f else 0f, 0f, Color.yellow, core)
            it.tile.setNet(core, Team.sharded, 0)
        }
    }
}
