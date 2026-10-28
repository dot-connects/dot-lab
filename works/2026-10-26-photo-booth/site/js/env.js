// Where the page runs: in-app browser detection and the hand-off link to Chrome.
const ua = navigator.userAgent;

export const app = /Instagram/.test(ua) ? "Instagram"
  : /Barcelona|Threads/i.test(ua) ? "Threads"
  : /KAKAOTALK/i.test(ua) ? "KakaoTalk"
  : /FBAN|FBAV/.test(ua) ? "Facebook"
  : /; wv\)/.test(ua) ? "앱"             // any other Android WebView (Round 6 #3)
  : "브라우저";
export const inApp = app !== "브라우저";
export const isIOS = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
export const framed = (() => { try { return top !== self; } catch { return true; } })();

// Measured on the operator's phone 10-26 (spike): both hand-offs open Chrome with the same URL.
export function chromeUrl(url) {
  if (app === "KakaoTalk") return "kakaotalk://web/openExternal?url=" + encodeURIComponent(url);
  if (/Android/.test(ua)) return `intent://${url.replace(/^https?:\/\//, "")}#Intent;scheme=https;package=com.android.chrome;end`;
  return url;
}
