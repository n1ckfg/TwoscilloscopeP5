package twoscilloscopeP5;

import processing.core.*;
import processing.opengl.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * A port of OsciMesh from Hansi Raber's Oscilloscope app, the renderer at the heart of the
 * audio-to-vector half of the library.
 * <p>Every pair of neighbouring samples becomes a quad around the line between them, and a
 * shader fills the quad with the light a gaussian electron beam leaves as it sweeps along
 * that line (the technique from m1el's woscope). A beam that moves quickly between two
 * samples spreads its light thinly, so fast strokes come out dim and slow ones bright, the
 * way they do on a real CRT. Drawn additively into a slowly fading buffer (see
 * Oscilloscope), this is what gives the image its glow and persistence.</p>
 * <p>Differences from the original:</p>
 * <ul>
 * <li>The shader is built into the class and compiled by Processing's PShader, so it runs
 *     wherever P2D and P3D do, instead of being hot-loaded from
 *     bin/data/shaders/osci.vert/.frag.</li>
 * <li>Points are in scope units (-1..1). draw() uses the PGraphics's current matrix, where
 *     the original passed in its own view matrix.</li>
 * <li>The mesh goes to the GPU in one vertex buffer and one draw call, through Processing's
 *     low-level PGL, as the original's ofMesh did. Processing's beginShape()/vertex() would
 *     cost far more per vertex, and its custom vertex attributes stay bound afterwards and
 *     miscolor later fills. Needs a P2D or P3D renderer.</li>
 * </ul>
 */

public class OsciMesh {

  // The beam: the light a gaussian spot leaves as it sweeps along one
  // segment, integrated analytically with erf (after m1el's woscope).
  // vUvl.x runs along the segment, vUvl.y across it, vUvl.z is its length.
  // Normalized so a beam that stands still peaks at 1.
  static final String[] VERT = {
    "uniform mat4 uMatrix;",
    "attribute vec2 aPosition;",
    "attribute vec4 aBeam;",
    "varying vec3 vUvl;",
    "varying float vBright;",
    "void main() {",
    "  vUvl = aBeam.xyz;",
    "  vBright = aBeam.w;",
    "  gl_Position = uMatrix * vec4(aPosition, 0.0, 1.0);",
    "}"
  };

  static final String[] FRAG = {
    "#ifdef GL_ES",
    "#ifdef GL_FRAGMENT_PRECISION_HIGH",
    "precision highp float;",
    "#else",
    "precision mediump float;",
    "#endif",
    "#endif",
    "#define SQRT2 1.4142135623730951",
    "#define TAUR 2.5066282746310002",
    "uniform float uSize;",
    "uniform float uIntensity;",
    "uniform vec3 uRgb;",
    "varying vec3 vUvl;",
    "varying float vBright;",
    // approximates the error function, needed for the gaussian integral
    "float erfApprox(float x) {",
    "  float s = sign(x), a = abs(x);",
    "  x = 1.0 + (0.278393 + (0.230389 + 0.000972 * a + 0.078108 * a * a) * a) * a;",
    "  x *= x;",
    "  return s - s / (x * x);",
    "}",
    "void main() {",
    "  float len = vUvl.z;",
    "  vec2 xy = vUvl.xy;",
    "  float sigma = uSize / 3.0;",
    "  float b;",
    "  if (len < 1E-6) {",
    // too short to integrate, the intensity at the position
    "    b = exp(-dot(xy, xy) / (2.0 * sigma * sigma));",
    "  } else {",
    "    b = erfApprox(xy.x / SQRT2 / sigma) - erfApprox((xy.x - len) / SQRT2 / sigma);",
    "    b *= exp(-xy.y * xy.y / (2.0 * sigma * sigma)) * sigma * TAUR / (2.0 * len);",
    "  }",
    "  b *= vBright * uIntensity;",
    // where the beam is brightest it burns towards white
    "  vec3 col = uRgb * b + vec3(max(b - 1.0, 0.0) * 0.35);",
    "  gl_FragColor = vec4(col, 1.0);",
    "}"
  };

  static final float EPS = 1E-6f;
  static final int FLOATS_PER_VERTEX = 6; // x, y, along, across, length, brightness

  /**
   * the original's shader parameters
   */
  public float uSize = 0.01f;      // beam radius in scope units
  public PVector uRgb = new PVector(1, 1, 1);  // beam color, 0..1
  public float uIntensity = 1;

  // it's a mesh. can you believe it?
  FloatArray vertices = new FloatArray(6 * 6 * 4096);

  PShader shader;
  boolean shaderTried = false;
  float lastX = 0, lastY = 0;

  // the vertex buffer, made for the shader's GL program
  int vbo = 0;
  int vboProgram = -1;
  FloatBuffer upload;

  public OsciMesh() {
    clear();
  }

  public int getNumVertices() {
    return vertices.size / FLOATS_PER_VERTEX;
  }

  public void addLines(float[] left, float[] right, float[] bright, int n) {
    addLines(left, right, bright, n, 1);
  }

  /**
   * Add many lines at once.
   * left: x coordinates (-1..1)
   * right: y coordinates (-1..1)
   * bright: brightness (0..1), or null for full brightness
   * stride: step between samples in left and right (not bright)
   */
  public void addLines(float[] left, float[] right, float[] bright, int n, int stride) {
    // no work? go home watch tv or something
    if (n <= 0 || stride <= 0) return;

    addLine(lastX, lastY, left[0], right[0], bright == null ? 1 : bright[0]);
    int lastIndex = ((n - 1) / stride) * stride;
    lastX = left[lastIndex];
    lastY = right[lastIndex];

    vertices.ensureCapacity(vertices.size + 6 * FLOATS_PER_VERTEX * (n / stride + 1));

    for (int i = stride; i < n; i += stride) {
      addLine(left[i - stride], right[i - stride], left[i], right[i], bright == null ? 1 : bright[i]);
    }
  }

  /**
   * Add one line from (x0, y0) to (x1, y1) (-1..1), with brightness 0..1.
   */
  public void addLine(float x0, float y0, float x1, float y1, float bright) {
    float dx = x1 - x0, dy = y1 - y0;
    float z = (float) Math.sqrt(dx * dx + dy * dy);
    if (z > EPS) {
      dx /= z;
      dy /= z;
    } else {
      dx = 1;
      dy = 0;
    }

    dx *= uSize;
    dy *= uSize;
    float nx = -dy, ny = dx;

    add(x0 - dx - nx, y0 - dy - ny, -uSize, -uSize, z, bright);
    add(x0 - dx + nx, y0 - dy + ny, -uSize, uSize, z, bright);
    add(x1 + dx - nx, y1 + dy - ny, z + uSize, -uSize, z, bright);

    add(x0 - dx + nx, y0 - dy + ny, -uSize, uSize, z, bright);
    add(x1 + dx - nx, y1 + dy - ny, z + uSize, -uSize, z, bright);
    add(x1 + dx + nx, y1 + dy + ny, z + uSize, uSize, z, bright);
  }

  void add(float x, float y, float u, float v, float len, float bright) {
    vertices.ensureCapacity(vertices.size + FLOATS_PER_VERTEX);
    float[] d = vertices.data;
    int i = vertices.size;
    d[i] = x;
    d[i + 1] = y;
    d[i + 2] = u;
    d[i + 3] = v;
    d[i + 4] = len;
    d[i + 5] = bright;
    vertices.size += FLOATS_PER_VERTEX;
  }

  boolean loadShader(PGraphics g) {
    shaderTried = true;
    try {
      shader = new PShader(g.parent, VERT, FRAG);
    } catch (Exception e) {
      System.err.println("OsciMesh: couldn't compile the beam shader: " + e.getMessage());
      shader = null;
    }
    return shader != null;
  }

  /**
   * Draws additively into g, which must be P2D or P3D.
   */
  public void draw(PGraphics g) {
    int n = getNumVertices();
    if (n == 0) return;
    if (!(g instanceof PGraphicsOpenGL)) {
      if (!shaderTried) System.err.println("OsciMesh: the beam needs a P2D or P3D renderer");
      shaderTried = true;
      return;
    }
    if (!shaderTried) loadShader(g);
    if (shader == null) return;
    PGraphicsOpenGL pg = (PGraphicsOpenGL) g;

    g.pushStyle();
    // additive beams pile up; a depth test would throw the overlaps away
    boolean depth = g.is3D();
    if (depth) g.hint(PConstants.DISABLE_DEPTH_TEST);
    g.blendMode(PConstants.ADD);
    shader.set("uMatrix", pg.projmodelview);
    shader.set("uRgb", uRgb.x, uRgb.y, uRgb.z);
    shader.set("uSize", uSize);
    shader.set("uIntensity", uIntensity);

    PGL pgl = g.beginPGL();
    shader.bind();
    if (vbo == 0 || vboProgram != shader.glProgram) {
      // a new GL program means a new GL context, and the old buffer is gone with it
      IntBuffer ids = IntBuffer.allocate(1);
      pgl.genBuffers(1, ids);
      vbo = ids.get(0);
      vboProgram = shader.glProgram;
    }
    int count = n * FLOATS_PER_VERTEX;
    if (upload == null || upload.capacity() < count) {
      upload = ByteBuffer.allocateDirect(Math.max(count, 6 * 6 * 4096) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }
    upload.clear();
    upload.put(vertices.data, 0, count);
    upload.flip();

    int posLoc = pgl.getAttribLocation(shader.glProgram, "aPosition");
    int beamLoc = pgl.getAttribLocation(shader.glProgram, "aBeam");
    int stride = FLOATS_PER_VERTEX * 4;
    pgl.bindBuffer(PGL.ARRAY_BUFFER, vbo);
    pgl.bufferData(PGL.ARRAY_BUFFER, count * 4, upload, PGL.STREAM_DRAW);
    pgl.enableVertexAttribArray(posLoc);
    pgl.vertexAttribPointer(posLoc, 2, PGL.FLOAT, false, stride, 0);
    pgl.enableVertexAttribArray(beamLoc);
    pgl.vertexAttribPointer(beamLoc, 4, PGL.FLOAT, false, stride, 2 * 4);
    pgl.drawArrays(PGL.TRIANGLES, 0, n);
    pgl.disableVertexAttribArray(posLoc);
    pgl.disableVertexAttribArray(beamLoc);
    pgl.bindBuffer(PGL.ARRAY_BUFFER, 0);
    shader.unbind();
    g.endPGL();

    if (depth) g.hint(PConstants.ENABLE_DEPTH_TEST);
    g.popStyle();
  }

  public void clear() {
    vertices.clear();
  }

}
