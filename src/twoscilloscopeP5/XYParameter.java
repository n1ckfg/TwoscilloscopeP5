package twoscilloscopeP5;

/**
 * A named setting with a range, standing in for openFrameworks' ofParameter. Every effect
 * setting is one of these, so a sketch can build sliders for a whole effect chain from
 * XYEffect.parameters (or hand them to ControlP5). Like ofParameter, set() doesn't clamp to
 * the range; the range is a hint for user interfaces.
 */

public class XYParameter {

  String name;
  float value;
  float min;
  float max;
  boolean integer;

  public XYParameter(String _name, float _value, float _min, float _max) {
    this(_name, _value, _min, _max, false);
  }

  public XYParameter(String _name, float _value, float _min, float _max, boolean _integer) {
    name = _name;
    min = _min;
    max = _max;
    integer = _integer;
    set(_value);
  }

  public String getName() {
    return name;
  }

  public float get() {
    return value;
  }

  public int getInt() {
    return Math.round(value);
  }

  public void set(float _value) {
    value = integer ? Math.round(_value) : _value;
  }

  public float getMin() {
    return min;
  }

  public float getMax() {
    return max;
  }

  /**
   * Whole numbers only, like an ofParameter&lt;int&gt;.
   */
  public boolean isInteger() {
    return integer;
  }

  /**
   * The value as 0..1 across the range, for sliders.
   */
  public float getNormalized() {
    return max != min ? (value - min) / (max - min) : 0;
  }

  public void setNormalized(float t) {
    t = Math.max(0, Math.min(1, t));
    set(min + t * (max - min));
  }

  public String toString() {
    return name + ": " + (integer ? String.valueOf(getInt()) : String.valueOf(value));
  }

}
