@file:Depends("coreMindustry")
@file:Depends("wayzer/maps", "获取地图信息")
@file:Depends("xkldklp/KVars")
@file:Depends("wayzer/map/mapInfo", "显示地图信息", soft = true)
@file:Import("mapScript.lib.*", defaultImport = true)

package mapScript

import cf.wayzer.scriptAgent.events.ScriptStateChangeEvent
import coreLibrary.lib.with
import coreMindustry.lib.*
import mindustry.Vars.state
import mindustry.game.EventType
import wayzer.MapManager

val moduleId = id

val kVars = contextScript<xkldklp.KVars>()

val toEnable = mutableSetOf<ScriptInfo>()

listen<EventType.ResetEvent> {
    toEnable.clear()
    MindustryDispatcher.safeBlocking {
        ScriptManager.transaction {
            add("$moduleId/")
            disable()
            getForState(ScriptState.ToEnable).forEach {
                it.stateUpdateForce(ScriptState.Loaded)
            }
        }
    }
}

//find and ensure update & loaded
fun findScript(id: String): ScriptInfo? {
    val script = ScriptRegistry.findScriptInfo(id) ?: return null
    if (script.compiledScript?.source == script.source)
        return script
    MindustryDispatcher.safeBlocking {
        ScriptManager.transaction {
            add(script)
            unload();load()
        }
    }
    return script.takeIf { it.inst != null }
}

fun loadMapScript(id: String): Boolean {
    val script = findScript(id)?.scriptInfo
    if (script == null) {
        launch(Dispatchers.gamePost) {
            broadcast("[red]该服务器不存在对应地图脚本，请联系管理员: {id}".with("id" to id))
        }
        return false
    }
    toEnable.add(script.scriptInfo)
    if (script.enabled) {
        return true
    }
    MindustryDispatcher.safeBlocking {
        ScriptManager.enableScript(script, true)
    }
    launch(Dispatchers.gamePost) {
        if (script.enabled)
            broadcast("[yellow]加载地图特定脚本完成: {id}".with("id" to script.id))
        else
            broadcast(
                "[red]地图脚本{id}加载失败，请联系管理员: {reason}"
                    .with("id" to script.id, "reason" to script.failReason.orEmpty())
            )
    }
    return script.enabled
}

listen<EventType.PlayEvent> {
    if (kVars.ruleMode?.active == true) {
        val ruleMode = kVars.ruleMode
        if (ruleMode?.id == MapManager.current.id && state.rules.tags.get("@mapScript")?.toIntOrNull() != 100003) {
            loadMapScript("$moduleId/100003")
        }
        ruleMode!!.active = false
    }
    if (kVars.enableMapScripts.isNotEmpty() && MapManager.current.id >= 10000) {
        kVars.enableMapScripts.forEach  {
            if (MapManager.current.id != it && state.rules.tags.get("@mapScript")?.toIntOrNull() != it)
                loadMapScript("$moduleId/$it")
        }
    }
    kVars.enableMapScripts.clear()

    val blacklist = listOf("100003")

    val scriptId = ScriptManager.getScriptNullable("$moduleId/${MapManager.current.id}")?.id
        ?: state.rules.tags.get("@mapScript")
            ?.run { "$moduleId/${toIntOrNull() ?: MapManager.current.id}" }
        ?: return@listen

    if (scriptId !in blacklist)
        loadMapScript(scriptId)
}

//阻止其他脚本启用
listenTo<ScriptStateChangeEvent.Cancellable>(Event.Priority.Intercept) {
    if (!script.id.startsWith("$moduleId/")) return@listenTo
    fun allowEnable() = toEnable.any { it.dependsOn(script.scriptInfo, includeSoft = true) }
    when (next) {
        ScriptState.ToEnable -> if (!allowEnable()) cancelled = true
        ScriptState.Enabling -> if (!allowEnable()) {
            cancelled = true
            script.stateUpdateForce(ScriptState.Loaded).join()
        }

        else -> {}
    }
}

GeneratorSupport//init
command("mapScriptLoad", "测试: 加载指定地图脚本") {
    permission = "$dotId.load"
    usage = "<script>"
    body {
        val script = arg.firstOrNull() ?: replyUsage()
        loadMapScript(script)
    }
}