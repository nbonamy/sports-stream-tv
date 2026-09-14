import { execFileSync } from "node:child_process";
import { mkdir, readFile, writeFile, rm } from "node:fs/promises";
import { load } from "cheerio";

// Keep the Android vector as the source; only add the Mac icon's rounded mask.
const $ = load(
  await readFile("../android/app/src/main/res/drawable/ic_sports.xml", "utf8"),
  { xmlMode: true },
);
const paths = $("path")
  .toArray()
  .map((path) => {
    const node = $(path);
    const attributes = [
      ["d", "android:pathData"],
      ["fill", "android:fillColor"],
      ["stroke", "android:strokeColor"],
      ["stroke-width", "android:strokeWidth"],
    ].flatMap(([svg, android]) => {
      const value = node.attr(android);
      return value
        ? [
            `${svg}="${value === "@android:color/transparent" ? "none" : value}"`,
          ]
        : [];
    });
    return `<path ${attributes.join(" ")}/>`;
  })
  .join("");
await mkdir("build/app.iconset", { recursive: true });
await mkdir("resources", { recursive: true });
await writeFile(
  "build/app-icon.svg",
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-12 -12 132 132"><defs><clipPath id="mask"><rect width="108" height="108" rx="24"/></clipPath></defs><g clip-path="url(#mask)">${paths}</g></svg>`,
);
execFileSync("rsvg-convert", [
  "-w",
  "1024",
  "-h",
  "1024",
  "-o",
  "resources/icon.png",
  "build/app-icon.svg",
]);
for (const size of [16, 32, 128, 256, 512]) {
  for (const scale of [1, 2]) {
    execFileSync(
      "sips",
      [
        "-z",
        String(size * scale),
        String(size * scale),
        "resources/icon.png",
        "--out",
        `build/app.iconset/icon_${size}x${size}${scale === 2 ? "@2x" : ""}.png`,
      ],
      { stdio: "ignore" },
    );
  }
}
execFileSync("iconutil", [
  "-c",
  "icns",
  "build/app.iconset",
  "-o",
  "resources/icon.icns",
]);
await rm("build/app.iconset", { recursive: true });
