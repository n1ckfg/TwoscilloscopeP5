// The beam: the light a gaussian spot leaves as it sweeps along one
// segment, integrated analytically with erf (after m1el's woscope).
// Normalized so a beam that stands still peaks at 1.

#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

#define SQRT2 1.4142135623730951
#define TAUR 2.5066282746310002

uniform float uSize;
uniform float uIntensity;
uniform vec3 uRgb;

varying vec3 vUvl;
varying float vBright;

// approximates the error function, needed for the gaussian integral
float erfApprox(float x) {
  float s = sign(x), a = abs(x);
  x = 1.0 + (0.278393 + (0.230389 + 0.000972 * a + 0.078108 * a * a) * a) * a;
  x *= x;
  return s - s / (x * x);
}

void main() {
  float len = vUvl.z;
  vec2 xy = vUvl.xy;
  float sigma = uSize / 3.0;
  float b;
  if (len < 1E-6) {
    // too short to integrate, the intensity at the position
    b = exp(-dot(xy, xy) / (2.0 * sigma * sigma));
  } else {
    b = erfApprox(xy.x / SQRT2 / sigma) - erfApprox((xy.x - len) / SQRT2 / sigma);
    b *= exp(-xy.y * xy.y / (2.0 * sigma * sigma)) * sigma * TAUR / (2.0 * len);
  }
  b *= vBright * uIntensity;
  // where the beam is brightest it burns towards white
  vec3 col = uRgb * b + vec3(max(b - 1.0, 0.0) * 0.35);
  gl_FragColor = vec4(col, 1.0);
}
