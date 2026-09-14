import { DotReporter } from "vitest/node";

export default class SilentReporter extends DotReporter {
  onTestCaseReady() {}

  onTestCaseResult() {}

  onTestRunEnd() {
    setTimeout(() => console.log("[TESTS:DONE]"), 100);
  }
}
