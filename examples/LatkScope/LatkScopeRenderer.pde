// Draws a Latk animation through TwoscilloscopeP5, the way the Transform
// example draws a shape: the current frame is projected to the screen,
// encoded as one loop of XY audio, run through the effect chain, and drawn
// back from the altered audio.
//
// The strokes are encoded here rather than by XYscope, so that every sample
// of the loop is known to belong to one stroke. The effects pass Z through
// untouched, so that still holds after them, and each stroke is drawn from
// its own samples in its own colour.

class LatkScopeRenderer {

  // shapes -> audio -> effects -> shapes; add effects to transformer.effects
  XYTransformer transformer = new XYTransformer();

  XYParameter loopFreq = new XYParameter("loop Hz", 5, 1, 100);          // lower gives the drawing more samples
  XYParameter beamSize = new XYParameter("beam size", 3, 0.5, 12);       // beam radius, px
  XYParameter beamIntensity = new XYParameter("beam intensity", 1, 0, 4); // brightness of a stroke drawn at an even speed
  ArrayList<XYParameter> parameters = new ArrayList<XYParameter>();

  class Stats {
    int pieces = 0;
    int dropped = 0;      // pieces left out because the loop is too short
    int samples = 0;      // per loop
    float pathLength = 0; // px
    float ms = 0;         // projecting, encoding and transforming
  }
  Stats stats = new Stats();

  class Piece implements Comparable<Piece> {
    ArrayList<PVector> points = new ArrayList<PVector>(); // canvas px
    int col;
    float length = 0;
    int start = 0; // first sample: blanked, on the first point
    int lit = 0;   // then this many lit samples, from end to end

    // longest first
    public int compareTo(Piece other) {
      return Float.compare(other.length, length);
    }
  }

  int sampleRate = 44100;
  float freq = 5;
  int cycleFrames = 8820;
  float canvasW = 0;
  float canvasH = 0;

  ArrayList<Piece> pieces = new ArrayList<Piece>();
  int written = 0;                                  // samples written to the loop so far
  float[] x = new float[0], y = new float[0], z = new float[0]; // one loop of the altered audio

  OsciMesh osci = new OsciMesh();
  float[] sx = new float[0]; // x in scope units
  LinkedHashMap<Integer, ArrayList<Piece>> beams = new LinkedHashMap<Integer, ArrayList<Piece>>(); // the pieces of each colour
  float beamExposure = 1;
  boolean beamsDirty = true;

  ArrayList<XYPolyline> strokes = new ArrayList<XYPolyline>();
  ArrayList<Piece> strokePieces = new ArrayList<Piece>(); // the piece each stroke was decoded from
  boolean strokesDirty = true;

  LatkScopeRenderer(int sampleRate) {
    this.sampleRate = sampleRate;
    parameters.add(loopFreq);
    parameters.add(beamSize);
    parameters.add(beamIntensity);
  }

  float getFreq() {
    return freq;
  }

  // Projects, encodes and transforms the current frame of each layer, as
  // pg's camera sees it, onto a canvas the size of pg.
  void update(Latk latk, PGraphics pg) {
    long startNanos = System.nanoTime();
    beamsDirty = true;
    strokesDirty = true;
    if (pg.width < 1 || pg.height < 1) return;

    // The loop is a whole number of samples, so XYscope plays it back one
    // table entry per sample.
    cycleFrames = max(2, round(sampleRate / max(0.1, loopFreq.get())));
    freq = (float) sampleRate / cycleFrames;
    if (pg.width != canvasW || pg.height != canvasH || freq != transformer.getFreq()) {
      transformer.setup(pg.width, pg.height, sampleRate, freq, 512);
    }
    canvasW = pg.width;
    canvasH = pg.height;

    project(latk, (PGraphicsOpenGL) pg);
    encode();
    stats.ms = (System.nanoTime() - startNanos) / 1e6;
  }

  void project(Latk latk, PGraphicsOpenGL pg) {
    pieces.clear();
    // The camera's matrix, computed once, as screenX() and screenY() would for every point.
    PMatrix3D m = pg.projection.get();
    m.apply(pg.modelview);
    float[] t = new float[2];

    for (LatkLayer layer : latk.layers) {
      if (layer.currentFrame < 0 || layer.currentFrame >= layer.frames.size()) continue;

      for (LatkStroke stroke : layer.frames.get(layer.currentFrame).strokes) {
        boolean open = false; // whether the next segment continues the last piece
        boolean lastValid = false;
        PVector last = new PVector();
        for (LatkPoint p : stroke.points) {
          // LatkStroke.run() draws each point as (z, -y, x), scaled by globalScale, so match it.
          float wx = p.co.z * stroke.globalScale;
          float wy = -p.co.y * stroke.globalScale;
          float wz = p.co.x * stroke.globalScale;
          float cx = m.m00 * wx + m.m01 * wy + m.m02 * wz + m.m03;
          float cy = m.m10 * wx + m.m11 * wy + m.m12 * wz + m.m13;
          float cz = m.m20 * wx + m.m21 * wy + m.m22 * wz + m.m23;
          float cw = m.m30 * wx + m.m31 * wy + m.m32 * wz + m.m33;
          // Behind the camera or outside its depth range: break the stroke here.
          boolean valid = cw > 0 && cz / cw >= -1 && cz / cw <= 1;
          PVector screen = new PVector((cx / cw + 1) * 0.5 * canvasW, (1 - cy / cw) * 0.5 * canvasH);

          if (valid && lastValid && clipSegment(last, screen, t)) {
            // The window is the scope's canvas, and past its edges the audio
            // would clip, so cut the stroke where it leaves the window.
            PVector a = PVector.lerp(last, screen, t[0]);
            PVector b = PVector.lerp(last, screen, t[1]);
            if (!open || t[0] > 0) {
              Piece piece = new Piece();
              piece.col = stroke.col;
              piece.points.add(a);
              pieces.add(piece);
            }
            Piece piece = pieces.get(pieces.size() - 1);
            piece.points.add(b);
            piece.length += PVector.dist(a, b);
            open = t[1] == 1;
          } else {
            open = false;
          }
          last = screen;
          lastValid = valid;
        }
      }
    }

    stats.pathLength = 0;
    for (Piece piece : pieces) stats.pathLength += piece.length;
  }

  // Cuts the segment a-b to the canvas (Liang-Barsky). Returns false if none
  // of it is inside, or sets t[0] and t[1] to where the inside part starts and ends.
  boolean clipSegment(PVector a, PVector b, float[] t) {
    float dx = b.x - a.x, dy = b.y - a.y;
    float[] p = { -dx, dx, -dy, dy };
    float[] q = { a.x, canvasW - a.x, a.y, canvasH - a.y };
    t[0] = 0;
    t[1] = 1;
    for (int i = 0; i < 4; i++) {
      if (p[i] == 0) {
        // parallel to this edge, so all in or all out
        if (q[i] < 0) return false;
        continue;
      }
      float r = q[i] / p[i];
      if (p[i] < 0) t[0] = max(t[0], r);
      else t[1] = min(t[1], r);
      if (t[0] > t[1]) return false;
    }
    return true;
  }

  void encode() {
    int n = cycleFrames;
    stats.samples = n;
    stats.dropped = 0;

    // Every piece takes a blank sample that jumps the beam to its start, and at
    // least two lit ones, for its ends. If the loop is too short for that, the
    // shortest pieces are left out.
    int maxPieces = n / 3;
    if (pieces.size() > maxPieces) {
      ArrayList<Piece> longest = new ArrayList<Piece>(pieces);
      Collections.sort(longest); // stable, so equal lengths keep their order
      HashSet<Piece> keep = new HashSet<Piece>(longest.subList(0, maxPieces));
      ArrayList<Piece> kept = new ArrayList<Piece>(maxPieces);
      for (Piece piece : pieces) {
        if (keep.contains(piece)) kept.add(piece);
      }
      stats.dropped = pieces.size() - kept.size();
      pieces = kept;
    }
    stats.pieces = pieces.size();

    // The rest of the loop is shared out by length, so the beam moves at an
    // even speed, as it does in XYscope's waveforms.
    double totalLength = 0;
    for (Piece piece : pieces) totalLength += piece.length;
    int spare = n - 3 * pieces.size();

    // one loop in XYscope's format: X, Y and Z interleaved, the canvas mapped
    // to -1..1 with +Y up, and Z blanking the beam between pieces
    float[] cycle = new float[n * 3];
    written = 0;

    double before = 0; // length of the pieces so far
    for (int k = 0; k < pieces.size(); k++) {
      Piece piece = pieces.get(k);
      // rounded from running totals, so the shares add up to exactly the spare samples
      double after = before + piece.length;
      int share;
      if (totalLength > 0) {
        share = (int) (Math.round(spare * after / totalLength) - Math.round(spare * before / totalLength));
      } else {
        share = spare * (k + 1) / pieces.size() - spare * k / pieces.size();
      }
      before = after;

      piece.start = written;
      piece.lit = 2 + share;
      write(cycle, piece.points.get(0), false);

      // lit samples at even steps along the piece, from its first point to its last
      int seg = 0;
      float segStart = 0; // length along the piece to points[seg]
      float segLength = PVector.dist(piece.points.get(0), piece.points.get(1));
      for (int j = 0; j < piece.lit; j++) {
        float at = piece.length * j / (piece.lit - 1);
        while (seg + 2 < piece.points.size() && segStart + segLength < at) {
          segStart += segLength;
          seg++;
          segLength = PVector.dist(piece.points.get(seg), piece.points.get(seg + 1));
        }
        float u = segLength > 0 ? constrain((at - segStart) / segLength, 0, 1) : 1;
        write(cycle, PVector.lerp(piece.points.get(seg), piece.points.get(seg + 1), u), true);
      }
    }
    // with nothing to draw, the beam rests blanked in the middle
    PVector middle = new PVector(canvasW / 2, canvasH / 2);
    while (written < n) write(cycle, middle, false);

    // XYTransformer runs the effects over a few loops, so that filters and
    // echoes settle, and keeps the last one.
    int loops = max(0, transformer.settleCycles) + 1;
    XYSoundBuffer encoded = new XYSoundBuffer(n * loops, 3, sampleRate);
    for (int l = 0; l < loops; l++) {
      System.arraycopy(cycle, 0, encoded.samples, l * cycle.length, cycle.length);
    }
    transformer.transform(encoded);
    float[][] waves = transformer.getProcessedWaves();
    x = waves[0];
    y = waves[1];
    z = waves[2];
  }

  void write(float[] cycle, PVector p, boolean lit) {
    XYDecoderSettings levels = transformer.decoder;
    cycle[written * 3] = p.x / canvasW * 2 - 1;
    cycle[written * 3 + 1] = 1 - p.y / canvasH * 2;
    cycle[written * 3 + 2] = lit ? levels.zMax : levels.zMin;
    written++;
  }

  void buildBeams() {
    beamsDirty = false;
    beams.clear();
    if (x.length != cycleFrames) return;

    // Scope units: -1..1 up the canvas, and as far across it as its shape
    // allows, so the beam stays round in any window.
    float aspect = canvasW / canvasH;
    sx = new float[x.length];
    for (int i = 0; i < x.length; i++) sx[i] = x[i] * aspect;
    osci.uSize = beamSize.get() / (canvasH / 2);

    // One beam per colour: the beams add up, so the drawing order doesn't matter.
    for (Piece piece : pieces) {
      ArrayList<Piece> members = beams.get(piece.col);
      if (members == null) {
        members = new ArrayList<Piece>();
        beams.put(piece.col, members);
      }
      members.add(piece);
    }

    // A beam leaves less light on a line the faster it moves, so a longer
    // drawing or a shorter loop comes out dimmer. Scale the light by the
    // average step, so a stroke peaks at about beamIntensity either way.
    double stepSum = 0;
    int steps = 0;
    for (Piece piece : pieces) {
      int first = piece.start + 1;
      for (int i = first + 1; i < first + piece.lit; i++) {
        stepSum += dist(sx[i - 1], y[i - 1], sx[i], y[i]);
        steps++;
      }
    }
    float sigma = osci.uSize / 3;
    beamExposure = steps > 0 ? (float) (stepSum / steps) / (sigma * sqrt(TWO_PI)) : 1;
  }

  // The altered audio drawn by the oscilloscope beam, on screen. pg must be P2D or P3D.
  void drawBeams(PGraphics pg) {
    if (beamsDirty) buildBeams();

    pg.pushMatrix();
    pg.translate(canvasW / 2, canvasH / 2);
    // scope +Y is up
    pg.scale(canvasH / 2, -canvasH / 2);
    osci.uIntensity = beamIntensity.get() * beamExposure;
    for (Map.Entry<Integer, ArrayList<Piece>> beam : beams.entrySet()) {
      // one mesh, refilled for each colour
      osci.clear();
      for (Piece piece : beam.getValue()) {
        // The piece's lit samples, end to end. OsciMesh.addLines() would also
        // join them to the last piece, but that jump is blanked, so leave it out.
        int first = piece.start + 1;
        for (int i = first + 1; i < first + piece.lit; i++) {
          osci.addLine(sx[i - 1], y[i - 1], sx[i], y[i], 1);
        }
      }
      int c = beam.getKey();
      osci.uRgb.set(red(c) / 255, green(c) / 255, blue(c) / 255);
      osci.draw(pg);
    }
    pg.popMatrix();
  }

  void decodeStrokes() {
    strokesDirty = false;
    strokes.clear();
    strokePieces.clear();
    if (x.length != cycleFrames) return;

    XYDecoderSettings settings = transformer.decoder.copy();
    settings.width = canvasW;
    settings.height = canvasH;
    settings.sampleRate = sampleRate;
    settings.freq = freq;
    for (Piece piece : pieces) {
      // Each piece's samples, blank and all, decoded on their own so that
      // whatever the effects made of them keeps the piece's colour.
      int s = piece.start;
      int e = piece.start + piece.lit + 1;
      float[] pz = z.length > 0 ? Arrays.copyOfRange(z, s, e) : null;
      for (XYPolyline line : XYDecoder.decodeCycle(Arrays.copyOfRange(x, s, e), Arrays.copyOfRange(y, s, e), pz, e - s, settings)) {
        strokes.add(line);
        strokePieces.add(piece);
      }
    }
  }

  // The decoded strokes, on a canvas the size of the screen.
  ArrayList<XYPolyline> getStrokes() {
    if (strokesDirty) decodeStrokes();
    return strokes;
  }

  // The altered audio decoded back into strokes, on screen.
  void drawStrokes(PGraphics pg) {
    if (strokesDirty) decodeStrokes();

    pg.pushStyle();
    pg.noFill();
    pg.strokeWeight(2); // as LatkStroke draws them
    for (int i = 0; i < strokes.size(); i++) {
      pg.stroke(strokePieces.get(i).col);
      strokes.get(i).draw(pg);
    }
    pg.popStyle();
  }

}
