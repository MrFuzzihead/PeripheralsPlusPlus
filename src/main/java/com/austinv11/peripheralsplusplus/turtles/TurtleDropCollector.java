package com.austinv11.peripheralsplusplus.turtles;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.austinv11.peripheralsplusplus.utils.TurtleUtil;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dan200.computercraft.api.turtle.ITurtleAccess;

/**
 * Collects the drops of an entity a tool upgrade killed straight into the turtle's inventory.
 *
 * <p>
 * The mapping used to be a bare {@code static HashMap} that was never pruned, so any entity that died without firing
 * {@link LivingDropsEvent} (despawn, {@code /kill} with mob loot off, a {@code doMobLoot=false} world) leaked its
 * turtle, world and chunk references for the lifetime of the JVM. It is also now bounded and only registered once:
 * {@code registerUpgrade} used to add one listener per RedPower-like upgrade, i.e. thirty identical handlers
 * (CODE_REVIEW.md section 4.16).
 * </p>
 */
public abstract class TurtleDropCollector {

    /** Upper bound on tracked entities. Killing more than this between drops is not worth unbounded memory. */
    private static final int MAX_TRACKED = 256;

    private static final Map<Entity, ITurtleAccess> TRACKED = new HashMap<Entity, ITurtleAccess>();

    private final Listener listener = new Listener();

    /**
     * Records that {@code turtle} killed {@code entity}, so the entity's drops can be redirected into the turtle.
     *
     * @param turtle the turtle that landed the blow
     * @param ent    the entity that was hit
     */
    public void addEntity(ITurtleAccess turtle, Entity ent) {
        if (ent == null) return;
        synchronized (TRACKED) {
            if (TRACKED.size() >= MAX_TRACKED) {
                // Forget the oldest entries rather than growing without bound.
                Iterator<Entity> iterator = TRACKED.keySet()
                    .iterator();
                while (TRACKED.size() >= MAX_TRACKED && iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            TRACKED.put(ent, turtle);
        }
    }

    /**
     * The single event listener for this feature; registering it more than once is a no-op.
     *
     * @return the listener instance
     */
    public Object newInstanceOfListener() {
        return listener;
    }

    /**
     * @return the number of entities currently waiting for their drops to be collected
     */
    public static int trackedCount() {
        synchronized (TRACKED) {
            return TRACKED.size();
        }
    }

    /**
     * Forgets every tracked entity, for example when a world unloads.
     */
    public static void clear() {
        synchronized (TRACKED) {
            TRACKED.clear();
        }
    }

    public static class Listener {

        @SubscribeEvent
        public void onDrops(LivingDropsEvent event) {
            ITurtleAccess turtle;
            synchronized (TRACKED) {
                turtle = TRACKED.remove(event.entity);
            }
            if (turtle == null) return;
            TurtleUtil.addItemListToInv(TurtleUtil.entityItemsToItemStack(event.drops), turtle);
            event.setCanceled(true);
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload event) {
            // Tracked entities belong to a world; drop the references when it goes away.
            clear();
        }
    }
}
