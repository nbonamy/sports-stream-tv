import { cp, mkdir, copyFile } from "node:fs/promises";
export async function prepareAssets() {
  await cp("../assets/sports/drawable-nodpi", "build/public/art", {
    recursive: true,
  });
  await mkdir("build/public/fonts", { recursive: true });
  for (const font of ["lato_regular.ttf", "lato_bold.ttf", "lato_black.ttf"])
    await copyFile(
      `../android/app/src/main/res/font/${font}`,
      `build/public/fonts/${font}`,
    );
  await copyFile("../licenses/Lato-OFL.txt", "build/public/fonts/LICENSE.txt");
}
