package twoscilloscopeP5;

/**
 * Mixes X and Y with a rotation matrix, optionally spinning.
 */
public class XYRotate extends XYEffect {

  public XYParameter angle = addParameter("angle", 30, -180, 180);           // degrees
  public XYParameter spin = addParameter("spin (deg/s)", 0, -3600, 3600);    // degrees per second

  double time = 0;
  double dt = 0;

  public XYRotate() {
    super("rotate");
  }

  public void reset() {
    time = 0;
  }

  public void prepare(float sr) {
    dt = 1.0 / sr;
  }

  public void processFrame(float[] xy) {
    double theta = Math.toRadians(angle.get() + spin.get() * time);
    time += dt;
    float c = (float) Math.cos(theta), s = (float) Math.sin(theta);
    float rx = xy[0] * c - xy[1] * s;
    float ry = xy[0] * s + xy[1] * c;
    xy[0] = rx;
    xy[1] = ry;
  }

}
