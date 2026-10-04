package twoscilloscopeP5;

/**
 * Folds the signal back at the edges, a kaleidoscope.
 */
public class XYWavefold extends XYEffect {

  public XYParameter gain = addParameter("gain", 2, 1, 8);

  public XYWavefold() {
    super("wavefold");
  }

  public void processFrame(float[] xy) {
    float g = gain.get();
    xy[0] = (float) Math.sin(g * xy[0] * Math.PI / 2);
    xy[1] = (float) Math.sin(g * xy[1] * Math.PI / 2);
  }

}
