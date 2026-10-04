package twoscilloscopeP5;

/**
 * Multiplies by a sine: shapes pulse in and out of the center.
 */
public class XYRingMod extends XYEffect {

  public XYParameter freq = addParameter("freq (Hz)", 150, 0.1f, 2000);
  public XYParameter depth = addParameter("depth", 0.5f, 0, 1);

  double phase = 0;
  double step = 0;

  public XYRingMod() {
    super("ring mod");
  }

  public void reset() {
    phase = 0;
  }

  public void prepare(float sr) {
    step = freq.get() / sr;
  }

  public void processFrame(float[] xy) {
    float d = depth.get();
    float m = 1 - d + d * (float) Math.sin(2 * Math.PI * phase);
    phase += step;
    phase -= Math.floor(phase);
    xy[0] *= m;
    xy[1] *= m;
  }

}
