package twoscilloscopeP5;

import processing.core.*;
import java.util.ArrayList;

/**
 * A run of points, open or closed, standing in for openFrameworks' ofPolyline. Shapes go
 * into XYscope as polylines (or PShapes) and come back out of XYDecoder and XYTransformer
 * as polylines, in canvas pixels.
 * <p>simplify() and getPerimeter() behave as ofPolyline's do, so decoding gives the same
 * strokes as the openFrameworks addon.</p>
 */

public class XYPolyline {

  public ArrayList<PVector> points = new ArrayList<PVector>();
  boolean closed = false;

  public XYPolyline() {
  }

  public XYPolyline(XYPolyline other) {
    for (PVector p : other.points) points.add(p.copy());
    closed = other.closed;
  }

  public XYPolyline copy() {
    return new XYPolyline(this);
  }

  public void addVertex(float x, float y) {
    points.add(new PVector(x, y));
  }

  public void addVertex(float x, float y, float z) {
    points.add(new PVector(x, y, z));
  }

  public void addVertex(PVector p) {
    points.add(p.copy());
  }

  public ArrayList<PVector> getVertices() {
    return points;
  }

  public PVector get(int i) {
    return points.get(i);
  }

  public int size() {
    return points.size();
  }

  public void clear() {
    points.clear();
    closed = false;
  }

  public boolean isClosed() {
    return closed;
  }

  public void setClosed(boolean _closed) {
    closed = _closed;
  }

  /**
   * Length along the points, including the closing segment of a closed polyline.
   */
  public float getPerimeter() {
    int n = points.size();
    if (n < 2) return 0;
    float length = 0;
    for (int i = 0; i + 1 < n; i++) length += PVector.dist(points.get(i), points.get(i + 1));
    if (closed) length += PVector.dist(points.get(n - 1), points.get(0));
    return length;
  }

  /**
   * Draws the outline with the current stroke, never filled, like ofPolyline::draw().
   */
  public void draw(PGraphics g) {
    if (points.size() < 2) return;
    g.pushStyle();
    g.noFill();
    g.beginShape();
    for (PVector p : points) g.vertex(p.x, p.y);
    g.endShape(closed ? PConstants.CLOSE : PConstants.OPEN);
    g.popStyle();
  }

  /**
   * Vertex reduction, then Douglas-Peucker, with tolerance in the points' units.
   * From ofPolyline::simplify(), after Dan Sunday's softSurfer algorithm.
   */
  public void simplify(float tol) {
    int n = points.size();
    if (n < 2) return;

    float tol2 = tol * tol;
    PVector[] vt = new PVector[n];
    int[] mk = new int[n];

    // stage 1: vertex reduction within tolerance of the prior vertex cluster
    vt[0] = points.get(0);
    int k = 1;
    int pv = 0;
    for (int i = 1; i < n; i++) {
      if (dist2(points.get(i), points.get(pv)) < tol2) continue;
      vt[k++] = points.get(i);
      pv = i;
    }
    if (pv < n - 1) vt[k++] = points.get(n - 1); // finish at the end

    // stage 2: Douglas-Peucker polyline simplification
    mk[0] = mk[k - 1] = 1; // mark the first and last vertices
    simplifyDP(tol, vt, 0, k - 1, mk);

    ArrayList<PVector> simplified = new ArrayList<PVector>();
    for (int i = 0; i < k; i++) {
      if (mk[i] != 0) simplified.add(vt[i]);
    }
    points = simplified;
  }

  static float dist2(PVector a, PVector b) {
    float dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
    return dx * dx + dy * dy + dz * dz;
  }

  static void simplifyDP(float tol, PVector[] v, int j, int k, int[] mk) {
    if (k <= j + 1) return; // there is nothing to simplify

    // check for adequate approximation by the segment from v[j] to v[k]
    int maxi = j;
    float maxd2 = 0;
    float tol2 = tol * tol;
    PVector p0 = v[j], p1 = v[k];
    float ux = p1.x - p0.x, uy = p1.y - p0.y, uz = p1.z - p0.z;
    double cu = ux * ux + uy * uy + uz * uz; // segment length squared

    // test each vertex for max distance from the segment
    for (int i = j + 1; i < k; i++) {
      float wx = v[i].x - p0.x, wy = v[i].y - p0.y, wz = v[i].z - p0.z;
      float cw = wx * ux + wy * uy + wz * uz;
      float dv2;
      if (cw <= 0) {
        dv2 = dist2(v[i], p0);
      } else if (cu <= cw) {
        dv2 = dist2(v[i], p1);
      } else {
        float b = (float) (cw / cu);
        float bx = p0.x + ux * b - v[i].x, by = p0.y + uy * b - v[i].y, bz = p0.z + uz * b - v[i].z;
        dv2 = bx * bx + by * by + bz * bz;
      }
      if (dv2 <= maxd2) continue;
      maxi = i;
      maxd2 = dv2;
    }

    if (maxd2 > tol2) {
      // split at the farthest vertex and simplify both halves
      mk[maxi] = 1;
      simplifyDP(tol, v, j, maxi, mk);
      simplifyDP(tol, v, maxi, k, mk);
    }
  }

}
