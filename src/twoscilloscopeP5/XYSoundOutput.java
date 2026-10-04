package twoscilloscopeP5;

/**
 * Something that fills audio buffers for an XYSoundStream, like
 * openFrameworks' ofBaseSoundOutput. Called on the audio thread.
 */
public interface XYSoundOutput {
  void audioOut(XYSoundBuffer buffer);
}
