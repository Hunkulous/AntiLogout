package org.samo_lego.antilogout.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.samo_lego.antilogout.datatracker.LogoutRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerDeathMsgSaver {

    @Unique
    private static final int MAX_DEATH_MESSAGE_LENGTH = 256;
    @Unique
    private final ServerPlayer self = (ServerPlayer) (Object) this;

    @Unique
    public abstract net.minecraft.world.level.Level antilogout_level();

    /**
     * Injects into the player death handler to save death messages for fake/disconnected players.
     * Stores the message in SKIPPED_DEATH_MESSAGES for later display.
     * @param damageSource the source of damage
     * @param ci callback info
     */
    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        if (((LogoutRules) this).al_isFake()) {
            ServerLevel serverLevel = (ServerLevel) this.antilogout_level();
            boolean seeDeathMsgs = true;

            Component deathMsg;
            if (seeDeathMsgs) {
                deathMsg = Component.literal(self.getName().getString() + " died");

                if (deathMsg.getString().length() > MAX_DEATH_MESSAGE_LENGTH) {
                    String string = deathMsg.getString().substring(0, MAX_DEATH_MESSAGE_LENGTH);
                        var attackTooLongMsg = Component.translatable("death.attack.message_too_long",
                            Component.literal(string).withStyle(ChatFormatting.YELLOW));

                            deathMsg = Component.translatable("death.attack.even_more_magic", self.getDisplayName());
                }
            } else {
                deathMsg = CommonComponents.EMPTY;
            }
            LogoutRules.SKIPPED_DEATH_MESSAGES.put(self.getUUID(), deathMsg);
        }
    }
}
