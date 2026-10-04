# TwoscilloscopeP5
Vector shapes to oscilloscope audio and back again in Processing, tested with Processing 4.5.7.<br>
A port of the [ofxTwoscilloscope](https://github.com/n1ckfg/ofxTwoscilloscope) openFrameworks addon.

Two oscilloscope projects, joined at the audio:

* **[XYscope](https://teddavis.org/xyscope)** by Ted Davis, a Processing library that
  turns vector drawings into audio for analog vector displays, became `XYscope`.
* **[Oscilloscope](https://github.com/kritzikratzi/Oscilloscope)** by Hansi Raber, an
  openFrameworks app that renders XY audio the way an analog scope does, became
  `Oscilloscope`, plus `XYDecoder` to recover the vector shapes from the audio.

With both halves in one place there's a third thing to do: `XYTransformer` turns a
vector shape into a new vector shape by encoding it as audio, running the audio through
effects, and decoding it again.

```java
import twoscilloscopeP5.*;
```

No other libraries are needed: sound goes through `javax.sound.sampled`, and the Hershey
fonts are built in.

## XYscope format audio

A drawing becomes one loop of stereo audio, repeated `freq()` times a second (50 by
default): X on the left channel, Y on the right, each from -1 to 1 with +Y up. A third
channel, Z, blanks the beam (-1) while it travels between shapes (+1 is on). A canvas
point maps to audio as

    x = 2 * px / width - 1        y = 1 - 2 * py / height

Feed the left and right channels of a DC-coupled sound card into an oscilloscope in X-Y
mode, a modded Vectrex or a laser and the drawing appears.

## 1. Vectors to audio: `XYscope`

```java
XYscope xy;

void setup() {
  size(512, 512);
  xy = new XYscope(this);   // canvas = sketch size, 44.1kHz, 512 sample waves
  xy.openAudioOut();        // the default sound card
}

void draw() {
  background(0);
  xy.clearWaves();
  xy.circle(width / 2, height / 2, 300);
  xy.textSize(48);
  xy.text("hello", 40, 40);
  xy.buildWaves();

  xy.drawXY();              // a preview of what the scope will show
}
```

The drawing API follows XYscope and Processing: `point`, `line`, `rect`, `square`,
`ellipse`, `circle`, `lissajous`, `beginShape`/`vertex`/`endShape(CLOSE)`, 3D `box`,
`sphere`, `ellipsoid` and `torus`, a transform stack (`pushMatrix`, `translate`, `rotate`,
`rotateX`...), and text in 32 single stroke Hershey fonts (`XYscope.fonts()`).
`polyline()` takes an `XYPolyline` and `shape()` takes a `PShape` made of vertices,
such as an SVG from `loadShape()`. `freq()`, `amp()`, `steps()`, `waveSize()`,
`limitPoints()`, `limitPath()`, `zRange()` and `vectrex()` work as they do in XYscope.
XYscope doesn't draw to the screen itself, so it works with any renderer.

Other ways to get the audio out:

```java
stream.setOutListener(xy);                 // an XYSoundStream of your own
xy.audioOut(buffer);                       // from your own audioOut()
xy.audioOutAdd(buffer);                    // mix several XYscopes, for additive synthesis
XYSoundBuffer audio = xy.render(4);        // 4 seconds, offline
xy.recorderBegin(); ... xy.recorderEnd();  // record to XYscope_<date>.wav in the sketch folder
xy.process(1 / frameRate);                 // no sound card: run on the clock
```

`openAudioOut(-1, 3)` sends Z on a third channel, and `render(seconds, 3)` makes
3-channel audio for `WavFile.save()`, which `Oscilloscope` reads as brightness.
`XYscope.listDevices()` prints the sound cards and their ids.

## 2. Audio to vectors: `Oscilloscope` and `XYDecoder`

```java
Oscilloscope scope;
XYPlayer player;
XYSoundStream stream;
ArrayList<XYPolyline> shapes;

void setup() {
  size(512, 512, P2D);              // the beam is drawn with a shader: P2D or P3D
  scope = new Oscilloscope(this);   // the sketch's size
  player = new XYPlayer(this);
  player.load("xyscope.wav");
  player.setScope(scope);
  player.play();
  stream = new XYSoundStream(this); // calls the sketch's audioOut()
  stream.setup(2, 0, 44100, 512);   // 2 channels out, 0 in
}

void audioOut(XYSoundBuffer buffer) {
  player.audioOut(buffer);          // to the sound card, and to the scope
}

void draw() {
  scope.update();
  shapes = scope.getShapes(512, 512);
  scope.draw();
}
```

`Oscilloscope` takes audio from any thread (`addBuffer()`, or make it the input listener
of an `XYSoundStream`), upsamples it to 192kHz and draws it with the original app's
gaussian beam shader into a fading offscreen buffer, for the glow and afterglow of a CRT.
1, 2, 3 and 4 channel audio draw as Y-T, X-Y, X-Y with brightness, and a red/cyan pair,
as they did in the app. The settings are plain fields: `strokeWeight`, `intensity`,
`afterglow`, `hue`, `scale`, `invertX`, `invertY`, `flipXY`, `zModulation`.

`getShapes()` hands the most recent audio to `XYDecoder`, which finds the loop (from
`decoderSettings.freq` if you know it, otherwise with the YIN pitch detector), maps one
loop of samples back to the canvas, and breaks it into strokes where Z blanks the beam or
the beam jumps. Use `XYDecoder` directly on any buffer:

```java
XYSoundBuffer audio = WavFile.load(this, "drawing.wav");
XYDecoderSettings settings = new XYDecoderSettings(512, 512);
ArrayList<XYPolyline> shapes = XYDecoder.decode(audio, settings);
XYDecoder.saveSvg(this, "drawing.svg", shapes, 512, 512);
```

## 3. Vectors to audio to vectors: `XYTransformer`

```java
XYTransformer transformer;

void setup() {
  transformer = new XYTransformer(512, 512);   // canvas, 44.1kHz, 50Hz loop
  transformer.effects.add(new XYLowPass()).cutoff.set(1200);
  transformer.effects.add(new XYChannelDelay()).delayY.set(0.5);
}

void draw() {
  altered = transformer.transform(shapes);
}
```

The shapes are encoded exactly as `XYscope` would play them, the audio runs through the
effect chain for a few loops so filters and echoes settle, and the last loop is decoded
back onto the same canvas. `getProcessedWaves()` is that loop of altered audio, ready for
`XYscope.setWaveforms()`, so you can hear (or scope) exactly the shape you see.

| Effect | What it does to a shape |
| --- | --- |
| `XYLowPass` | rounds corners and swallows small detail |
| `XYHighPass` | AC coupling: shapes sag and smear, like a cheap sound card |
| `XYChannelDelay` | delays X or Y, shearing the shape and opening lines into loops |
| `XYEcho` | ghost copies from earlier in the loop |
| `XYBitCrush` | snaps the beam to a coarse grid |
| `XYSampleHold` | lowers the sample rate: steps, corners and stray dots |
| `XYDrive` | tanh saturation pushes shapes out towards a rounded square |
| `XYWavefold` | folds the signal back at the edges, a kaleidoscope |
| `XYRingMod` | multiplies by a sine: shapes pulse in and out of the center |
| `XYNoise` | jitter, seeded so the same settings give the same shape |
| `XYRotate` | mixes X and Y with a rotation matrix, optionally spinning |

Every setting is an `XYParameter` (a name, a value and a range) in the effect's
`parameters` list, so a sketch can build sliders for a whole chain; the Transform example
does. Extend `XYEffect` and override `processFrame()` for your own:

```java
class Mirror extends XYEffect {
  Mirror() { super("mirror"); }
  void processFrame(float[] xy) { xy[0] = abs(xy[0]); }   // xy[0] is X, xy[1] is Y
}
```

## Examples

* **Encode**: shapes, Hershey text, a 3D torus or a mouse drawing, played out of the sound
  card as XYscope audio, with the wavetables and output alongside. `r` records, `e`
  exports 10 seconds offline as a 3-channel WAV.
* **Decode**: plays `data/xyscope.wav` (or any WAV you open with `o`, or the line input
  with `i`) through the beam renderer, and decodes it into vector shapes beside it. `s`
  saves them as SVG.
* **Transform**: a shape, the same shape as audio through the effects (as a beam), and the
  shape decoded from that audio. Pick sources with `1`-`4` (or draw in the first panel),
  solo effects with `e`, tweak them in the panel, and press `a` to feed the result back in
  for another generation. `s` saves SVG, `w` saves WAV.
* **LatkScope**: a 3D [Latk](https://github.com/LightningArtist/latkProcessing) animation,
  seen through a PeasyCam camera, is encoded frame by frame as XYscope audio, run through
  the effects and drawn back as the beam or as decoded strokes, each in its stroke's colour.
  `l` switches between those and the original lines, and the panel, `e` and `n` work as in
  Transform. Needs the Latk for Processing and PeasyCam libraries.

Without a sound card the examples keep running silently, on the clock.

## Differences from ofxTwoscilloscope

The numbers match the addon's: the same shapes give the same wavetables, decoded strokes,
effects and beam meshes, to within float rounding. What changed is the plumbing:

* `ofSoundStream` is replaced by `XYSoundStream`, built on `javax.sound.sampled`. It
  calls the sketch's `audioOut(XYSoundBuffer)` and `audioIn(XYSoundBuffer)` methods by
  default, or any `XYSoundOutput` / `XYSoundInput`. Samples go to the sound card as 16-bit
  PCM.
* `ofSoundBuffer`, `ofPolyline` and `ofParameter` are replaced by `XYSoundBuffer`,
  `XYPolyline` and `XYParameter`; `ofPath` input by `PShape`. Alignment, rect mode and
  close modes take Processing's constants (`CENTER`, `TOP`, `CLOSE`...).
* All 32 Hershey fonts are built into the library, so there's no data folder to copy.
* Files are read with Processing's paths (the data folder, or an absolute path) and
  written to the sketch folder, Processing's habit, rather than to `bin/data`.
* The beam renderer draws its mesh with one vertex buffer through Processing's low-level
  PGL, as the addon's `ofMesh` did, so `Oscilloscope` needs a P2D or P3D sketch.
* The Transform and LatkScope examples build their own small effect panel in place of
  ofxGui, and LatkScope uses PeasyCam in place of ofEasyCam. example-latk is called
  LatkScope here because a sketch named Latk would hide latkProcessing's `Latk` class.

The addon's own [differences from the originals](https://github.com/n1ckfg/ofxTwoscilloscope#differences-from-the-originals)
still apply: Minim is gone, Z goes out as a third channel, XYscope keeps its own transform
stack, Hershey glyphs use their bearings, WAV is the only file format, and laser RGB
output isn't ported.

## Building

`src/build.sh` (Linux and macOS) or `src\build.bat` (Windows) compiles
`library/TwoscilloscopeP5.jar` against Processing's `core.jar`, which they look for in the
usual install locations; set `CORE_JAR` to point at it otherwise. `src/build.sh --docs`
also writes the javadoc to `reference/`. On Linux, Processing only finds the library if the folder and the
jar share a name exactly: `libraries/TwoscilloscopeP5/library/TwoscilloscopeP5.jar`.

## License

LGPL v3, since `XYscope`, `XYWavetable` and `HersheyFont` are ports of XYscope (LGPL v3).
The code ported from Oscilloscope is also under its MIT license, and the Hershey font data
carries its own acknowledgements. See [LICENSE.txt](LICENSE.txt).
