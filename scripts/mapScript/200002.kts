import mapScript.Module

name = "floodv2"
val mainScript = contextScript<Module>()
onEnable {
    mainScript.loadMapScript("mapScript/floodV2")
    mainScript.loadMapScript("mapScript/floodV2/debug", true)
    mainScript.loadMapScript("mapScript/floodV2/emitterCharged", true)
    mainScript.loadMapScript("mapScript/floodV2/emitterCore", true)
    mainScript.loadMapScript("mapScript/floodV2/forceProjector", true)
    mainScript.loadMapScript("mapScript/floodV2/mapFilter", true)
    mainScript.loadMapScript("mapScript/floodV2/nuclearReactor", true)
    mainScript.loadMapScript("mapScript/floodV2/unitForceProjector", true)
}