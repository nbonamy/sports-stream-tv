import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";

if (process.platform !== "darwin")
  throw new Error("Build the Mac release on macOS.");
if (existsSync(".env")) process.loadEnvFile(".env");
process.env.DEBUG = "";
const { sign } = await import("@electron/osx-sign");
const { notarize } = await import("@electron/notarize");
const required = [
  "IDENTIFY_DARWIN_CODE",
  "APPLE_ID",
  "APPLE_PASSWORD",
  "APPLE_TEAM_ID",
];
const missing = required.filter((name) => !process.env[name]);
if (missing.length)
  throw new Error(`Missing signing settings: ${missing.join(", ")}`);
// Never print signing credentials, including in third-party command failures.
const redact = (text) =>
  required.reduce(
    (result, name) => result.replaceAll(process.env[name], "[redacted]"),
    String(text),
  );
function run(command, args) {
  const result = spawnSync(command, args, {
    encoding: "utf8",
    env: { ...process.env, DEBUG: "" },
  });
  if (result.stdout) process.stdout.write(redact(result.stdout));
  if (result.stderr) process.stderr.write(redact(result.stderr));
  if (result.error || result.status !== 0) throw new Error(`${command} failed`);
}
try {
  run("npm", ["run", "pack"]);
  const directory = process.arch === "arm64" ? "mac-arm64" : "mac";
  const appPath = path.resolve("out", directory, "Sports.app");
  if (!existsSync(appPath))
    throw new Error("Packaged Sports.app was not found.");
  console.log("Signing Sports.app…");
  await sign({
    app: appPath,
    identity: process.env.IDENTIFY_DARWIN_CODE,
    optionsForFile: () => ({
      hardenedRuntime: true,
      entitlements: path.resolve("scripts/entitlements.mac.plist"),
    }),
  });
  console.log("Submitting to Apple for notarization…");
  await notarize({
    appPath,
    appleId: process.env.APPLE_ID,
    appleIdPassword: process.env.APPLE_PASSWORD,
    teamId: process.env.APPLE_TEAM_ID,
  });
  run("codesign", ["--verify", "--deep", "--strict", appPath]);
  run("spctl", ["--assess", "--type", "execute", appPath]);
  console.log("Creating Sports DMG…");
  run("npx", ["electron-builder", "--mac", "dmg", "--prepackaged", appPath]);
  const diskImage = path.resolve("out", `Sports-mac-${process.arch}.dmg`);
  if (!existsSync(diskImage))
    throw new Error("Packaged Sports DMG was not found.");
  run("codesign", [
    "--force",
    "--sign",
    process.env.IDENTIFY_DARWIN_CODE,
    "--timestamp",
    diskImage,
  ]);
  console.log("Submitting Sports DMG to Apple for notarization…");
  await notarize({
    appPath: diskImage,
    appleId: process.env.APPLE_ID,
    appleIdPassword: process.env.APPLE_PASSWORD,
    teamId: process.env.APPLE_TEAM_ID,
  });
  run("codesign", ["--verify", "--strict", diskImage]);
  run("xcrun", ["stapler", "validate", diskImage]);
  run("spctl", [
    "--assess",
    "--type",
    "open",
    "--context",
    "context:primary-signature",
    diskImage,
  ]);
  const archive = path.resolve("out", `Sports-mac-${process.arch}.zip`);
  run("ditto", [
    "-c",
    "-k",
    "--keepParent",
    "--sequesterRsrc",
    appPath,
    archive,
  ]);
  console.log(
    `Signed and notarized: ${appPath}\nDisk image: ${diskImage}\nArchive: ${archive}`,
  );
} catch (error) {
  console.error(redact(error instanceof Error ? error.message : error));
  process.exitCode = 1;
}
