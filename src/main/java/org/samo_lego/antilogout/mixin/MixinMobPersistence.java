package org.samo_lego.antilogout.mixin;

import net.minecraft.world.entity.Mob;
import org.samo_lego.antilogout.chunk.TemporaryMobPersistence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Mob.class)
public abstract class MixinMobPersistence implements TemporaryMobPersistence {
    @Shadow
    private boolean persistenceRequired;

    @Override
    public void antilogout_setPersistenceRequired(boolean required) {
        this.persistenceRequired = required;
    }
}
