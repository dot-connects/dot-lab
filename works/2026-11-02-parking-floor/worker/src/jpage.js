const STYLE = `<style>
*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#e9e7e3;color:#000;font-family:system-ui,-apple-system,"Noto Sans KR",sans-serif}
main{width:100%;max-width:420px;padding:24px 16px;text-align:center}
h1{font-size:22px;margin:0 0 24px}
#code{font-size:34px;font-weight:700;letter-spacing:.06em;margin:0 0 20px;word-break:break-all;font-variant-numeric:tabular-nums}
button,a.btn{display:block;width:100%;margin:0 0 12px;padding:16px;border:2px solid #000;border-radius:12px;background:transparent;color:#000;font-size:17px;font-weight:700;text-decoration:none;font-family:inherit}
a.go{background:#ff6a13;border-color:#ff6a13;color:#1a0f06}
p{font-size:14px;margin:16px 0 0;line-height:1.5}
</style>`;

// Invite landing page for /j: shows the code from the URL and hands it to the app.
export function renderJoinPage(env) {
  return `<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>어디댔어 그룹 초대</title>
${STYLE}</head>
<body><main>
<h1>어디댔어 그룹 초대</h1>
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
    "#Intent;scheme=https;package=kr.dot.wherepark;S.browser_fallback_url=" + encodeURIComponent("https://play.google.com/store/apps/details?id=kr.dot.wherepark&referrer=" + encodeURIComponent("code=" + code)) + ";end";
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

// Join request page for /r#<req>.<name>: with the app installed App Links open it before this page; otherwise
// it says the app is needed to accept. [앱으로 열기] passes the request as ?q= (the intent URI uses the fragment itself).
export function renderRequestPage(env) {
  return `<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>어디댔어 그룹 요청</title>
${STYLE}</head>
<body><main>
<h1 id="who">그룹 요청이 왔어요</h1>
<a id="open" class="btn go" href="#">앱으로 열기</a>
<p>받으려면 이 폰에 어디댔어가 있어야 해요. 앱에서 [받기]를 누르면 바로 같이 써요</p>
</main>
<script>
(function () {
  var q = new URLSearchParams(location.search);
  var raw = (location.hash.slice(1) || q.get("q") || "").trim();
  var dot = raw.indexOf("."), name = "";
  try { name = dot > 0 ? decodeURIComponent(raw.slice(dot + 1)) : ""; } catch (e) {}
  if (name) document.getElementById("who").textContent = name.slice(0, 12) + "님이 그룹에 들어오고 싶어 해요";
  var self = location.origin + location.pathname;
  document.getElementById("open").href = "intent://dot-lab-parking.dotconnects.workers.dev/r?q=" + encodeURIComponent(raw) +
    "#Intent;scheme=https;package=kr.dot.wherepark;S.browser_fallback_url=" + encodeURIComponent(self) + ";end";
})();
</script></body></html>`;
}
