import type { CapacitorConfig } from "@capacitor/cli";
const config: CapacitorConfig = {
  appId: "fr.bonamy.sports.mobile",
  appName: "Sports",
  webDir: "dist",
  loggingBehavior: "none",
  ios: {
    contentInset: "never",
    backgroundColor: "#07111d",
    allowsLinkPreview: false,
  },
  android: { backgroundColor: "#07111d", allowMixedContent: false },
};
export default config;
