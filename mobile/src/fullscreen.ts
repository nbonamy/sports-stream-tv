type MobileVideo = HTMLVideoElement & {
  webkitDisplayingFullscreen?: boolean;
  webkitEnterFullscreen?: () => void;
  webkitExitFullscreen?: () => void;
};
export async function fullscreen(enabled?: boolean): Promise<boolean> {
  const video = document.querySelector<MobileVideo>(".player video");
  const wasFullscreen =
    !!document.fullscreenElement || !!video?.webkitDisplayingFullscreen;
  const next = enabled ?? !wasFullscreen;
  if (next === wasFullscreen) return wasFullscreen;
  if (!next) {
    if (document.fullscreenElement) await document.exitFullscreen();
    else video?.webkitExitFullscreen?.();
  } else {
    const player = document.querySelector<HTMLElement>(".player");
    if (player?.requestFullscreen) await player.requestFullscreen();
    else video?.webkitEnterFullscreen?.();
  }
  return wasFullscreen;
}
