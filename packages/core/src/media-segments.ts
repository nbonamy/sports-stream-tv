// Some players transport gzipped MPEG-TS in the RGB bytes of a PNG, not an image.
// Decode bytes directly: canvas/image decoding can change the encoded colors.
const LIMIT = 32 * 1024 * 1024;
const PNG = [137, 80, 78, 71, 13, 10, 26, 10];
const invalid = () => new Error("Unsupported media segment");

async function inflate(
  bytes: Uint8Array,
  format: CompressionFormat,
  limit: number,
  signal?: AbortSignal,
): Promise<Uint8Array> {
  signal?.throwIfAborted();
  const reader = new Blob([new Uint8Array(bytes)])
    .stream()
    .pipeThrough(new DecompressionStream(format))
    .getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  const abort = () => {
    void reader.cancel().catch(() => {});
  };
  signal?.addEventListener("abort", abort, { once: true });
  try {
    while (true) {
      const { value, done } = await reader.read();
      signal?.throwIfAborted();
      if (done) break;
      size += value.length;
      if (size > limit) throw invalid();
      chunks.push(value);
    }
    const out = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) {
      out.set(chunk, offset);
      offset += chunk.length;
    }
    return out;
  } finally {
    signal?.removeEventListener("abort", abort);
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

export async function decodeMediaSegment(
  bytes: Uint8Array,
  signal?: AbortSignal,
): Promise<Uint8Array> {
  signal?.throwIfAborted();
  if (!PNG.every((v, i) => bytes[i] === v)) return bytes;
  if (bytes.length > LIMIT) throw invalid();
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let width = 0,
    height = 0,
    bpp = 0,
    offset = 8,
    ended = false,
    packedSize = 0;
  const idats: Uint8Array[] = [];
  while (offset + 12 <= bytes.length) {
    const size = view.getUint32(offset);
    if (size > bytes.length - offset - 12) throw invalid();
    const type = String.fromCharCode(...bytes.subarray(offset + 4, offset + 8));
    const data = bytes.subarray(offset + 8, offset + 8 + size);
    if (type === "IHDR") {
      if (offset !== 8 || size !== 13) throw invalid();
      width = view.getUint32(offset + 8);
      height = view.getUint32(offset + 12);
      bpp = data[9] === 2 ? 3 : data[9] === 6 ? 4 : 0;
      if (
        !width ||
        !height ||
        !bpp ||
        data[8] !== 8 ||
        data[10] ||
        data[11] ||
        data[12] ||
        (width * bpp + 1) * height > LIMIT
      )
        throw invalid();
    } else if (type === "IDAT") {
      if (!bpp) throw invalid();
      idats.push(data);
      packedSize += size;
    } else if (type === "IEND") {
      if (size !== 0) throw invalid();
      ended = true;
      break;
    }
    offset += size + 12;
  }
  if (!ended || !bpp || !packedSize) throw invalid();
  const packed = new Uint8Array(packedSize);
  offset = 0;
  for (const part of idats) {
    packed.set(part, offset);
    offset += part.length;
  }
  const stride = width * bpp,
    expected = (stride + 1) * height;
  const raw = await inflate(packed, "deflate", expected, signal);
  if (raw.length !== expected) throw invalid();
  const rgb = new Uint8Array(width * height * 3);
  let previous = new Uint8Array(stride),
    row = new Uint8Array(stride),
    src = 0,
    dst = 0;
  for (let y = 0; y < height; y++) {
    const filter = raw[src++];
    if (filter > 4) throw invalid();
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? row[i - bpp] : 0,
        b = previous[i],
        c = i >= bpp ? previous[i - bpp] : 0;
      let predictor = 0;
      if (filter === 1) predictor = a;
      else if (filter === 2) predictor = b;
      else if (filter === 3) predictor = (a + b) >> 1;
      else if (filter === 4) {
        const p = a + b - c,
          pa = Math.abs(p - a),
          pb = Math.abs(p - b),
          pc = Math.abs(p - c);
        predictor = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      }
      row[i] = raw[src++] + predictor;
    }
    for (let i = 0; i < stride; i += bpp) {
      rgb[dst++] = row[i];
      rgb[dst++] = row[i + 1];
      rgb[dst++] = row[i + 2];
    }
    [previous, row] = [row, previous];
    if ((y & 31) === 31) {
      await new Promise((r) => setTimeout(r, 0));
      signal?.throwIfAborted();
    }
  }
  if (
    rgb.length < 12 ||
    new TextDecoder().decode(rgb.subarray(0, 8)) !== "TIKTIKPX"
  )
    throw invalid();
  const size = new DataView(rgb.buffer).getUint32(8);
  if (!size || size > rgb.length - 12 || rgb[12] !== 31 || rgb[13] !== 139)
    throw invalid();
  const ts = await inflate(rgb.subarray(12, 12 + size), "gzip", LIMIT, signal);
  if (ts.length < 188 || ts.length % 188 !== 0) throw invalid();
  for (let i = 0; i < ts.length; i += 188) if (ts[i] !== 0x47) throw invalid();
  return ts;
}
