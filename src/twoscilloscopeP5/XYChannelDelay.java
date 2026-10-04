package twoscilloscopeP5;

/**
 * Delays X or Y, shearing the shape and opening lines into loops.
 */
public class XYChannelDelay extends XYEffect {

  public XYParameter delayX = addParameter("delay x (ms)", 0, 0, 20);
  public XYParameter delayY = addParameter("delay y (ms)", 2, 0, 20);

  XYDelayLine lineX = new XYDelayLine();
  XYDelayLine lineY = new XYDelayLine();
  float samplesX = 1, samplesY = 1;

  public XYChannelDelay() {
    super("channel delay");
  }

  public void reset() {
    lineX.reset();
    lineY.reset();
  }

  public void prepare(float sr) {
    int maxSamples = (int) Math.ceil(delayX.getMax() / 1000.0f * sr) + 2;
    lineX.setup(maxSamples);
    lineY.setup(maxSamples);
    samplesX = delayX.get() / 1000.0f * sr;
    samplesY = delayY.get() / 1000.0f * sr;
  }

  public void processFrame(float[] xy) {
    lineX.write(xy[0]);
    lineY.write(xy[1]);
    // the newest sample is a delay of 1, so shift by one to make 0 ms a straight pass
    if (samplesX > 0) xy[0] = lineX.read(samplesX + 1);
    if (samplesY > 0) xy[1] = lineY.read(samplesY + 1);
  }

}
