// The beam's vertex shader, written in Processing's GLSL dialect (no #version,
// attribute/varying): PShader adds the #version line and translates the rest
// for whichever OpenGL the sketch is running on.
// vUvl.x runs along the segment, vUvl.y across it, vUvl.z is its length.

uniform mat4 uMatrix;

attribute vec2 aPosition;
attribute vec4 aBeam;

varying vec3 vUvl;
varying float vBright;

void main() {
  vUvl = aBeam.xyz;
  vBright = aBeam.w;
  gl_Position = uMatrix * vec4(aPosition, 0.0, 1.0);
}
