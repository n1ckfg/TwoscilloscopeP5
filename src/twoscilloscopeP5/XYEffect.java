package twoscilloscopeP5;

import java.util.ArrayList;

/**
 * Audio effects for XY signals: the tools XYTransformer uses to turn one vector shape into
 * another by treating the shape as sound.
 * <p>Each effect works on X (channel 0) and Y (channel 1) and passes Z through untouched,
 * so a time-based effect moves the beam relative to its blanking, as it would if only X and
 * Y went through a real effects unit. Every setting is an XYParameter in the effect's
 * parameters list, so a sketch can build sliders for a whole chain.</p>
 * <p>What they do to a shape:</p>
 * <pre>
 * XYLowPass       rounds corners and swallows small detail
 * XYHighPass      AC coupling: shapes sag and smear, like a cheap sound card
 * XYChannelDelay  delays X or Y, shearing the shape and opening lines into loops
 * XYEcho          ghost copies from earlier in the loop, blended in
 * XYBitCrush      snaps the beam to a coarse grid
 * XYSampleHold    lowers the sample rate: steps, corners and stray dots
 * XYDrive         tanh saturation pushes shapes out towards a rounded square
 * XYWavefold      folds the signal back at the edges, a kaleidoscope
 * XYRingMod       multiplies by a sine: shapes pulse in and out of the center
 * XYNoise         jitter (seeded, so the same settings give the same shape)
 * XYRotate        mixes X and Y with a rotation matrix, optionally spinning
 * </pre>
 * <p>Write your own by extending XYEffect and overriding processFrame(), which gets each
 * frame as xy[0] (X) and xy[1] (Y) and changes them in place:</p>
 * <pre>
 * class Mirror extends XYEffect {
 *   Mirror() { super("mirror"); }
 *   public void processFrame(float[] xy) { xy[0] = Math.abs(xy[0]); }
 * }
 * </pre>
 */

public abstract class XYEffect {

  public boolean enabled = true;
  public ArrayList<XYParameter> parameters = new ArrayList<XYParameter>();

  String name;
  protected float sampleRate = 44100;
  float[] frame = new float[2];

  public XYEffect(String _name) {
    name = _name;
  }

  public String getName() {
    return name;
  }

  public XYParameter getParameter(String parameterName) {
    for (XYParameter p : parameters) {
      if (p.getName().equals(parameterName)) return p;
    }
    return null;
  }

  protected XYParameter addParameter(String parameterName, float value, float min, float max) {
    XYParameter p = new XYParameter(parameterName, value, min, max);
    parameters.add(p);
    return p;
  }

  protected XYParameter addIntParameter(String parameterName, int value, int min, int max) {
    XYParameter p = new XYParameter(parameterName, value, min, max, true);
    parameters.add(p);
    return p;
  }

  /**
   * Process channels 0 and 1 of an interleaved buffer in place.
   * Does nothing while the effect is disabled.
   */
  public void process(XYSoundBuffer buffer) {
    if (!enabled) return;
    int nCh = buffer.getNumChannels();

    sampleRate = buffer.getSampleRate() > 0 ? buffer.getSampleRate() : 44100;
    prepare(sampleRate);

    float[] samples = buffer.samples;
    int n = buffer.getNumFrames();
    for (int i = 0; i < n; i++) {
      int at = i * nCh;
      frame[0] = samples[at];
      frame[1] = nCh > 1 ? samples[at + 1] : 0;
      processFrame(frame);
      samples[at] = frame[0];
      if (nCh > 1) samples[at + 1] = frame[1];
    }
  }

  /**
   * Clear filter memory, delay lines and oscillators.
   */
  public void reset() {
  }

  /**
   * Called once per buffer before the samples, with the buffer's sample rate.
   */
  public void prepare(float sampleRate) {
  }

  /**
   * One frame: xy[0] is X, xy[1] is Y. Change them in place.
   */
  public abstract void processFrame(float[] xy);

}
