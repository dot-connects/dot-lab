// Invite landing page for /j: shows the code from the URL and hands it to the app.
export function renderJoinPage(env) {
  return `<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>어디댔어 가족 초대</title>
<style>
*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#e9e7e3;color:#000;font-family:system-ui,-apple-system,"Noto Sans KR",sans-serif}
main{width:100%;max-width:420px;padding:24px 16px;text-align:center}
h1{font-size:22px;margin:0 0 24px}
#code{font-size:34px;font-weight:700;letter-spacing:.06em;margin:0 0 20px;word-break:break-all;font-variant-numeric:tabular-nums}
button,a.btn{display:block;width:100%;margin:0 0 12px;padding:16px;border:2px solid #000;border-radius:12px;background:transparent;color:#000;font-size:17px;font-weight:700;text-decoration:none;font-family:inherit}
a.go{background:#ff6a13;border-color:#ff6a13;color:#1a0f06}
p{font-size:14px;margin:16px 0 0;line-height:1.5}
</style></head>
<body><main>
<h1>어디댔어 가족 초대</h1>
<div id="code" aria-live="polite"></div>
<button id="copy" type="button">코드 복사</button>
<a id="open" class="btn go" href="#">앱으로 열기</a>
<p>앱이 없으면 설치한 뒤 이 코드를 붙여 넣으세요</p>
</main>
<script>
(function () {
  var q = new URLSearchParams(location.search);
  var code = (location.hash.slice(1) || q.get("c") || "").trim();
  var codeEl = document.getElementById("code");
  codeEl.textContent = code;
  var intent = "intent://dot-lab-parking.dotconnects.workers.dev/j?c=" + encodeURIComponent(code) +
    "#Intent;scheme=https;package=kr.dot.wherepark;S.browser_fallback_url=" + encodeURIComponent(location.href) + ";end";
  document.getElementById("open").href = intent;
  document.getElementById("copy").addEventListener("click", function () {
    var btn = this;
    function done() { btn.textContent = "복사됨"; setTimeout(function () { btn.textContent = "코드 복사"; }, 1500); }
    function fallback() {
      var r = document.createRange(); r.selectNodeContents(codeEl);
      var s = getSelection(); s.removeAllRanges(); s.addRange(r);
      try { document.execCommand("copy"); done(); } catch (e) {}
    }
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(code).then(done, fallback);
    else fallback();
  });
})();
</script></body></html>`;
}
