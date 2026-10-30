// Toss build: may the user save the videos yet? One rewarded ad per decorate visit pays for both videos and every
// later re-render; saving also needs the render for the current look (app.js checks D[kind].key === vkey()).
// Events: "earned" (userEarnedReward), "dismissed" (ad closed), "failed" (nothing to show: load / show failed, which
// never blocks the user, operator 10-31), "retry" (다시 보기 tapped).
export const start = () => ({ earned: false, closed: false });
export function step(s, ev) {
  if (ev === "earned" || ev === "failed") return { earned: true, closed: false };
  if (ev === "dismissed") return s.earned ? s : { earned: false, closed: true };
  if (ev === "retry") return { ...s, closed: false };
  return s;
}
export const adOk = s => s.earned;
export const needsRetry = s => !s.earned && s.closed;
