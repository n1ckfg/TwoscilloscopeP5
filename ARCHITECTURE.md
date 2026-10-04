# TwoscilloscopeP5 Architecture

## Overview
TwoscilloscopeP5 is a Processing library that enables seamless conversion between vector shapes and oscilloscope audio. It is a Java port of the `ofxTwoscilloscope` openFrameworks addon, which itself combines features from two key projects: `XYscope` (vectors to audio) and `Oscilloscope` (audio to vectors).

The library allows users to draw vector shapes, output them as audio to drive a hardware oscilloscope or Vectrex, visualize audio input as an analog CRT scope, and apply audio effects to vector shapes by converting them back and forth between domains.

## Core Components

The architecture is divided into three primary pipelines, backed by a custom audio subsystem.

### 1. Vectors to Audio (`XYscope.java`)
`XYscope` is responsible for converting 2D/3D vector drawing commands into audio waveforms ("XYscope format" audio). 
- **Drawing API**: It maintains a local transform stack (`matrixStack`) and accumulates shapes (as `ArrayList<PVector>`) via commands like `line()`, `rect()`, `vertex()`. 
- **Waveform Generation**: The `buildWaves()` method processes collected shapes, converting coordinates to audio samples. X and Y are mapped to the left and right audio channels, respectively, and an optional Z channel handles beam blanking.
- **Oscillators**: Uses `XYWavetable` to cycle through the generated waveforms at a specified frequency (e.g., 50Hz) and outputs them to an `XYSoundBuffer`.

### 2. Audio to Vectors (`Oscilloscope.java` & `XYDecoder.java`)
This half of the library visualizes audio signals as an analog oscilloscope would, and decodes them back into vector data.
- **Rendering (`Oscilloscope.java` & `OsciMesh.java`)**: `Oscilloscope` takes audio input, upsamples it to a high visual sample rate (e.g., 192kHz) using `StreamResampler`, and draws the beam using a shader (`osci.vert` / `osci.frag`) into a fading offscreen buffer (P2D/P3D) to simulate CRT glow and afterglow.
- **Decoding (`XYDecoder.java`)**: Reconstructs vectors from the raw audio buffer. It detects the loop period (pitch) and translates the X/Y (and Z) audio channels back into a series of continuous `XYPolyline` strokes.

### 3. Transformation (`XYTransformer.java`)
`XYTransformer` bridges the encoding and decoding sides to allow vector shapes to be distorted via audio effects before being displayed on the canvas.
- **Pipeline**: `shape -> XYscope (encode) -> XY audio -> XYEffectChain -> altered audio -> XYDecoder (decode) -> new shape`
- **Effects (`XYEffect.java`)**: The audio buffer is passed through a chain of digital signal processing effects (e.g., `XYLowPass`, `XYBitCrush`, `XYEcho`, `XYRingMod`). The audio loops several times (`settleCycles`) to allow effects like delays and filters to settle into a steady state before decoding the final cycle back to the screen.

## Audio Subsystem

### `XYSoundStream.java`
A custom sound card stream manager built natively on Java's `javax.sound.sampled`, requiring no external libraries (replacing tools like Minim or `ofSoundStream`). 
- Manages dedicated high-priority input and output threads.
- Streams audio as 16-bit PCM (critical for maintaining DC-coupled signals for oscilloscopes).
- Interfaces via `XYSoundInput` and `XYSoundOutput` listeners. By default, it dynamically invokes the host Processing sketch's `audioIn()` and `audioOut()` methods using reflection.

### `XYSoundBuffer.java`
A robust container for audio data (interleaved float samples) passed between the stream, encoders, decoders, and effects.

## Fonts & Shapes
- **`HersheyFont.java`**: Built-in support for 32 single-stroke vector fonts (Hershey fonts), allowing text to be cleanly encoded into oscilloscope audio without the need for external font files or assets.
- **`XYPolyline.java`**: Represents the decoded continuous vector paths. Replaces `ofPolyline` from the C++ version.

## Design Philosophy
- **Processing Idioms**: The port strongly aligns with Processing conventions—using `PVector`, `PMatrix3D`, `PApplet` constants, and adhering to standard sketch data folder input/output patterns.
- **Independence**: Operations can run silently (headless or offline via `.render()`) without an active soundcard or graphics context.
- **Modularity**: Components (`XYscope`, `Oscilloscope`, `XYDecoder`) can be used completely independently or seamlessly chained together.
