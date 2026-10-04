package twoscilloscopeP5;

import java.util.Random;

/**
 * A port of XYscope's XYWavetable.java, itself Hansi Raber's fix of Minim's Wavetable: a
 * float array you can sample with a normalized [0,1] position.
 * <p>The Java version fixed an ArrayIndexOutOfBoundsException that happened when the
 * drawing thread replaced the array while the audio thread was reading it. Here the array
 * sits behind a volatile reference that is only ever replaced, never edited, so the audio
 * thread can grab the current table once per buffer and keep reading it safely even if a
 * new one gets swapped in halfway through.</p>
 * <p>Differences from the Java original:</p>
 * <ul>
 * <li>The transform methods (scale, smooth, warp...) build a new table and swap it in,
 *     rather than editing the one the audio thread might be reading.</li>
 * <li>smooth() is a true moving average. Minim's divided a window of n+1 samples by n.</li>
 * </ul>
 */

public class XYWavetable {

  volatile float[] waveform;

  static final Random noiseRng = new Random();

  public XYWavetable() {
    this(0);
  }

  public XYWavetable(int size) {
    waveform = new float[Math.max(0, size)];
  }

  public XYWavetable(float[] _waveform) {
    setWaveform(_waveform);
  }

  /**
   * Copies the array in.
   */
  public void setWaveform(float[] _waveform) {
    waveform = _waveform != null ? _waveform.clone() : new float[0];
  }

  /**
   * A copy of the current table.
   */
  public float[] getWaveform() {
    return waveform.clone();
  }

  /**
   * The current table itself, safe to keep reading on another thread.
   * Don't change its values: replace the table with setWaveform() instead.
   */
  public float[] getWaveformArray() {
    return waveform;
  }

  public float get(int i) {
    float[] wave = waveform;
    return i >= 0 && i < wave.length ? wave[i] : 0;
  }

  public void set(int i, float value) {
    float[] next = getWaveform();
    if (i >= 0 && i < next.length) next[i] = value;
    waveform = next;
  }

  public int size() {
    return waveform.length;
  }

  /**
   * Sample the table at a position in [0,1], with linear interpolation.
   * Positions outside [0,1] wrap around, so does the last sample, which
   * interpolates back to the first one.
   */
  public float value(float at) {
    return valueAt(waveform, at);
  }

  public static float valueAt(float[] wave, double at) {
    int n = wave.length;
    if (n == 0) return 0;
    double wrapped = at - Math.floor(at);
    double whichSample = n * wrapped;

    // linearly interpolate between the two samples we want
    int lowSamp = ((int) whichSample) % n;
    int hiSamp = (lowSamp + 1) % n;
    float rem = (float) (whichSample - Math.floor(whichSample));

    return wave[lowSamp] + rem * (wave[hiSamp] - wave[lowSamp]);
  }

  public void scale(float scale) {
    float[] w = getWaveform();
    for (int i = 0; i < w.length; i++) w[i] *= scale;
    waveform = w;
  }

  public void offset(float amount) {
    float[] w = getWaveform();
    for (int i = 0; i < w.length; i++) w[i] += amount;
    waveform = w;
  }

  public void normalize() {
    float[] w = getWaveform();
    float max = 0;
    for (float v : w) max = Math.max(max, Math.abs(v));
    if (max > 0) {
      for (int i = 0; i < w.length; i++) w[i] /= max;
    }
    waveform = w;
  }

  public void invert() {
    flip(0);
  }

  public void flip(float in) {
    float[] w = getWaveform();
    for (int i = 0; i < w.length; i++) w[i] = in - (w[i] - in);
    waveform = w;
  }

  public void addNoise(float sigma) {
    float[] w = getWaveform();
    synchronized (noiseRng) {
      for (int i = 0; i < w.length; i++) w[i] += (float) noiseRng.nextGaussian() * sigma;
    }
    waveform = w;
  }

  public void rectify() {
    float[] w = getWaveform();
    for (int i = 0; i < w.length; i++) w[i] = Math.abs(w[i]);
    waveform = w;
  }

  public void smooth(int windowLength) {
    if (windowLength < 1) return;
    float[] temp = waveform;
    float[] w = temp.clone();
    for (int i = windowLength; i < w.length; i++) {
      float avg = 0;
      for (int j = i - windowLength; j <= i; j++) avg += temp[j];
      w[i] = avg / (windowLength + 1);
    }
    waveform = w;
  }

  public void warp(float warpPoint, float warpTarget) {
    float[] source = waveform;
    float[] w = new float[source.length];
    for (int s = 0; s < w.length; s++) {
      float lookup = (float) s / w.length;
      if (lookup <= warpTarget) {
        // normalize look up to [0,warpTarget], expand to [0,warpPoint]
        lookup = warpTarget > 0 ? (lookup / warpTarget) * warpPoint : 0;
      } else {
        // map (warpTarget,1] to (warpPoint,1]
        lookup = warpPoint + (1 - (1 - lookup) / (1 - warpTarget)) * (1 - warpPoint);
      }
      w[s] = valueAt(source, lookup);
    }
    waveform = w;
  }

}
