package twoscilloscopeP5;

import processing.core.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import javax.sound.sampled.*;

/**
 * A sound card stream, standing in for openFrameworks' ofSoundStream, built on
 * javax.sound.sampled so it needs no other libraries.
 * <p>The output side runs its own thread that asks a listener to fill each buffer and
 * writes it to the sound card; the input side reads buffers from the sound card and hands
 * them to a listener. Listeners are XYSoundOutput and XYSoundInput objects (XYscope,
 * XYPlayer and Oscilloscope are all one or the other), or, by default, the sketch's own
 * methods:</p>
 * <pre>
 * XYSoundStream stream;
 *
 * void setup() {
 *   stream = new XYSoundStream(this);
 *   stream.setup(2, 0, 44100, 512);  // 2 out, 0 in
 * }
 *
 * void audioOut(XYSoundBuffer buffer) {
 *   player.audioOut(buffer);         // runs on the audio thread
 * }
 * </pre>
 * <p>Add void audioIn(XYSoundBuffer buffer) to the sketch and open input channels to listen
 * to the line in. Samples go out as 16-bit PCM, so XYscope format audio needs a DC-coupled
 * sound card to reach a scope intact. Device ids are indexes into listDevices(), -1 for the
 * default device.</p>
 */

public class XYSoundStream {

  PApplet parent;
  XYSoundOutput outListener;
  XYSoundInput inListener;
  int outDevice = -1;
  int inDevice = -1;

  int numOutputChannels = 0;
  int numInputChannels = 0;
  int sampleRate = 44100;
  int bufferSize = 512;
  int numBuffers = 4;

  SourceDataLine outLine;
  TargetDataLine inLine;
  Thread outThread;
  Thread inThread;
  volatile boolean running = false;

  public XYSoundStream() {
  }

  /**
   * With a PApplet, the stream closes when the sketch does, and calls the
   * sketch's audioOut() and audioIn() methods unless it's given other listeners.
   */
  public XYSoundStream(PApplet _parent) {
    parent = _parent;
    if (parent != null) parent.registerMethod("dispose", this);
  }

  public void setOutListener(XYSoundOutput listener) {
    outListener = listener;
  }

  public void setInListener(XYSoundInput listener) {
    inListener = listener;
  }

  public void setOutDevice(int deviceId) {
    outDevice = deviceId;
  }

  public void setOutDevice(String name) {
    outDevice = findDevice(name);
  }

  public void setInDevice(int deviceId) {
    inDevice = deviceId;
  }

  public void setInDevice(String name) {
    inDevice = findDevice(name);
  }

  /**
   * Buffers queued in the sound card: more is steadier, fewer is quicker.
   */
  public void setNumBuffers(int _numBuffers) {
    numBuffers = Math.max(1, _numBuffers);
  }

  public boolean setup(int _numOutputChannels, int _numInputChannels) {
    return setup(_numOutputChannels, _numInputChannels, 44100, 512);
  }

  /**
   * Opens the sound card. Returns false (and closes everything) if any
   * requested output or input couldn't be opened.
   */
  public boolean setup(int _numOutputChannels, int _numInputChannels, int _sampleRate, int _bufferSize) {
    close();
    numOutputChannels = Math.max(0, _numOutputChannels);
    numInputChannels = Math.max(0, _numInputChannels);
    sampleRate = Math.max(1000, _sampleRate);
    bufferSize = Math.max(16, _bufferSize);

    try {
      if (numOutputChannels > 0) {
        if (outListener == null) outListener = sketchOutput();
        AudioFormat format = new AudioFormat(sampleRate, 16, numOutputChannels, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        outLine = (SourceDataLine) (outDevice >= 0 ? getMixer(outDevice).getLine(info) : AudioSystem.getLine(info));
        outLine.open(format, bufferSize * numBuffers * format.getFrameSize());
      }
      if (numInputChannels > 0) {
        if (inListener == null) inListener = sketchInput();
        AudioFormat format = new AudioFormat(sampleRate, 16, numInputChannels, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        inLine = (TargetDataLine) (inDevice >= 0 ? getMixer(inDevice).getLine(info) : AudioSystem.getLine(info));
        inLine.open(format, bufferSize * numBuffers * format.getFrameSize());
      }
    } catch (Exception e) {
      System.err.println("XYSoundStream: couldn't open the sound card: " + e.getMessage());
      close();
      return false;
    }

    running = true;
    if (outLine != null) {
      outLine.start();
      outThread = new Thread(this::runOutput, "XYSoundStream output");
      outThread.setDaemon(true);
      outThread.setPriority(Thread.MAX_PRIORITY);
      outThread.start();
    }
    if (inLine != null) {
      inLine.start();
      inThread = new Thread(this::runInput, "XYSoundStream input");
      inThread.setDaemon(true);
      inThread.setPriority(Thread.MAX_PRIORITY);
      inThread.start();
    }
    return true;
  }

  public void close() {
    running = false;
    if (outLine != null) {
      outLine.stop();
      outLine.flush();
      outLine.close();
    }
    if (inLine != null) {
      inLine.stop();
      inLine.flush();
      inLine.close();
    }
    join(outThread);
    join(inThread);
    outLine = null;
    inLine = null;
    outThread = null;
    inThread = null;
  }

  public void dispose() {
    close();
  }

  public boolean isOutputOpen() {
    return outLine != null && outLine.isOpen();
  }

  public boolean isInputOpen() {
    return inLine != null && inLine.isOpen();
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public int getBufferSize() {
    return bufferSize;
  }

  public int getNumOutputChannels() {
    return numOutputChannels;
  }

  public int getNumInputChannels() {
    return numInputChannels;
  }

  // ---------------------------------------------------------------- audio threads

  void runOutput() {
    int nCh = numOutputChannels;
    XYSoundBuffer buffer = new XYSoundBuffer(bufferSize, nCh, sampleRate);
    byte[] bytes = new byte[bufferSize * nCh * 2];
    SourceDataLine line = outLine;

    while (running) {
      if (buffer.samples.length != bufferSize * nCh) buffer.allocate(bufferSize, nCh);
      buffer.fill(0);
      XYSoundOutput listener = outListener;
      if (listener != null) {
        try {
          listener.audioOut(buffer);
        } catch (Throwable e) {
          report("audioOut", e);
          outListener = null;
        }
      }
      float[] s = buffer.samples;
      for (int i = 0, b = 0; i < s.length && b + 1 < bytes.length; i++, b += 2) {
        int v = (int) (Math.max(-1, Math.min(1, s[i])) * 32767);
        bytes[b] = (byte) v;
        bytes[b + 1] = (byte) (v >> 8);
      }
      line.write(bytes, 0, bytes.length);
    }
  }

  void runInput() {
    int nCh = numInputChannels;
    byte[] bytes = new byte[bufferSize * nCh * 2];
    TargetDataLine line = inLine;

    while (running) {
      int read = 0;
      while (running && read < bytes.length) {
        int r = line.read(bytes, read, bytes.length - read);
        if (r <= 0) break;
        read += r;
      }
      if (!running || read < bytes.length) continue;

      XYSoundBuffer buffer = new XYSoundBuffer(bufferSize, nCh, sampleRate);
      float[] s = buffer.samples;
      for (int i = 0, b = 0; i < s.length; i++, b += 2) {
        s[i] = ((short) ((bytes[b] & 0xff) | (bytes[b + 1] << 8))) / 32768.0f;
      }
      XYSoundInput listener = inListener;
      if (listener != null) {
        try {
          listener.audioIn(buffer);
        } catch (Throwable e) {
          report("audioIn", e);
          inListener = null;
        }
      }
    }
  }

  void report(String where, Throwable e) {
    if (e instanceof InvocationTargetException && e.getCause() != null) e = e.getCause();
    System.err.println("XYSoundStream: " + where + "() threw an exception and won't be called again:");
    e.printStackTrace();
  }

  static void join(Thread thread) {
    if (thread == null || thread == Thread.currentThread()) return;
    try {
      thread.join(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // ---------------------------------------------------------------- the sketch's own methods

  XYSoundOutput sketchOutput() {
    final Method m = findMethod(parent, "audioOut");
    if (m == null) {
      if (parent != null) System.err.println("XYSoundStream: add void audioOut(XYSoundBuffer buffer) to the sketch, or set an output listener");
      return null;
    }
    return buffer -> {
      try {
        m.invoke(parent, buffer);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e instanceof InvocationTargetException ? e.getCause() : e);
      }
    };
  }

  XYSoundInput sketchInput() {
    final Method m = findMethod(parent, "audioIn");
    if (m == null) {
      if (parent != null) System.err.println("XYSoundStream: add void audioIn(XYSoundBuffer buffer) to the sketch, or set an input listener");
      return null;
    }
    return buffer -> {
      try {
        m.invoke(parent, buffer);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e instanceof InvocationTargetException ? e.getCause() : e);
      }
    };
  }

  static Method findMethod(Object target, String name) {
    if (target == null) return null;
    for (Class<?> c = target.getClass(); c != null && c != PApplet.class && c != Object.class; c = c.getSuperclass()) {
      try {
        Method m = c.getDeclaredMethod(name, XYSoundBuffer.class);
        m.setAccessible(true);
        return m;
      } catch (NoSuchMethodException e) {
      }
    }
    return null;
  }

  // ---------------------------------------------------------------- devices

  static Mixer getMixer(int deviceId) {
    Mixer.Info[] infos = AudioSystem.getMixerInfo();
    if (deviceId < 0 || deviceId >= infos.length) throw new IllegalArgumentException("no audio device " + deviceId);
    return AudioSystem.getMixer(infos[deviceId]);
  }

  static int findDevice(String name) {
    Mixer.Info[] infos = AudioSystem.getMixerInfo();
    for (int i = 0; i < infos.length; i++) {
      if (infos[i].getName().equals(name)) return i;
    }
    System.err.println("XYSoundStream: no audio device named \"" + name + "\", using the default");
    return -1;
  }

  /**
   * Every device, by id. Not all of them have outputs or inputs.
   */
  public static String[] getDeviceNames() {
    Mixer.Info[] infos = AudioSystem.getMixerInfo();
    String[] names = new String[infos.length];
    for (int i = 0; i < infos.length; i++) names[i] = infos[i].getName();
    return names;
  }

  /**
   * Prints the devices that can play or record, with their ids.
   */
  public static void listDevices() {
    Mixer.Info[] infos = AudioSystem.getMixerInfo();
    for (int i = 0; i < infos.length; i++) {
      Mixer mixer = AudioSystem.getMixer(infos[i]);
      ArrayList<String> kinds = new ArrayList<String>();
      if (mixer.isLineSupported(new Line.Info(SourceDataLine.class))) kinds.add("out");
      if (mixer.isLineSupported(new Line.Info(TargetDataLine.class))) kinds.add("in");
      if (!kinds.isEmpty()) System.out.println(i + " = " + infos[i].getName() + " (" + String.join(", ", kinds) + ")");
    }
  }

}
