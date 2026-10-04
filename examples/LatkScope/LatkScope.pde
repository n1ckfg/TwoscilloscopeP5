// Example 4: a 3D Latk animation -> XY audio -> audio effects -> strokes.
//
// Each frame of a Latk drawing (from the Lightning Artist Toolkit) is seen
// through the camera, encoded as one loop of XYscope audio, run through the
// effect chain in the panel, and drawn back from the altered audio, as the
// oscilloscope beam or decoded into strokes, each in its stroke's colour.
// The altered loop also plays out of the sound card, so what you hear is
// what you see. Drag to orbit, scroll to zoom, double-click to reset.
//
// Needs the Latk for Processing and PeasyCam libraries.
//
// l view, e solo next effect, n no effects, m mute, g panel,
// s save svg, w save wav, o save latk

import twoscilloscopeP5.*;
import latkProcessing.*;
import peasy.*;
import java.util.*;

Latk latk;
PeasyCam cam;
LatkScopeRenderer scope;
XYscope player; // loops the altered audio out of the sound card
EffectPanel gui;

final int BEAMS = 0, STROKES = 1, LINES = 2;
String[] viewNames = { "beams", "decoded strokes", "original lines" };
int view = BEAMS;
int soloIndex = -1;
boolean showGui = true;
String status = "";

void setup() {
  size(1024, 768, P3D);
  surface.setTitle("TwoscilloscopeP5: Latk -> audio -> strokes");
  textFont(createFont("Monospaced", 12));

  latk = new Latk(this, "jellyfish.latk");
  cam = new PeasyCam(this, 100);
  // the default near plane would cut through the drawing this close up
  float fov = PI / 3;
  float cameraZ = (height / 2.0) / tan(fov / 2);
  perspective(fov, float(width) / height, cameraZ / 100, cameraZ * 100);

  scope = new LatkScopeRenderer(44100);

  // The effect chain from the Transform example, in order.
  // Every setting shows up in the panel.
  XYEffectChain effects = scope.transformer.effects;
  effects.add(new XYLowPass()).cutoff.set(1500);
  effects.add(new XYChannelDelay()).delayY.set(0.6);
  effects.add(new XYHighPass());
  effects.add(new XYEcho());
  effects.add(new XYRingMod());
  effects.add(new XYRotate());
  effects.add(new XYDrive());
  effects.add(new XYWavefold());
  effects.add(new XYBitCrush());
  effects.add(new XYSampleHold());
  effects.add(new XYNoise());
  for (int i = 2; i < effects.size(); i++) {
    effects.get(i).enabled = false;
  }

  gui = new EffectPanel(effects, 10, 10);
  gui.add("scope", scope.parameters);

  // The altered loop plays out of the sound card, X left and Y right, so
  // what you hear is what you see.
  player = new XYscope(this, 0, 0, 44100, 512);
  status = player.openAudioOut() ? "the altered strokes are playing on the default audio out"
    : "no sound card found, running silently";
}

void update() {
  // Latk.run() advances and draws. Only advance here; draw() does the drawing.
  if (latk.checkInterval()) {
    for (LatkLayer layer : latk.layers) layer.nextFrame();
  }
  latk.lastMillis = millis();

  // The whole round trip, every frame: strokes -> audio -> effects -> strokes,
  // through the camera PeasyCam has already set for this frame.
  scope.update(latk, g);

  // loop the altered audio, Z (blanking) included
  float[][] waves = scope.transformer.getProcessedWaves();
  player.freq(scope.getFreq());
  player.setWaveforms(waves[0], waves[1], waves[2]);

  // Dragging a slider shouldn't orbit the camera. Leave PeasyCam off while
  // the pointer is over the panel, and don't switch mid-drag.
  if (!mousePressed) {
    cam.setActive(!(showGui && gui.inside(mouseX, mouseY)));
  }
}

void draw() {
  update();
  background(0);

  if (view == LINES) {
    for (LatkLayer layer : latk.layers) layer.run();
  }

  // The beams and strokes are already in screen pixels, like the panel.
  cam.beginHUD();
  if (view == BEAMS) scope.drawBeams(g);
  if (view == STROKES) scope.drawStrokes(g);
  hint(DISABLE_DEPTH_TEST); // the beam turns it back on
  if (showGui) gui.draw();

  LatkScopeRenderer.Stats stats = scope.stats;
  String info = round(frameRate) + " fps | " + viewNames[view] + " | loop " + nf(scope.getFreq(), 0, 1) + " Hz: "
    + stats.samples + " samples for " + round(stats.pathLength) + " px of " + stats.pieces + " strokes";
  if (stats.dropped > 0) info += " (" + stats.dropped + " too short to fit)";
  info += " | " + nf(stats.ms, 0, 1) + " ms";
  highlight(info, 10, height - 50);
  highlight("l view   e solo next effect   n no effects   m mute   g panel   s save svg   w save wav   o save latk", 10, height - 30);
  highlight(status, 10, height - 10);
  cam.endHUD();
}

// white text on a black box, like ofDrawBitmapStringHighlight()
void highlight(String s, float x, float y) {
  noStroke();
  fill(0);
  rect(x - 4, y - textAscent() - 4, textWidth(s) + 8, textAscent() + textDescent() + 8);
  fill(255);
  text(s, x, y);
}

void soloEffect(int index) {
  // turn on one effect at a time, to see what each one does
  soloIndex = index;
  XYEffectChain effects = scope.transformer.effects;
  for (int i = 0; i < effects.size(); i++) {
    XYEffect effect = effects.get(i);
    effect.enabled = i == index;
    gui.setOpen(i, effect.enabled);
  }
}

String timestamp() {
  return year() + nf(month(), 2) + nf(day(), 2) + "_" + nf(hour(), 2) + nf(minute(), 2) + nf(second(), 2);
}

void keyPressed() {
  if (key == 'l') {
    view = (view + 1) % 3;
  } else if (key == 'e') {
    soloEffect((soloIndex + 1) % scope.transformer.effects.size());
    status = "solo: " + scope.transformer.effects.get(soloIndex).getName();
  } else if (key == 'n') {
    soloEffect(-1);
    status = "no effects: the round trip on its own";
  } else if (key == 'm') {
    if (player.isAudioOutOpen()) {
      player.closeAudioOut();
      status = "muted";
    } else {
      status = player.openAudioOut() ? "playing on the default audio out" : "no sound card found, running silently";
    }
  } else if (key == 'g') {
    showGui = !showGui;
  } else if (key == 's') {
    String path = "transformed_" + timestamp() + ".svg";
    XYDecoder.saveSvg(this, path, scope.getStrokes(), width, height);
    status = "saved " + path;
  } else if (key == 'w') {
    // four seconds of the altered loop, X Y Z
    String path = "transformed_" + timestamp() + ".wav";
    WavFile.save(this, path, player.render(4, 3));
    status = "saved " + path;
  } else if (key == 'o') {
    // Latk.write() steps every layer through its frames, so put them back after
    int[] frames = new int[latk.layers.size()];
    for (int i = 0; i < frames.length; i++) frames[i] = latk.layers.get(i).currentFrame;
    latk.write("test.latk");
    for (int i = 0; i < frames.length; i++) latk.layers.get(i).currentFrame = frames[i];
    status = "saved data/test.latk";
  }
}

void mousePressed() {
  if (showGui) gui.mousePressed(mouseX, mouseY);
}

void mouseDragged() {
  gui.mouseDragged(mouseX, mouseY);
}

void mouseReleased() {
  gui.mouseReleased();
}
