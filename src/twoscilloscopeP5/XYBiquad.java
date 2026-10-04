package twoscilloscopeP5;

/**
 * Second order filter from Robert Bristow-Johnson's Audio EQ Cookbook.
 */
public class XYBiquad {

  double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
  double x1 = 0, x2 = 0, y1 = 0, y2 = 0;

  void set(double _b0, double _b1, double _b2, double a0, double _a1, double _a2) {
    b0 = _b0 / a0;
    b1 = _b1 / a0;
    b2 = _b2 / a0;
    a1 = _a1 / a0;
    a2 = _a2 / a0;
  }

  public void lowPass(double cutoff, double q, double sampleRate) {
    double w0 = 2 * Math.PI * Math.max(1, Math.min(sampleRate * 0.49, cutoff)) / sampleRate;
    double alpha = Math.sin(w0) / (2 * Math.max(0.05, q));
    double c = Math.cos(w0);
    set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha);
  }

  public void highPass(double cutoff, double q, double sampleRate) {
    double w0 = 2 * Math.PI * Math.max(1, Math.min(sampleRate * 0.49, cutoff)) / sampleRate;
    double alpha = Math.sin(w0) / (2 * Math.max(0.05, q));
    double c = Math.cos(w0);
    set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha);
  }

  public void reset() {
    x1 = x2 = y1 = y2 = 0;
  }

  public float process(float x) {
    double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
    x2 = x1;
    x1 = x;
    y2 = y1;
    y1 = y;
    return (float) y;
  }

}
