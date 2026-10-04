package twoscilloscopeP5;

import processing.core.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A port of XYscope.java by Ted Davis (https://teddavis.org/xyscope), by way of the
 * ofxTwoscilloscope addon: the vector-to-audio half of the library.
 * <p>Drawing commands don't draw to the screen. They collect shapes, and buildWaves() turns
 * those shapes into wavetables that loop at freq() Hz: X on the left channel, Y on the
 * right, and an optional Z (beam blanking) channel. Play them through a DC-coupled sound
 * card into an oscilloscope in X-Y mode, a modded Vectrex or a laser, and the shapes appear
 * on the display.</p>
 * <pre>
 * XYscope xy;
 *
 * void setup() {
 *   size(512, 512, P2D);
 *   xy = new XYscope(this);   // canvas = sketch size, 44.1kHz, 512 sample waves
 *   xy.openAudioOut();        // default sound card
 * }
 *
 * void draw() {
 *   background(0);
 *   xy.clearWaves();
 *   xy.circle(width / 2, height / 2, height / 2);
 *   xy.buildWaves();
 *   xy.drawXY();              // preview of what the scope will show
 * }
 * </pre>
 * <p>Coordinates work like Processing's: pixels on a canvas (the sketch, by default) with y
 * pointing down. "XYscope format" audio maps the canvas to -1..1, with +Y up: x = 2 * px /
 * width - 1, y = 1 - 2 * py / height.</p>
 * <p>Differences from the Java original:</p>
 * <ul>
 * <li>Minim is replaced by XYSoundStream (javax.sound.sampled). openAudioOut() opens a
 *     stream that XYscope owns, or give an XYscope to an XYSoundStream of your own as its
 *     output listener. audioOutAdd() mixes into a buffer instead of overwriting it, which
 *     is how several XYscopes patch together for additive synthesis.</li>
 * <li>Z goes out as a third channel on the same device, rather than on a second sound card.
 *     render() and the recorder write it as a 3-channel WAV, the Z-modulated layout the
 *     Oscilloscope app reads.</li>
 * <li>Processing's screenX()/screenY() are replaced by a transform stack of XYscope's own
 *     (pushMatrix, translate, rotate...). It projects 3D points the way Processing's
 *     default P3D camera does, so 2D drawing is unchanged, 3D shapes need no camera setup,
 *     and the sketch can use any renderer.</li>
 * <li>render() renders audio offline and process() runs the oscillators without a sound
 *     card. Both work headless.</li>
 * <li>Text is drawn with Hershey fonts built into the library, aligned TOP by default, with
 *     the glyph spacing described in HersheyFont.</li>
 * <li>Laser RGB mode (5-channel laser DACs) isn't ported.</li>
 * </ul>
 */

public class XYscope implements XYSoundOutput {

  static class Oscillators {
    double phaseX = 0;
    double phaseY = 0;
    double phaseZ = 0;
  }

  static class Params {
    float freqX, freqY, freqZ;
    float ampX, ampY, ampZ;
    float panX, panY;
    boolean useZ;
    float zMax;
  }

  PApplet parent;

  float xyWidth = 512;
  float xyHeight = 512;
  int sampleRateVal = 44100;
  int bufferSizeVal = 512;
  int waveSizeVal = 512;
  int stepsSize = 24;

  boolean useLimitPoints = false;
  int limitPointsVal = 512;
  boolean useLimitPath = false;
  float limitVal = 1;

  PVector freqVal = new PVector(50, 50, 50);
  PVector ampVal = new PVector(1, 1, 1);
  float panXVal = -1;
  float panYVal = 1;
  boolean useZ = true;
  float zaxisMin = -1;
  float zaxisMax = 1;

  boolean useVectrex = false;
  float vectrexAmp = 0.82f;
  float vectrexAmpInit = 0.6f;
  int vectrexRotation = 0;

  int rectM = PConstants.CORNER;
  int ellipseDetailVal = 30;
  boolean debugWave = false;

  ArrayList<ArrayList<PVector>> shapes = new ArrayList<ArrayList<PVector>>();
  boolean shapeOpen = false;

  ArrayList<PMatrix3D> matrixStack = new ArrayList<PMatrix3D>();
  PMatrix3D matrix = new PMatrix3D();
  boolean usePerspectiveVal = true;
  float projX, projY;

  HersheyFont font;
  float textSizeVal = HersheyFont.CAP_HEIGHT;
  float textLeadingVal = HersheyFont.CAP_HEIGHT * 1.5f;
  int textAlignX = PConstants.LEFT;
  int textAlignY = PConstants.TOP;

  public XYWavetable tableX = new XYWavetable();
  public XYWavetable tableY = new XYWavetable();
  public XYWavetable tableZ = new XYWavetable();

  // live output, shared with the audio thread
  final Object paramLock = new Object();
  final Object oscLock = new Object();
  final Object audioLock = new Object();
  Oscillators liveOscs = new Oscillators();
  double processRemainder = 0;
  FloatArray lastBuffer = new FloatArray();
  int lastChannels = 1;
  int lastSampleRate = 44100;
  boolean recording = false;
  String recordingPath;
  FloatArray recordBuffer = new FloatArray();
  int recordChannels = 1;
  int recordSampleRate = 44100;

  XYSoundStream soundStream;
  boolean audioOutOpen = false;
  int audioDeviceId = -1;
  int audioChannels = 2;

  // ---------------------------------------------------------------- setup

  /**
   * An XYscope with no sketch: a 512 x 512 canvas, for offline rendering.
   */
  public XYscope() {
    this(null, 512, 512, 44100, 512);
  }

  /**
   * The canvas is the sketch's size.
   */
  public XYscope(PApplet _parent) {
    this(_parent, 0, 0, 44100, 512);
  }

  public XYscope(PApplet _parent, float width, float height) {
    this(_parent, width, height, 44100, 512);
  }

  /**
   * A width/height of 0 uses the sketch's size. The buffer size is also the wavetable size.
   */
  public XYscope(PApplet _parent, float width, float height, int sampleRate, int bufferSize) {
    parent = _parent;
    font = new HersheyFont(parent);
    tableX.setWaveform(new float[waveSizeVal]);
    tableY.setWaveform(new float[waveSizeVal]);
    float[] on = new float[waveSizeVal];
    java.util.Arrays.fill(on, zaxisMax);
    tableZ.setWaveform(on);
    setup(width, height, sampleRate, bufferSize);
    if (parent != null && !welcomed) {
      System.out.println("XYscope 3.0.0 for Processing (TwoscilloscopeP5) - https://teddavis.org/xyscope");
      welcomed = true;
    }
  }

  static boolean welcomed = false;
  boolean disposeRegistered = false;

  /**
   * A width/height of 0 uses the sketch's size.
   */
  public void setup(float width, float height, int _sampleRate, int _bufferSize) {
    if (width <= 0 && parent != null) width = parent.width;
    if (height <= 0 && parent != null) height = parent.height;
    setCanvasSize(width > 0 ? width : 512, height > 0 ? height : 512);
    sampleRateVal = _sampleRate;
    bufferSizeVal = Math.max(16, _bufferSize);
    waveSize(bufferSizeVal);
    limitPointsVal = bufferSizeVal;
  }

  public void dispose() {
    closeAudioOut();
  }

  public boolean openAudioOut() {
    return openAudioOut(-1, 2);
  }

  public boolean openAudioOut(int deviceId) {
    return openAudioOut(deviceId, 2);
  }

  /**
   * Open a sound card output that this XYscope owns and fills.
   * deviceId comes from listDevices(), -1 for the default device.
   * Use 3 channels to send Z on the third.
   */
  public boolean openAudioOut(int deviceId, int numChannels) {
    closeAudioOut();
    // close the sound card with the sketch
    if (parent != null && !disposeRegistered) {
      parent.registerMethod("dispose", this);
      disposeRegistered = true;
    }
    soundStream = new XYSoundStream();
    soundStream.setOutListener(this);
    soundStream.setOutDevice(deviceId);
    audioDeviceId = deviceId;
    audioChannels = Math.max(1, numChannels);
    audioOutOpen = soundStream.setup(audioChannels, 0, sampleRateVal, bufferSizeVal);
    if (!audioOutOpen) {
      System.err.println("XYscope: couldn't open an audio output, use process() to run without one");
      soundStream = null;
    }
    return audioOutOpen;
  }

  public void closeAudioOut() {
    if (soundStream != null) {
      soundStream.close();
      soundStream = null;
    }
    audioOutOpen = false;
  }

  public boolean isAudioOutOpen() {
    return audioOutOpen;
  }

  public static void listDevices() {
    XYSoundStream.listDevices();
  }

  public void setCanvasSize(float width, float height) {
    xyWidth = Math.max(1.0f, width);
    xyHeight = Math.max(1.0f, height);
  }

  public float getWidth() {
    return xyWidth;
  }

  public float getHeight() {
    return xyHeight;
  }

  public int sampleRate() {
    return sampleRateVal;
  }

  public void sampleRate(int _sampleRate) {
    sampleRateVal = _sampleRate;
    if (audioOutOpen) openAudioOut(audioDeviceId, audioChannels);
  }

  public int bufferSize() {
    return bufferSizeVal;
  }

  public void bufferSize(int _bufferSize) {
    if (_bufferSize > 16) bufferSizeVal = _bufferSize;
    if (audioOutOpen) openAudioOut(audioDeviceId, audioChannels);
  }

  // ---------------------------------------------------------------- audio

  Params getParams() {
    synchronized (paramLock) {
      Params p = new Params();
      p.freqX = freqVal.x;
      p.freqY = freqVal.y;
      p.freqZ = freqVal.z;
      p.ampX = ampVal.x;
      p.ampY = ampVal.y;
      p.ampZ = ampVal.z;
      p.panX = panXVal;
      p.panY = panYVal;
      p.useZ = useZ;
      p.zMax = zaxisMax;
      return p;
    }
  }

  void synth(XYSoundBuffer buffer, boolean add, Oscillators o) {
    Params params = getParams();
    float[] waveX = tableX.getWaveformArray();
    float[] waveY = tableY.getWaveformArray();
    float[] waveZ = tableZ.getWaveformArray();

    int nCh = buffer.getNumChannels();
    int nFrames = buffer.getNumFrames();
    double sr = buffer.getSampleRate() > 0 ? buffer.getSampleRate() : sampleRateVal;
    float[] out = buffer.samples;

    // Minim's Pan: equal power, -1 is all left and 1 is all right
    double thetaX = (params.panX + 1) * Math.PI / 4;
    double thetaY = (params.panY + 1) * Math.PI / 4;
    float lx = (float) Math.cos(thetaX), rx = (float) Math.sin(thetaX);
    float ly = (float) Math.cos(thetaY), ry = (float) Math.sin(thetaY);

    double stepX = params.freqX / sr;
    double stepY = params.freqY / sr;
    double stepZ = params.freqZ / sr;
    // no Z wave means the beam stays on
    boolean zOn = params.useZ && waveZ.length > 0;

    for (int i = 0; i < nFrames; i++) {
      float x = params.ampX * XYWavetable.valueAt(waveX, o.phaseX);
      float y = params.ampY * XYWavetable.valueAt(waveY, o.phaseY);
      float z = zOn ? params.ampZ * XYWavetable.valueAt(waveZ, o.phaseZ) : params.zMax;

      o.phaseX += stepX;
      o.phaseY += stepY;
      o.phaseZ += stepZ;
      o.phaseX -= Math.floor(o.phaseX);
      o.phaseY -= Math.floor(o.phaseY);
      o.phaseZ -= Math.floor(o.phaseZ);

      int frame = i * nCh;
      float left = x * lx + y * ly;
      float right = x * rx + y * ry;
      if (add) {
        out[frame] += left;
        if (nCh > 1) out[frame + 1] += right;
        if (nCh > 2) out[frame + 2] += z;
      } else {
        out[frame] = left;
        if (nCh > 1) out[frame + 1] = right;
        if (nCh > 2) out[frame + 2] = z;
        for (int c = 3; c < nCh; c++) out[frame + c] = 0;
      }
    }
  }

  int previewFrames() {
    Params params = getParams();
    float slowest = Math.max(1.0f, Math.min(params.freqX, params.freqY));
    return Math.max(bufferSizeVal, Math.min(sampleRateVal, (int) Math.ceil(sampleRateVal / slowest)));
  }

  void finishBuffer(XYSoundBuffer buffer) {
    int keep = previewFrames();
    synchronized (audioLock) {
      // keep at least one full cycle for drawXY() and drawWave()
      int nCh = buffer.getNumChannels();
      if (lastChannels != nCh) {
        lastBuffer.clear();
        lastChannels = nCh;
      }
      lastBuffer.add(buffer.samples, 0, buffer.samples.length);
      if (lastBuffer.size > keep * nCh) lastBuffer.removeFront(lastBuffer.size - keep * nCh);
      lastSampleRate = buffer.getSampleRate();

      if (recording) {
        if (recordBuffer.size == 0) {
          recordChannels = nCh;
          recordSampleRate = buffer.getSampleRate();
        }
        if (recordChannels == nCh) recordBuffer.add(buffer.samples, 0, buffer.samples.length);
      }
    }
  }

  /**
   * Fill an audio buffer: X -&gt; channel 0, Y -&gt; channel 1, Z -&gt; channel 2.
   */
  public void audioOut(XYSoundBuffer buffer) {
    synchronized (oscLock) {
      synth(buffer, false, liveOscs);
    }
    finishBuffer(buffer);
  }

  /**
   * The same, but adds to what's already in the buffer.
   */
  public void audioOutAdd(XYSoundBuffer buffer) {
    synchronized (oscLock) {
      synth(buffer, true, liveOscs);
    }
    finishBuffer(buffer);
  }

  public void process(float seconds) {
    process(seconds, 2);
  }

  /**
   * Run the oscillators for this many seconds without a sound card,
   * feeding the recorder and the drawXY()/drawWave() previews.
   */
  public void process(float seconds, int numChannels) {
    double frames = seconds * (double) sampleRateVal + processRemainder;
    int numFrames = (int) Math.max(0.0, Math.floor(frames));
    processRemainder = frames - numFrames;
    if (numFrames == 0) return;
    audioOut(new XYSoundBuffer(numFrames, Math.max(1, numChannels), sampleRateVal));
  }

  public XYSoundBuffer render(float seconds) {
    return render(seconds, 2);
  }

  /**
   * Render audio offline. Starts from phase 0 every time and doesn't
   * touch the live oscillators, so it can run while audio is playing.
   */
  public XYSoundBuffer render(float seconds, int numChannels) {
    XYSoundBuffer buffer = new XYSoundBuffer();
    render(buffer, (int) (Math.max(0.0f, seconds) * sampleRateVal), numChannels);
    return buffer;
  }

  public void render(XYSoundBuffer buffer, int numFrames, int numChannels) {
    buffer.allocate(numFrames, Math.max(1, numChannels));
    buffer.setSampleRate(sampleRateVal);
    synth(buffer, false, new Oscillators());
  }

  /**
   * The last buffer the oscillators produced (at least one loop of it).
   */
  public XYSoundBuffer getLastBuffer() {
    synchronized (audioLock) {
      return new XYSoundBuffer(lastBuffer.toArray(), lastChannels, lastSampleRate);
    }
  }

  // ---------------------------------------------------------------- waves

  public void clearWaves() {
    shapes.clear();
    shapeOpen = false;
    // Processing resets the matrix at the start of every draw(), and XYscope
    // drew through Processing's matrix, so start each frame from scratch too.
    resetMatrix();
  }

  public void buildWaves() {
    if (shapeOpen) endShape();
    if (shapes.isEmpty()) {
      emptyWave();
      return;
    }

    // waveform gen v4 (mar 2020): spread each shape's segments over the
    // wave in proportion to their length, so the beam moves at an even speed
    long totalPoints = 0;
    double totalDist = 0;
    for (ArrayList<PVector> shape : shapes) {
      totalPoints += shape.size();
      for (int j = 0; j + 1 < shape.size(); j++) totalDist += dist2D(shape.get(j), shape.get(j + 1));
    }
    double waveSizeD = (double) totalPoints * stepsSize;

    // x, y and a blank flag on each shape's last point
    FloatArray colX = new FloatArray(4096);
    FloatArray colY = new FloatArray(4096);
    FloatArray colZ = new FloatArray(4096);
    for (ArrayList<PVector> shape : shapes) {
      for (int j = 0; j + 1 < shape.size(); j++) {
        PVector p1 = shape.get(j);
        PVector p2 = shape.get(j + 1);
        double lineDist = dist2D(p1, p2);
        long secPer = Math.round(1 + (totalDist > 0 ? lineDist / totalDist * waveSizeD : 0));
        boolean lastSegment = j + 2 == shape.size();
        for (long k = 0; k <= secPer; k++) {
          float t = (float) ((double) k / secPer);
          colX.add(p1.x * (1 - t) + p2.x * t);
          colY.add(p1.y * (1 - t) + p2.y * t);
          colZ.add((lastSegment && k == secPer) ? 1.0f : 0.0f);
        }
      }
    }

    int n = waveSizeVal;
    int m = colX.size;
    float[] mfx = new float[n];
    float[] mfy = new float[n];
    float[] mfz = new float[n];
    for (int i = 0; i < n; i++) {
      int from = (int) ((long) i * m / n);
      int to = Math.max(from + 1, (int) ((long) (i + 1) * m / n));
      mfx[i] = colX.data[from] * 2 - 1;
      mfy[i] = colY.data[from] * -2 + 1;

      // blank if any point that falls in this sample ends a shape,
      // so the blanking can't get skipped over by the resampling
      boolean blank = false;
      for (int k = from; k < to && k < m; k++) blank = blank || colZ.data[k] == 1.0f;
      mfz[i] = blank ? zaxisMin : zaxisMax;

      if (useVectrex) {
        float tfxx = mfx[i];
        float tfyy = mfy[i];
        if (vectrexRotation == 90) {
          mfx[i] = tfyy;
          mfy[i] = -tfxx;
        } else if (vectrexRotation == -90) {
          mfx[i] = -tfyy;
          mfy[i] = tfxx;
        } else {
          mfx[i] = -tfxx;
          mfy[i] = -tfyy;
        }
      }
    }

    setWaveforms(mfx, mfy, mfz);
  }

  static double dist2D(PVector a, PVector b) {
    float dx = a.x - b.x, dy = a.y - b.y;
    return Math.sqrt(dx * dx + dy * dy);
  }

  /**
   * Waveforms in -1..1, as built by buildWaves(). The beam stays on.
   */
  public void setWaveforms(float[] mfx, float[] mfy) {
    setWaveforms(mfx, mfy, new float[0]);
  }

  /**
   * Waveforms in -1..1, as built by buildWaves(), with Z.
   */
  public void setWaveforms(float[] mfx, float[] mfy, float[] mfz) {
    if (mfz == null) mfz = new float[0];
    if (useLimitPoints && (mfx.length > limitPointsVal || mfy.length > limitPointsVal)) {
      tableX.setWaveform(limit(mfx));
      tableY.setWaveform(limit(mfy));
      if (useZ) tableZ.setWaveform(limit(mfz));
    } else {
      tableX.setWaveform(mfx);
      tableY.setWaveform(mfy);
      if (useZ) tableZ.setWaveform(mfz);
    }
  }

  float[] limit(float[] src) {
    if (src.length == 0) return src;
    float[] dst = new float[limitPointsVal];
    for (int i = 0; i < limitPointsVal; i++) dst[i] = src[(int) ((long) i * src.length / limitPointsVal)];
    return dst;
  }

  void emptyWave() {
    tableX.setWaveform(new float[0]);
    tableY.setWaveform(new float[0]);
    if (useZ) tableZ.setWaveform(new float[0]);
  }

  /**
   * Custom waveform in 0..1, resampled to waveSize().
   */
  public void buildX(float[] wave) {
    if (wave == null || wave.length == 0) return;
    float[] out = new float[waveSizeVal];
    for (int i = 0; i < waveSizeVal; i++) out[i] = map01(wave[(int) ((long) i * wave.length / waveSizeVal)], -1, 1);
    tableX.setWaveform(out);
  }

  /**
   * Custom waveform in 0..1 (0 at the top), resampled to waveSize().
   */
  public void buildY(float[] wave) {
    if (wave == null || wave.length == 0) return;
    float[] out = new float[waveSizeVal];
    for (int i = 0; i < waveSizeVal; i++) out[i] = map01(wave[(int) ((long) i * wave.length / waveSizeVal)], 1, -1);
    tableY.setWaveform(out);
  }

  /**
   * Custom Z waveform in 0..1 (beam off to on), resampled to waveSize().
   */
  public void buildZ(float[] wave) {
    if (wave == null || wave.length == 0) return;
    float[] out = new float[waveSizeVal];
    for (int i = 0; i < waveSizeVal; i++) out[i] = map01(wave[(int) ((long) i * wave.length / waveSizeVal)], zaxisMin, zaxisMax);
    tableZ.setWaveform(out);
  }

  static float map01(float v, float lo, float hi) {
    return lo + v * (hi - lo);
  }

  public int waveSize() {
    return waveSizeVal;
  }

  public void waveSize(int newSize) {
    waveSizeVal = Math.max(2, newSize);
  }

  public int steps() {
    return stepsSize;
  }

  public void steps(float newSteps) {
    stepsSize = Math.max(1, (int) newSteps);
  }

  public int limitPoints() {
    return limitPointsVal;
  }

  /**
   * 0 turns the limit off.
   */
  public void limitPoints(int newLimit) {
    if (newLimit == 0) {
      useLimitPoints = false;
    } else {
      limitPointsVal = Math.abs(newLimit);
      useLimitPoints = true;
    }
  }

  public float limitPath() {
    return limitVal;
  }

  /**
   * Break shapes that come closer than this many pixels to the canvas edge.
   */
  public void limitPath(float newLimit) {
    limitVal = newLimit;
    useLimitPath = true;
  }

  public void waveReset() {
    synchronized (oscLock) {
      liveOscs = new Oscillators();
    }
  }

  public void resetWaves() {
    waveReset();
  }

  // ---------------------------------------------------------------- oscillators

  public PVector freq() {
    synchronized (paramLock) {
      return freqVal.copy();
    }
  }

  public void freq(float newFreq) {
    freq(newFreq, newFreq, newFreq);
  }

  public void freq(float newFreqX, float newFreqY) {
    freq(newFreqX, newFreqY, freq().z);
  }

  public void freq(float newFreqX, float newFreqY, float newFreqZ) {
    synchronized (paramLock) {
      freqVal.set(newFreqX, newFreqY, newFreqZ);
    }
  }

  public void freq(PVector newFreq) {
    freq(newFreq.x, newFreq.y, newFreq.z);
  }

  public PVector amp() {
    synchronized (paramLock) {
      return ampVal.copy();
    }
  }

  public void amp(float newAmp) {
    amp(newAmp, newAmp, newAmp);
  }

  public void amp(float newAmpX, float newAmpY) {
    amp(newAmpX, newAmpY, amp().z);
  }

  public void amp(float newAmpX, float newAmpY, float newAmpZ) {
    synchronized (paramLock) {
      ampVal.x = PApplet.constrain(newAmpX, 0, 1);
      if (useVectrex) ampVal.x *= vectrexAmp;
      ampVal.y = PApplet.constrain(newAmpY, 0, 1);
      ampVal.z = PApplet.constrain(newAmpZ, 0, 1);
    }
  }

  public void amp(PVector newAmp) {
    amp(newAmp.x, newAmp.y, newAmp.z);
  }

  /**
   * Equal power pan for the X and Y oscillators, -1 (left) to 1 (right).
   * The default (-1, 1) sends X left and Y right; (1, -1) swaps them.
   */
  public void pan(float panX, float panY) {
    synchronized (paramLock) {
      panXVal = PApplet.constrain(panX, -1, 1);
      panYVal = PApplet.constrain(panY, -1, 1);
    }
  }

  /**
   * Z output for beam blanked (x) and on (y).
   */
  public PVector zRange() {
    synchronized (paramLock) {
      return new PVector(zaxisMin, zaxisMax);
    }
  }

  /**
   * Z output for beam blanked and on. Swap them for inverted Z inputs.
   */
  public void zRange(float zMin, float zMax) {
    synchronized (paramLock) {
      zaxisMin = zMin;
      zaxisMax = zMax;
    }
  }

  public boolean zAuto() {
    return useZ;
  }

  public void zAuto(boolean zAutoBool) {
    useZ = zAutoBool;
  }

  // ---------------------------------------------------------------- vectrex

  public void vectrex() {
    vectrex(0);
  }

  /**
   * Match the canvas to a modded Vectrex: 310 x 410, rotation 0, 90 or -90.
   */
  public void vectrex(int rotation) {
    if (rotation == 90 || rotation == -90) {
      vectrex(410, 310, vectrexAmpInit, rotation);
    } else {
      vectrex(310, 410, vectrexAmpInit, 0);
    }
  }

  public void vectrex(float width, float height, float initAmp, int rotation) {
    useVectrex = true;
    vectrexRotation = rotation;
    setCanvasSize(width, height);
    vectrexAmpInit = initAmp;
    amp(vectrexAmpInit);
  }

  public float vectrexRatio() {
    return vectrexAmp;
  }

  public void vectrexRatio(float ratio) {
    vectrexAmp = PApplet.constrain(ratio, 0, 1);
    amp(vectrexAmpInit);
  }

  // ---------------------------------------------------------------- shapes

  public void beginShape() {
    if (shapeOpen) endShape();
    shapes.add(new ArrayList<PVector>());
    shapeOpen = true;
  }

  public void vertex(float x, float y) {
    vertexAdd(x, y, 0);
  }

  public void vertex(float x, float y, float z) {
    vertexAdd(x, y, z);
  }

  public void vertex(PVector p) {
    vertexAdd(p.x, p.y, p.z);
  }

  /**
   * Sent as a normal vertex, as in XYscope.
   */
  public void curveVertex(float x, float y) {
    vertex(x, y);
  }

  public void curveVertex(float x, float y, float z) {
    vertex(x, y, z);
  }

  public void endShape() {
    endShape(false);
  }

  /**
   * endShape(CLOSE) joins the last point back to the first.
   */
  public void endShape(int mode) {
    endShape(mode == PConstants.CLOSE);
  }

  public void endShape(boolean close) {
    if (!shapeOpen || shapes.isEmpty()) return;
    ArrayList<PVector> shape = shapes.get(shapes.size() - 1);
    if (close && !shape.isEmpty()) {
      PVector first = shape.get(0);
      shape.add(new PVector(first.x, first.y, 0));
    }
    if (shape.size() > 1) {
      shape.get(shape.size() - 1).z = 1; // the beam blanks here, on its way to the next shape
    } else {
      shapes.remove(shapes.size() - 1);
    }
    shapeOpen = false;
  }

  // Projects a point through the transform stack into projX/projY.
  // Returns false for points behind the camera's near plane.
  boolean project(float px, float py, float pz) {
    float tx = matrix.multX(px, py, pz);
    float ty = matrix.multY(px, py, pz);
    float tz = matrix.multZ(px, py, pz);
    projX = tx;
    projY = ty;
    if (!usePerspectiveVal || tz == 0) return true;

    // Processing's default P3D camera: the eye sits in front of the middle
    // of the canvas, far enough back that z = 0 maps 1:1 to pixels
    float cameraZ = (xyHeight / 2) / (float) Math.tan(Math.PI / 6);
    float depth = cameraZ - tz;
    if (depth < cameraZ * 0.1f) return false; // closer than Processing's near plane
    float s = cameraZ / depth;
    projX = xyWidth / 2 + (tx - xyWidth / 2) * s;
    projY = xyHeight / 2 + (ty - xyHeight / 2) * s;
    return true;
  }

  void vertexAdd(float px, float py, float pz) {
    if (!shapeOpen) beginShape();

    boolean valid = project(px, py, pz);
    if (useLimitPath) {
      valid = valid && projX >= limitVal && projX <= xyWidth - limitVal &&
        projY >= limitVal && projY <= xyHeight - limitVal;
    }

    if (valid) {
      shapes.get(shapes.size() - 1).add(new PVector(projX / xyWidth, projY / xyHeight, 0));
    } else {
      // break the shape where it leaves the canvas
      endShape();
      beginShape();
    }
  }

  public void point(float x, float y) {
    line(x, y, x + 1, y + 1);
  }

  public void point(float x, float y, float z) {
    line(x, y, z, x + 1, y + 1, z + 1);
  }

  public void line(float x1, float y1, float x2, float y2) {
    beginShape();
    vertex(x1, y1);
    vertex(x2, y2);
    endShape();
  }

  public void line(float x1, float y1, float z1, float x2, float y2, float z2) {
    beginShape();
    vertex(x1, y1, z1);
    vertex(x2, y2, z2);
    endShape();
  }

  public void rect(float x, float y, float w) {
    rect(x, y, w, w);
  }

  public void rect(float x, float y, float w, float h) {
    if (rectM == PConstants.CENTER) {
      x -= w / 2;
      y -= h / 2;
    }
    vertexRect(x, y, w, h);
  }

  public void square(float x, float y, float extent) {
    rect(x, y, extent, extent);
  }

  /**
   * CORNER (the default) or CENTER.
   */
  public void rectMode(int mode) {
    if (mode == PConstants.CORNER || mode == PConstants.CENTER) rectM = mode;
  }

  void vertexRect(float x1, float y1, float w1, float h1) {
    beginShape();
    vertex(x1, y1);
    vertex(x1 + w1, y1);
    vertex(x1 + w1, y1 + h1);
    vertex(x1, y1 + h1);
    vertex(x1, y1);
    endShape();
  }

  public void ellipse(float x, float y, float d) {
    ellipse(x, y, d, d);
  }

  public void circle(float x, float y, float d) {
    ellipse(x, y, d, d);
  }

  /**
   * Always drawn from the center, w and h across.
   * based on
   * http://stackoverflow.com/questions/5886628/effecient-way-to-draw-ellipse-with-opengl-or-d3d
   */
  public void ellipse(float cx, float cy, float rx, float ry) {
    float theta = PConstants.TWO_PI / ellipseDetailVal;
    float c = (float) Math.cos(theta);
    float s = (float) Math.sin(theta);
    float x = 0.5f; // start at angle = 0
    float y = 0;

    beginShape();
    for (int ii = 0; ii < ellipseDetailVal + 1; ii++) {
      vertex(x * rx + cx, y * ry + cy);
      // apply the rotation matrix
      float t = x;
      x = c * x - s * y;
      y = s * t + c * y;
    }
    endShape();
  }

  public int ellipseDetail() {
    return ellipseDetailVal;
  }

  public void ellipseDetail(int detail) {
    ellipseDetailVal = Math.max(3, Math.abs(detail));
  }

  /**
   * phase in degrees, resolution in points (1..360).
   */
  public void lissajous(float xPos, float yPos, float radius, float ratioA, float ratioB, float phase, float resolution) {
    resolution = PApplet.constrain(resolution, 1, 360);
    float theta = PConstants.TWO_PI / resolution;
    beginShape();
    for (int i = 0; i < resolution + 1; i++) {
      float x = (float) Math.sin(i * theta * ratioA) * radius;
      float y = (float) Math.sin(PApplet.radians(phase) + i * theta * ratioB) * radius;
      vertex(xPos + x, yPos + y);
    }
    endShape();
  }

  public void box(float size) {
    box(size, size, size);
  }

  /**
   * extended from: https://stackoverflow.com/a/72277489/10885535
   */
  public void box(float w, float h, float d) {
    // half size: keep the pivot at the center of the mesh
    float rx = w * 0.5f;
    float ry = h * 0.5f;
    float rz = d * 0.5f;
    beginShape();
    // back (-z)
    vertex(-rx, -ry, -rz);
    vertex(+rx, -ry, -rz);
    vertex(+rx, +ry, -rz);
    vertex(-rx, +ry, -rz);
    // slide to otherside
    vertex(-rx, -ry, -rz);
    // front (+z)
    vertex(-rx, -ry, +rz);
    vertex(+rx, -ry, +rz);
    vertex(+rx, +ry, +rz);
    vertex(-rx, +ry, +rz);
    // top (-y)
    vertex(-rx, -ry, +rz);
    vertex(-rx, -ry, -rz);
    vertex(+rx, -ry, -rz);
    vertex(+rx, -ry, +rz);
    // bottom (+y)
    vertex(+rx, +ry, +rz);
    vertex(+rx, +ry, -rz);
    vertex(-rx, +ry, -rz);
    vertex(-rx, +ry, +rz);
    // left (-x)
    vertex(-rx, -ry, +rz);
    vertex(-rx, -ry, -rz);
    vertex(-rx, +ry, -rz);
    vertex(-rx, +ry, +rz);
    // slide to otherside
    vertex(-rx, -ry, +rz);
    // right (+x)
    vertex(+rx, -ry, +rz);
    vertex(+rx, -ry, -rz);
    vertex(+rx, +ry, -rz);
    vertex(+rx, +ry, +rz);
    endShape();
  }

  public void sphere(float r) {
    ellipsoid(r, r, r, 24, 24);
  }

  public void sphere(float r, int detail) {
    ellipsoid(r, r, r, detail, detail);
  }

  public void ellipsoid(float rx, float ry, float rz) {
    ellipsoid(rx, ry, rz, 24, 24);
  }

  /**
   * based on: Processing Examples » Topics » Textures » Texture Sphere
   */
  public void ellipsoid(float rx, float ry, float rz, int dx, int dy) {
    int numvW = PApplet.constrain(dx, 1, 50);
    int numvH_2pi = PApplet.constrain(dy, 1, 50);

    // the number of points around the width and height
    int numPointsW = numvW + 1;
    int numPointsH_2pi = numvH_2pi; // how many actual points around the sphere (not just from top to bottom)
    int numPointsH = (int) Math.ceil(numPointsH_2pi / 2.0f) + 1; // how many points from top to bottom

    float[] coorX = new float[numPointsW];   // all the x-coor in a horizontal circle radius 1
    float[] coorY = new float[numPointsH];   // all the y-coor in a vertical circle radius 1
    float[] coorZ = new float[numPointsW];   // all the z-coor in a horizontal circle radius 1
    float[] multXZ = new float[numPointsH];  // the radius of each horizontal circle

    for (int i = 0; i < numPointsW; i++) {
      float thetaW = i * 2 * PConstants.PI / (numPointsW - 1);
      coorX[i] = (float) Math.sin(thetaW);
      coorZ[i] = (float) Math.cos(thetaW);
    }

    for (int i = 0; i < numPointsH; i++) {
      if (numPointsH_2pi % 2 != 0 && i == numPointsH - 1) { // odd numPointsH_2pi and the last point
        float thetaH = (i - 1) * 2 * PConstants.PI / numPointsH_2pi;
        coorY[i] = (float) Math.cos(PConstants.PI + thetaH);
        multXZ[i] = 0;
      } else {
        // allows a flat bottom if numPointsH is odd
        float thetaH = i * 2 * PConstants.PI / numPointsH_2pi;
        // PI+ makes the top always the point instead of the bottom
        coorY[i] = (float) Math.cos(PConstants.PI + thetaH);
        multXZ[i] = (float) Math.sin(thetaH);
      }
    }

    beginShape();
    for (int i = 0; i < numPointsH - 1; i++) {
      for (int j = 0; j < numPointsW; j++) {
        vertex(coorX[j] * multXZ[i] * rx, coorY[i] * ry, coorZ[j] * multXZ[i] * rz);
        vertex(coorX[j] * multXZ[i + 1] * rx, coorY[i + 1] * ry, coorZ[j] * multXZ[i + 1] * rz);
      }
    }
    endShape();
  }

  public void torus(float radius, float tubeRadius) {
    torus(radius, tubeRadius, 24, 24);
  }

  /**
   * built upon: https://processing.org/examples/toroid.html
   */
  public void torus(float radius, float tubeRadius, int dx, int dy) {
    dx = PApplet.constrain(dx, 1, 50);
    dy = PApplet.constrain(dy, 1, 50);

    float[] vx = new float[dx + 1];
    float[] vz = new float[dx + 1];
    float[] v2x = new float[dx + 1];
    float[] v2y = new float[dx + 1];
    float[] v2z = new float[dx + 1];

    float angle = 0;
    for (int i = 0; i <= dx; i++) {
      vx[i] = radius + (float) Math.sin(PApplet.radians(angle)) * tubeRadius;
      vz[i] = (float) Math.cos(PApplet.radians(angle)) * tubeRadius;
      angle += 360.0f / dx;
    }

    float latheAngle = 0;
    for (int i = 0; i <= dy; i++) {
      beginShape();
      for (int j = 0; j <= dx; j++) {
        if (i > 0) vertex(v2x[j], v2y[j], v2z[j]);
        v2x[j] = (float) Math.cos(PApplet.radians(latheAngle)) * vx[j];
        v2y[j] = (float) Math.sin(PApplet.radians(latheAngle)) * vx[j];
        v2z[j] = vz[j];
        vertex(v2x[j], v2y[j], v2z[j]);
      }
      latheAngle += 360.0f / dy;
      endShape();
    }
  }

  public void polyline(XYPolyline poly) {
    if (poly == null || poly.size() < 2) return;
    beginShape();
    for (PVector v : poly.points) vertex(v.x, v.y, v.z);
    if (poly.isClosed()) vertex(poly.points.get(0));
    endShape();
  }

  public void polylines(List<XYPolyline> polys) {
    for (XYPolyline poly : polys) polyline(poly);
  }

  /**
   * A PShape made of vertices: createShape() with beginShape()/vertex(), an
   * SVG from loadShape(), or a GROUP of them. Bezier and quadratic vertices
   * are flattened; curve vertices go in as plain vertices, as in XYscope.
   * Primitives (createShape(RECT...)) and the shape's own transforms aren't read.
   */
  public void shape(PShape s) {
    if (s == null || !s.isVisible()) return;
    for (int i = 0; i < s.getChildCount(); i++) shape(s.getChild(i));

    int n = s.getVertexCount();
    if (n < 2) return;
    boolean closed = s.isClosed();
    int codeCount = s.getVertexCodeCount();
    int[] codes = s.getVertexCodes();

    beginShape();
    PVector first = null;
    PVector last = null;
    int vi = 0;
    int numCodes = codeCount > 0 ? codeCount : n;
    for (int ci = 0; ci < numCodes && vi < n; ci++) {
      int code = codeCount > 0 ? codes[ci] : PConstants.VERTEX;
      if (code == PConstants.BEZIER_VERTEX && last != null && vi + 2 < n) {
        PVector c1 = s.getVertex(vi), c2 = s.getVertex(vi + 1), p = s.getVertex(vi + 2);
        vi += 3;
        for (int k = 1; k <= CURVE_STEPS; k++) {
          float t = k / (float) CURVE_STEPS;
          vertex(bezier(last.x, c1.x, c2.x, p.x, t), bezier(last.y, c1.y, c2.y, p.y, t), bezier(last.z, c1.z, c2.z, p.z, t));
        }
        last = p;
      } else if (code == PConstants.QUADRATIC_VERTEX && last != null && vi + 1 < n) {
        PVector c = s.getVertex(vi), p = s.getVertex(vi + 1);
        vi += 2;
        for (int k = 1; k <= CURVE_STEPS; k++) {
          float t = k / (float) CURVE_STEPS, u = 1 - t;
          vertex(u * u * last.x + 2 * u * t * c.x + t * t * p.x, u * u * last.y + 2 * u * t * c.y + t * t * p.y,
            u * u * last.z + 2 * u * t * c.z + t * t * p.z);
        }
        last = p;
      } else if (code == PConstants.BREAK) {
        if (closed && first != null) vertex(first);
        endShape();
        beginShape();
        first = null;
        last = null;
      } else {
        PVector v = s.getVertex(vi++);
        vertex(v);
        if (first == null) first = v;
        last = v;
      }
    }
    if (closed && first != null) vertex(first);
    endShape();
  }

  static final int CURVE_STEPS = 12;

  static float bezier(float a, float b, float c, float d, float t) {
    float u = 1 - t;
    return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d;
  }

  // ---------------------------------------------------------------- transforms

  public void pushMatrix() {
    matrixStack.add(matrix.get());
  }

  public void popMatrix() {
    if (matrixStack.isEmpty()) {
      System.err.println("XYscope: popMatrix() without a pushMatrix()");
      return;
    }
    matrix = matrixStack.remove(matrixStack.size() - 1);
  }

  public void resetMatrix() {
    matrix.reset();
    matrixStack.clear();
  }

  public void translate(float x, float y) {
    translate(x, y, 0);
  }

  public void translate(float x, float y, float z) {
    matrix.translate(x, y, z);
  }

  /**
   * angles in radians, as in Processing
   */
  public void rotate(float angle) {
    rotateZ(angle);
  }

  public void rotateX(float angle) {
    matrix.rotateX(angle);
  }

  public void rotateY(float angle) {
    matrix.rotateY(angle);
  }

  public void rotateZ(float angle) {
    matrix.rotateZ(angle);
  }

  public void scale(float s) {
    scale(s, s, s);
  }

  public void scale(float x, float y) {
    scale(x, y, 1);
  }

  public void scale(float x, float y, float z) {
    matrix.scale(x, y, z);
  }

  /**
   * Perspective for 3D points, on by default. Off flattens z.
   */
  public void perspective(boolean usePerspective) {
    usePerspectiveVal = usePerspective;
  }

  // ---------------------------------------------------------------- text

  public static String[] fonts() {
    return HersheyFont.getFontNames();
  }

  /**
   * One of fonts(), or the path to a .jhf file.
   */
  public boolean textFont(String fontName) {
    return font.load(fontName);
  }

  public float textSize() {
    return textSizeVal;
  }

  /**
   * size is the height of a capital letter; it also resets the leading
   */
  public void textSize(float size) {
    textSizeVal = size;
    textLeadingVal = size * 1.5f;
  }

  public float textLeading() {
    return textLeadingVal;
  }

  public void textLeading(float leading) {
    textLeadingVal = leading;
  }

  /**
   * LEFT, CENTER or RIGHT.
   */
  public void textAlign(int alignX) {
    textAlign(alignX, textAlignY);
  }

  /**
   * LEFT, CENTER or RIGHT, then TOP, CENTER, BOTTOM or BASELINE.
   */
  public void textAlign(int alignX, int alignY) {
    textAlignX = alignX;
    textAlignY = alignY;
  }

  public void text(String s, float x, float y) {
    for (XYPolyline stroke : textPaths(s, x, y)) {
      beginShape();
      for (PVector v : stroke.points) vertex(v.x, v.y);
      endShape();
    }
  }

  public float textWidth(String s) {
    return font.getWidth(s, textSizeVal);
  }

  public ArrayList<XYPolyline> textPaths(String s, float x, float y) {
    return font.getStrokes(s, x, y, textSizeVal, textLeadingVal, textAlignX, textAlignY);
  }

  public HersheyFont getFont() {
    return font;
  }

  // ---------------------------------------------------------------- inspection

  /**
   * Shapes as normalized 0..1 points. z is 1 on a shape's last point,
   * where the beam blanks, as in XYscope.
   */
  public ArrayList<ArrayList<PVector>> getShapes() {
    return shapes;
  }

  /**
   * Shapes in canvas pixels.
   */
  public ArrayList<XYPolyline> getPolylines() {
    ArrayList<XYPolyline> result = new ArrayList<XYPolyline>();
    for (ArrayList<PVector> shape : shapes) {
      XYPolyline poly = new XYPolyline();
      for (PVector p : shape) poly.addVertex(p.x * xyWidth, p.y * xyHeight);
      result.add(poly);
    }
    return result;
  }

  public ArrayList<PVector> wavePoints() {
    ArrayList<PVector> points = new ArrayList<PVector>();
    for (ArrayList<PVector> shape : shapes) points.addAll(shape);
    return points;
  }

  // ---------------------------------------------------------------- drawing

  static final int WHITE = 0xffffffff;
  static final int GREEN = 0xff00ff00;
  static final int SCOPE_GREEN = 0xff32ff32;
  static final int WAVE_BLUE = 0xff3232ff;
  static final int WAVE_RED = 0xffff3232;

  PGraphics graphics() {
    if (parent == null) throw new IllegalStateException("XYscope: this XYscope has no sketch, pass a PGraphics to draw into");
    return parent.g;
  }

  int mouseX() {
    return parent != null ? parent.mouseX : 0;
  }

  public void drawAll() {
    drawAll(graphics());
  }

  public void drawAll(PGraphics g) {
    drawPath(g);
    drawWaveform(g);
    drawWave(g);
    drawXY(g);
    drawPoints(g);
  }

  public void drawPath() {
    drawPath(graphics(), WHITE);
  }

  public void drawPath(int color) {
    drawPath(graphics(), color);
  }

  public void drawPath(PGraphics g) {
    drawPath(g, WHITE);
  }

  /**
   * The shapes as they were drawn.
   */
  public void drawPath(PGraphics g, int color) {
    g.pushStyle();
    g.noFill();
    g.stroke(color);
    for (XYPolyline poly : getPolylines()) poly.draw(g);
    g.popStyle();
  }

  public void drawPoints() {
    drawPoints(graphics(), GREEN);
  }

  public void drawPoints(int color) {
    drawPoints(graphics(), color);
  }

  public void drawPoints(PGraphics g) {
    drawPoints(g, GREEN);
  }

  /**
   * Every point of every shape.
   */
  public void drawPoints(PGraphics g, int color) {
    g.pushStyle();
    g.noStroke();
    g.fill(color);
    g.ellipseMode(PConstants.CENTER);
    for (ArrayList<PVector> shape : shapes) {
      for (PVector p : shape) g.circle(p.x * xyWidth, p.y * xyHeight, 3);
    }
    g.popStyle();
  }

  public void drawXY() {
    drawXY(graphics(), SCOPE_GREEN);
  }

  public void drawXY(int color) {
    drawXY(graphics(), color);
  }

  public void drawXY(PGraphics g) {
    drawXY(g, SCOPE_GREEN);
  }

  /**
   * The output signal plotted X against Y, like the scope will show it.
   */
  public void drawXY(PGraphics g, int color) {
    XYSoundBuffer buffer = getLastBuffer();
    int nCh = buffer.getNumChannels();
    int nFrames = buffer.getNumFrames();
    if (nCh < 2 || nFrames < 2) return;
    float[] s = buffer.samples;

    g.pushStyle();
    g.noFill();
    g.stroke(color);
    g.pushMatrix();
    g.translate(xyWidth / 2, xyHeight / 2);
    g.beginShape();
    for (int i = 0; i < nFrames; i++) {
      float l = s[i * nCh];
      float r = s[i * nCh + 1];
      float lAudio = l * xyWidth / 2;
      float rAudio = r * xyHeight / 2;
      if (useVectrex) {
        // undo the Vectrex wiring, so the preview stays upright
        if (vectrexRotation == 90) {
          lAudio = -r * xyWidth / 2;
          rAudio = l * xyHeight / 2;
        } else if (vectrexRotation == -90) {
          lAudio = r * xyWidth / 2;
          rAudio = -l * xyHeight / 2;
        } else {
          lAudio = -l * xyWidth / 2;
          rAudio = -r * xyHeight / 2;
        }
      }
      g.vertex(lAudio, -rAudio);
    }
    g.endShape();

    if (debugWave) {
      PVector a = amp();
      float mouseT = mouseX() / xyWidth;
      float mx = tableX.value(mouseT) * xyWidth / 2 * a.x;
      float my = -tableY.value(mouseT) * xyHeight / 2 * a.y;
      g.noStroke();
      g.fill(color);
      g.ellipseMode(PConstants.CENTER);
      g.circle(mx, my, 10);
    }

    g.popMatrix();
    g.popStyle();
  }

  public void drawWaveform() {
    drawWaveform(graphics(), WAVE_BLUE, WAVE_RED);
  }

  public void drawWaveform(int colorX, int colorY) {
    drawWaveform(graphics(), colorX, colorY);
  }

  public void drawWaveform(PGraphics g) {
    drawWaveform(g, WAVE_BLUE, WAVE_RED);
  }

  /**
   * The wavetables: X in the top half, Y in the bottom, Z through the middle.
   */
  public void drawWaveform(PGraphics g, int colorX, int colorY) {
    g.pushStyle();
    g.noFill();
    g.stroke(colorX);
    plot(g, tableX, xyHeight * 0.25f);
    g.stroke(colorY);
    plot(g, tableY, xyHeight * 0.75f);
    if (useZ) {
      g.stroke(SCOPE_GREEN);
      plot(g, tableZ, xyHeight * 0.5f);
    }

    if (debugWave) {
      float mx = mouseX();
      float t = mx / xyWidth;
      g.noStroke();
      g.ellipseMode(PConstants.CENTER);
      g.fill(colorX);
      g.circle(mx, xyHeight * 0.25f - xyHeight * 0.125f * tableX.value(t), 10);
      g.fill(colorY);
      g.circle(mx, xyHeight * 0.75f - xyHeight * 0.125f * tableY.value(t), 10);
    }
    g.popStyle();
  }

  void plot(PGraphics g, XYWavetable table, float centerY) {
    int w = Math.max(2, (int) xyWidth);
    float[] wave = table.getWaveformArray();
    g.beginShape();
    for (int i = 0; i < w; i++) {
      g.vertex(i, centerY - xyHeight * 0.125f * XYWavetable.valueAt(wave, (float) i / w));
    }
    g.endShape();
  }

  public void drawWave() {
    drawWave(graphics(), WHITE);
  }

  public void drawWave(int color) {
    drawWave(graphics(), color);
  }

  public void drawWave(PGraphics g) {
    drawWave(g, WHITE);
  }

  /**
   * The output signal over time: left channel on top, right below, Z between.
   */
  public void drawWave(PGraphics g, int color) {
    XYSoundBuffer buffer = getLastBuffer();
    int nCh = buffer.getNumChannels();
    int nFrames = buffer.getNumFrames();
    if (nFrames < 2) return;
    float[] s = buffer.samples;

    g.pushStyle();
    g.noFill();
    g.stroke(color);
    float[] centers = { xyHeight * 0.25f, xyHeight * 0.75f, xyHeight * 0.5f };
    for (int c = 0; c < nCh && c < 3; c++) {
      g.beginShape();
      for (int i = 0; i < nFrames; i++) {
        g.vertex(i * xyWidth / nFrames, centers[c] - xyHeight * 0.25f * s[i * nCh + c]);
      }
      g.endShape();
    }
    g.popStyle();
  }

  public boolean debugView() {
    return debugWave;
  }

  /**
   * Marks the point in the wave under the mouse in drawXY() and drawWaveform().
   */
  public void debugView(boolean debug) {
    debugWave = debug;
  }

  // ---------------------------------------------------------------- recording

  public void recorderBegin() {
    recorderBegin("XYscope");
  }

  /**
   * Record the output to &lt;name&gt;_&lt;timestamp&gt;.wav in the sketch folder.
   */
  public void recorderBegin(String name) {
    synchronized (audioLock) {
      recordingPath = name + "_" + new SimpleDateFormat("yyyy_MM_dd_HHmmssSSS").format(new Date()) + ".wav";
      recordBuffer.clear();
      recordChannels = 1;
      recording = true;
    }
    System.out.println("XYscope: beginRecord");
  }

  /**
   * Stops recording and saves the file. Returns its full path, or "" if nothing was
   * recorded.
   */
  public String recorderEnd() {
    float[] toSave;
    int channels, rate;
    String path;
    synchronized (audioLock) {
      if (!recording) return "";
      recording = false;
      toSave = recordBuffer.toArray();
      recordBuffer.clear();
      channels = recordChannels;
      rate = recordSampleRate;
      path = recordingPath;
    }
    if (toSave.length == 0) {
      System.err.println("XYscope: endRecord: nothing was recorded");
      return "";
    }
    WavFile.save(parent, path, new XYSoundBuffer(toSave, channels, rate), WavFile.PCM_16);
    String fullPath = parent != null ? parent.savePath(path) : new File(path).getAbsolutePath();
    System.out.println("XYscope: endRecord + saved " + fullPath);
    return fullPath;
  }

  public boolean isRecording() {
    synchronized (audioLock) {
      return recording;
    }
  }

}
