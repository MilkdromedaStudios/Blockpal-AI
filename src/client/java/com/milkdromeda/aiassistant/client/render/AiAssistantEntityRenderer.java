package com.milkdromeda.aiassistant.client.render;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.util.Identifier;

public class AiAssistantEntityRenderer extends
        BipedEntityRenderer<AiAssistantEntity, AiAssistantEntityRenderState, AiAssistantEntityModel> {

    private static final Identifier TEXTURE =
            Identifier.of("minecraft", "textures/entity/player/wide/steve.png");

    public AiAssistantEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx,
                new AiAssistantEntityModel(ctx.getPart(AiAssistantEntityModel.LAYER)),
                0.5f);

        this.addFeature(new ArmorFeatureRenderer<>(
                this,
                new AiAssistantEntityModel(ctx.getPart(AiAssistantEntityModel.LAYER)),
                new AiAssistantEntityModel(ctx.getPart(AiAssistantEntityModel.LAYER)),
                ctx.getEquipmentRenderer()
        ));
    }

    @Override
    public AiAssistantEntityRenderState createRenderState() {
        return new AiAssistantEntityRenderState();
    }

    @Override
    public void updateRenderState(AiAssistantEntity entity, AiAssistantEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.assistantName = entity.getAssistantName();
    }

    @Override
    public Identifier getTexture(AiAssistantEntityRenderState state) {
        return TEXTURE;
    }
}
