@file:Depends("coreMindustry")
@file:Depends("wayzer/maps", "获取地图信息")
@file:Depends("wayzer/map/mapInfo", "显示地图信息", soft = true)
@file:Import("mapScript.lib.*", defaultImport = true)

package mapScript

import arc.util.Interval
import arc.util.Log
import arc.util.serialization.Jval
import cf.wayzer.placehold.PlaceHoldApi.with
import cf.wayzer.scriptAgent.events.ScriptEnableEvent
import cf.wayzer.scriptAgent.events.ScriptStateChangeEvent
import mindustry.Vars
import wayzer.MapInfo
import wayzer.MapManager
import wayzer.MapProvider
import wayzer.MapRegistry
import java.io.File
import java.net.URL
import java.time.LocalDateTime
import java.util.logging.Level
import kotlin.system.measureTimeMillis

val moduleId = id
var willreloadscript: String = ""

@Savable(serializable = false)
@Volatile
var inRunning: ScriptInfo? = null

listen<EventType.ResetEvent> {
    runBlocking {
        ScriptManager.transaction {
            add("$moduleId/")
            disable()
            getForState(ScriptState.ToEnable).forEach {
                it.stateUpdateForce(ScriptState.Loaded)
            }
        }
    }
    inRunning = null
}

fun loadMapScript(id: String): Boolean {
    val script = ScriptManager.getScriptNullable(id)?.scriptInfo
    if (script == null) {
        launch(Dispatchers.gamePost) {
            broadcast("[red]该服务器不存在对应地图脚本，请联系管理员: {id}".with("id" to id))
        }
        return false
    }
    inRunning = script
    if (script.enabled) return true
    runBlocking {
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
    val scriptId = ScriptManager.getScriptNullable("$moduleId/${MapManager.current.id}")?.id
        ?: state.rules.tags.get("@mapScript")
            ?.run { "$moduleId/${toIntOrNull() ?: MapManager.current.id}" }
        ?: return@listen
    loadMapScript(scriptId)
}

//阻止其他脚本启用
listenTo<ScriptStateChangeEvent.Cancellable>(Event.Priority.Intercept) {
    fun checkId() = script.id.startsWith(moduleId)
            && script.id != inRunning?.id && inRunning?.dependsOn(script.scriptInfo, includeSoft = true) != true
    when (next) {
        ScriptState.ToEnable -> if (checkId()) cancelled = true
        ScriptState.Enabling -> if (checkId()) {
            cancelled = true
            script.stateUpdateForce(ScriptState.Loaded).join()
        }

        else -> {}
    }
}

//检测Generator脚本
listenTo<ScriptStateChangeEvent>(Event.Priority.Watch) {
    if (next == ScriptState.Loaded && script.id.startsWith(moduleId)) {
        script.inst?.let(GeneratorSupport::checkScript)
    }
}

//loadGenerator
listenTo<ScriptEnableEvent>(Event.Priority.Before) {
    val map = script.mapInfo ?: return@listenTo
    try {
        world.loadGenerator(map.width, map.height) { tiles ->
            script.genRound.forEach { (name, round) ->
                val time = measureTimeMillis { round(tiles) }
                script.logger.info("Do $name costs $time ms.")
            }
        }
    } catch (e: Throwable) {
        script.logger.log(Level.SEVERE, "loadGenerator出错", e)
        ScriptManager.disableScript(script, "loadGenerator出错: $e")
    }
}

MapRegistry.register(this, object : MapProvider() {
    override val supportFilter: Set<String> get() = GeneratorSupport.knownMaps.flatMapTo(mutableSetOf()) { it.value.second }
    override fun getMaps(filter: String) = GeneratorSupport.knownMaps.values
        .filter { filter in it.second }
        .map { it.first }

    override suspend fun findById(id: Int, reply: ((PlaceHoldString) -> Unit)?): MapInfo? {
        return GeneratorSupport.findGenerator(id)
    }
})

//插件自动更新与wz mapScript基础插件合并
//管理员插件自定义更新，插件备份，备份插件再次启用或创建新kts,管理员操作历史记录，强制更新预加载插件
//模板插件更新
//   /updateMapscript 14822.kts(全名) 10(尝试次数)
//   /useback 2022-11-09T15:09:57.344new14822.kts.tmp(已备份插件名) 14822.kts(新kts)
//   /reloadMapscript :立即加载预重载插件
//   /deleteMapscript 14822.kts :删除插件
//   更新插件 (管理员用于快速更新)
//   /adminhistory (获取管理员操作记录)  /mapscripthistory  (获取管理员插件管理历史)
val URL = "xem8k5/TheDevil-MapScripts"

fun loadMapScript2(id: String): Boolean {
    val script = ScriptManager.getScriptNullable(id)?.scriptInfo
    if (script == null) {
        launch(Dispatchers.gamePost) {
            broadcast("[red]该服务器不存在对应地图脚本，请联系管理员: {id}".with("id" to id))
        }
        return false
    }
    inRunning = script
    runBlocking {
        ScriptManager.unloadScript(script, "更新插件")
        delay(3000)
        ScriptManager.loadScript(script,true)
        delay(3000)
        ScriptManager.enableScript(script,true)
    }
    launch(Dispatchers.gamePost) {
        if (script.enabled)
            broadcast("[yellow]加载地图特定脚本完成: {id}".with("id" to script.id))
        else
            broadcast(
                "[red]地图脚本${id}加载失败，请联系管理员: {reason}"
                    .with("id" to script.id, "reason" to script.failReason.orEmpty())
            )
    }
    return script.enabled
}

suspend fun download(url: String, file: File): Int = withContext(Dispatchers.IO) {
    val steam = URL(url).openStream()
    val buffer = ByteArray(128 * 1024)//128KB
    val logInterval = Interval()
    var len = 0
    steam.use { input ->
        file.outputStream().use { output ->
            while (isActive) {
                val i = input.read(buffer)
                if (i == -1) break
                output.write(buffer, 0, i)
                len += i
                if (logInterval[60f])
                    logger.info("Downloaded ${len / 1024}KB")
            }
        }
    }
    return@withContext len
}
//管理员快捷更新插件(更新当前
listen<EventType.PlayerChatEvent> {
    thistrytime = 0
    if (it.message.equals("更新插件", true) && it.player.admin) {
        val scriptid = Vars.state.rules.tags.get("@mapScript")
        val history = File("/root/config/scripts/mapScript/history.txt")
        if(!history.isFile){ history.createNewFile() }
        history.appendText("${LocalDateTime.now()}:${it.player?.name ?: "控制台"}使用了插件快捷更新 插件:${scriptid}固定尝试次数为10次\n")
        launch {
            try {
                val txt = URL("https://api.github.com/repos/$URL/releases").readText()
                val json = Jval.read(txt).asArray().first()
                val asset = json.get("assets").asArray().find {
                    it.getString("name", "").contains("$scriptid.kts", ignoreCase = true)
                } ?: error("未找到kts插件")
                val url = asset.getString("browser_download_url", "")
                broadcast(("[green]插件${scriptid}因为没有镜像站将会进行多次尝试去安装插件10次未成功将会放弃不影响正常游戏".with()))
                try {
                    update(url, "$scriptid.kts",10)
                    cancel()
                } catch (e: Throwable) {
                }
            } catch (e: Throwable) {
                broadcast("插件{script}更新失败请检测你的是否上传github".with("script" to scriptid))
                logger.warning("获取更新数据失败: $e")
            }
        }
    }
}
// 自定义更新插件
var thistrytime: Int = 0
command("updateMapscript","强制更新插件") {
    usage = "插件编号"
    permission = dotId
    body{
        thistrytime = 0
        val scriptid = arg.getOrNull(0)?.toString(); if("$scriptid" == null) {returnReply("[red]请输入正确的插件名全名要和github一致".with())}
        var trytime = arg.getOrNull(1)?.toIntOrNull(); if(trytime == null)  {returnReply("[red]请输入正确的下载尝试次数".with())}
        val history = File("/root/config/scripts/mapScript/history.txt")
        if(!history.isFile){ history.createNewFile() }
        history.appendText("${LocalDateTime.now()}:${this.player?.name ?: "控制台"}使用了插件更新 插件:${scriptid}最大尝试次数为${trytime}\n")
        launch {
            //broadcast("插件{script}准备更新".with("script" to scriptid))
            try {
                val txt = URL("https://api.github.com/repos/$URL/releases").readText()
                val json = Jval.read(txt).asArray().first()
                val asset = json.get("assets").asArray().find {
                    it.getString("name", "").contains("$scriptid", ignoreCase = true)
                } ?: error("未找到kts插件")
                val url = asset.getString("browser_download_url", "")
                broadcast(("[green]插件${scriptid}因为没有镜像站将会进行多次尝试去安装插件${trytime}次未成功将会放弃不影响正常游戏".with()))
                try {
                    scriptid?.let { update(url, it,trytime) }
                    cancel()
                } catch (e: Throwable) {
                }
            } catch (e: Throwable) {
                broadcast("插件{script}更新失败请检测你的是否上传github".with("script" to scriptid))
                logger.warning("获取更新数据失败: $e")
            }
        }
    }
}
//强制重载预更新插件
command("reloadMapscript","立即重载插件"){
    usage = "插件编号"
    permission = dotId
    body {
        if(willreloadscript == ""){
            returnReply("[red]没有需要立即重载的插件".with())
        }else{
            broadcast("[red]管理员强制重启了插件".with())
            loadMapScript2("mapScript/$willreloadscript")
            val history = File("/root/config/scripts/mapScript/history.txt")
            if(!history.isFile){ history.createNewFile() }
            history.appendText("${LocalDateTime.now()}:${this.player?.name ?: "控制台"}使用了插件强制重新启用${willreloadscript}\n")
            willreloadscript = ""
        }
    }
}
command("deleteMapscript","删除插件"){
    usage = "插件编号"
    permission = dotId
    body {
        val filename = ArrayList<String>()
        val scriptid = arg.getOrNull(0)?.toString()?:let{
            val list = File("/root/config/scripts/mapScript")
            for (file in list.walk()) {
                if(file.isFile && file.name != "history.txt"){
                        filename.add(file.name+"\n")
                }
            }
            returnReply("$filename".with())
        }
        filename.clear()
        val file = File("/root/config/scripts/mapScript/$scriptid")
        if(!file.isFile) returnReply("插件不存在".with())
        if(file.name == "history.txt") returnReply("[red]管理员没有权力删除插件管理历史".with())
        file.delete()
        val history = File("/root/config/scripts/mapScript/history.txt")
        if(!history.isFile){ history.createNewFile() }
        history.appendText("${LocalDateTime.now()}管理员${this.player?.name?: "控制台"}删除:${scriptid}\n")
        returnReply("[green]成功删除了${scriptid}".with())
    }
}
//管理员操作历史查询
command("adminhistory","管理员操作历史查询"){
    usage = "查询"
    permission = dotId
    body {
        val history = File("/root/config/scripts/adminhistory.txt")
        returnReply("${history.readText()}".with())
    }
}
command("mapscripthistory","管理员操作历史查询"){
    usage = "查询"
    permission = dotId
    body {
        val history = File("/root/config/scripts/mapScript/history.txt")
        returnReply("${history.readText()}".with())
    }
}
command("useback","使用备份插件"){
    usage = "插件编号"
    permission = dotId
    body {
        val filename = ArrayList<String>()
        val scriptid = arg.getOrNull(0)?.toString()?:let{
            val list = File("/root/config/scripts/mapScript/backupFile")
            for (file in list.walk()) {
                if(file.isFile){
                    filename.add(file.name+"\n")
                }
            }
            returnReply("$filename".with())
        }
        filename.clear()
        val scriptnewid = arg.getOrNull(1)?.toString()
        val sourceFile = File("/root/config/scripts/mapScript/backupFile/$scriptid")
        val dest = File("/root/config/scripts/mapScript/$scriptnewid")
        sourceFile.copyTo(dest.resolveSibling("$scriptnewid"),true)
        val scriptnum = scriptnewid?.substring(0,scriptnewid.lastIndexOf("."))
        val scripttype = scriptnewid?.substring(scriptnewid.lastIndexOf("."),scriptnewid.length)
        if(scripttype?.contains(".kts") != true && scripttype?.contains(".ktc") != true ){ Log.info("[red]非可加载文件") }
        broadcast("[green]插件备份重启完毕将在游戏结束自动加载".with())
        val history = File("/root/config/scripts/mapScript/history.txt")
        if(!history.isFile){ history.createNewFile() }
        history.appendText("${LocalDateTime.now()}:${this.player?.name ?: "控制台"}使用了插件备份启用${scriptid}->${scriptnewid}\n")
        if(scripttype?.contains(".kts") != true && scripttype?.contains(".ktc") != true ){ returnReply("[red]文件不可直接加载".with()) }
        if (scriptnum != null) {
            willreloadscript = scriptnum
        }
    }
}
//更新
suspend fun update(url: String, scriptid: String,trytime: Int) {
    Log.info("正在从 $url 下载")
    val dest = File("/root/config/scripts/mapScript/$scriptid")
    val sourceFile = File("/root/config/scripts/mapScript/backupFile/$scriptid")
    val tmp = dest.resolveSibling("${(Math.random()*1000).toInt()}$scriptid")//防止因创建文件相同未卡死程序
    Log.info("插件下载到${tmp}")
    val size = try {
        download(url, tmp)
    } catch (e: Throwable) {
        tmp.delete()
        broadcast("插件{script}更新失败".with("script" to scriptid))
        logger.warning("下载更新失败: $e")
        if (thistrytime <= trytime) {
            thistrytime++;broadcast("[red]插件{script}更新已经尝试${thistrytime}".with("script" to scriptid));delay(3000);update(url,scriptid, trytime)
        }
        throw e
    }
    Log.info("下载完成${size / 1024}kb")
    val history = File("/root/config/scripts/mapScript/history.txt")
    if(!history.isFile){ history.createNewFile() }
    history.appendText("${LocalDateTime.now()}:插件更新完成插件:${scriptid}尝试次数为${thistrytime}\n")
    thistrytime = 0
    delay(5000)
    if(dest.isFile) dest.copyTo(sourceFile.resolveSibling("${LocalDateTime.now()}old$scriptid.tmp"), true)
    tmp.copyTo(sourceFile.resolveSibling("${LocalDateTime.now()}new$scriptid.tmp"), true)
    tmp.copyTo(dest.resolveSibling("$scriptid"), true)
    tmp.delete()
    delay(10000)
    val scriptnum = scriptid.substring(0,scriptid.lastIndexOf("."))
    val scripttype = scriptid.substring(scriptid.lastIndexOf("."),scriptid.length)
    if(".kts" !in scripttype && ".ktc" !in scripttype){ broadcast("[red]${scriptid}[red]非可直接加载文件".with()); return}
    broadcast("[green]插件更新完成当本局游戏结束自动加载".with())
    willreloadscript = scriptnum
}
//监听
listen<EventType.GameOverEvent> {
    if(willreloadscript != "") {
        loadMapScript2("mapScript/$willreloadscript")
        willreloadscript = ""
    }
}


