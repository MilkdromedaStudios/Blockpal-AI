package com.milkdromeda.aiassistant.client.render;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public class AiAssistantEntityModel extends BipedEntityModel<AiAssistantEntityRenderState> {

    public static final EntityModelLayer LAYER =
            new EntityModelLayer(Identifier.of("ai-assistant", "ai_assistant"), "main");

    public AiAssistantEntityModel(ModelPart root) {
        super(root);
    }

    public static TexturedModelData createModelData() {
        ModelData modelData = BipedEntityModel.getModelData(Dilation.NONE, 0.0f);
        return TexturedModelData.of(modelData, 64, 64);
    }
}
