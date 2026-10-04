package twoscilloscopeP5;

// A growable float array for the rolling audio buffers, without boxing.
class FloatArray {

  float[] data;
  int size;

  FloatArray() {
    this(1024);
  }

  FloatArray(int capacity) {
    data = new float[Math.max(16, capacity)];
  }

  void clear() {
    size = 0;
  }

  void ensureCapacity(int capacity) {
    if (capacity > data.length) {
      data = java.util.Arrays.copyOf(data, Math.max(capacity, data.length * 2));
    }
  }

  void add(float v) {
    ensureCapacity(size + 1);
    data[size++] = v;
  }

  void add(float[] src, int offset, int length) {
    ensureCapacity(size + length);
    System.arraycopy(src, offset, data, size, length);
    size += length;
  }

  // Drops the first n values.
  void removeFront(int n) {
    n = Math.min(Math.max(0, n), size);
    if (n == 0) return;
    System.arraycopy(data, n, data, 0, size - n);
    size -= n;
  }

  float[] toArray() {
    return java.util.Arrays.copyOf(data, size);
  }

}
