package twoscilloscopeP5;

import processing.core.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Turns XY audio back into vector shapes: XYPolylines on an XYscope-style canvas (pixels, y
 * down).
 * <p>The Oscilloscope app only ever drew the beam. This goes one step further and recovers
 * the drawing itself, the inverse of XYscope.buildWaves():</p>
 * <p>1. Find the period: the signal loops at XYscope's freq(), so the shape is one cycle
 * long. Given freq it's sampleRate / freq; otherwise it's found with the YIN difference
 * function. 2. Take the most recent full cycle and map each sample to the canvas: px = (x +
 * 1) / 2 * width,  py = (1 - y) / 2 * height. 3. Cut it into strokes wherever the beam
 * blanks (Z below zThreshold, when there's a Z channel) or jumps (a step much longer than
 * the typical one, which is XYscope's pen moving from one shape to the next). 4. Join the
 * stroke that runs off the end of the cycle back onto the one at the start, since the cycle
 * loops, then simplify each stroke.</p>
 */

public class XYDecoder {

  static class Stroke {
    int start;
    int end; // inclusive
    ArrayList<PVector> points = new ArrayList<PVector>();

    Stroke(int _start) {
      start = _start;
      end = _start;
    }
  }

  static ArrayList<XYPolyline> decodeSamples(float[] x, float[] y, float[] z, int offset, int n,
    XYDecoderSettings s, boolean cyclic) {
    ArrayList<XYPolyline> result = new ArrayList<XYPolyline>();
    if (n < 2) return result;

    // samples -> canvas, inverting XYscope's mapping
    PVector[] pts = new PVector[n];
    for (int i = 0; i < n; i++) {
      pts[i] = new PVector((x[offset + i] + 1) * 0.5f * s.width, (1 - y[offset + i]) * 0.5f * s.height);
    }

    boolean[] blank = new boolean[n];
    if (z != null && s.useZ && s.zMax != s.zMin) {
      for (int i = 0; i < n; i++) {
        blank[i] = (z[offset + i] - s.zMin) / (s.zMax - s.zMin) < s.zThreshold;
      }
    }

    // step[i] is the distance from the previous sample (wrapping, for a loop)
    float[] step = new float[n];
    for (int i = 1; i < n; i++) step[i] = PVector.dist(pts[i], pts[i - 1]);
    step[0] = cyclic ? PVector.dist(pts[0], pts[n - 1]) : 0;

    float[] moving = new float[n];
    int numMoving = 0;
    for (int i = 1; i < n; i++) {
      if (step[i] > 1e-4f && !blank[i]) moving[numMoving++] = step[i];
    }
    float median = 0;
    if (numMoving > 0) {
      Arrays.sort(moving, 0, numMoving);
      median = moving[numMoving / 2];
    }
    float threshold = s.jumpThreshold > 0 ? s.jumpThreshold : Math.max(1.0f, s.jumpFactor * median);
    float closeThreshold = s.closeThreshold > 0 ? s.closeThreshold : 2.5f * median;

    // walk the samples, cutting at blanks and jumps
    ArrayList<Stroke> strokes = new ArrayList<Stroke>();
    boolean open = false;
    for (int i = 0; i < n; i++) {
      if (blank[i]) {
        // the beam is still where the stroke ended when it blanks
        // (XYscope blanks right on a shape's last point), so keep that spot
        if (open && step[i] <= threshold) {
          Stroke last = strokes.get(strokes.size() - 1);
          last.points.add(pts[i]);
          last.end = i;
        }
        open = false;
        continue;
      }
      if (open && step[i] > threshold) open = false;
      if (!open) {
        strokes.add(new Stroke(i));
        open = true;
      }
      Stroke last = strokes.get(strokes.size() - 1);
      last.points.add(pts[i]);
      last.end = i;
    }

    // the cycle loops, so a stroke running off the end continues at the start
    boolean joined = cyclic && !blank[0] && !blank[n - 1] && step[0] <= threshold;
    boolean closedLoop = false;
    if (joined && !strokes.isEmpty() && strokes.get(0).start == 0 && strokes.get(strokes.size() - 1).end == n - 1) {
      if (strokes.size() > 1) {
        strokes.get(strokes.size() - 1).points.addAll(strokes.get(0).points);
        strokes.remove(0);
      } else {
        closedLoop = true; // one unbroken loop
      }
    }

    for (Stroke stroke : strokes) {
      XYPolyline poly = new XYPolyline();
      for (PVector p : stroke.points) {
        if (poly.size() == 0 || PVector.dist(poly.points.get(poly.size() - 1), p) > 1e-3f) {
          poly.addVertex(p.x, p.y);
        }
      }
      // XYscope blanks a closed shape's last point, which leaves a gap of a sample or two
      if (closedLoop || (s.closeThreshold >= 0 && poly.size() > 3 &&
        PVector.dist(poly.points.get(0), poly.points.get(poly.size() - 1)) <= closeThreshold)) {
        poly.setClosed(true);
      }
      if (s.simplify > 0 && poly.size() > 2) poly.simplify(s.simplify);
      if (poly.size() < s.minPoints) continue;
      if (s.minLength > 0 && poly.getPerimeter() < s.minLength) continue;
      result.add(poly);
    }

    return result;
  }

  /**
   * Decode exactly these samples as one loop, no period detection. z can be null.
   */
  public static ArrayList<XYPolyline> decodeCycle(float[] x, float[] y, float[] z, int n, XYDecoderSettings settings) {
    return decodeSamples(x, y, z, 0, n, settings, true);
  }

  /**
   * Decode the latest full cycle of a signal. z can be null.
   */
  public static ArrayList<XYPolyline> decode(float[] x, float[] y, float[] z, int n, XYDecoderSettings settings) {
    float period = 0;
    if (settings.freq > 0) {
      period = settings.sampleRate / settings.freq;
    } else {
      period = detectPeriod(x, y, n, settings.sampleRate / Math.max(1.0f, settings.maxFreq),
        settings.sampleRate / Math.max(1.0f, settings.minFreq));
    }

    int m = Math.round(period);
    if (m < 2 || m > n) {
      // nothing loops: decode everything we have, as one open run
      return decodeSamples(x, y, z, 0, n, settings, false);
    }

    return decodeSamples(x, y, z, n - m, m, settings, true);
  }

  /**
   * Decode an interleaved buffer: X on channel 0, Y on 1, Z on 2 (if any).
   * Mono buffers decode with the signal on Y, the way the Oscilloscope app draws them.
   */
  public static ArrayList<XYPolyline> decode(XYSoundBuffer buffer, XYDecoderSettings settings) {
    int nCh = buffer.getNumChannels();
    int n = buffer.getNumFrames();
    if (n == 0) return new ArrayList<XYPolyline>();
    XYDecoderSettings s = settings.copy();
    if (buffer.getSampleRate() > 0) s.sampleRate = buffer.getSampleRate();
    float[] samples = buffer.samples;

    float[] x = new float[n];
    float[] y = new float[n];
    if (nCh == 1) {
      // a mono signal is a waveform: time across, signal up
      for (int i = 0; i < n; i++) {
        x[i] = -1 + 2.0f * i / Math.max(1, n - 1);
        y[i] = samples[i];
      }
      return decodeSamples(x, y, null, 0, n, s, false);
    }

    for (int i = 0; i < n; i++) {
      x[i] = samples[i * nCh];
      y[i] = samples[i * nCh + 1];
    }
    float[] z = null;
    if (nCh == 3) {
      z = new float[n];
      for (int i = 0; i < n; i++) z[i] = samples[i * nCh + 2];
    }
    return decode(x, y, z, n, s);
  }

  // After YIN (de Cheveigné & Kawahara 2002), on both channels at once.
  static float yinPeriod(float[] x, float[] y, int n, float minPeriod, float maxPeriod) {
    int minLag = Math.max(2, (int) Math.floor(minPeriod));
    int maxLag = Math.min(n / 2, (int) Math.ceil(maxPeriod));
    if (maxLag <= minLag + 1) return 0;

    // compare the most recent window against itself, shifted by up to maxLag + 1
    int window = Math.min(n - maxLag - 1, 2048);
    int t0 = n - maxLag - 1 - window;
    if (window < 1) return 0;

    double energy = 0;
    for (int t = t0; t < t0 + window; t++) energy += x[t] * x[t] + y[t] * y[t];
    if (energy < 1e-9 * window) return 0; // silence

    double[] d = new double[maxLag + 2];
    for (int tau = 1; tau <= maxLag + 1; tau++) {
      double sum = 0;
      for (int t = t0; t < t0 + window; t++) {
        double dx = x[t] - x[t + tau];
        double dy = y[t] - y[t + tau];
        sum += dx * dx + dy * dy;
      }
      d[tau] = sum;
    }

    // cumulative mean normalized difference
    double[] dn = new double[maxLag + 2];
    Arrays.fill(dn, 1.0);
    double running = 0;
    for (int tau = 1; tau <= maxLag + 1; tau++) {
      running += d[tau];
      dn[tau] = running > 0 ? d[tau] * tau / running : 1.0;
    }

    int best = -1;
    final double threshold = 0.1;
    for (int tau = minLag; tau <= maxLag; tau++) {
      if (dn[tau] < threshold) {
        while (tau + 1 <= maxLag && dn[tau + 1] < dn[tau]) tau++;
        best = tau;
        break;
      }
    }
    if (best < 0) {
      best = minLag;
      for (int tau = minLag; tau <= maxLag; tau++) {
        if (dn[tau] < dn[best]) best = tau;
      }
      if (dn[best] > 0.5) return 0; // not periodic enough to trust
    }

    // parabolic interpolation for a fractional period
    double refined = best;
    if (best > 1 && best < maxLag + 1) {
      double a = dn[best - 1], b = dn[best], c = dn[best + 1];
      double denom = a - 2 * b + c;
      if (Math.abs(denom) > 1e-12) refined = best + 0.5 * (a - c) / denom;
    }
    return (float) refined;
  }

  /**
   * Period in samples (fractional), or 0 if nothing periodic was found.
   */
  public static float detectPeriod(float[] x, float[] y, int n, float minPeriod, float maxPeriod) {
    // Search a decimated copy first, so long periods stay cheap...
    int factor = Math.max(1, (int) Math.ceil(maxPeriod / 512.0f));
    if (factor == 1) return yinPeriod(x, y, n, minPeriod, maxPeriod);

    int m = n / factor;
    int offset = n - m * factor; // line the blocks up with the newest sample
    float[] xd = new float[m];
    float[] yd = new float[m];
    for (int i = 0; i < m; i++) {
      float sx = 0, sy = 0;
      for (int k = 0; k < factor; k++) {
        sx += x[offset + i * factor + k];
        sy += y[offset + i * factor + k];
      }
      xd[i] = sx / factor;
      yd[i] = sy / factor;
    }
    float coarse = yinPeriod(xd, yd, m, minPeriod / factor, maxPeriod / factor);
    if (coarse <= 0) return 0;

    // ...then refine around its answer at full resolution.
    int center = Math.round(coarse * factor);
    int lo = Math.max(2, center - 2 * factor);
    int hi = center + 2 * factor;
    int window = Math.min(n - hi - 2, 1024);
    if (window < 64) return coarse * factor;
    int t0 = n - hi - 2 - window;

    double[] d = new double[hi - lo + 3];
    for (int tau = lo - 1; tau <= hi + 1; tau++) {
      if (tau < 1) continue;
      double sum = 0;
      for (int t = t0; t < t0 + window; t++) {
        double dx = x[t] - x[t + tau];
        double dy = y[t] - y[t + tau];
        sum += dx * dx + dy * dy;
      }
      d[tau - lo + 1] = sum;
    }
    int best = lo;
    for (int tau = lo; tau <= hi; tau++) {
      if (d[tau - lo + 1] < d[best - lo + 1]) best = tau;
    }
    double a = d[best - lo], b = d[best - lo + 1], c = d[best - lo + 2];
    double denom = a - 2 * b + c;
    double refined = best;
    if (best - 1 >= 1 && Math.abs(denom) > 1e-12) refined = best + 0.5 * (a - c) / denom;
    return (float) refined;
  }

  /**
   * How well the end of the signal repeats after period samples:
   * 0 for a perfect loop, around 1 for no relation at all.
   */
  public static float periodError(float[] x, float[] y, int n, float period) {
    int p = Math.round(period);
    if (p < 1 || p + 16 > n) return 1;
    int window = Math.min(n - p, 1024);
    int t0 = n - p - window;

    double mx = 0, my = 0;
    for (int t = t0; t < t0 + window + p; t++) {
      mx += x[t];
      my += y[t];
    }
    mx /= window + p;
    my /= window + p;

    double diff = 0, energy = 0;
    for (int t = t0; t < t0 + window; t++) {
      double ax = x[t] - mx, ay = y[t] - my;
      double bx = x[t + p] - mx, by = y[t + p] - my;
      diff += (ax - bx) * (ax - bx) + (ay - by) * (ay - by);
      energy += ax * ax + ay * ay + bx * bx + by * by;
    }
    return energy > 1e-12 ? (float) (diff / energy) : 1;
  }

  /**
   * The shapes as an SVG of open (or closed) paths.
   */
  public static String toSvg(List<XYPolyline> shapes, float width, float height, int strokeColor, float strokeWidth) {
    StringBuilder svg = new StringBuilder();
    svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(num(width)).append("\" height=\"").append(num(height))
      .append("\" viewBox=\"0 0 ").append(num(width)).append(" ").append(num(height)).append("\">\n");
    String color = String.format("#%02x%02x%02x", (strokeColor >> 16) & 0xff, (strokeColor >> 8) & 0xff, strokeColor & 0xff);

    for (XYPolyline shape : shapes) {
      if (shape.size() < 2) continue;
      svg.append("  <path fill=\"none\" stroke=\"").append(color).append("\" stroke-width=\"").append(num(strokeWidth))
        .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M");
      for (int i = 0; i < shape.size(); i++) {
        PVector v = shape.get(i);
        svg.append(i == 0 ? " " : " L ").append(String.format(Locale.US, "%.2f %.2f", v.x, v.y));
      }
      if (shape.isClosed()) svg.append(" Z");
      svg.append("\"/>\n");
    }
    svg.append("</svg>\n");
    return svg.toString();
  }

  public static boolean saveSvg(PApplet parent, String path, List<XYPolyline> shapes, float width, float height) {
    return saveSvg(parent, path, shapes, width, height, 0xff000000, 1);
  }

  /**
   * Save shapes as an SVG. With a PApplet, path is relative to the sketch folder.
   */
  public static boolean saveSvg(PApplet parent, String path, List<XYPolyline> shapes, float width, float height,
    int strokeColor, float strokeWidth) {
    String svg = toSvg(shapes, width, height, strokeColor, strokeWidth);
    try {
      OutputStream out;
      if (parent != null) {
        out = parent.createOutput(path);
      } else {
        File file = new File(path).getAbsoluteFile();
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        out = new FileOutputStream(file);
      }
      if (out == null) return false;
      out.write(svg.getBytes("UTF-8"));
      out.close();
      return true;
    } catch (IOException e) {
      System.err.println("XYDecoder: couldn't write " + path + ": " + e.getMessage());
      return false;
    }
  }

  // like the << operator: 512 rather than 512.0
  static String num(float v) {
    return v == Math.rint(v) && Math.abs(v) < 1e9 ? String.valueOf((long) v) : String.valueOf(v);
  }

}
