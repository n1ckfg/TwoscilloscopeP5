package twoscilloscopeP5;

import java.util.ArrayList;

/**
 * Effects run in order, each over the whole buffer.
 */
public class XYEffectChain {

  public ArrayList<XYEffect> effects = new ArrayList<XYEffect>();

  /**
   * Adds an effect to the end of the chain and hands it back, so
   * XYLowPass lowPass = chain.add(new XYLowPass());
   */
  public <T extends XYEffect> T add(T effect) {
    if (effect != null) effects.add(effect);
    return effect;
  }

  public XYEffect get(int i) {
    return effects.get(i);
  }

  public int size() {
    return effects.size();
  }

  public void clear() {
    effects.clear();
  }

  /**
   * run every enabled effect, in order
   */
  public void process(XYSoundBuffer buffer) {
    for (XYEffect effect : effects) effect.process(buffer);
  }

  public void reset() {
    for (XYEffect effect : effects) effect.reset();
  }

}
