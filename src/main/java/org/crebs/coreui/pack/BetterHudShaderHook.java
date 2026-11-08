package org.crebs.coreui.pack;

import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.manager.ShaderManager;

import java.util.List;

/**
 * BetterHud (Kokonut_HUD) replaces the 26.3 core text shader with its own generated version.
 * Two overlays cannot both own text.vsh/text.fsh, so CoreUi injects its GUI logic into
 * BetterHud's generated shader through the official tag hooks instead of shipping its own copy.
 *
 * Only load this class after confirming BetterHud is enabled.
 */
public final class BetterHudShaderHook {
    private BetterHudShaderHook() {}

    public static void register() {
        ShaderManager shaders = BetterHudAPI.inst().getShaderManager();
        shaders.addTagSupplier(ShaderManager.ShaderType.TEXT_VERTEX, () -> ShaderManager.newTag()
                .add("GenerateOtherDefinedMethod", List.of(
                        "#ifdef IS_GUI",
                        "#define CORE_LOCATION(n) layout(location = n)",
                        "#include <coreui:position_marker.glsl>",
                        "#include <coreui:stretch_quads.glsl>",
                        "#endif"))
                .add("GenerateOtherMainMethod", List.of(
                        "#ifdef IS_GUI",
                        "coreStretchIndex = -1;",
                        "coreStretchPos = vec2(0.0);",
                        "coreStretchSize = vec2(0.0);",
                        "corePopupOpacity = 1.0;",
                        "coreSkinOrigin = ivec2(0);",
                        "pos.xy += coreOffset(ivec4(round(Color * 255.0)));",
                        "#endif")));
        shaders.addTagSupplier(ShaderManager.ShaderType.TEXT_FRAGMENT, () -> ShaderManager.newTag()
                .add("GenerateOtherDefinedMethod", List.of(
                        "#ifdef IS_GUI",
                        "#define CORE_LOCATION(n) layout(location = n)",
                        "#include <coreui:position_marker.glsl>",
                        "#include <coreui:quad_paint.glsl>",
                        "#endif"))
                .add("GenerateOtherMainMethod", List.of(
                        "#ifdef IS_GUI",
                        "if (coreStretchIndex == -2) discard;",
                        "if (coreStretchIndex >= 0) {",
                        "coreDrawQuad();",
                        "return;",
                        "}",
                        "#ifndef OIT_ALPHA_ONLY",
                        "if (vertexColor.a < 0.1) discard;",
                        "#endif",
                        "#endif")));
    }
}
