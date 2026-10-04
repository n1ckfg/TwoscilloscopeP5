package twoscilloscopeP5;

/**
 * Rounds corners and swallows small detail.
 */
public class XYLowPass extends XYEffect {

  public XYParameter cutoff = addParameter("cutoff", 2000, 20, 20000);
  public XYParameter resonance = addParameter("resonance", 0.707f, 0.3f, 10);

  XYBiquad filterX = new XYBiquad();
  XYBiquad filterY = new XYBiquad();

  public XYLowPass() {
    super("low pass");
  }

  public void reset() {
    filterX.reset();
    filterY.reset();
  }

  public void prepare(float sr) {
    filterX.lowPass(cutoff.get(), resonance.get(), sr);
    filterY.lowPass(cutoff.get(), resonance.get(), sr);
  }

  public void processFrame(float[] xy) {
    xy[0] = filterX.process(xy[0]);
    xy[1] = filterY.process(xy[1]);
  }

}
