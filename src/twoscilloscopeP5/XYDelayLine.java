package twoscilloscopeP5;

import java.util.Arrays;

/**
 * A delay line read with linear interpolation, for fractional delays.
 */
public class XYDelayLine {

  float[] buffer = new float[0];
  int writeIndex = 0;

  public void setup(int maxSamples) {
    if (buffer.length != maxSamples + 2) {
      buffer = new float[maxSamples + 2];
      writeIndex = 0;
    }
  }

  public void reset() {
    Arrays.fill(buffer, 0);
    writeIndex = 0;
  }

  public void write(float x) {
    if (buffer.length == 0) return;
    buffer[writeIndex] = x;
    writeIndex = (writeIndex + 1) % buffer.length;
  }

  /**
   * delay in samples, at least 1
   */
  public float read(float delay) {
    int n = buffer.length;
    if (n < 2) return 0;
    delay = Math.max(1, Math.min(n - 1, delay));
    // the newest sample sits just behind writeIndex
    double pos = writeIndex - (double) delay;
    while (pos < 0) pos += n;
    int i0 = ((int) pos) % n;
    int i1 = (i0 + 1) % n;
    float frac = (float) (pos - Math.floor(pos));
    return buffer[i0] + frac * (buffer[i1] - buffer[i0]);
  }

}
