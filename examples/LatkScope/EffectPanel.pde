// A small stand-in for ofxGui, from the Transform example: one row per
// effect (click the box to switch it on, the name to fold it open) and a
// slider for each of its settings, built from the effect's XYParameters.
// add() puts another group of sliders below them, always open.

class EffectPanel {

  XYEffectChain chain;
  String groupName = "";
  ArrayList<XYParameter> group = new ArrayList<XYParameter>();
  float x, y;
  float w = 220;
  float rowH = 18;
  boolean[] open;
  XYParameter dragging;

  EffectPanel(XYEffectChain chain, float x, float y) {
    this.chain = chain;
    this.x = x;
    this.y = y;
    open = new boolean[chain.size()];
    for (int i = 0; i < chain.size(); i++) open[i] = chain.get(i).enabled;
  }

  void add(String name, ArrayList<XYParameter> parameters) {
    groupName = name;
    group = parameters;
  }

  void setOpen(int i, boolean isOpen) {
    if (i >= 0 && i < open.length) open[i] = isOpen;
  }

  float getHeight() {
    float h = rowH + 2;
    for (int i = 0; i < chain.size(); i++) {
      h += rowH + 1;
      if (open[i]) h += (rowH + 1) * chain.get(i).parameters.size();
    }
    if (!group.isEmpty()) h += (rowH + 1) * (group.size() + 1);
    return h;
  }

  boolean inside(float mx, float my) {
    return mx >= x && mx <= x + w && my >= y && my < y + getHeight();
  }

  void drawSlider(XYParameter p, float yy) {
    noStroke();
    fill(25);
    rect(x + 10, yy, w - 10, rowH);
    fill(50, 100, 170);
    rect(x + 10, yy, (w - 10) * p.getNormalized(), rowH);
    fill(230);
    text(p.getName() + ": " + (p.isInteger() ? str(p.getInt()) : nf(p.get(), 0, 2)), x + 14, yy + rowH / 2);
  }

  void draw() {
    pushStyle();
    textAlign(LEFT, CENTER);
    float yy = y;
    fill(255);
    text("effects", x, yy + rowH / 2);
    yy += rowH + 2;
    for (int i = 0; i < chain.size(); i++) {
      XYEffect effect = chain.get(i);
      noStroke();
      fill(40);
      rect(x, yy, w, rowH);
      stroke(200);
      fill(effect.enabled ? color(60, 255, 120) : color(0));
      rect(x + 4, yy + 4, rowH - 8, rowH - 8);
      fill(230);
      text((open[i] ? "- " : "+ ") + effect.getName(), x + rowH + 2, yy + rowH / 2);
      yy += rowH + 1;
      if (!open[i]) continue;
      for (XYParameter p : effect.parameters) {
        drawSlider(p, yy);
        yy += rowH + 1;
      }
    }
    if (!group.isEmpty()) {
      noStroke();
      fill(40);
      rect(x, yy, w, rowH);
      fill(230);
      text(groupName, x + 4, yy + rowH / 2);
      yy += rowH + 1;
      for (XYParameter p : group) {
        drawSlider(p, yy);
        yy += rowH + 1;
      }
    }
    popStyle();
  }

  // true if the click landed on the panel
  boolean mousePressed(float mx, float my) {
    if (mx < x || mx > x + w) return false;
    float yy = y + rowH + 2;
    for (int i = 0; i < chain.size(); i++) {
      XYEffect effect = chain.get(i);
      if (my >= yy && my < yy + rowH) {
        if (mx < x + rowH) effect.enabled = !effect.enabled;
        else open[i] = !open[i];
        return true;
      }
      yy += rowH + 1;
      if (!open[i]) continue;
      for (XYParameter p : effect.parameters) {
        if (pressSlider(p, yy, mx, my)) return true;
        yy += rowH + 1;
      }
    }
    if (!group.isEmpty()) {
      if (my >= yy && my < yy + rowH) return true; // the group's title
      yy += rowH + 1;
      for (XYParameter p : group) {
        if (pressSlider(p, yy, mx, my)) return true;
        yy += rowH + 1;
      }
    }
    return false;
  }

  boolean pressSlider(XYParameter p, float yy, float mx, float my) {
    if (my < yy || my >= yy + rowH || mx < x + 10) return false;
    dragging = p;
    mouseDragged(mx, my);
    return true;
  }

  boolean mouseDragged(float mx, float my) {
    if (dragging == null) return false;
    dragging.setNormalized((mx - (x + 10)) / (w - 10));
    return true;
  }

  void mouseReleased() {
    dragging = null;
  }

}
