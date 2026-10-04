package twoscilloscopeP5;

/**
 * tanh saturation pushes shapes out towards a rounded square.
 */
public class XYDrive extends XYEffect {

  public XYParameter gain = addParameter("gain", 3, 1, 20);

  float norm = 1;

  public XYDrive() {
    super("drive");
  }

  public void prepare(float sr) {
    norm = 1.0f / (float) Math.tanh(gain.get());
  }

  public void processFrame(float[] xy) {
    float g = gain.get();
    xy[0] = (float) Math.tanh(g * xy[0]) * norm;
    xy[1] = (float) Math.tanh(g * xy[1]) * norm;
  }

}
