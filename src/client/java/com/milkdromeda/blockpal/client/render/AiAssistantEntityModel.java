package com.milkdromeda.blockpal.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.Identifier;

public class AiAssistantEntityModel extends HumanoidModel<AiAssistantEntityRenderState> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath("blockpal", "ai_assistant"), "main");

    public AiAssistantEntityModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createModelData() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
