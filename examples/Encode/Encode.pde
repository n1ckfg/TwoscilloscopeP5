// Example 1: vector shapes -> XYscope format audio.
//
// Shapes drawn into an XYscope come out of the sound card as X (left) and
// Y (right) audio. Plug that into an oscilloscope in X-Y mode, or record it
// and open it in the Decode example.
//
// 1-4 scenes, f next font, r record, e export 10s offline,
// c clear drawing, d debug view

import twoscilloscopeP5.*;

XYscope xy;
boolean audioOk;

int scene = 0;
String[] sceneNames = { "shapes", "text", "3D", "draw" };
String[] fontNames = { "futural", "scripts", "gothiceng", "timesr", "rowmand", "cursive" };
int fontIndex = 0;
ArrayList<XYPolyline> drawing = new ArrayList<XYPolyline>(); // the mouse drawing, in canvas pixels

float canvasSize;
String status = "";
int lastMillis;

void setup() {
  size(1100, 768, P2D);
  surface.setTitle("TwoscilloscopeP5: vectors -> audio");
  frameRate(60);
  textFont(createFont("Monospaced", 12));

  // the drawing canvas is the square on the left of the window
  canvasSize = height;

  // canvas size, sample rate, buffer size (which is also the wavetable size)
  xy = new XYscope(this, canvasSize, canvasSize, 44100, 512);
  xy.freq(50); // the whole drawing repeats 50 times a second

  // X on the left channel, Y on the right. Pass 3 channels to send Z
  // (beam blanking) on the third, if your sound card has one.
  audioOk = xy.openAudioOut();
  status = audioOk ? "audio out: default device" : "no sound card: running silently";
  lastMillis = millis();
}

// Draws the current scene into an XYscope. The scope and the time are
// arguments, so the same drawing can go to the sound card or to an
// offline export.
void drawScene(XYscope scope, float t) {
  float s = canvasSize;

  switch (scene) {
  case 0:
    // Processing-style primitives
    scope.ellipse(s * 0.28, s * 0.28, s * (0.26 + 0.06 * sin(t * 2)));

    scope.rectMode(CENTER);
    scope.pushMatrix();
    scope.translate(s * 0.72, s * 0.28);
    scope.rotate(t * 0.5);
    scope.rect(0, 0, s * 0.26, s * 0.26);
    scope.popMatrix();

    scope.lissajous(s * 0.28, s * 0.72, s * 0.14, 3, 2, t * 40, 120);

    scope.pushMatrix();
    scope.translate(s * 0.72, s * 0.72);
    scope.rotate(-t);
    scope.beginShape();
    for (int i = 0; i < 5; i++) {
      // a star, every second point of a pentagon
      float a = -HALF_PI + i * TWO_PI * 2 / 5;
      scope.vertex(cos(a) * s * 0.15, sin(a) * s * 0.15);
    }
    scope.endShape(CLOSE);
    scope.popMatrix();
    break;

  case 1:
    // Hershey single stroke fonts
    String fontName = fontNames[fontIndex];
    if (!scope.getFont().getName().equals(fontName)) scope.textFont(fontName);

    scope.textAlign(CENTER, CENTER);
    scope.textSize(s * 0.13);
    scope.text("XYscope", s / 2, s * 0.32);
    scope.textSize(s * 0.09);
    scope.text(nf(hour(), 2) + ":" + nf(minute(), 2) + ":" + nf(second(), 2), s / 2, s * 0.56);
    scope.textSize(s * 0.04);
    scope.text(fontName, s / 2, s * 0.78);
    break;

  case 2:
    // 3D, through the default perspective
    scope.translate(s / 2, s / 2);
    scope.rotateY(t * 0.7);
    scope.rotateX(t * 0.4);
    scope.torus(s * 0.2, s * 0.08, 16, 10);
    break;

  case 3:
    scope.polylines(drawing);
    if (drawing.isEmpty()) {
      if (!scope.getFont().getName().equals("futural")) scope.textFont("futural");
      scope.textAlign(CENTER, CENTER);
      scope.textSize(s * 0.05);
      scope.text("draw with the mouse", s / 2, s / 2);
    }
    break;
  }
}

void draw() {
  // no sound card: keep the oscillators running on the clock, so the
  // previews and the recorder still work
  int now = millis();
  if (!audioOk) xy.process((now - lastMillis) / 1000.0);
  lastMillis = now;

  // build this frame's waves, the way an XYscope sketch does in draw()
  xy.clearWaves();
  drawScene(xy, millis() / 1000.0);
  xy.buildWaves();

  background(0);

  // the canvas: the vector shapes, faintly, under the signal as a scope shows it
  stroke(30);
  noFill();
  rect(0.5, 0.5, canvasSize - 1, canvasSize - 1);
  xy.drawPath(color(255, 60));
  xy.drawXY();

  // the side panel: the audio itself
  float panelX = canvasSize + 16;
  float panelW = width - panelX - 16;
  float y = 24;
  fill(255);
  text("vectors -> audio", panelX, y);
  fill(160);
  text("scene: " + sceneNames[scene], panelX, y += 24);
  text(xy.getShapes().size() + " shapes, " + xy.wavePoints().size() + " points", panelX, y += 16);
  text(round(xy.freq().x) + " Hz loop, " + xy.waveSize() + " samples", panelX, y += 16);

  // the wavetables: X (blue) on top, Y (red) below
  y += 24;
  fill(255);
  text("wavetables", panelX, y);
  pushMatrix();
  translate(panelX, y + 8);
  scale(panelW / canvasSize, 200 / canvasSize);
  strokeWeight(canvasSize / 200); // scale() thins the lines too
  xy.drawWaveform();
  popMatrix();

  // what's going out of the sound card: left on top, right below
  y += 230;
  fill(255);
  text("audio out (L / R)", panelX, y);
  pushMatrix();
  translate(panelX, y + 8);
  scale(panelW / canvasSize, 200 / canvasSize);
  strokeWeight(canvasSize / 200);
  xy.drawWave(color(50, 255, 50));
  popMatrix();
  strokeWeight(1);

  y += 236;
  fill(160);
  String keys =
    "1-4   scenes\n" +
    "f     next font\n" +
    "r     record " + (xy.isRecording() ? "(RECORDING)" : "") + "\n" +
    "e     export 10s offline\n" +
    "c     clear drawing\n" +
    "d     debug view";
  text(keys, panelX, y);

  fill(xy.isRecording() ? color(255, 60, 60) : color(120));
  text(status, panelX, height - 30);
}

void exportAnimation(float seconds) {
  // A second XYscope with no sound card renders the animation offline,
  // frame by frame, without disturbing the live output.
  XYscope exporter = new XYscope(this, canvasSize, canvasSize, xy.sampleRate(), xy.bufferSize());
  exporter.freq(xy.freq().x);
  exporter.recorderBegin("export");

  int frames = int(seconds * 60);
  for (int f = 0; f < frames; f++) {
    exporter.clearWaves();
    drawScene(exporter, f / 60.0);
    exporter.buildWaves();
    exporter.process(1 / 60.0, 3); // X, Y and Z
  }

  String path = exporter.recorderEnd();
  status = "exported to the sketch folder:\n" + new File(path).getName();
}

void keyPressed() {
  if (key >= '1' && key <= '4') {
    scene = key - '1';
  } else if (key == 'f') {
    fontIndex = (fontIndex + 1) % fontNames.length;
    scene = 1;
  } else if (key == 'r') {
    if (xy.isRecording()) {
      String path = xy.recorderEnd();
      status = path.isEmpty() ? "nothing recorded" : "saved to the sketch folder:\n" + new File(path).getName();
    } else {
      xy.recorderBegin("XYscope");
      status = "recording...";
    }
  } else if (key == 'e') {
    exportAnimation(10);
  } else if (key == 'c') {
    drawing.clear();
  } else if (key == 'd') {
    xy.debugView(!xy.debugView());
  }
}

void mousePressed() {
  if (scene != 3 || mouseX >= canvasSize) return;
  XYPolyline line = new XYPolyline();
  line.addVertex(mouseX, mouseY);
  drawing.add(line);
}

void mouseDragged() {
  if (scene != 3 || drawing.isEmpty() || mouseX >= canvasSize) return;
  XYPolyline line = drawing.get(drawing.size() - 1);
  // skip tiny steps, they only cost points
  PVector last = line.get(line.size() - 1);
  if (dist(last.x, last.y, mouseX, mouseY) > 4) line.addVertex(mouseX, mouseY);
}
