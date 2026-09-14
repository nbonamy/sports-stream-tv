export function rangeHeader(range?: [number, number]): string | undefined {
  if (range === undefined) return undefined;
  if (
    !Array.isArray(range) ||
    range.length !== 2 ||
    !range.every(Number.isSafeInteger) ||
    range[0] < 0 ||
    range[1] <= range[0]
  )
    throw new Error("Invalid range");
  return `bytes=${range[0]}-${range[1] - 1}`;
}
