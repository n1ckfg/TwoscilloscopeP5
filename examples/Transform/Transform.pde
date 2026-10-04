// Example 3: a vector shape -> XY audio -> audio effects -> a new vector shape.
//
// The shape on the left is encoded as XYscope audio, run through the effect
// chain in the panel, and decoded back into the shape on the right. The
// altered audio also loops out of the sound card (and through the beam in
// the middle), so what you hear is what you see.
//
// 1-4 source (4: draw in panel 1), e solo next effect, n no effects,
// a apply (feed the result back in), c clear drawing, s save svg, w save wav

import twoscilloscopeP5.*;

XYTransformer transformer; // shape -> audio -> effects -> shape
XYscope player;            // loops the altered audio
Oscilloscope scope;        // shows it as a beam
XYSoundStream soundStream;
boolean audioOk;
double silentFrames = 0;
XYSoundBuffer xyz;         // the audio thread's X, Y and Z

EffectPanel gui;

String[] sourceNames = { "shapes", "text", "spiral", "drawing" };
int sourceIndex = 0;
int generation = 0;
ArrayList<XYPolyline> drawing = new ArrayList<XYPolyline>(); // the mouse drawing, in canvas pixels
ArrayList<XYPolyline> source = new ArrayList<XYPolyline>();
ArrayList<XYPolyline> result = new ArrayList<XYPolyline>();
int soloIndex = -1;

float canvasSize = 512; // the shapes live on a canvasSize square
float panelSize = 320;  // and are drawn at panelSize
PVector panel1, panel2, panel3;
String status = "";
int lastMillis;

void setup() {
  size(1280, 760, P2D);
  surface.setTitle("TwoscilloscopeP5: vector -> audio -> vector");
  frameRate(60);
  textFont(createFont("Monospaced", 12));

  panel1 = new PVector(250, 40);
  panel2 = new PVector(panel1.x + panelSize + 25, 40);
  panel3 = new PVector(panel2.x + panelSize + 25, 40);

  // shapes on a 512 x 512 canvas, encoded at 44.1kHz as a 50Hz loop
  transformer = new XYTransformer(canvasSize, canvasSize, 44100, 50);

  // the effect chain, in order; every setting shows up in the panel
  transformer.effects.add(new XYLowPass()).cutoff.set(1500);
  transformer.effects.add(new XYChannelDelay()).delayY.set(0.6);
  transformer.effects.add(new XYHighPass());
  transformer.effects.add(new XYEcho());
  transformer.effects.add(new XYRingMod());
  transformer.effects.add(new XYRotate());
  transformer.effects.add(new XYDrive());
  transformer.effects.add(new XYWavefold());
  transformer.effects.add(new XYBitCrush());
  transformer.effects.add(new XYSampleHold());
  transformer.effects.add(new XYNoise());
  for (int i = 2; i < transformer.effects.size(); i++) {
    transformer.effects.get(i).enabled = false;
  }

  gui = new EffectPanel(transformer.effects, 10, 10);

  // the altered loop plays back through an XYscope, and into the beam
  player = new XYscope(this, canvasSize, canvasSize, 44100, 512);
  player.freq(transformer.getFreq());
  scope = new Oscilloscope(this, (int) panelSize, (int) panelSize);

  // calls this sketch's audioOut()
  soundStream = new XYSoundStream(this);
  audioOk = soundStream.setup(2, 0, 44100, 512);
  status = audioOk ? "the altered shape is playing on the default audio out" : "no sound card found, running silently";

  makeSource();
  lastMillis = millis();
}

void makeSource() {
  source = new ArrayList<XYPolyline>();
  generation = 0;
  float s = canvasSize;

  switch (sourceIndex) {
  case 0:
    XYPolyline circle = new XYPolyline();
    for (int i = 0; i < 60; i++) {
      float a = TWO_PI * i / 60;
      circle.addVertex(s * 0.3 + cos(a) * s * 0.17, s * 0.3 + sin(a) * s * 0.17);
    }
    circle.setClosed(true);
    source.add(circle);

    XYPolyline square = new XYPolyline();
    square.addVertex(s * 0.56, s * 0.14);
    square.addVertex(s * 0.88, s * 0.14);
    square.addVertex(s * 0.88, s * 0.46);
    square.addVertex(s * 0.56, s * 0.46);
    square.setClosed(true);
    source.add(square);

    XYPolyline star = new XYPolyline();
    for (int i = 0; i < 5; i++) {
      float a = -HALF_PI + i * TWO_PI * 2 / 5;
      star.addVertex(s * 0.5 + cos(a) * s * 0.2, s * 0.72 + sin(a) * s * 0.2);
    }
    star.setClosed(true);
    source.add(star);
    break;

  case 1:
    HersheyFont font = new HersheyFont();
    font.load("timesr");
    source = font.getStrokes("p5", s / 2, s * 0.42, s * 0.3, s * 0.4, CENTER, CENTER);
    font.load("futural");
    source.addAll(font.getStrokes("vector > audio > vector", s / 2, s * 0.75, s * 0.05, s * 0.08, CENTER, CENTER));
    break;

  case 2:
    XYPolyline spiral = new XYPolyline();
    for (int i = 0; i <= 400; i++) {
      float t = i / 400.0;
      float a = t * TWO_PI * 5;
      spiral.addVertex(s / 2 + cos(a) * t * s * 0.42, s / 2 + sin(a) * t * s * 0.42);
    }
    source.add(spiral);
    break;

  case 3:
    source.addAll(drawing);
    break;
  }
}

void soloEffect(int index) {
  // turn on one effect at a time, to see what each one does
  soloIndex = index;
  for (int i = 0; i < transformer.effects.size(); i++) {
    XYEffect effect = transformer.effects.get(i);
    effect.enabled = i == index;
    gui.setOpen(i, effect.enabled);
  }
}

void update() {
  // the whole round trip, every frame: shape -> audio -> effects -> shape
  result = transformer.transform(source);

  // loop the altered audio, Z (blanking) included
  float[][] waves = transformer.getProcessedWaves();
  player.setWaveforms(waves[0], waves[1], waves[2]);

  int now = millis();
  if (!audioOk) {
    // no sound card: run the playback on the clock instead
    silentFrames += (now - lastMillis) / 1000.0 * 44100;
    int frames = (int) silentFrames;
    silentFrames -= frames;
    if (frames > 0) {
      XYSoundBuffer buffer = new XYSoundBuffer(frames, 3, 44100);
      player.audioOut(buffer);
      scope.addBuffer(buffer);
    }
  }
  lastMillis = now;
  scope.update();
}

void audioOut(XYSoundBuffer buffer) {
  // render X, Y and Z, show all three, send X and Y
  int n = buffer.getNumFrames();
  if (xyz == null || xyz.getNumFrames() != n) xyz = new XYSoundBuffer(n, 3, buffer.getSampleRate());
  player.audioOut(xyz);
  scope.addBuffer(xyz);

  int nCh = buffer.getNumChannels();
  for (int i = 0; i < n; i++) {
    for (int c = 0; c < nCh; c++) {
      buffer.samples[i * nCh + c] = c < 2 ? xyz.samples[i * 3 + c] : 0;
    }
  }
}

PVector toCanvas(float x, float y) {
  return new PVector(x - panel1.x, y - panel1.y).mult(canvasSize / panelSize);
}

boolean insidePanel1(float x, float y) {
  return x >= panel1.x && x < panel1.x + panelSize && y >= panel1.y && y < panel1.y + panelSize;
}

void drawPanel(PVector p, String title) {
  noStroke();
  fill(14);
  rect(p.x, p.y, panelSize, panelSize);
  fill(220);
  text(title, p.x, p.y - 10);
}

void drawShapes(ArrayList<XYPolyline> shapes, PVector p, boolean colored, boolean points) {
  pushMatrix();
  translate(p.x, p.y);
  scale(panelSize / canvasSize);
  strokeWeight(canvasSize / panelSize); // scale() thins the lines too
  noFill();
  for (int i = 0; i < shapes.size(); i++) {
    if (colored) {
      colorMode(HSB, 255);
      stroke((i * 37) % 255, 120, 255);
      colorMode(RGB, 255);
    }
    XYPolyline shape = shapes.get(i);
    shape.draw(g);
    if (points) {
      for (PVector v : shape.getVertices()) circle(v.x, v.y, 3);
    }
  }
  popMatrix();
  strokeWeight(1);
}

void drawWave(XYSoundBuffer audio, float x, float y, float w, float h, int col) {
  // one loop of X (top) and Y (bottom)
  int nCh = audio.getNumChannels();
  int n = audio.getNumFrames();
  if (nCh < 2 || n < 2) return;
  stroke(col);
  noFill();
  for (int c = 0; c < 2; c++) {
    beginShape();
    for (int i = 0; i < n; i++) {
      vertex(x + w * i / (n - 1), y + h * (0.25 + 0.5 * c) - h * 0.22 * audio.samples[i * nCh + c]);
    }
    endShape();
  }
}

void draw() {
  update();
  background(0);
  gui.draw();

  // 1. the source shape
  drawPanel(panel1, "1. vector shape" + (generation > 0 ? " (generation " + generation + ")" : ""));
  stroke(255);
  drawShapes(source, panel1, false, false);

  // 2. the altered audio, as a beam
  drawPanel(panel2, "2. as XY audio, through the effects");
  scope.draw(panel2.x, panel2.y, panelSize, panelSize);

  // 3. the altered audio decoded, over a ghost of the source
  drawPanel(panel3, "3. decoded vector shape");
  // effects can push the shape off the canvas, so clip to the panel
  clip(panel3.x, panel3.y, panelSize, panelSize);
  stroke(55);
  drawShapes(source, panel3, false, false);
  drawShapes(result, panel3, true, true);
  noClip();

  // the audio: one loop before (grey) and after (green) the effects
  float waveY = panel1.y + panelSize + 40;
  float waveW = panel3.x + panelSize - panel1.x;
  fill(220);
  text("one loop of X (top) and Y (bottom): encoded (grey), after the effects (green)", panel1.x, waveY - 10);
  noStroke();
  fill(14);
  rect(panel1.x, waveY, waveW, 200);
  XYSoundBuffer processedCycle = transformer.getProcessedCycle();
  XYSoundBuffer encodedCycle = transformer.getEncodedAudio().getLastFrames(processedCycle.getNumFrames());
  drawWave(encodedCycle, panel1.x, waveY, waveW, 200, color(110));
  drawWave(processedCycle, panel1.x, waveY, waveW, 200, color(60, 255, 120));

  // info
  int sourcePoints = 0, resultPoints = 0;
  for (XYPolyline p : source) sourcePoints += p.size();
  for (XYPolyline p : result) resultPoints += p.size();
  float infoY = waveY + 230;
  fill(160);
  text("source: " + sourceNames[sourceIndex] + ", " + source.size() + " shapes, " + sourcePoints +
    " points   ->   result: " + result.size() + " shapes, " + resultPoints + " points", panel1.x, infoY);
  text("1-4 source (4: draw in panel 1)   e solo next effect   n no effects   a apply (feed the result back in)\n" +
    "c clear drawing   s save svg   w save wav", panel1.x, infoY + 22);
  fill(120);
  text(status, panel1.x, height - 12);
}

String timestamp() {
  return year() + nf(month(), 2) + nf(day(), 2) + "_" + nf(hour(), 2) + nf(minute(), 2) + nf(second(), 2);
}

void keyPressed() {
  if (key >= '1' && key <= '4') {
    sourceIndex = key - '1';
    makeSource();
  } else if (key == 'e') {
    soloEffect((soloIndex + 1) % transformer.effects.size());
    status = "solo: " + transformer.effects.get(soloIndex).getName();
  } else if (key == 'n') {
    soloEffect(-1);
    status = "no effects: the round trip on its own";
  } else if (key == 'a') {
    // feed the altered shape back in, and alter it again
    source = new ArrayList<XYPolyline>(result);
    generation++;
    status = "generation " + generation;
  } else if (key == 'c') {
    drawing.clear();
    if (sourceIndex == 3) makeSource();
  } else if (key == 's') {
    String path = "transformed_" + timestamp() + ".svg";
    XYDecoder.saveSvg(this, path, result, canvasSize, canvasSize);
    status = "saved " + path;
  } else if (key == 'w') {
    // four seconds of the altered loop, X Y Z
    String path = "transformed_" + timestamp() + ".wav";
    WavFile.save(this, path, player.render(4, 3));
    status = "saved " + path;
  }
}

void mousePressed() {
  if (gui.mousePressed(mouseX, mouseY)) return;
  if (!insidePanel1(mouseX, mouseY)) return;
  if (sourceIndex != 3) {
    sourceIndex = 3;
    drawing.clear();
  }
  XYPolyline line = new XYPolyline();
  line.addVertex(toCanvas(mouseX, mouseY));
  drawing.add(line);
  makeSource();
}

void mouseDragged() {
  if (gui.mouseDragged(mouseX, mouseY)) return;
  if (sourceIndex != 3 || drawing.isEmpty() || !insidePanel1(mouseX, mouseY)) return;
  PVector p = toCanvas(mouseX, mouseY);
  XYPolyline line = drawing.get(drawing.size() - 1);
  if (PVector.dist(line.get(line.size() - 1), p) > 4) {
    line.addVertex(p);
    makeSource();
  }
}

void mouseReleased() {
  gui.mouseReleased();
}
