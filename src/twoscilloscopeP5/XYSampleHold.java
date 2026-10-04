package twoscilloscopeP5;

/**
 * Lowers the sample rate: steps, corners and stray dots.
 */
public class XYSampleHold extends XYEffect {

  public XYParameter rate = addParameter("rate (Hz)", 2000, 50, 48000);

  double phase = 1;
  double step = 0.1;
  float heldX = 0, heldY = 0;

  public XYSampleHold() {
    super("sample & hold");
  }

  public void reset() {
    phase = 1;
    heldX = heldY = 0;
  }

  public void prepare(float sr) {
    step = rate.get() / sr;
  }

  public void processFrame(float[] xy) {
    if (phase >= 1) {
      phase -= Math.floor(phase);
      heldX = xy[0];
      heldY = xy[1];
    }
    phase += step;
    xy[0] = heldX;
    xy[1] = heldY;
  }

}
