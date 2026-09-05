package org.samo_lego.antilogout.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.samo_lego.antilogout.datatracker.LogoutRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public class MixinServerCommonPacketListenerImpl {

    /**
     * Injects into the disconnect method to ensure fake/disconnected players are properly handled.
     * Calls onDisconnected for fake players to trigger cleanup logic.
     * @param disconnectionInfo the disconnection info
     * @param ci callback info
     */
    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("TAIL"))
    private void al$disconnect(DisconnectionDetails disconnectionInfo, CallbackInfo ci) {
        if (((Object) this) instanceof ServerGamePacketListenerImpl serverGamePacketListener) {
            if (((LogoutRules) serverGamePacketListener.player).al_isFake()) {
                serverGamePacketListener.onDisconnect(disconnectionInfo);
            }
        }
    }
}
