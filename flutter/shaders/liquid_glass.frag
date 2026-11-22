#version 460 core
#include <flutter/runtime_effect.glsl>

// The engine owns these first two uniforms. Do not set them from Dart.
uniform vec2 uSize;
uniform sampler2D uTexture;

// Corner radii are normalized by logical width: TL, TR, BR, BL.
uniform vec4 uRadii;
uniform vec2 uSurfaceSize;
// refraction strength, refraction width, chromatic aberration, specular strength
uniform vec4 uOptics;
// brightness, contrast, saturation, unused
uniform vec4 uTone;

out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 b, vec4 r) {
  float radius = p.x > 0.0
      ? (p.y > 0.0 ? r.z : r.y)
      : (p.y > 0.0 ? r.w : r.x);
  vec2 q = abs(p) - b + radius;
  return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - radius;
}

vec4 sampleBilinear(vec2 uv) {
  vec2 safeUv = clamp(
    uv,
    vec2(0.5, 0.5) / uSize,
    vec2(1.0, 1.0) - vec2(0.5, 0.5) / uSize
  );
  vec2 st = safeUv * uSize - 0.5;
  vec2 i = floor(st);
  vec2 f = fract(st);
  vec2 inv = 1.0 / uSize;
  return mix(
    mix(
      texture(uTexture, (i + vec2(0.5, 0.5)) * inv),
      texture(uTexture, (i + vec2(1.5, 0.5)) * inv),
      f.x
    ),
    mix(
      texture(uTexture, (i + vec2(0.5, 1.5)) * inv),
      texture(uTexture, (i + vec2(1.5, 1.5)) * inv),
      f.x
    ),
    f.y
  );
}

vec3 toneMap(vec3 color) {
  float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
  color = mix(vec3(luma, luma, luma), color, uTone.z);
  color = (color - 0.5) * uTone.y + 0.5;
  color *= uTone.x;
  return clamp(color, 0.0, 1.0);
}

void main() {
  vec2 uv = FlutterFragCoord().xy / uSize;
#ifdef IMPELLER_TARGET_OPENGLES
  uv.y = 1.0 - uv.y;
#endif

  float aspect = uSurfaceSize.y / max(uSurfaceSize.x, 0.0001);
  vec2 b = vec2(1.0, aspect);
  vec2 p = (uv * 2.0 - 1.0) * b;
  vec4 radii = uRadii * 2.0;

  float d = sdRoundBox(p, b, radii);
  float epsilon = 2.0 / max(uSurfaceSize.x, 0.0001);
  vec2 grad = vec2(
    sdRoundBox(p + vec2(epsilon, 0.0), b, radii)
      - sdRoundBox(p - vec2(epsilon, 0.0), b, radii),
    sdRoundBox(p + vec2(0.0, epsilon), b, radii)
      - sdRoundBox(p - vec2(0.0, epsilon), b, radii)
  ) / (2.0 * epsilon);
  vec2 normal = normalize(grad + vec2(0.000001, 0.000001));

  float widthN = 2.0 * uOptics.y / max(uSurfaceSize.x, 0.0001);
  float edge = 1.0 - smoothstep(0.0, widthN, -d);
  float rim = 1.0 - smoothstep(
    0.0,
    2.0 * 1.25 / max(uSurfaceSize.x, 0.0001),
    -d
  );
  float fresnel = pow(rim, 3.0);

  float strengthN = 2.0 * uOptics.x / max(uSurfaceSize.x, 0.0001);
  float dispersionN = 2.0 * uOptics.z / max(uSurfaceSize.x, 0.0001);
  vec2 warp = -normal * strengthN * pow(edge, 1.35);
  vec2 dispersion = normal * dispersionN * edge;

  vec2 uvR = (p + warp - dispersion - b) / (2.0 * b) + 0.5;
  vec2 uvG = (p + warp - b) / (2.0 * b) + 0.5;
  vec2 uvB = (p + warp + dispersion - b) / (2.0 * b) + 0.5;

  vec4 sampleR = sampleBilinear(uvR);
  vec4 sampleG = sampleBilinear(uvG);
  vec4 sampleB = sampleBilinear(uvB);
  vec3 straightR = sampleR.a > 0.0001 ? sampleR.rgb / sampleR.a : sampleR.rgb;
  vec3 straightG = sampleG.a > 0.0001 ? sampleG.rgb / sampleG.a : sampleG.rgb;
  vec3 straightB = sampleB.a > 0.0001 ? sampleB.rgb / sampleB.a : sampleB.rgb;
  float refractedAlpha = max(max(sampleR.a, sampleG.a), sampleB.a);
  vec3 straight = vec3(straightR.r, straightG.g, straightB.b);
  vec3 graded = toneMap(straight);

  vec2 lightDir = normalize(vec2(-0.55, -0.83));
  float ndl = max(dot(normal, lightDir), 0.0);
  float specular =
      (pow(ndl, 36.0) * 0.85 + pow(ndl, 5.0) * 0.10) * rim
      + fresnel * 0.12;
  float diagonal =
      (p.x * 0.72 + p.y * 0.35 + b.y * 0.18) / max(b.y * 0.08, 0.0001);
  float mirror = exp(-diagonal * diagonal) * rim;
  specular += mirror * 0.16;
  specular *= uOptics.w;

  vec3 outC = (graded + vec3(specular, specular, specular)) * refractedAlpha;
  fragColor = vec4(outC, refractedAlpha);
}
