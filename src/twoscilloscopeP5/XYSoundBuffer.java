package twoscilloscopeP5;

/**
 * Interleaved audio, standing in for openFrameworks' ofSoundBuffer: frame i, channel c is
 * samples[i * numChannels + c], each from -1 to 1.
 * <p>XYscope format audio has X on channel 0, Y on channel 1 and, optionally, Z (beam
 * blanking) on channel 2.</p>
 */

public class XYSoundBuffer {

  public float[] samples;
  int numChannels;
  int sampleRate;

  public XYSoundBuffer() {
    this(0, 1, 44100);
  }

  public XYSoundBuffer(int numFrames, int _numChannels, int _sampleRate) {
    allocate(numFrames, _numChannels);
    sampleRate = _sampleRate;
  }

  /**
   * Wraps an existing interleaved array, without copying it.
   */
  public XYSoundBuffer(float[] interleaved, int _numChannels, int _sampleRate) {
    samples = interleaved != null ? interleaved : new float[0];
    numChannels = Math.max(1, _numChannels);
    sampleRate = _sampleRate;
  }

  public XYSoundBuffer(XYSoundBuffer other) {
    samples = other.samples.clone();
    numChannels = other.numChannels;
    sampleRate = other.sampleRate;
  }

  public XYSoundBuffer copy() {
    return new XYSoundBuffer(this);
  }

  /**
   * numFrames frames of silence.
   */
  public void allocate(int numFrames, int _numChannels) {
    numChannels = Math.max(1, _numChannels);
    samples = new float[Math.max(0, numFrames) * numChannels];
  }

  public int getNumFrames() {
    return samples.length / numChannels;
  }

  public int getNumChannels() {
    return numChannels;
  }

  /**
   * Changes how the samples are read, not the samples themselves.
   */
  public void setNumChannels(int _numChannels) {
    numChannels = Math.max(1, _numChannels);
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public void setSampleRate(int _sampleRate) {
    sampleRate = _sampleRate;
  }

  /**
   * Length in seconds.
   */
  public float getDuration() {
    return sampleRate > 0 ? getNumFrames() / (float) sampleRate : 0;
  }

  public int size() {
    return samples.length;
  }

  public float get(int frame, int channel) {
    return samples[frame * numChannels + channel];
  }

  public void set(int frame, int channel, float value) {
    samples[frame * numChannels + channel] = value;
  }

  /**
   * Sets every sample, e.g. fill(0) for silence.
   */
  public void fill(float value) {
    java.util.Arrays.fill(samples, value);
  }

  /**
   * One channel as its own array.
   */
  public float[] getChannel(int channel) {
    int n = getNumFrames();
    float[] out = new float[n];
    if (channel < 0 || channel >= numChannels) return out;
    for (int i = 0; i < n; i++) out[i] = samples[i * numChannels + channel];
    return out;
  }

  /**
   * The last numFrames frames (or all of them, if there are fewer).
   */
  public XYSoundBuffer getLastFrames(int numFrames) {
    int m = Math.max(0, Math.min(numFrames, getNumFrames()));
    float[] out = new float[m * numChannels];
    System.arraycopy(samples, samples.length - out.length, out, 0, out.length);
    return new XYSoundBuffer(out, numChannels, sampleRate);
  }

}
