struct VSInput {
    uint vertexID : SV_VertexID;
};

struct VSOutput {
    float4 pos : SV_Position;
    float4 color : color;
};

static float2 positions[3] = float2[](
    float2(0.0, -0.5), //
    float2(0.5, 0.5), //
    float2(-0.5, 0.5) //
);

static float3 colors[3] = float3[](
    float3(1.0, 0.0, 0.0), //
    float3(0.0, 1.0, 0.0), //
    float3(0.0, 0.0, 1.0) //
);


[shader("vertex")]
VSOutput vertexMain(VSInput input) {
    VSOutput output;

    output.pos = float4(positions[input.vertexID], 0.0, 1.0);
    output.color = float4(colors[input.vertexID], 1.0);

    return output;
}

struct FSOutput {
    float4 color : SV_Target;
};

[shader("fragment")]
FSOutput fragmentMain(VSOutput input) {
    FSOutput output;

    output.color = input.color;

    return output;
}



