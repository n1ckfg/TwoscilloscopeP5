package twoscilloscopeP5;

import processing.core.*;
import java.util.ArrayList;

/**
 * The audio-to-vector half of the library: the rendering pipeline of Hansi Raber's
 * Oscilloscope app (https://github.com/kritzikratzi/Oscilloscope), taken out of the app and
 * packed into one class.
 * <p>Feed it audio from any thread (an XYPlayer, the sketch's audioIn() or audioOut(), an
 * XYscope), then update() and draw() it each frame. The beam draws with a shader, so the
 * sketch needs a P2D or P3D renderer:</p>
 * <pre>
 * Oscilloscope scope;
 *
 * void setup() {
 *   size(512, 512, P2D);
 *   scope = new Oscilloscope(this);   // the sketch's size
 * }
 *
 * void audioIn(XYSoundBuffer buffer) {
 *   scope.addBuffer(buffer);
 * }
 *
 * void draw() {
 *   scope.update();
 *   scope.draw();
 * }
 * </pre>
 * <p>The channels decide the layout, as they did for the app's audio files: 1 channel draws
 * the signal against a sawtooth sweep, 2 are X and Y, 3 are X, Y and Z (brightness), 4 are
 * two stereo pairs drawn as a red/cyan anaglyph.</p>
 * <p>getShapes() turns the most recent audio back into vector shapes with XYDecoder.</p>
 * <p>Differences from the original:</p>
 * <ul>
 * <li>The app's Globals settings are plain fields here.</li>
 * <li>Audio is upsampled to the visual sample rate (192kHz by default) with StreamResampler
 *     instead of FFmpeg's swresample, for any input, not just files. That's why live input
 *     needs no intensity boost here.</li>
 * <li>The mesh draws into a P2D offscreen buffer the size given to the constructor; draw()
 *     scales the buffer to fit wherever it's drawn.</li>
 * <li>Z is read through zRange. The default (0, 1) shows the app's 0..1 brightness as-is
 *     and blanks XYscope's -1 (beam off) level.</li>
 * </ul>
 */

public class Oscilloscope implements XYSoundInput {

  public static final int MONO = 0;              // signal on Y, swept along X by a sawtooth
  public static final int STEREO = 1;            // X-Y
  public static final int STEREO_ZMODULATED = 2; // X-Y plus brightness
  public static final int QUAD = 3;              // two X-Y pairs, red and cyan

  // How bright the beam is at intensity 1, for audio upsampled to 192kHz.
  // Beam light builds up with every sample drawn, so it's scaled by the
  // visual sample rate to look the same at any rate.
  static final float BEAM_GAIN = 0.15f;

  // never queue more than this many seconds of samples for update(), so a
  // slow frame can't snowball into a bigger mesh and an even slower frame
  static final float MAX_PENDING_SECONDS = 1.0f / 15.0f;
  // keep this many seconds of input for getShapes()
  static final float HISTORY_SECONDS = 2.0f;
  // the window getShapes() searches for a loop
  static final float DECODE_SECONDS = 0.5f;

  // ---------------------------------------------------------------- settings

  public float scale = 1;            // 1 fills the shorter side of the buffer
  public boolean invertX = false;
  public boolean invertY = false;
  public boolean flipXY = false;
  public boolean zModulation = true;
  public PVector zRange = new PVector(0, 1); // Z values for black (x) and full brightness (y)

  public float strokeWeight = 10;    // 1..20
  public float intensity = 0.4f;     // 0..1
  public float afterglow = 0.5f;     // 0..1, how much of each frame is left for the next
  public float hue = 50;             // 0..360, 360 is white

  /**
   * StreamResampler.SINC or StreamResampler.LINEAR
   */
  public int interpolation = StreamResampler.SINC;

  /**
   * Decoding settings for getShapes(); set decoderSettings.freq if you know it.
   */
  public XYDecoderSettings decoderSettings = new XYDecoderSettings();

  public OsciMesh mesh = new OsciMesh();
  public OsciMesh mesh2 = new OsciMesh(); // the second pair in QUAD layout

  PApplet parent;
  int width = 512;
  int height = 512;
  int visualSampleRate = 192000;

  PGraphics fbo;
  boolean changed = false;
  boolean needsClear = true;
  int dropped = 0;
  float sweep = 0;

  final Object lock = new Object();
  int layout = STEREO;
  int numChannels = 0;
  int sourceSampleRate = 0;
  int activeInterpolation = StreamResampler.SINC;
  StreamResampler[] resamplers = new StreamResampler[0];
  // visual rate samples waiting for update()
  FloatArray[] pending = new FloatArray[0];
  // source rate history for getShapes()
  FloatArray history = new FloatArray(1 << 16);
  int historyFrames = 0;

  float detectedPeriod = 0;

  /**
   * The sketch's size.
   */
  public Oscilloscope(PApplet _parent) {
    this(_parent, _parent.width, _parent.height, 192000);
  }

  public Oscilloscope(PApplet _parent, int _width, int _height) {
    this(_parent, _width, _height, 192000);
  }

  public Oscilloscope(PApplet _parent, int _width, int _height, int _visualSampleRate) {
    parent = _parent;
    resize(_width, _height);
    setVisualSampleRate(_visualSampleRate);
  }

  public void resize(int _width, int _height) {
    width = Math.max(1, _width);
    height = Math.max(1, _height);
    needsClear = true;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public void setVisualSampleRate(int rate) {
    synchronized (lock) {
      visualSampleRate = Math.max(8000, rate);
      numChannels = 0; // reconfigure on the next samples
    }
  }

  public int getVisualSampleRate() {
    return visualSampleRate;
  }

  public int getLayout() {
    return layout;
  }

  public int getSourceSampleRate() {
    return sourceSampleRate;
  }

  /**
   * how many sample batches were dropped to keep up (the app's "Dropped")
   */
  public int getDropped() {
    return dropped;
  }

  void configure(int _numChannels, int sampleRate) {
    numChannels = _numChannels;
    sourceSampleRate = sampleRate;
    activeInterpolation = interpolation;

    switch (numChannels) {
    case 1:
      layout = MONO;
      break;
    case 2:
      layout = STEREO;
      break;
    case 3:
      layout = STEREO_ZMODULATED;
      break;
    default:
      layout = numChannels >= 4 ? QUAD : STEREO;
      break;
    }

    int used = Math.min(numChannels, 4);
    resamplers = new StreamResampler[used];
    pending = new FloatArray[used];
    for (int c = 0; c < used; c++) {
      resamplers[c] = new StreamResampler(sampleRate, visualSampleRate, interpolation);
      pending[c] = new FloatArray(8192);
    }
    history.clear();
    historyFrames = 0;
  }

  // ---------------------------------------------------------------- input (any thread)

  public void addSamples(float[] interleaved, int numFrames, int _numChannels, int sampleRate) {
    addSamples(interleaved, 0, numFrames, _numChannels, sampleRate);
  }

  /**
   * numFrames frames of interleaved audio, starting at frame offset.
   */
  public void addSamples(float[] interleaved, int offset, int numFrames, int _numChannels, int sampleRate) {
    if (interleaved == null || numFrames <= 0 || _numChannels <= 0) return;
    if (sampleRate <= 0) sampleRate = 44100;
    int base = offset * _numChannels;

    synchronized (lock) {
      if (_numChannels != numChannels || sampleRate != sourceSampleRate || interpolation != activeInterpolation) {
        configure(_numChannels, sampleRate);
      }

      // the visual stream, upsampled
      int used = resamplers.length;
      for (int c = 0; c < used; c++) {
        resamplers[c].process(interleaved, base + c, numFrames, _numChannels, pending[c]);
      }

      int maxPending = (int) (visualSampleRate * MAX_PENDING_SECONDS);
      if (pending.length > 0 && pending[0].size > maxPending) {
        int excess = pending[0].size - maxPending;
        for (FloatArray p : pending) p.removeFront(Math.min(excess, p.size));
        dropped++;
      }

      // the source stream, for decoding shapes
      history.ensureCapacity(history.size + numFrames * used);
      for (int i = 0; i < numFrames; i++) {
        history.add(interleaved, base + i * _numChannels, used);
      }
      historyFrames += numFrames;
      int maxFrames = (int) (sampleRate * HISTORY_SECONDS);
      if (historyFrames > 2 * maxFrames) {
        // trim now and then rather than on every buffer
        int drop = historyFrames - maxFrames;
        history.removeFront(drop * used);
        historyFrames = maxFrames;
      }
    }
  }

  public void addBuffer(XYSoundBuffer buffer) {
    addSamples(buffer.samples, 0, buffer.getNumFrames(), buffer.getNumChannels(), buffer.getSampleRate());
  }

  /**
   * so an Oscilloscope can listen to an XYSoundStream directly
   */
  public void audioIn(XYSoundBuffer buffer) {
    addBuffer(buffer);
  }

  public void clear() {
    synchronized (lock) {
      for (StreamResampler resampler : resamplers) resampler.reset();
      for (FloatArray p : pending) p.clear();
      history.clear();
      historyFrames = 0;
      needsClear = true;
    }
  }

  // ---------------------------------------------------------------- update/draw (animation thread)

  public void update() {
    float[][] samples;
    int currentLayout;
    synchronized (lock) {
      samples = new float[pending.length][];
      for (int c = 0; c < pending.length; c++) {
        samples[c] = pending[c].toArray();
        pending[c].clear();
      }
      currentLayout = layout;
    }

    mesh.clear();
    mesh2.clear();
    mesh.uSize = strokeWeight / 1000.0f;
    mesh2.uSize = mesh.uSize;

    if (samples.length == 0 || samples[0].length == 0) return;
    changed = true;
    int n = samples[0].length;

    switch (currentLayout) {
    case MONO:
      // a sawtooth sweeps the beam across, as on a scope in Y-T mode
      float[] sweepX = new float[n];
      for (int i = 0; i < n; i++) {
        sweepX[i] = -1 + 2 * sweep;
        sweep += 1.0f / 2048;
        if (sweep >= 1) sweep -= 1;
      }
      mesh.addLines(sweepX, samples[0], null, n);
      break;
    case STEREO:
      mesh.addLines(samples[0], samples[1], null, n);
      break;
    case STEREO_ZMODULATED:
      float[] bright = null;
      if (zModulation) {
        bright = new float[n];
        float range = zRange.y - zRange.x;
        for (int i = 0; i < n; i++) {
          bright[i] = range != 0 ? PApplet.constrain((samples[2][i] - zRange.x) / range, 0, 1) : 1;
        }
      }
      mesh.addLines(samples[0], samples[1], bright, n);
      break;
    case QUAD:
      mesh.addLines(samples[0], samples[1], null, n);
      mesh2.addLines(samples[2], samples[3], null, n);
      break;
    }
  }

  void drawMesh(PGraphics g) {
    g.pushMatrix();
    g.translate(width / 2.0f, height / 2.0f);
    float s = Math.min(width, height) / 2.0f * scale;
    // scope +Y is up
    g.scale(s * (invertX ? -1 : 1), -s * (invertY ? -1 : 1));
    if (flipXY) g.applyMatrix(0, 1, 0, 1, 0, 0);

    float gain = intensity * BEAM_GAIN * 192000.0f / visualSampleRate;
    mesh.uIntensity = gain;
    mesh2.uIntensity = gain;

    if (layout == QUAD) {
      mesh.uRgb.set(1, 0, 0);
      mesh2.uRgb.set(0, 1, 1);
    } else if (hue >= 360) {
      mesh.uRgb.set(1, 1, 1);
    } else {
      int rgb = java.awt.Color.HSBtoRGB(hue / 360.0f, 1, 1);
      mesh.uRgb.set(((rgb >> 16) & 0xff) / 255.0f, ((rgb >> 8) & 0xff) / 255.0f, (rgb & 0xff) / 255.0f);
    }

    mesh.draw(g);
    mesh2.draw(g);
    g.popMatrix();
  }

  /**
   * The offscreen buffer the beam glows and fades in, made on the first draw().
   */
  public PGraphics getGraphics() {
    return fbo;
  }

  public void draw() {
    draw(parent.g, 0, 0, width, height);
  }

  public void draw(float x, float y) {
    draw(parent.g, x, y, width, height);
  }

  public void draw(float x, float y, float w, float h) {
    draw(parent.g, x, y, w, h);
  }

  /**
   * Draws the beam into target, which must be P2D or P3D. Call it from draw().
   */
  public void draw(PGraphics target, float x, float y, float w, float h) {
    if (fbo == null || fbo.width != width || fbo.height != height) {
      fbo = parent.createGraphics(width, height, PConstants.P2D);
      fbo.noSmooth();
      needsClear = true;
    }

    if (needsClear) {
      fbo.beginDraw();
      fbo.background(0);
      fbo.endDraw();
      needsClear = false;
    }

    if (changed) {
      fbo.beginDraw();
      fbo.pushStyle();
      // the afterglow: fade what's there, rather than clearing it
      fbo.blendMode(PConstants.BLEND);
      fbo.noStroke();
      fbo.fill(0, (1 - PApplet.constrain(afterglow, 0, 1)) * 255);
      fbo.rect(0, 0, width, height);
      fbo.popStyle();
      drawMesh(fbo);
      fbo.endDraw();
      changed = false;
    }

    target.pushStyle();
    target.blendMode(PConstants.REPLACE);
    target.noTint();
    target.image(fbo, x, y, w, h);
    target.popStyle();
  }

  // ---------------------------------------------------------------- vector shapes

  /**
   * The last couple of seconds of input at its own sample rate.
   */
  public XYSoundBuffer getHistory() {
    synchronized (lock) {
      int used = Math.max(1, resamplers.length);
      return new XYSoundBuffer(history.toArray(), used, sourceSampleRate > 0 ? sourceSampleRate : 44100);
    }
  }

  /**
   * The loop period found by the last getShapes(), in samples (0 = none).
   */
  public float getDetectedPeriod() {
    return detectedPeriod;
  }

  /**
   * Decode the most recent audio into shapes on a w x h canvas.
   * Uses the source sample rate; set decoderSettings.freq if you know it.
   */
  public ArrayList<XYPolyline> getShapes(float w, float h) {
    XYDecoderSettings s = decoderSettings.copy();
    s.width = w;
    s.height = h;
    s.zMin = zRange.x;
    s.zMax = zRange.y;
    s.useZ = s.useZ && zModulation;

    // copy out only the most recent stretch
    int nCh, n;
    float[] x, y, z = null;
    synchronized (lock) {
      nCh = Math.max(1, resamplers.length);
      s.sampleRate = sourceSampleRate > 0 ? sourceSampleRate : 44100;
      int total = history.size / nCh;
      n = Math.min(total, (int) (s.sampleRate * DECODE_SECONDS));
      int start = total - n;
      x = new float[n];
      y = new float[n];
      if (nCh == 3) z = new float[n];
      float[] h0 = history.data;
      for (int i = 0; i < n; i++) {
        int frame = (start + i) * nCh;
        if (nCh == 1) {
          y[i] = h0[frame];
        } else {
          x[i] = h0[frame];
          y[i] = h0[frame + 1];
          if (nCh == 3) z[i] = h0[frame + 2];
        }
      }
    }
    if (n < 2) return new ArrayList<XYPolyline>();

    if (nCh == 1) {
      detectedPeriod = 0;
      return XYDecoder.decode(new XYSoundBuffer(y, 1, (int) s.sampleRate), s);
    }

    if (s.freq > 0) {
      detectedPeriod = s.sampleRate / s.freq;
    } else {
      // keep the last period while the signal still loops at it (and not at half of it)
      boolean stillLoops = detectedPeriod > 0 &&
        XYDecoder.periodError(x, y, n, detectedPeriod) < 0.02f &&
        XYDecoder.periodError(x, y, n, detectedPeriod / 2) > 0.1f;
      if (!stillLoops) {
        detectedPeriod = XYDecoder.detectPeriod(x, y, n, s.sampleRate / Math.max(1.0f, s.maxFreq),
          s.sampleRate / Math.max(1.0f, s.minFreq));
      }
      if (detectedPeriod > 0) s.freq = s.sampleRate / detectedPeriod;
    }

    return XYDecoder.decode(x, y, z, n, s);
  }

}
