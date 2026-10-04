package twoscilloscopeP5;

/**
 * Ghost copies from earlier in the loop, blended in.
 */
public class XYEcho extends XYEffect {

  public XYParameter time = addParameter("time (ms)", 5, 0.1f, 1000);
  public XYParameter feedback = addParameter("feedback", 0.5f, 0, 0.95f);
  public XYParameter mix = addParameter("mix", 0.5f, 0, 1);

  XYDelayLine lineX = new XYDelayLine();
  XYDelayLine lineY = new XYDelayLine();
  float samples = 1;

  public XYEcho() {
    super("echo");
  }

  public void reset() {
    lineX.reset();
    lineY.reset();
  }

  public void prepare(float sr) {
    int maxSamples = (int) Math.ceil(time.getMax() / 1000.0f * sr) + 2;
    lineX.setup(maxSamples);
    lineY.setup(maxSamples);
    samples = Math.max(1.0f, time.get() / 1000.0f * sr);
  }

  public void processFrame(float[] xy) {
    float fb = feedback.get(), m = mix.get();
    float dx = lineX.read(samples);
    float dy = lineY.read(samples);
    lineX.write(xy[0] + fb * dx);
    lineY.write(xy[1] + fb * dy);
    xy[0] = (1 - m) * xy[0] + m * dx;
    xy[1] = (1 - m) * xy[1] + m * dy;
  }

}
