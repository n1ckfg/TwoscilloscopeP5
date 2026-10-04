package twoscilloscopeP5;

/**
 * AC coupling: shapes sag and smear, like a cheap sound card.
 */
public class XYHighPass extends XYEffect {

  public XYParameter cutoff = addParameter("cutoff", 60, 1, 2000);
  public XYParameter resonance = addParameter("resonance", 0.707f, 0.3f, 10);

  XYBiquad filterX = new XYBiquad();
  XYBiquad filterY = new XYBiquad();

  public XYHighPass() {
    super("high pass");
  }

  public void reset() {
    filterX.reset();
    filterY.reset();
  }

  public void prepare(float sr) {
    filterX.highPass(cutoff.get(), resonance.get(), sr);
    filterY.highPass(cutoff.get(), resonance.get(), sr);
  }

  public void processFrame(float[] xy) {
    xy[0] = filterX.process(xy[0]);
    xy[1] = filterY.process(xy[1]);
  }

}
