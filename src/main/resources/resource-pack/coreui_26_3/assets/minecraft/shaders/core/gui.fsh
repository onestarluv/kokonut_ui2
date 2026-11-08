#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection { mat4 ProjMat; };
layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 QuadCorner;
layout(location = 2) in vec2 GuiPosition;
layout(location = 0) out vec4 fragColor;
void main() {
    mat2 cornerJacobian = mat2(dFdx(QuadCorner), dFdy(QuadCorner));
    mat2 positionJacobian = mat2(dFdx(GuiPosition), dFdy(GuiPosition));
    float cornerDeterminant = determinant(cornerJacobian);
    mat2 quadAxes = abs(cornerDeterminant) > 0.00000001 ? positionJacobian * inverse(cornerJacobian) : mat2(0.0);
    vec2 quadSize = abs(quadAxes[0]) + abs(quadAxes[1]);
    vec2 quadOrigin = GuiPosition - quadAxes * QuadCorner
            + min(quadAxes[0], vec2(0.0)) + min(quadAxes[1], vec2(0.0));
    float screenCenter = -ProjMat[3][0] / ProjMat[0][0];
    bool white = all(greaterThan(vertexColor, vec4(0.999)));
    // Dialog body width is canvas + 31: 511 for classic menus, up to 751 for wide image menus.
    bool horizontal = quadSize.x > 510.9 && quadSize.x < 751.1 && abs(quadSize.y - 1.0) < 0.05
            && abs(quadOrigin.x + quadSize.x * 0.5 - screenCenter) < 20.0;
    float rowCount = (quadSize.y - 6.0) / 9.0;
    bool vertical = abs(quadSize.x - 1.0) < 0.05 && rowCount >= 3.99 && rowCount <= 60.01
    && abs(rowCount - round(rowCount)) < 0.01
    && abs(abs(quadOrigin.x + 0.5 - screenCenter) - 315.5) < 80.0;
    if (vertexColor.a == 0.0 || (white && (horizontal || vertical))) discard;
    fragColor = vertexColor * ColorModulator;
}
