package twoscilloscopeP5;

import processing.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Hershey single-stroke vector fonts, the text engine from XYscope.java (which credits
 * https://github.com/ixd-hof/HersheyFont).
 * <p>Hershey glyphs are made of open strokes rather than filled outlines, so they draw
 * cleanly with a single beam. All 32 fonts in getFontNames() are built into the library, so
 * load("cursive") works without any data files. load() also takes a path to any other .jhf
 * file, resolved like Processing's loadStrings() when the font has a PApplet.</p>
 * <p>Differences from the Java original:</p>
 * <ul>
 * <li>Glyphs are placed with their left and right bearings, the standard Hershey layout.
 *     XYscope centered each glyph on the pen and then advanced by its width, and its width
 *     had an operator precedence slip (right - left * factor), so spacing here is tighter
 *     and stays proportional at every text size.</li>
 * <li>The .jhf parser counts the vertices each glyph declares, so it copes with glyphs that
 *     wrap onto several lines.</li>
 * </ul>
 */

public class HersheyFont {

  public static class Glyph {
    public int left = 0;
    public int right = 0;
    // Each stroke is a run of points in Hershey units:
    // x right and y down, (0,0) near the middle of a capital letter.
    public ArrayList<float[][]> strokes = new ArrayList<float[][]>();
  }

  /**
   * Hershey units from the top of a capital letter to the baseline.
   */
  public static final float CAP_HEIGHT = 21.0f;

  // In Hershey units, the top of a capital is at y = -12 and the baseline at y = 9.
  static final float HERSHEY_BASELINE = 9.0f;

  static final String[] FONT_NAMES = {
    "astrology", "cursive", "cyrilc_1", "cyrillic", "futural", "futuram", "gothgbt", "gothgrt",
    "gothiceng", "gothicger", "gothicita", "gothitt", "greek", "greekc", "greeks", "japanese",
    "markers", "mathlow", "mathupp", "meteorology", "music", "rowmand", "rowmans", "rowmant",
    "scriptc", "scripts", "symbolic", "timesg", "timesi", "timesib", "timesr", "timesrb"
  };

  PApplet parent;
  ArrayList<Glyph> glyphs = new ArrayList<Glyph>();
  String name = "";

  public HersheyFont() {
    this(null);
  }

  /**
   * The PApplet resolves paths to .jhf files of your own; it can be null.
   */
  public HersheyFont(PApplet _parent) {
    parent = _parent;
    load("futural");
  }

  public static String[] getFontNames() {
    return FONT_NAMES.clone();
  }

  public boolean isLoaded() {
    return !glyphs.isEmpty();
  }

  public String getName() {
    return name;
  }

  /**
   * A built-in font name, or the path to a .jhf file.
   */
  public boolean load(String nameOrPath) {
    boolean isName = Arrays.asList(FONT_NAMES).contains(nameOrPath);
    InputStream in = null;
    try {
      if (isName) {
        in = HersheyFont.class.getResourceAsStream("hershey_fonts/" + nameOrPath + ".jhf");
      } else if (parent != null) {
        in = parent.createInput(nameOrPath);
      } else if (new File(nameOrPath).exists()) {
        in = new FileInputStream(nameOrPath);
      }
      if (in == null) {
        System.err.println("HersheyFont: couldn't find " + nameOrPath);
        return false;
      }
      String jhf = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
      String fontName = isName ? nameOrPath : new File(nameOrPath).getName().replaceFirst("\\.[^.]*$", "");
      return loadFromString(jhf, fontName);
    } catch (IOException e) {
      System.err.println("HersheyFont: couldn't read " + nameOrPath + ": " + e.getMessage());
      return false;
    } finally {
      try {
        if (in != null) in.close();
      } catch (IOException e) {
      }
    }
  }

  public boolean loadFromString(String jhf, String _name) {
    ArrayList<Glyph> parsed = new ArrayList<Glyph>();

    // Each glyph starts with a 5 character id and a 3 character vertex count,
    // followed by that many coordinate pairs. The first pair holds the left and
    // right bearings, and " R" lifts the pen. A long glyph may wrap onto the
    // next line, so keep reading until all of its pairs have been collected.
    StringBuilder data = new StringBuilder();
    int wanted = 0;

    for (String line : jhf.split("\n", -1)) {
      if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
      if (line.isEmpty()) continue;

      if (wanted == 0) {
        if (line.length() < 8) continue;
        try {
          wanted = Integer.parseInt(line.substring(5, 8).trim()) * 2;
        } catch (NumberFormatException e) {
          wanted = 0;
        }
        if (wanted <= 0) {
          wanted = 0;
          continue;
        }
        line = line.substring(8);
      }

      data.append(line, 0, Math.min(line.length(), wanted - data.length()));
      if (data.length() >= wanted) {
        parsed.add(parseGlyph(data));
        data.setLength(0);
        wanted = 0;
      }
    }

    if (parsed.isEmpty()) {
      System.err.println("HersheyFont: no glyphs found in " + (_name == null || _name.isEmpty() ? "font data" : _name));
      return false;
    }

    glyphs = parsed;
    name = _name != null ? _name : "";
    return true;
  }

  // Hershey coordinates are stored as characters, offset from 'R'.
  static int hershey2coord(char c) {
    return c - 'R';
  }

  static Glyph parseGlyph(CharSequence data) {
    Glyph glyph = new Glyph();
    glyph.left = hershey2coord(data.charAt(0));
    glyph.right = hershey2coord(data.charAt(1));
    ArrayList<float[]> stroke = new ArrayList<float[]>();
    for (int i = 2; i + 1 < data.length(); i += 2) {
      if (data.charAt(i) == ' ' && data.charAt(i + 1) == 'R') {
        if (stroke.size() > 1) glyph.strokes.add(stroke.toArray(new float[0][]));
        stroke.clear();
      } else {
        stroke.add(new float[] { hershey2coord(data.charAt(i)), hershey2coord(data.charAt(i + 1)) });
      }
    }
    if (stroke.size() > 1) glyph.strokes.add(stroke.toArray(new float[0][]));
    return glyph;
  }

  /**
   * Glyphs start at ASCII 32 (space).
   */
  public Glyph getGlyph(int codePoint) {
    if (codePoint < 32) return null;
    int index = codePoint - 32;
    return index < glyphs.size() ? glyphs.get(index) : null;
  }

  static String[] splitLines(String text) {
    return text.replace("\r", "").split("\n", -1);
  }

  float getLineWidth(String line, float factor) {
    float width = 0;
    Glyph space = getGlyph(' ');
    for (int c : line.codePoints().toArray()) {
      Glyph glyph = getGlyph(c);
      if (glyph == null) glyph = space;
      if (glyph != null) width += (glyph.right - glyph.left) * factor;
    }
    return width;
  }

  /**
   * Width of the widest line, in pixels.
   */
  public float getWidth(String text, float size) {
    float factor = size / CAP_HEIGHT;
    float width = 0;
    for (String line : splitLines(text)) width = Math.max(width, getLineWidth(line, factor));
    return width;
  }

  public ArrayList<XYPolyline> getStrokes(String text, float x, float y, float size, float leading) {
    return getStrokes(text, x, y, size, leading, PConstants.LEFT, PConstants.TOP);
  }

  /**
   * Lay out a string (with \n for line breaks) as strokes in pixels.
   * size is the height of a capital letter, leading is the distance from one
   * baseline to the next. alignX is LEFT, CENTER or RIGHT; alignY is TOP,
   * CENTER, BOTTOM or BASELINE.
   */
  public ArrayList<XYPolyline> getStrokes(String text, float x, float y, float size, float leading, int alignX, int alignY) {
    ArrayList<XYPolyline> result = new ArrayList<XYPolyline>();
    float factor = size / CAP_HEIGHT;
    String[] lines = splitLines(text);
    float blockHeight = size + (lines.length - 1) * leading;

    // baseline of the first line
    float baseline = y;
    if (alignY == PConstants.TOP) {
      baseline = y + size;
    } else if (alignY == PConstants.CENTER) {
      baseline = y - blockHeight / 2 + size;
    } else if (alignY == PConstants.BOTTOM) {
      baseline = y - (lines.length - 1) * leading;
    }

    Glyph space = getGlyph(' ');
    for (String line : lines) {
      float penX = x;
      if (alignX == PConstants.CENTER) {
        penX -= getLineWidth(line, factor) / 2;
      } else if (alignX == PConstants.RIGHT) {
        penX -= getLineWidth(line, factor);
      }

      for (int c : line.codePoints().toArray()) {
        Glyph glyph = getGlyph(c);
        if (glyph == null) glyph = space;
        if (glyph == null) continue;

        for (float[][] stroke : glyph.strokes) {
          XYPolyline polyline = new XYPolyline();
          for (float[] p : stroke) {
            polyline.addVertex(penX + (p[0] - glyph.left) * factor, baseline + (p[1] - HERSHEY_BASELINE) * factor);
          }
          result.add(polyline);
        }
        penX += (glyph.right - glyph.left) * factor;
      }
      baseline += leading;
    }

    return result;
  }

}
