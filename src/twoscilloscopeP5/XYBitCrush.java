package twoscilloscopeP5;

/**
 * Snaps the beam to a coarse grid.
 */
public class XYBitCrush extends XYEffect {

  public XYParameter bits = addParameter("bits", 4, 1, 16);

  float levels = 8;

  public XYBitCrush() {
    super("bit crush");
  }

  public void prepare(float sr) {
    levels = (float) Math.pow(2.0, bits.get() - 1);
  }

  public void processFrame(float[] xy) {
    xy[0] = round(xy[0] * levels) / levels;
    xy[1] = round(xy[1] * levels) / levels;
  }

  // halves away from zero, like std::round
  static float round(float v) {
    return (float) (Math.signum(v) * Math.floor(Math.abs(v) + 0.5));
  }

}
