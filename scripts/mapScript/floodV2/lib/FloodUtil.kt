package mapScript.floodV2.lib

import arc.math.Mathf
import arc.util.Interval
import cf.wayzer.scriptAgent.contextScript
import coreLibrary.lib.util.loop
import coreMindustry.lib.game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.yield
import mapScript.floodV2.Module
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.game.Team
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.type.UnitType
import mindustry.world.Tile
import mindustry.world.blocks.environment.Cliff
import mindustry.world.blocks.environment.StaticWall
import java.util.logging.Level
import kotlin.system.measureTimeMillis

@Suppress("MemberVisibilityCanBePrivate")
object FloodUtil {
    const val TAG = "@floodV2"

    //const
    val script = contextScript<Module>()
    val nearBy = listOf(-1 to 0, 1 to 0, 0 to 1, 0 to -1)
    val creepTeam = Team.blue!!
    val patch
        get() = javaClass.classLoader.getResourceAsStream("floodPatch.json")?.use {
            it.reader().readText()
        } ?: error("找不到patch文件")

    //config
    val transferRate by script::transferRate //传播速度 0-1
    val creepDamage by script::creepDamage //damage per creep*gameTime
    val creepUnitDamage by script::creepUnitDamage //damage per creep*gameTime
    val damageEvaporationRate by script::damageEvaporationRate
    val creeperBlocks by script::creeperBlocks
    val creeperResistanceMap: Map<UnitType, Float> by script::creeperResistanceMap
    val dmgPerFlood by script::dmgPerFlood

    //calculated
    val creeperBlocksSet = creeperBlocks.toSet()
    fun creepToHealth(creep: Float) = 100 * creep
    fun creepToHealthInv(health: Float) = (health / 100)
    var healthToBlock = sortedMapOf(*creeperBlocks.map { it.health.toFloat() to it }.toTypedArray())
    var maxCreep = creepToHealthInv(creeperBlocks.maxOf { it.health - 0.01f })

    //data
    @Volatile
    var enable = false
    var creepMap = TileFloatMap.EMPTY
    var terrainMap = TileFloatMap.EMPTY
    var hinderMap = TileFloatMap.EMPTY
    var healthMap = TileFloatMap.EMPTY
    var dynamicHinderMap = TileFloatMap.EMPTY
    val otherBuildsTracker = BuildingTracker.new(true, FloodUtil::enable, { BuildingBinder.Null }) { it: Building ->
        it.team != creepTeam
    }
    val statistics = mutableMapOf<String, String>()

    val UnitType.creeperResistance get() = creeperResistanceMap.getValue(this)


    fun initWorld() {
        kotlin.runCatching {
            contextScript<coreMindustry.ContentsTweaker>().addPatch("floodV2", patch)
            script.logger.info("成功加载Patch")
        }.recover {
            script.logger.log(Level.WARNING, "加载CT文件失败", it)
        }
        healthToBlock = sortedMapOf(*creeperBlocks.map { it.health.toFloat() to it }.toTypedArray())
        maxCreep = creepToHealthInv(creeperBlocks.maxOf { it.health - 0.01f })
        statistics["最高水层"] = maxCreep.toString()
        Vars.state.rules.reactorExplosions = false
        Vars.state.rules.modeName = "FloodV2"
        creepMap = TileFloatMap(Vars.world)
        terrainMap = TileFloatMap(Vars.world)
        hinderMap = TileFloatMap(Vars.world)
        dynamicHinderMap = TileFloatMap(Vars.world)
        healthMap = TileFloatMap(Vars.world)
        Vars.world.tiles.eachTile { tile ->
            when {
                tile.block() is StaticWall -> {
                    terrainMap[tile] = 20f;hinderMap[tile] = 5f
                }

                tile.floor().run { isDeep && isLiquid } -> {
                    terrainMap[tile] = 30f;hinderMap[tile] = 0.5f
                }

                tile.floor() == Blocks.empty -> {
                    terrainMap[tile] = 50f;hinderMap[tile] = 0.5f
                }

                tile.block() is Cliff -> hinderMap[tile] = 10f
                tile.floor() == Blocks.metalFloor5 -> hinderMap[tile] = -1f
                tile.floor().placeableOn.not() -> terrainMap[tile] = maxCreep
            }
            if (tile.team() == creepTeam)
                creepMap[tile] = creepToHealthInv(tile.build.health)
        }
        otherBuildsTracker.load()
        Emitter.initWorld()
        enable = true
        launchCreeperDraw()
    }

    private val timer = Interval(3)
    fun update() {
        if (enable) {
            Vars.state.teams[creepTeam]?.plans?.clear()
            Emitter.update()
            if (!enable) return
            if (timer.check(0, 10f)) {
                val damaged = Vars.indexer.getDamaged(creepTeam).toList()
                var damagedDraw = 0
                Vars.indexer.getDamaged(creepTeam).forEach {
                    updateCreep(it.tile)
                    if (creeperDraw(it.tile)) damagedDraw++
                }
                statistics["Damaged建筑"] = damaged.size.toString()
                statistics["Damaged建筑绘制"] = damagedDraw.toString()
            }
            if (timer.check(1, 6f * (lastBuildDamageCnt / 20 + 1))) {
                val delta = timer.getTime(1)
                statistics["建筑伤害耗时(ms)"] = measureTimeMillis { loopBuildDamage(delta) }.toString()
                timer.reset(1, 0f)
            }
            if (timer.check(2, 6f)) {
                val delta = timer.getTime(2)
                loopUnitDamage(delta)
                timer.reset(2, 0f)
            }
        }
    }

    fun reset() {
        enable = false
        script.coroutineContext.cancelChildren()
        creepMap = TileFloatMap.EMPTY
        terrainMap = TileFloatMap.EMPTY
        hinderMap = TileFloatMap.EMPTY
        healthMap = TileFloatMap.EMPTY
        dynamicHinderMap = TileFloatMap.EMPTY
        otherBuildsTracker.reset()
        Emitter.reset()
    }

    fun updateCreep(tile: Tile) {
        val self = creepMap[tile]
        val height = terrainMap[tile]
        val hinder = hinderMap[tile] + dynamicHinderMap[tile]

        val (sourceT, delta) = nearBy.mapNotNull { (dx, dy) ->
            val sourceT = tile.nearby(dx, dy) ?: return@mapNotNull null
            if (sourceT.build?.team.let { it != null && it != creepTeam }) return@mapNotNull null
            val source = creepMap[sourceT]
            val sourceHeight = terrainMap[sourceT]
            val delta = (source + sourceHeight) - (self + height) - hinder
            sourceT to delta.coerceAtMost(maxCreep - self - height).coerceAtMost(source)
        }.maxByOrNull { it.second } ?: return
        if (delta < 0) return
        creepMap[tile] += delta * transferRate
        creepMap[sourceT] -= delta * transferRate
    }

    /**@return updated */
    fun creeperDraw(tile: Tile): Boolean {
        if (creepMap[tile] < 1f || creepMap[tile] > maxCreep || (tile.build?.team ?: creepTeam) != creepTeam) {
            healthMap[tile] = 0f
            return false
        }
        //伤害转换为层数削减
        tile.build?.health?.let { curHealth ->
            val damage = healthMap[tile] - curHealth
            if (damage <= 0) return@let
            creepMap[tile] = (creepMap[tile] - creepToHealthInv(damage)).coerceAtLeast(0f)
        }

        //层数增加，绘制方块
        val targetHealth = creepToHealth(creepMap[tile])
        if (targetHealth > tile.block().health && (tile.block().alwaysReplace || tile.block() in creeperBlocksSet)) {
            val targetBlock = healthToBlock.tailMap(targetHealth).values
                .firstOrNull() ?: creeperBlocks.last()
            if (targetBlock != tile.block()) {
                tile.setNet(targetBlock, creepTeam, 0)
                healthMap[tile] = tile.build!!.health()
                return true
            }
        }
        //层数增加，恢复血量
        tile.build?.apply {
            val heal = targetHealth.coerceAtMost(maxHealth) - health
            if (heal > 0) heal(heal)
        }
        healthMap[tile] = tile.build?.health ?: 0f
        return false
    }

    /** 洪水方块更新 */
    fun launchCreeperDraw() {
        script.loop(Dispatchers.game) {
            var lastDrawCnt = 0
            var drawCnt = 0
            var cnt = 0
            val all = Vars.world.run { width() * height() }.coerceAtLeast(360)
            for (tile in Vars.world.tiles.shuffled()) {
                cnt++
                updateCreep(tile)
                if (creeperDraw(tile)) {
                    lastDrawCnt++
                    drawCnt++
                }
                if (drawCnt >= 10 || cnt >= all / 30) {//最快绘制速度：0.5s，变动频率600/s
                    cnt = 0
                    drawCnt = 0
                    yield()
                    if (!enable) cancel()
                }
            }
            statistics["绘制刷新"] = lastDrawCnt.toString()
        }
    }

    var lastBuildDamageCnt = 0

    /** 洪水建筑伤害 */
    fun loopBuildDamage(deltaTime: Float) {
        val doAfter = mutableListOf<() -> Unit>()
        otherBuildsTracker.map.keys.forEach { build ->
            val tile = build.tile
            var damageTotal = 0f
            tile.getLinkedTiles {
                if (creepMap[it] < 1f) return@getLinkedTiles
                if (Mathf.chanceDelta(0.01))
                    Call.effect(Fx.bubble, build.x, build.y, 0f, creepTeam.color)
                damageTotal += creepMap[it] * creepDamage
                creepMap[it] *= damageEvaporationRate
            }
            if (damageTotal > 0f) doAfter.add {
                build.damage(creepTeam, damageTotal * deltaTime / 60f)
            }
        }
        lastBuildDamageCnt = doAfter.size
        statistics["建筑伤害刷新"] = lastBuildDamageCnt.toString()
        doAfter.forEach { it() }
    }

    /** 洪水单位伤害 */
    fun loopUnitDamage(deltaTime: Float) {
        Groups.unit.forEach { unit ->
            if(unit.team == creepTeam)return@forEach
            with(unit){
                val tile = tileOn()
                if (tile?.team() != creepTeam) return@forEach
                val creep = creepMap[tile]
                if (creep < 1) return@forEach
                damage(creep * (1f - type.creeperResistance) * creepUnitDamage * deltaTime / 60f)

                if (creep > 1 && Mathf.chance(2.0 * deltaTime / 60f))
                    Call.effect(Fx.bubble, x, y, 0f, creepTeam.color)
            }
        }
    }
}