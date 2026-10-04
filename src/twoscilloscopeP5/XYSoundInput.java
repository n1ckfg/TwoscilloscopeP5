package twoscilloscopeP5;

/**
 * Something that takes audio buffers from an XYSoundStream, like
 * openFrameworks' ofBaseSoundInput. Called on the audio thread.
 */
public interface XYSoundInput {
  void audioIn(XYSoundBuffer buffer);
}
