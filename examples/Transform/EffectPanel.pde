// A small stand-in for ofxGui: one row per effect (click the box to switch
// it on, the name to fold it open) and a slider for each of its settings,
// built from the effect's XYParameters.

class EffectPanel {

  XYEffectChain chain;
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

  void setOpen(int i, boolean isOpen) {
    if (i >= 0 && i < open.length) open[i] = isOpen;
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
        noStroke();
        fill(25);
        rect(x + 10, yy, w - 10, rowH);
        fill(50, 100, 170);
        rect(x + 10, yy, (w - 10) * p.getNormalized(), rowH);
        fill(230);
        text(p.getName() + ": " + (p.isInteger() ? str(p.getInt()) : nf(p.get(), 0, 2)), x + 14, yy + rowH / 2);
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
        if (my >= yy && my < yy + rowH && mx >= x + 10) {
          dragging = p;
          mouseDragged(mx, my);
          return true;
        }
        yy += rowH + 1;
      }
    }
    return false;
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
