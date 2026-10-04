package twoscilloscopeP5;

import java.util.Random;

/**
 * Jitter, seeded so the same settings give the same shape.
 */
public class XYNoise extends XYEffect {

  public XYParameter amount = addParameter("amount", 0.02f, 0, 0.5f);
  public XYParameter seed = addIntParameter("seed", 1, 0, 1000);

  Random rng = new Random();

  public XYNoise() {
    super("noise");
    reset();
  }

  public void reset() {
    rng.setSeed(seed.getInt());
  }

  public void processFrame(float[] xy) {
    float a = amount.get();
    xy[0] += (rng.nextFloat() * 2 - 1) * a;
    xy[1] += (rng.nextFloat() * 2 - 1) * a;
  }

}
