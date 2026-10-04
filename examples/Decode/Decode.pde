// Example 2: XYscope format audio -> vector shapes.
//
// An audio file (or the line input) plays through the Oscilloscope renderer,
// which draws it the way an analog scope would. The same audio is decoded
// back into vector shapes, drawn on the right and saved as SVG.
//
// space play/pause, left/right seek, o open a .wav, i line in, s save svg,
// p points, +/- beam, g glow, h hue, z z-mod

import twoscilloscopeP5.*;

Oscilloscope scope;  // renders the beam, and decodes the shapes
XYPlayer player;     // plays the file, to the sound card and to the scope
XYSoundStream soundStream;
boolean audioOk;
boolean liveInput = false;

ArrayList<XYPolyline> shapes = new ArrayList<XYPolyline>();
float panelSize;
boolean showPoints = false;
String status = "";
int lastMillis;

void setup() {
  size(1024, 600, P2D);
  surface.setTitle("TwoscilloscopeP5: audio -> vectors");
  frameRate(60);
  textFont(createFont("Monospaced", 12));

  // two square panels side by side, with a strip of text underneath
  panelSize = width / 2;

  // the beam renders into a panelSize buffer, from audio upsampled to 192kHz
  scope = new Oscilloscope(this, (int) panelSize, (int) panelSize, 192000);
  scope.decoderSettings.simplify = 0.75; // px; 0 keeps every sample

  player = new XYPlayer(this);
  player.setScope(scope);
  player.setLoop(true);
  loadFile("xyscope.wav");

  // calls this sketch's audioOut() and audioIn()
  soundStream = new XYSoundStream(this);
  audioOk = openSoundStream(false);
  if (!audioOk) status = "no sound card found, playing silently";
  lastMillis = millis();
}

boolean openSoundStream(boolean withInput) {
  // 2 channels out, 2 in (or none), 44.1kHz, 512 frame buffers
  return soundStream.setup(2, withInput ? 2 : 0, 44100, 512);
}

void loadFile(String path) {
  if (player.load(path)) {
    player.play();
    status = "playing " + player.getFilename();
  } else {
    status = "couldn't load " + path + " (press o to open a .wav)";
  }
}

void audioOut(XYSoundBuffer buffer) {
  if (liveInput) {
    buffer.fill(0);
  } else {
    player.audioOut(buffer); // also feeds the scope
  }
}

void audioIn(XYSoundBuffer buffer) {
  if (liveInput) scope.addBuffer(buffer);
}

void draw() {
  int now = millis();
  if (!audioOk && !liveInput) player.update((now - lastMillis) / 1000.0);
  lastMillis = now;

  scope.update();

  // turn the latest loop of audio back into vector shapes
  shapes = scope.getShapes(panelSize, panelSize);

  background(0);

  // left: the audio as an analog scope draws it
  scope.draw(0, 0, panelSize, panelSize);

  // right: the vector shapes decoded from it
  pushMatrix();
  translate(panelSize, 0);
  noStroke();
  fill(12);
  rect(0, 0, panelSize, panelSize);
  stroke(28);
  for (int i = 1; i < 8; i++) {
    line(i * panelSize / 8, 0, i * panelSize / 8, panelSize);
    line(0, i * panelSize / 8, panelSize, i * panelSize / 8);
  }

  int numPoints = 0;
  noFill();
  for (int i = 0; i < shapes.size(); i++) {
    // a different hue per shape, so you can see where the strokes break
    colorMode(HSB, 255);
    stroke((i * 37) % 255, 120, 255);
    colorMode(RGB, 255);
    XYPolyline shape = shapes.get(i);
    shape.draw(g);
    numPoints += shape.size();
    if (showPoints) {
      for (PVector v : shape.getVertices()) circle(v.x, v.y, 3);
    }
  }
  popMatrix();

  // info
  float y = panelSize + 22;
  fill(255);
  text("beam (Oscilloscope)", 12, y);
  text("vector shapes (XYDecoder)", panelSize + 12, y);

  fill(160);
  String source = liveInput ? "line in" :
    player.getFilename() + "  " + nf(player.getPositionMS() / 1000.0, 0, 1) + " / " +
    nf(player.getDurationMS() / 1000.0, 0, 1) + "s  " + player.getNumChannels() + "ch";
  text(source, 12, y + 18);
  float period = scope.getDetectedPeriod();
  String loop = period > 0 ? nf(scope.getSourceSampleRate() / period, 0, 2) + " Hz loop" : "no loop found";
  text(loop + ", " + shapes.size() + " shapes, " + numPoints + " points", panelSize + 12, y + 18);

  text("space play/pause  </> seek  o open  i line in  s save svg  p points  +/- beam  g glow  h hue  z z-mod",
    12, y + 40);
  fill(120);
  text(status, 12, height - 10);
  text(round(frameRate) + " fps, " + scope.getDropped() + " dropped", panelSize + 12, height - 10);
}

void keyPressed() {
  if (key == ' ') {
    player.setPaused(player.isPlaying());
  } else if (key == CODED && keyCode == LEFT) {
    player.setPositionMS(max(0, player.getPositionMS() - 2000));
  } else if (key == CODED && keyCode == RIGHT) {
    player.setPositionMS(player.getPositionMS() + 2000);
  } else if (key == 'o') {
    selectInput("Open a WAV file", "fileSelected");
  } else if (key == 'i') {
    liveInput = !liveInput;
    scope.clear();
    audioOk = openSoundStream(liveInput);
    if (liveInput && !audioOk) {
      liveInput = false;
      audioOk = openSoundStream(false);
      status = "no audio input found";
    } else {
      status = liveInput ? "listening to the line input" : "playing " + player.getFilename();
    }
  } else if (key == 's') {
    String path = "shapes_" + year() + nf(month(), 2) + nf(day(), 2) + "_" + nf(hour(), 2) + nf(minute(), 2) + nf(second(), 2) + ".svg";
    XYDecoder.saveSvg(this, path, shapes, panelSize, panelSize);
    status = "saved " + path;
  } else if (key == 'p') {
    showPoints = !showPoints;
  } else if (key == '+' || key == '=') {
    scope.strokeWeight = min(20, scope.strokeWeight + 1);
  } else if (key == '-') {
    scope.strokeWeight = max(1, scope.strokeWeight - 1);
  } else if (key == 'g') {
    scope.afterglow = scope.afterglow > 0.85 ? 0 : scope.afterglow + 0.15;
  } else if (key == 'h') {
    scope.hue = scope.hue >= 360 ? 0 : scope.hue + 30;
  } else if (key == 'z') {
    scope.zModulation = !scope.zModulation;
  }
}

void fileSelected(File selection) {
  if (selection != null) {
    liveInput = false;
    loadFile(selection.getAbsolutePath());
  }
}
