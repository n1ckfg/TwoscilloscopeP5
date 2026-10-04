package twoscilloscopeP5;

/**
 * Streaming sample rate conversion for one channel of audio.
 * <p>The Oscilloscope app drew its lines from audio upsampled to a high "visual" sample
 * rate (192kHz or more) with FFmpeg's swresample, so the beam follows the band-limited
 * curve between samples instead of cutting straight across. This does the same job without
 * FFmpeg: SINC is a windowed sinc (Lanczos, 4 lobes), like swresample's default filter, and
 * LINEAR matches the app's "interpolate = false" option.</p>
 */

public class StreamResampler {

  public static final int LINEAR = 0;
  public static final int SINC = 1;

  double inRate = 44100;
  double outRate = 192000;
  int interpolation = SINC;
  int taps = 4;
  double step = 44100.0 / 192000.0;
  double stepCos = Math.cos(Math.PI / 4);
  double stepSin = Math.sin(Math.PI / 4);

  FloatArray history = new FloatArray(64);
  double pos = 0;

  public StreamResampler() {
    reset();
  }

  public StreamResampler(double _inRate, double _outRate, int _interpolation) {
    setup(_inRate, _outRate, _interpolation);
  }

  public void setup(double _inRate, double _outRate) {
    setup(_inRate, _outRate, SINC);
  }

  public void setup(double _inRate, double _outRate, int _interpolation) {
    inRate = Math.max(1.0, _inRate);
    outRate = Math.max(1.0, _outRate);
    interpolation = _interpolation;
    taps = interpolation == SINC ? 4 : 1;
    step = inRate / outRate;
    stepCos = Math.cos(Math.PI / taps);
    stepSin = Math.sin(Math.PI / taps);
    reset();
  }

  public void reset() {
    // start with a little silence, so the first samples have neighbours
    history.clear();
    for (int i = 0; i < taps; i++) history.add(0);
    pos = taps;
  }

  public double getInRate() {
    return inRate;
  }

  public double getOutRate() {
    return outRate;
  }

  // Converts the next n input samples (every stride-th float of in, from
  // offset) and appends the output samples to out.
  void process(float[] in, int offset, int n, int stride, FloatArray out) {
    history.ensureCapacity(history.size + n);
    for (int i = 0; i < n; i++) history.data[history.size++] = in[offset + i * stride];
    float[] h = history.data;
    out.ensureCapacity(out.size + (int) ((history.size - pos) / step) + 2);

    // an output sample at pos needs input samples up to floor(pos) + taps
    while (Math.floor(pos) + taps < history.size) {
      int i0 = (int) Math.floor(pos);
      double frac = pos - i0;

      if (interpolation == LINEAR) {
        out.add((float) (h[i0] + frac * (h[i0 + 1] - h[i0])));
      } else {
        // kernel(pos - k) for each tap, with the sines stepped from tap to tap
        // (the taps are one sample apart) rather than computed afresh
        double a = taps;
        double x = pos - (i0 - taps + 1);
        double sinPi = Math.sin(Math.PI * x);
        double sinA = Math.sin(Math.PI * x / a);
        double cosA = Math.cos(Math.PI * x / a);
        double sum = 0;
        double weights = 0;
        for (int k = i0 - taps + 1; k <= i0 + taps; k++, x -= 1) {
          double w;
          if (x == 0) {
            w = 1;
          } else if (Math.abs(x) >= a) {
            w = 0;
          } else {
            double px = Math.PI * x;
            w = a * sinPi * sinA / (px * px);
          }
          sum += w * h[k];
          weights += w;
          // sin(pi (x - 1)) = -sin(pi x); sin and cos of pi (x - 1) / a by rotation
          sinPi = -sinPi;
          double s = sinA * stepCos - cosA * stepSin;
          cosA = cosA * stepCos + sinA * stepSin;
          sinA = s;
        }
        out.add((float) (weights != 0 ? sum / weights : 0));
      }
      pos += step;
    }

    // drop the samples that no future output will need
    int keepFrom = (int) Math.floor(pos) - taps + 1;
    if (keepFrom > 0) {
      keepFrom = Math.min(keepFrom, history.size);
      history.removeFront(keepFrom);
      pos -= keepFrom;
    }
  }

  /**
   * Converts every stride-th sample of in, from offset, and returns the output samples.
   */
  public float[] process(float[] in, int offset, int n, int stride) {
    FloatArray out = new FloatArray((int) (n / step) + 8);
    process(in, offset, n, stride, out);
    return out.toArray();
  }

}
