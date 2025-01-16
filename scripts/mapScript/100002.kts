@file:Import("@coreMindustry/util/tools.kt", sourceFile = true)

package mapScript

import arc.Core
import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import coreMindustry.util.buildLineBar
import coreMindustry.util.getRomanNum
import coreMindustry.util.polyBuild
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Items
import mindustry.content.UnitTypes
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.world.Tile
import kotlin.random.Random


val msch_coreMiner = "bXNjaAF4nDWV2XIbRRiFWyPNIsnSSKN9Hy9JvMkmgTfgMSguFEsQVcmSka1QvB0U17wKFBdcUCkQ/+kvlDL+Mn3O6f67p6fHfeVeFV1pu3hcucJbV12unh/266eX9W7rnIs2i/erzbMLvvm24tKH3X41/2532C4XXg8/Lg6bF9d62iyeXxbb9eFx/rDbflz9tNu76ns1zpf79Wbjapv1D4f1cr7fHV5Wexeba3lYv7jK0+7H1X6+3S1XLn1cPXywTh4Wm/nT4fHJ1d/v18vvV/P/zdHzbq+0K3xtl/3sX+BCZ39KdgVCEZRAKIRoIVqIFqJFaBFahBahxWgxWowWoyVoCVqClqCV0cpoZbQyWgWtglZBq6BV0apoVbSqtKI7sZ81VF3qrKHqGq4YWJMPnBA4IXCiQGhogKacNZw1nDWcNZw1s3hkctZx1nHWcdbljAxpUvCrE9r/M6XqrqVUSiollZJKmVaq1RVikIAyqIAqOAE1YEUUbRoNzTy1gay/1LWlNRivwXgNxmtoQUJDG3TkbOJs4mzibOJsmsWjK2eGM8OZ4cxwZmbx6MnZwtnC2cLZkrNoY7dVbsucVm7L9RVoE2gTaBNo03XbLB4DOTs4Ozg7ODs4O2bxGMrZxdnF2cXZlTMy9BIN3zVrIIyU6JHokeiR6CkhdJ219+1n7T0LWLznxsr1yfXJ9cn1yfWVCw1jDdR3EwUGBAYEBgQGBAYEBuYMhKkCQwJDAkMCQwJDAkNzBsJMgRGBEYERgRGBkQUKQg/0FR9ZLhByxcfEx8THxMfExwTG5gyEUwUmBCYEJgQmBCYEJuYMhDMFpgSmBKYEpgSmBKbmDIRzBWYEZgRmBGYEZrwQM70QQgoaoAky0AJt0AFd0AP2ICPDIAmOfx+POj/tmlkZQWy40HGaU01ONTnV5DqDBF9NTjU51eRUk1NNTjU51eRUk1NNTjU51eSqJjacq+tThj1l2FOGPbXDwWv+cDjVdgy0dKAISqAi5xmnyRnOc5znOM9xnutrIEQgBgkogwrwvVzQywW9XOhDI4QgAjFIQBn09UJeaLVd8fjp+MmpxFfqLjFk8fH47192/epb/VivJRbdG50kuvP7+DXaG2mRIbOTuHT8x6nJK5d0eem7NMH5gS4RrxCvXKLx/rTrN5vClT0mJ9F7rpnitU3Rjt0b7YvI7pKk6ELrT1/Rgv8Y3miZBf8tutbBqZgv85q+btRXyZDJcqN9INg+SAwdFfGHXT87Wf3reqPjKXC3FHrrC/08C922Ffndrl+cTB1FbnUGSuzFsnl/bLd9TWpOBXNL25OYu5qfjtoz5La6mOvAjQ09Nd6xAneswL1WILS7RE/jnr11Z5u/INRBChqgCTLQAm0Veecn/XlCsd36Q+1efceGju6+0F3JvXX+7H5nV0GIQAwSnRnvtLmECqjqHfpS9QoRiEECyqAC7I36D17VrSQ="
val msch_coalMiner = "bXNjaAF4nF1Va2/aQBBc7owN2OC3DZhHpKaPL3zo76n6gQJVqXiVQKv+9TRSunMjNSWKktGtZ3budvcu8kHurXiH5X4jrY8SrjcPq/P2dNkeDyLi75ZfNrsHMZ8+BxKvjufN4uvxelgv3ff0sr0sD9vrfrE6Hn5ufh/PEn7ZLR8ui/V5u9tJvNv+uG7Xi+/Xw8oJAuWtr9uL9E7HX5vz4nBcbyTeb1bfNM1quVucrvuT2v4RaYmHPy0JxAI6hC6hRwgJEaFPGBBiQkJICRkhJxSEklARaoChraGtoa2hraGtoa2hraGtoa2hraGtoa2hrVEH0xJLB0sHSwdLB0sHSwdLB0sHSweryVwWl8zTHwdtgk+ICTnBMdtktslsk9kms01mG0yjX0ZogE+BT4FPgU+BT0FASkBKQEpASkBKh5QOKR1SOqR0SOmS0iWlS0qXlC4oKH8prlRDUXJXN2gxCk7Xo65HXQ86T2EoBkUdQdCTMQQhBSEFIQUhBaEyDco/hiCUBoKIgoiCiIKIgkiZBo1qIIhkIratMO+Y58fnR9cYDKZL0GeCPhP0kQBjk4prcYEEfQwjoEHWvqZzwSm2MWCWAbMMkMVTKMAcoDJYVZAPVOeCUwZnkMeUx5THlMeqMxipSty81JDHqnPBmbhOz8XdIydPKE8oT1SnzER1gtUQp0+k+e/0vq41D8JvNPz0/CSe/uJGuoQpE6YsSspbkfJWpLwVKW9FiluBu9RHwVJNqBlSXDFAjY2knIyUk5HRIYODp5CAkqHeWGXQZRyQjAOScUBy6nLqchUYXLdM3F3Kocs5JznmBLegEfecOF1BXaECpRT6VbAqUJtC6pvaFEiA8LxjWZl/9SmZrWR9SiVbQIAaVFoYrUGpk6asEg8EIIdhyUkq+axVyOIpRPhWoXZYDSCoODsVZ6fiA1hTUCvT4HUZiHs6YghqTkuNafEVapx6SMFQmfptqEzBKsFxh5LfHHcIJcINjvtyYH0UxxrXI42087oeofOAGElH7PWIvR4rRYNj1ACrHoMJgymDrruNUjTYKEWwChlMGcxwiIbtmShFgxOluFWE3U8kvtn9BBKE61fN8vTChNBP2ZApGzJj0Wcs+oxFn7OwcxTWV4iR8k7CG6c7fEU4f1UnT+7136Pu7d7t7eXDWw0bQAsm7xQMwGD1XsEArNi/EoeVHg=="
val msch_coreIndustry = "bXNjaAF4nE1R21aDMBCcEgJpAtQf4cFf8Dc8PiCNitKkJ4D9ebXuZnuOlofJXmZ2d4o73CmUYTh57O7hjn4Z03RepxgAVPPw7OcFxePTAc05Xnzql7il0aN7TcP5bVp9f05+WVCluK0+odvC0aeXOV7612H1MO9bGLNcGz//Fw7LNE9jDP1y8jMzreiHePQoP6Y5oFpi4sphCkRd/bG/JcwW5jjQHOjPYZtX7EloHabAzWNMvn+JtMdwO+MB/DPA7g+sQAMU9O0BRUDJksAJUE0TtOQDVQ0nFbdw5JiguEbuCa/kqCaKNgU4UUHivVH0Kq4/JIbrF3eIjBYZzURDsRGWFaDknviWd63hNPKgGo3mvRS9Wn4YXt4SUKMjcAKNJFs+0XKtpb6Gj7ZsBm9QIN+jRLiU6DamkqhGJufFHBMqAiWgueZQ53VcHoqORrQ8pEG2oGGOJai1SDTcxT0W2b4C2TclUYlshpZkxQa0skMr/1vHsgVBXqVjXk1Ajl+/ydtv8thQXLFOx5Rfd7FaIg=="
val msch_aaDef1 = "bXNjaAF4nEWOzQ6CMBCEp0AU8fcJjKJRo0D0eYyHWppIUsAUYuLby2YO9vJ1ZjrdxRXXEFGjawt1w7S0nfHVu6/aBsDI6ad1HYL7I8Kqtualm8pol5e+cg6xaZuP/bYe487ovrceiWm9zbuX9uXQv+B/AiIiRkRMJMSMWBArQA0tBSUIiBBK7hGzMc2YmDBLmM1pLogls+HPcBizk3XWyGTMGoUUNshlxQ3VVpRCSjMVM8CehT2fHJgdqI4snGieWDizcOaTjFlGlbNQ0CzE/AFVFSMa"
val msch_aaDef2 = "bXNjaAF4nCWNUQrCMBBEp2mosZZaBI/hh+cRP2K6YCBNSxoEb2/ihA3svN3H4gLdQke7CJo7TrPsLvkt+zUC6IJ9SdihHs8Wh93ZnCVhWsS9bfTOhtucfAgwbo0f+a6pOFfwNeWrWqq0qvk3pdqaFDShrrDcodBxxXBmmI4UesKewkBh4MrI2ch0pjARThX+AKoQF7E="
val msch_coreWall = "bXNjaAF4nDWOWw6CMBBFh9KHA6twAXy4HuNHhSaSVCDFxO1bPKH9OJmTe6eVTrpW7BLfSZqb9FPaxzJvn3ldRMTn+Ex5F3N/GOnHddtSGb4xZ+nGtaRhf8Uy1dxVztMAA9oTBrTAAgf8AXtEmqos01866o66Q3qkR3rmgAzIwEOBZYE9geSFpPJTpaAUlIKSVLYqFa1XflMgE4E="

val name = "[white][[#00ffff][cyan]"

fun polyBuild(x: Int, y: Int, msch: String, amount: Int) {
    polyBuild(x * 8f, y * 8f, msch, Team.sharded, amount)
}

val tiles by autoInit { Vars.world.tiles.filter { it.passable() && !it.floor().isLiquid } }
fun getCoreTile(): Tile? {
    return tiles.filter {
        Blocks.coreNucleus.canPlaceOn(it, Team.crux, 0)
    }.randomOrNull()

}

var ruleLevel = 1
var ruleExp = 0f
var ruleEnergy = 0f

class Rule(
    val name: String,
    val desc: String,
    val effect: () -> Unit
)

val appliedRules by autoInit { mutableListOf<Rule>() }

/*
val lv1rules = listOf(

)

 */

onEnable {




    ruleEnergy = 0f
    ruleExp = 0f
    ruleLevel = 1
    launch(Dispatchers.game) {
        Vars.state.rules.apply {
            limitMapArea = true
            limitX = 280
            limitY = 280
            limitHeight = 39
            limitWidth = 39
            modeName = "决战"
            blockWhitelist = true
            enemyCoreBuildRadius = 16f
        }
        Team.sharded.rules().blockHealthMultiplier = 5f
        Call.setRules(Vars.state.rules)
        delay(5000)
        Call.sendMessage(name + "观星台出现了紧急情况")
        delay(2000)
        Call.sendMessage(name + "我们不久前收集的<律令>突然开始向外发射强大的铭能流")
        delay(4000)
        Call.sendMessage(name + "观星台散发出的铭能流不计其数,但是<律令>的铭能流非同小可")
        delay(5000)
        Call.sendMessage(name + "观星台没有发出任何邀请,然而不断有入侵者越过虚空靠近")
        delay(4000)
        Call.sendMessage(name + "并且连‘我’都无法用压倒性的铭能压制<律令>")
        delay(4000)
        Call.sendMessage(name + "现在我们要打起十分甚至九分的精神了---")
        delay(2000)
        Call.sendMessage(name + "准备迎接祂吧")
        delay(4000)
        Call.sendMessage("""
            [white]---------------------
            
            [cyan]始源
                  [yellow]第十二铭
                            [white]<律令>
                                     [red]即将失控！
                                     
            [white]---------------------
        """.trimIndent())
        Core.settings.put("unlock-A9", false)
        delay(2000)
        Call.announce("[cyan]观星台展开!")
        Call.sendMessage(name + "作为一个极端唯心的建筑,我可以在尽量削弱<律令>的情况展开这座观星台,这已经是我最多能做的了")
        Vars.state.rules.apply {
            limitMapArea = false
        }
        Call.setMapArea(0, 0, 600, 600)
        Call.setRules(Vars.state.rules)
        delay(8000)
        Call.sendMessage(name + "部分资源正在刻印,稍后我将开启观星台的全部的刻印权限,不同以往的是,这次我们是主场作战!")
        repeat(10) {
            Team.sharded.core().items.add(Items.copper, Random.nextInt(500, 1000))
            delay(100)
        }
        repeat(10) {
            Team.sharded.core().items.add(Items.lead, Random.nextInt(500, 1000))
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
        Call.sendMessage(name + "刻印权限已经对你们开放了 --- 相信你们会喜欢一些基础生产设施的..尽管它们可能会有一些问题")
        Vars.state.rules.apply {
            blockWhitelist = false
        }
        Call.setRules(Vars.state.rules)

        polyBuild(229, 298, msch_coreMiner, 9)
        delay(12000)
        polyBuild(219, 329, msch_coalMiner, 5)
        delay(8000)
        polyBuild(208, 320, msch_coreIndustry, 2)
        delay(20_000)
        Call.sendMessage(name + "注意,他们来了!")
        launch(Dispatchers.game) {
            Call.sendMessage(name + "观星台的自主建造系统并不总是可靠,尽快手动建立防线！")
            Team.sharded.rules().blockHealthMultiplier = 1f
            Call.setRules(Vars.state.rules)
            polyBuild(263, 189, msch_aaDef1, 2)
            delay(2_000)
            polyBuild(393, 233, msch_aaDef2, 2)
            delay(2_000)
            polyBuild(370, 220, msch_coreWall, 3)
            delay(5_000)
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
        }
        delay(60_000)
        Call.sendMessage(name + "<律令>准备在场上放置了核心来收集铭能")
        loop(Dispatchers.game) {
            Call.setHudText(buildString {
                append("[yellow]律令等级${getRomanNum(ruleLevel)} ")
                appendLine(ruleExp.buildLineBar(10, 10f))
                append("[yellow]律令充能  ${ruleEnergy.buildLineBar(10, 100f)}")
            })
            delay(500)
        }
        loop(Dispatchers.game) {
            yield()
        }
        loop(Dispatchers.game) {
            var delay = 60_000L

            if (getCoreTile() != null) {
                Call.sendMessage(name + listOf(
                    "律令刻印了一处新的律令核心",
                    "一处新的律令核心生成完毕",
                    "新的律令核心正在给<律令>充能",
                ).random())
                getCoreTile()!!.setNet(Blocks.coreNucleus, Team.crux, 0)
            } else {
                Call.sendMessage(name + "律令找不到适合刻印律令核心的地方..祂现在专注于进攻了")
                Team.crux.rules().unitHealthMultiplier += 0.1f
                Team.crux.rules().unitDamageMultiplier += 0.1f
                Call.setRules(Vars.state.rules)
            }
            delay(delay)
            delay -= (delay - 1_000L).coerceAtLeast(20_000L)
        }
        delay(2000)
        Call.sendMessage(name + "尽快摧毁这些核心!")
    }
}
