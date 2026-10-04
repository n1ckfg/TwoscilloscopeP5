package twoscilloscopeP5;

import processing.core.*;
import java.io.File;

/**
 * Plays an XY audio file to the sound card and feeds the same audio to an Oscilloscope,
 * which is the job OsciAvAudioPlayer did in the Oscilloscope app: one stream at the sound
 * card's sample rate, one for the display.
 * <pre>
 * XYPlayer player;
 * Oscilloscope scope;
 * XYSoundStream stream;
 *
 * void setup() {
 *   size(512, 512, P2D);
 *   scope = new Oscilloscope(this);
 *   player = new XYPlayer(this);
 *   player.load("xyscope.wav");
 *   player.setScope(scope);
 *   player.setLoop(true);
 *   player.play();
 *   stream = new XYSoundStream(this);
 *   stream.setOutListener(player);
 *   stream.setup(2, 0, 44100, 512);
 * }
 * </pre>
 * <p>No sound card? Call player.update(seconds) each frame instead and it plays silently,
 * on the clock.</p>
 * <p>Differences from the original:</p>
 * <ul>
 * <li>Files are read whole with WavFile instead of streamed with FFmpeg.</li>
 * <li>The scope gets the file's own samples and upsamples them itself, rather than the
 *     player keeping a second 192kHz stream.</li>
 * </ul>
 */

public class XYPlayer implements XYSoundOutput {

  PApplet parent;
  final Object lock = new Object();
  XYSoundBuffer sound = new XYSoundBuffer();
  String filename = "";
  double position = 0; // in file frames
  boolean playing = false;
  boolean looping = false;
  float volume = 1;
  Oscilloscope scope;

  /**
   * called when playback reaches the end (on the audio thread!)
   */
  public Runnable onEnd;

  public XYPlayer() {
  }

  /**
   * The PApplet resolves file paths like Processing's loadStrings().
   */
  public XYPlayer(PApplet _parent) {
    parent = _parent;
  }

  public boolean load(String path) {
    XYSoundBuffer loaded = WavFile.load(parent, path);
    if (loaded == null) return false;
    setBuffer(loaded, new File(path).getName());
    return true;
  }

  public void setBuffer(XYSoundBuffer buffer) {
    setBuffer(buffer, "");
  }

  public void setBuffer(XYSoundBuffer buffer, String name) {
    synchronized (lock) {
      sound = buffer.copy();
      if (sound.getSampleRate() == 0) sound.setSampleRate(44100);
      filename = name;
      position = 0;
      if (scope != null) scope.clear();
    }
  }

  public void unload() {
    synchronized (lock) {
      sound = new XYSoundBuffer();
      filename = "";
      position = 0;
      playing = false;
    }
  }

  public void setScope(Oscilloscope _scope) {
    synchronized (lock) {
      scope = _scope;
    }
  }

  void feedScope(double from, double to) {
    if (scope == null) return;
    int n = sound.getNumFrames();
    int a = (int) Math.max(0.0, Math.floor(from));
    int b = Math.min(n, (int) Math.max(0.0, Math.floor(to)));
    if (b <= a) return;
    scope.addSamples(sound.samples, a, b - a, sound.getNumChannels(), sound.getSampleRate());
  }

  /**
   * from the audio thread: resamples to the buffer's rate and channels
   */
  public void audioOut(XYSoundBuffer buffer) {
    boolean ended = false;
    synchronized (lock) {
      buffer.fill(0);
      int n = sound.getNumFrames();
      if (!playing || n == 0) return;

      int inCh = sound.getNumChannels();
      int outCh = buffer.getNumChannels();
      double outRate = buffer.getSampleRate() > 0 ? buffer.getSampleRate() : sound.getSampleRate();
      double step = sound.getSampleRate() / outRate;
      float[] in = sound.samples;
      float[] out = buffer.samples;

      double start = position;
      int numFrames = buffer.getNumFrames();
      for (int i = 0; i < numFrames; i++) {
        if (position >= n) {
          feedScope(start, n);
          if (!looping) {
            playing = false;
            ended = true;
            position = n;
            break;
          }
          position -= n;
          start = 0;
        }

        int i0 = (int) position;
        int i1 = i0 + 1 < n ? i0 + 1 : (looping ? 0 : i0);
        float frac = (float) (position - i0);
        for (int c = 0; c < outCh; c++) {
          // mono files go to every channel, extra file channels (Z) only to the scope
          int src = inCh == 1 ? 0 : c;
          if (src >= inCh) continue;
          float a = in[i0 * inCh + src];
          float b = in[i1 * inCh + src];
          out[i * outCh + c] = (a + frac * (b - a)) * volume;
        }
        position += step;
      }
      if (!ended) feedScope(start, position);
    }
    if (ended && onEnd != null) onEnd.run();
  }

  /**
   * without a sound card: advance by this many seconds of playback
   */
  public void update(float seconds) {
    boolean ended = false;
    synchronized (lock) {
      int n = sound.getNumFrames();
      if (!playing || n == 0 || seconds <= 0) return;

      double target = position + seconds * (double) sound.getSampleRate();
      while (target >= n) {
        feedScope(position, n);
        if (!looping) {
          playing = false;
          ended = true;
          position = n;
          break;
        }
        target -= n;
        position = 0;
      }
      if (!ended) {
        feedScope(position, target);
        position = target;
      }
    }
    if (ended && onEnd != null) onEnd.run();
  }

  public void play() {
    synchronized (lock) {
      if (sound.getNumFrames() == 0) return;
      if (position >= sound.getNumFrames()) position = 0;
      playing = true;
    }
  }

  public void stop() {
    synchronized (lock) {
      playing = false;
    }
  }

  public void setPaused(boolean paused) {
    if (paused) stop();
    else play();
  }

  public void setLoop(boolean loop) {
    synchronized (lock) {
      looping = loop;
    }
  }

  public void setVolume(float _volume) {
    synchronized (lock) {
      volume = _volume;
    }
  }

  /**
   * 0..1 through the file
   */
  public void setPosition(float pct) {
    synchronized (lock) {
      position = PApplet.constrain(pct, 0, 1) * sound.getNumFrames();
    }
  }

  public void setPositionMS(int ms) {
    synchronized (lock) {
      position = Math.max(0, Math.min(sound.getNumFrames(), ms / 1000.0 * sound.getSampleRate()));
    }
  }

  public boolean isLoaded() {
    synchronized (lock) {
      return sound.getNumFrames() > 0;
    }
  }

  public boolean isPlaying() {
    synchronized (lock) {
      return playing;
    }
  }

  public boolean getLoop() {
    synchronized (lock) {
      return looping;
    }
  }

  public float getPosition() {
    synchronized (lock) {
      int n = sound.getNumFrames();
      return n > 0 ? (float) (position / n) : 0;
    }
  }

  public int getPositionMS() {
    synchronized (lock) {
      return sound.getSampleRate() > 0 ? (int) (position * 1000.0 / sound.getSampleRate()) : 0;
    }
  }

  public int getDurationMS() {
    synchronized (lock) {
      return sound.getSampleRate() > 0 ? (int) (sound.getNumFrames() * 1000.0 / sound.getSampleRate()) : 0;
    }
  }

  public int getNumChannels() {
    synchronized (lock) {
      return sound.getNumChannels();
    }
  }

  public int getSampleRate() {
    synchronized (lock) {
      return sound.getSampleRate();
    }
  }

  public String getFilename() {
    synchronized (lock) {
      return filename;
    }
  }

  /**
   * the whole file
   */
  public XYSoundBuffer getBuffer() {
    synchronized (lock) {
      return sound.copy();
    }
  }

}
