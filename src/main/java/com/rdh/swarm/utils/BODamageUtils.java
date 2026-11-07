/*
Modified from Meteor Client
https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/utils/player/DamageUtils.java
*/

package com.rdh.swarm.utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Minimal damage utils fallback for enhanced-swarm: provides a simple
 * crystalDamage approximation so modules can compile and run without
 * depending on BlackOut mixins.
 */
public class BODamageUtils {
    public static double crystalDamage(LivingEntity entity, AABB box, Vec3 pos, boolean ignoreTerrain) {
        return crystalDamage(entity, box, pos, (BlockPos) null, ignoreTerrain);
    }

    public static double crystalDamage(LivingEntity entity, AABB box, Vec3 pos, BlockPos ignorePos, boolean ignoreTerrain) {
        if (entity == null || pos == null) return 0;
        double dx = entity.getX() - pos.x;
        double dy = entity.getY() - pos.y;
        double dz = entity.getZ() - pos.z;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        // Simple approximation: closer -> higher damage, scaled to typical crystal ranges
        double base = Math.max(0.0, 6.0 - dist);
        return base;
    }
}
