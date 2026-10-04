package twoscilloscopeP5;

import java.util.ArrayList;
import java.util.List;

/**
 * The new feature, and the reason the two halves of the library live together: turn a
 * vector shape into a new vector shape by passing it through sound.
 * <pre>
 * shape --XYscope--&gt; XY audio --XYEffects--&gt; altered audio --XYDecoder--&gt; new shape
 *
 * XYTransformer transformer;
 *
 * void setup() {
 *   transformer = new XYTransformer(width, height);
 *   transformer.effects.add(new XYLowPass()).cutoff.set(800);
 *   transformer.effects.add(new XYChannelDelay());
 * }
 *
 * ...
 * ArrayList&lt;XYPolyline&gt; altered = transformer.transform(shapes);
 * </pre>
 * <p>The shapes are encoded exactly as XYscope would send them to a scope (one loop of
 * freq() Hz), run through the effect chain for a few loops so filters and echoes settle
 * into a steady state, and the last loop is decoded back into polylines on the same
 * canvas.</p>
 * <p>The altered audio is available too: getProcessedWaves() is one loop of it, ready for
 * XYscope.setWaveforms(), so you can hear (or put on a real scope) exactly the shape you
 * see.</p>
 */

public class XYTransformer {

  public XYEffectChain effects = new XYEffectChain();
  public XYDecoderSettings decoder = new XYDecoderSettings();
  /**
   * loops rendered before the one that's decoded
   */
  public int settleCycles = 4;

  XYscope encoder = new XYscope();
  XYSoundBuffer encoded = new XYSoundBuffer();
  XYSoundBuffer processed = new XYSoundBuffer();
  ArrayList<XYPolyline> result = new ArrayList<XYPolyline>();

  float width = 512;
  float height = 512;
  int sampleRate = 44100;
  float freq = 50;
  int cycleFrames = 882;

  public XYTransformer() {
    setup(width, height, sampleRate, freq, 512);
  }

  public XYTransformer(float _width, float _height) {
    setup(_width, _height, 44100, 50, 512);
  }

  public XYTransformer(float _width, float _height, int _sampleRate, float _freq) {
    setup(_width, _height, _sampleRate, _freq, 512);
  }

  public void setup(float _width, float _height, int _sampleRate, float _freq, int waveSize) {
    width = _width;
    height = _height;
    sampleRate = Math.max(1000, _sampleRate);
    freq = Math.max(0.1f, _freq);

    encoder.setCanvasSize(width, height);
    encoder.sampleRate(sampleRate);
    encoder.waveSize(waveSize);
    encoder.freq(freq);
  }

  int getCycleFrames(float f, int sr) {
    return Math.max(2, Math.round(sr / Math.max(0.1f, f)));
  }

  /**
   * shapes in canvas pixels -&gt; altered shapes in canvas pixels
   */
  public ArrayList<XYPolyline> transform(List<XYPolyline> shapes) {
    encoder.setCanvasSize(width, height);
    encoder.freq(freq);
    encoder.clearWaves();
    encoder.polylines(shapes);
    encoder.buildWaves();
    return transform(encoder);
  }

  /**
   * whatever an XYscope has built with buildWaves(), at its freq and canvas size
   */
  public ArrayList<XYPolyline> transform(XYscope scope) {
    float f = scope.freq().x;
    int sr = scope.sampleRate();
    int cycle = getCycleFrames(f, sr);
    scope.render(encoded, cycle * (Math.max(0, settleCycles) + 1), scope.zAuto() ? 3 : 2);
    return processAndDecode(f, sr, scope.getWidth(), scope.getHeight());
  }

  /**
   * audio that's already XYscope format, looping at getFreq()
   */
  public ArrayList<XYPolyline> transform(XYSoundBuffer encodedAudio) {
    encoded = encodedAudio.copy();
    int sr = encoded.getSampleRate() > 0 ? encoded.getSampleRate() : sampleRate;
    encoded.setSampleRate(sr);
    return processAndDecode(freq, sr, width, height);
  }

  ArrayList<XYPolyline> processAndDecode(float f, int sr, float w, float h) {
    processed = encoded.copy();
    effects.reset();
    effects.process(processed);

    cycleFrames = Math.min(getCycleFrames(f, sr), processed.getNumFrames());
    int nCh = processed.getNumChannels();
    result = new ArrayList<XYPolyline>();
    if (nCh < 2 || cycleFrames < 2) return result;

    float[][] waves = getProcessedWaves();

    XYDecoderSettings s = decoder.copy();
    s.width = w;
    s.height = h;
    s.sampleRate = sr;
    s.freq = f;
    result = XYDecoder.decodeCycle(waves[0], waves[1], waves[2].length > 0 ? waves[2] : null, waves[0].length, s);
    return result;
  }

  public ArrayList<XYPolyline> getResult() {
    return result;
  }

  public XYSoundBuffer getEncodedAudio() {
    return encoded;
  }

  public XYSoundBuffer getProcessedAudio() {
    return processed;
  }

  /**
   * The last loop of the processed audio as { x, y, z }: -1..1 waves for
   * XYscope.setWaveforms(). z is empty when the audio has no Z channel.
   */
  public float[][] getProcessedWaves() {
    int nCh = processed.getNumChannels();
    int n = processed.getNumFrames();
    int m = Math.min(cycleFrames, n);
    if (nCh < 2 || m == 0) return new float[][] { new float[0], new float[0], new float[0] };

    int start = n - m;
    float[] x = new float[m];
    float[] y = new float[m];
    float[] z = new float[nCh >= 3 ? m : 0];
    float[] s = processed.samples;
    for (int i = 0; i < m; i++) {
      x[i] = s[(start + i) * nCh];
      y[i] = s[(start + i) * nCh + 1];
      if (nCh >= 3) z[i] = s[(start + i) * nCh + 2];
    }
    return new float[][] { x, y, z };
  }

  /**
   * The last loop of the processed audio, all channels.
   */
  public XYSoundBuffer getProcessedCycle() {
    return processed.getLastFrames(cycleFrames);
  }

  /**
   * the XYscope that encodes shapes (set its steps(), zRange()... here)
   */
  public XYscope getEncoder() {
    return encoder;
  }

  public float getWidth() {
    return width;
  }

  public float getHeight() {
    return height;
  }

  public float getFreq() {
    return freq;
  }

  public int getSampleRate() {
    return sampleRate;
  }

}
