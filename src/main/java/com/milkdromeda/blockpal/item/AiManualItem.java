package com.milkdromeda.blockpal.item;

import com.milkdromeda.blockpal.network.AiNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The AI Manual — given once on first join, opens the in-game wiki when right-clicked. */
public class AiManualItem extends Item {

    public AiManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            AiNetworking.openManualFor(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
