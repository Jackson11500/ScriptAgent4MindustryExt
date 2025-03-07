package mapScript.floodV2.lib

import cf.wayzer.scriptAgent.define.Script
import coreMindustry.lib.listen
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Building
import mindustry.gen.Groups

interface BuildingBinder<T : Building> {
    val build: T
    fun update() {
        build.selfUpdate()
    }

    fun T.selfUpdate() {}
    fun onRemove(resetEvent: Boolean) {}

    object Null : BuildingBinder<Nothing> {
        override val build: Nothing
            get() = throw NullPointerException("This is Null")
    }
}

interface BuildingTracker<K : Building, V : BuildingBinder<*>> {
    val map: MutableMap<K, V>
    fun load()

    fun update() {
        map.values.forEach(BuildingBinder<*>::update)
    }

    fun reset(resetEvent: Boolean = false) {
        map.values.forEach { it.onRemove(resetEvent) }
        map.clear()
    }

    fun addTacker(build: K)
    fun removeTacker(build: K) {
        map.remove(build)?.onRemove(false)
    }

    fun listenChange(script: Script): BuildingTracker<K, V>
    fun listenLifecycle(script: Script, safeEnable: () -> Boolean): BuildingTracker<K, V>

    companion object {
        inline fun <reified K : Building, V : BuildingBinder<*>> new(
            searchAll: Boolean,
            crossinline enable: () -> Boolean,
            crossinline factory: (build: K) -> V,
            crossinline filter: (K) -> Boolean,
        ): BuildingTracker<K, V> {
            val tracker = object : BuildingTracker<K, V> {
                override val map = mutableMapOf<K, V>()
                override fun addTacker(build: K) {
                    if (build in map) return
                    map[build] = factory(build)
                }

                override fun load() {
                    if (!searchAll)
                        Groups.build.each {
                            if (it is K && filter(it))
                                addTacker(it)
                        }
                    else Vars.world.tiles.eachTile { tile ->
                        val build = tile.build
                        if (build?.tile == tile && build is K && filter(build))
                            addTacker(build)
                    }
                }

                override fun listenChange(script: Script): BuildingTracker<K, V> {
                    with(script) {
                        listen<EventType.TilePreChangeEvent> {
                            if (!enable()) return@listen
                            val build = it.tile.build
                            if (build?.tile == it.tile && build is K && filter(build))
                                removeTacker(build)
                        }
                        listen<EventType.TileChangeEvent> {
                            if (!enable()) return@listen
                            val build = it.tile.build
                            if (build?.tile == it.tile && build is K && filter(build))
                                addTacker(build)
                        }
                    }
                    return this
                }

                /** auto call [load] [update] [reset] */
                override fun listenLifecycle(script: Script, safeEnable: () -> Boolean): BuildingTracker<K, V> {
                    with(script) {
                        listen<EventType.PlayEvent> { if (safeEnable()) load() }
                        onEnable { if (safeEnable()) load() }
                        listen<EventType.ResetEvent> { reset(true) }
                        onDisable { reset() }
                        listen(EventType.Trigger.update) {
                            if (enable())
                                update()
                        }
                    }
                    return this
                }
            }
            return tracker
        }
    }
}