package twoscilloscopeP5;

/**
 * How XYDecoder turns XY audio back into shapes.
 */
public class XYDecoderSettings {

  /**
   * the canvas the shapes are mapped to
   */
  public float width = 512;
  public float height = 512;

  public float sampleRate = 44100;
  /**
   * loop frequency of the signal (XYscope's freq()), 0 to detect it
   */
  public float freq = 0;
  /**
   * range searched when detecting
   */
  public float minFreq = 20;
  public float maxFreq = 1000;

  /**
   * strokes break where a step is longer than this many pixels...
   */
  public float jumpThreshold = 0;
  /**
   * ...or, when jumpThreshold is 0, longer than jumpFactor x the median step
   */
  public float jumpFactor = 8;

  /**
   * with a Z channel, break the strokes where the beam is blanked
   */
  public boolean useZ = true;
  public float zMin = -1;          // XYscope's blanked level
  public float zMax = 1;           // XYscope's beam-on level
  public float zThreshold = 0.5f;  // 0..1 between zMin and zMax

  /**
   * close strokes whose ends are this close (pixels): 0 for 2.5 x the median step, -1 never
   */
  public float closeThreshold = 0;

  /**
   * Douglas-Peucker tolerance in pixels, 0 keeps every sample
   */
  public float simplify = 0.5f;
  /**
   * strokes with fewer points than this are dropped
   */
  public int minPoints = 2;
  /**
   * shorter strokes than this (in pixels) are dropped
   */
  public float minLength = 0;

  public XYDecoderSettings() {
  }

  public XYDecoderSettings(float _width, float _height) {
    width = _width;
    height = _height;
  }

  public XYDecoderSettings copy() {
    XYDecoderSettings s = new XYDecoderSettings();
    s.width = width;
    s.height = height;
    s.sampleRate = sampleRate;
    s.freq = freq;
    s.minFreq = minFreq;
    s.maxFreq = maxFreq;
    s.jumpThreshold = jumpThreshold;
    s.jumpFactor = jumpFactor;
    s.useZ = useZ;
    s.zMin = zMin;
    s.zMax = zMax;
    s.zThreshold = zThreshold;
    s.closeThreshold = closeThreshold;
    s.simplify = simplify;
    s.minPoints = minPoints;
    s.minLength = minLength;
    return s;
  }

}
