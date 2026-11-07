package com.rdh.swarm.mixin;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatUtils.class)
public class ChatUtilsMixin {
    @Inject(method = "info(Ljava/lang/String;[Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
    private static void onInfo(String message, Object[] args, CallbackInfo ci) {
        // Prevent spam logs where only the prefix is printed (e.g., "[Meteor] BotBrain")
        if ("BotBrain".equals(message) && (args == null || args.length == 0)) {
            ci.cancel();
        }
    }
}
