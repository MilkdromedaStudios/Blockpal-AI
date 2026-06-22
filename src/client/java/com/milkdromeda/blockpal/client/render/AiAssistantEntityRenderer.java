package com.milkdromeda.blockpal.client.render;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

import java.util.Locale;

public class AiAssistantEntityRenderer extends
        HumanoidMobRenderer<AiAssistantEntity, AiAssistantEntityRenderState, AiAssistantEntityModel> {

    private static final Identifier STEVE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    public AiAssistantEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AiAssistantEntityModel(ctx.bakeLayer(AiAssistantEntityModel.LAYER)), 0.5f);
    }

    @Override
    public AiAssistantEntityRenderState createRenderState() {
        return new AiAssistantEntityRenderState();
    }

    @Override
    public void extractRenderState(AiAssistantEntity entity, AiAssistantEntityRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.assistantName = entity.getAssistantName();
        state.skin = entity.getSkin();
    }

    /**
     * Resolves the custom skin id to a texture:
     * <ul>
     *   <li>"default"/"steve" (or empty) → the vanilla Steve skin</li>
     *   <li>contains ':' → used directly as a {@code namespace:path} texture</li>
     *   <li>a PNG dropped in {@code config/blockpal/skins/<name>.png} → that file</li>
     *   <li>otherwise → {@code blockpal:textures/entity/skins/<name>.png}</li>
     * </ul>
     */
    @Override
    public Identifier getTextureLocation(AiAssistantEntityRenderState state) {
        String skin = state.skin;
        if (skin == null || skin.isBlank()
                || skin.equalsIgnoreCase("default") || skin.equalsIgnoreCase("steve")) {
            return STEVE;
        }
        if (skin.indexOf(':') >= 0) {
            Identifier id = Identifier.tryParse(skin);
            if (id != null) return id;
        }
        // A user-supplied PNG in the skins folder takes priority over baked-in skins.
        Identifier runtime = RuntimeSkins.textureFor(skin);
        if (runtime != null) return runtime;
        Identifier id = Identifier.tryParse(
                "blockpal:textures/entity/skins/" + skin.toLowerCase(Locale.ROOT) + ".png");
        return id != null ? id : STEVE;
    }
}
