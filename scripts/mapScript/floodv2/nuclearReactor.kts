@file:Depends("coreMindustry/utilMapRule", "newContent")

package mapScript.floodV2

import arc.graphics.Color
import arc.math.Mathf
import arc.math.geom.Position
import coreLibrary.lib.config
import coreMindustry.lib.game
import coreMindustry.lib.listen
import mapScript.floodV2.Module
import mapScript.floodV2.lib.BuildingBinder
import mapScript.floodV2.lib.BuildingTracker
import mapScript.floodV2.lib.FloodUtil
import mapScript.floodV2.lib.depositCreeper
import mindustry.Vars.tilesize
import mindustry.Vars.world
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.content.Items
import mindustry.entities.bullet.ArtilleryBulletType
import mindustry.entities.bullet.BulletType
import mindustry.game.EventType
import mindustry.gen.Bullet
import mindustry.gen.Call
import mindustry.gen.CreateBulletCallPacket
import mindustry.gen.Groups
import mindustry.net.CrashSender.send
import mindustry.world.Tile
import mindustry.world.blocks.defense.turrets.ItemTurret
import mindustry.world.blocks.power.NuclearReactor


val sporeOffset by config.key(16f, "孢子目标随机范围")
val maxDistance by config.key(120f, "孢子攻击距离", "需要重载")

val mainScript = contextScript<Module>()
val util = contextScript<coreMindustry.UtilMapRule>()

val sporeType by lazy {
    util.newContent((Blocks.hail as ItemTurret).ammoTypes.values().first(), ::SporeBullet).apply {
        splashDamageRadius = 5f //spore radius
        splashDamage = 1000f //spore amount
        damage = 3000f  // 裂解需要拦截的量
        speed = 0.45f
        lifetime = maxDistance * tilesize / speed //max
        despawnHit = true
    }
}

//reactor
inner class FloodNuclearReactor(override val build: NuclearReactor.NuclearReactorBuild) :
    BuildingBinder<NuclearReactor.NuclearReactorBuild> {
    override fun update() {
        if (Mathf.chance(0.3 / 60))
            Call.setItem(build, Items.thorium, Mathf.random(0, 3))
    }

    fun onDestroy() {
        if (build.heat >= 0.999f) {
            launch(Dispatchers.game) {
                delay(100)
                if (!FloodUtil.enable) return@launch
                build.tile.setNet(Blocks.thoriumReactor, FloodUtil.creepTeam, 0)
            }
            val target = findTarget() ?: return
            sporeType.create(build, target)
        }
    }
}

val tracker = BuildingTracker.new(false, FloodUtil::enable, ::FloodNuclearReactor) {
    it.team() == FloodUtil.creepTeam
}
    .listenChange(this)
    .listenLifecycle(this, { true })

listen<EventType.BlockDestroyEvent> {
    if (!FloodUtil.enable) return@listen
    tracker.map[it.tile.build]?.onDestroy()
}

//spore

class SporeBullet(private val origin: BulletType) : ArtilleryBulletType() {
    fun create(from: Position, target: Position) {
        val time = (from.dst(target) / speed).coerceAtMost(lifetime)
        create(null, FloodUtil.creepTeam, from.x, from.y, from.angleTo(target)).apply {
            lifetime = time
        }

        val packet = CreateBulletCallPacket()
        packet.type = origin
        packet.team = FloodUtil.creepTeam
        packet.x = from.x
        packet.y = from.y
        packet.angle = from.angleTo(target)
        packet.damage = damage
        packet.velocityScl = speed / origin.speed
        packet.lifetimeScl = time / origin.lifetime
        net.send(packet, false)
    }

    override fun hit(b: Bullet, x: Float, y: Float) {
        super.hit(b, x, y)
        if (b.damage < 0 || !FloodUtil.enable) return
        //孢子爆炸
        val tile = world.tileWorld(x, y) ?: return
        Call.effect(Fx.sapExplosion, x, y, splashDamageRadius, FloodUtil.creepTeam.color)
        depositCreeper(tile, splashDamageRadius.toInt(), splashDamage * b.damage / b.type.damage)
    }

    override fun update(b: Bullet) {
        super.update(b)
        if (Mathf.chanceDelta(1.0 / 3 / 60)) {
            Call.effect(Fx.lancerLaserCharge, b.x, b.y, b.deltaAngle(), Color.blue);
        }
    }
}

fun findTarget(): Tile? {
    repeat(10) {
        val target = Groups.player.filter { it.unit().isValid }.randomOrNull() ?: return@repeat
        repeat(100) {
            val x = target.x + Mathf.random(sporeOffset * tilesize)
            val y = target.y + Mathf.random(sporeOffset * tilesize)
            val ret = world.tileWorld(x, y)
            if (ret != null && FloodUtil.creepMap[ret] >= 0)
                return ret
        }
    }
    return null
}