// English for the web build only (T-0174 6b). The Toss build stays Korean: platform.js is its seam, id !== "web" = off.
// Korean strings are the keys, so ko runs nothing here. DOM text is translated by one observer, not at each call site;
// canvas and share text have no DOM node and call tr() themselves.
// ponytail: exact-match dictionary + a few rules; a new Korean string shows in Korean until it is added here.
import { id } from "./platform.js";

const pickLang = () => {
  if (id !== "web") return "ko";
  const q = new URLSearchParams(location.search).get("lang"); let saved = null;
  try { saved = localStorage.getItem("lab.lang"); } catch {}
  return q === "en" || q === "ko" ? q : saved === "en" || saved === "ko" ? saved : /^ko/i.test(navigator.language) ? "ko" : "en";
};
export const lang = pickLang();
export const EN = lang === "en";

const NAME = "Apart Together";
const shots = n => `${n} shot${+n === 1 ? "" : "s"}`;

const en = {
  // product + film cover
  "따로 같이 네컷": NAME, "따로": "APART", "같이": "TOGETHER", "네컷": "strip", "따로 같이": "Apart Together", "착!": "Snap!",
  "D:OT lab · 사진은 7일 동안만 다운 가능해요": "D:OT lab · photos can be downloaded for 7 days",
  "멀리 있어도 한 칸에": "Miles apart, one frame", "멀리 있어도 한 칸에. 카메라가 필요해서 새 탭에서 열려요.": "Miles apart, one frame. It needs your camera, so it opens in a new tab.",
  "새 탭에서 열기": "Open in a new tab", "친구에게 보내기": "Send to a friend", "링크": "Link", "친구 도착 · 내 뒤에 서요": "Friend arrives · stands right behind you",
  "찍은 칸": "Your shot", "고른 칸": "Picked", "친구 칸": "Friend's frame", "고른 4칸 꺼내는 중": "Pulling out your 4", "링크로 친구에게 보내기": "Send the link to a friend", "착! 둘이 같이 네컷": "Snap! A strip for two", "착! 친구랑 같이 네컷": "Snap! A strip with your friend",
  "처음 온 내가 A, 먼저 찍어요": "You go first", "친구 뒤 빈자리에 내가 찰칵": "You snap into the spot by your friend", "친구는 내 뒤로, 나는 앞": "Friend behind, you in front", "포즈 바꿔 가며 찰칵": "Change poses, snap", "나": "Me", "친구": "Pal",

  // theme, landing, shape
  "테마": "Theme", "어떤 네컷을 만들까?": "What kind of strip?", "눌러서 고르면 바로 시작해": "Tap one to start", "화면 느낌": "Look",
  "필름 레일": "Film Rail", "검은 필름에 라임 프레임, 담백하게": "Black film, lime frame, nothing extra", "말랑 부스": "Soft Booth", "분홍 커튼에 말랑한 마스코트": "Pink curtain, squishy mascot",
  "친구도 같은 느낌으로 봐": "Your friend sees the same look", "‹ 테마": "‹ Theme", "6장 · 4컷": "6 shots · 4 frames", "원격 인생네컷 · 무료 · 가입 없음": "Remote photo booth · free · no sign-up",
  "내가 먼저 찍어요": "You shoot first", "내가 먼저 네컷을 찍고 링크를 보내면,": "Take your strip first and send the link.", "친구가 열자마자 내 옆에 서서 찍어.": "Your friend opens it and stands right next to you.",
  "아까 찍은 네컷 이어 하기": "Continue the strip you started", "먼저 찍기": "Shoot first", "6장 · 4컷 · 링크": "6 shots · 4 frames · link", "모양": "Shape",
  "어떤": "Which", "으로 찍을까?": "shape?", "네컷 모양": "Strip shape", "가로": "Landscape", "가로 사진 4장": "4 landscape photos", "길게 한 줄": "One long strip", "세로": "Portrait", "세로 사진 4장": "4 portrait photos", "옆으로 한 줄": "One row across",
  "친구도 같은 모양으로 찍어": "Your friend shoots the same shape", "가로로 찍기": "Shoot landscape", "세로로 찍기": "Shoot portrait", "다음": "Next",
  "FROM 친구": "FROM A FRIEND", "친구가 옆자리를": "Your friend saved", "비워 뒀어요": "you a spot", "친구가 먼저 찍어 뒀어요": "Your friend already took theirs",
  "친구 옆에 서서 6장 찍고 4장 고르면 완성!": "Stand next to them, take 6, pick 4. Done!", "2분 · 가입 없음 · 카메라만 켜면 돼": "2 minutes · no sign-up · just your camera",
  "다 찍고 올리면": "Once it's uploaded,", "너도 친구도": "you and your friend", "프레임·필터·스티커·보정으로 꾸며서": "can decorate with frames, filters, stickers and retouching", "각자 저장": "and each save your own", "할 수 있어": "",
  "바로 찍기": "Shoot now", "친구 옆 6장": "6 next to your friend", "준비됐어?": "Ready?",

  // shooting
  "소리": "Sound", "소리 켬": "Sound on", "소리 끔": "Sound off", "원본": "Original", "보기": "view", "방금 찍은 사진": "The photo you just took", "2초 녹화": "2 s clip", "6장 준비 중…": "Preparing 6 shots…",
  "카메라 켜는 중…": "Starting camera…", "카메라 바꾸는 중…": "Switching camera…", "배경 지우개 준비 중…": "Loading background eraser…", "알림 닫기": "Close notice", "사진은 찍은 뒤 고급 누끼로 다듬어져요": "Cutouts get refined after you shoot",
  "셔터 한 번이면 6장을 이어서 찍어 · 한 장에 5초": "One tap takes all 6 · 5 seconds each", "찍을 때 보기": "Camera view", "내 카메라 배경": "My background", "친구 배경에 같이": "On your friend's background", "좌우반전": "Mirror",
  "6장 이어서 찍기": "Take 6 in a row", "앞/뒤 카메라": "Front/back camera", "친구 자리를 생각해서 한쪽으로 비켜서 찍어 줘": "Stand to one side and leave room for your friend",
  "브이!": "Peace sign!", "반쪽 하트": "Half a heart", "꽃받침": "Flower pose", "깜짝 놀란 척": "Act surprised", "윙크 한 번": "Wink", "마음대로!": "Anything goes!",
  "찍기": "Shoot", "고르기": "Pick", "보내기": "Send", "완성": "Done", "꾸미기": "Decorate", "친구 옆": "Next to friend", "올리기": "Upload", "내 링크": "My link", "크롬으로": "To Chrome", "다 골랐어! 맘에 들어?": "All picked! Happy with it?",
  "앗, 찍다가 멈췄어. 셔터를 다시 눌러 줘": "Oops, it stopped. Tap the shutter again",

  // messages
  "앗, 연결이 안 돼": "Oops, no connection", "인터넷 연결을 확인하고 다시 해 줘.": "Check your connection and try again.", "다시 해 보기": "Try again", "올리다 멈췄어": "Upload stopped", "찍은 사진이 이 화면에 남아 있지 않아. 새로 찍어 줘.": "Your photos are no longer on this screen. Please shoot again.",
  "다시 찍기": "Shoot again", "아직 친구가 올리는 중이에요": "Your friend is still uploading", "조금 뒤에 이 링크를 다시 열어 줘.": "Open this link again in a moment.", "다시 확인": "Check again", "Safari에서 열기": "Open in Safari",
  "7일이 지나 사라진 링크예요": "This link expired after 7 days", "링크는 7일 뒤 사진·영상과 함께 지워져요.": "Links are deleted after 7 days, with the photos and videos.", "새로 찍기": "Start new", "없는 링크예요": "Link not found", "지워졌거나 주소가 조금 틀렸을 수 있어요.": "It was deleted, or the address is slightly off.",
  "앗, 카메라가 안 켜져": "Oops, the camera won't start", "토스 앱 설정에서 카메라 권한을 켜 줘. 휴대폰 설정 → 애플리케이션 → 토스 → 권한 → 카메라.": "Allow the camera for Toss in your phone settings.", "다른 앱이 카메라를 쓰고 있으면 끄고 다시 해 줘.": "If another app is using the camera, close it and try again.",
  "카메라가 대답을 안 해": "The camera isn't responding", "이 앱에서 카메라가 멈췄어. 크롬에서 열면 바로 돼.": "The camera stopped in this app. It works right away in Chrome.",
  "카메라 권한을 허용해 줘. 주소창 왼쪽 aA(또는 ⋯) → 웹사이트 설정 → 카메라 → 허용.": "Allow the camera: aA (or ⋯) next to the address bar → Website Settings → Camera → Allow.", "카메라 권한을 허용해 줘. 주소창 옆 자물쇠 → 카메라 → 허용.": "Allow the camera: lock icon next to the address bar → Camera → Allow.",
  "오른쪽 위 공유 메뉴 → Safari로 열기 → 같은 링크에서 다시 해 줘.": "Share menu at the top right → Open in Safari → try the same link again.", "크롬에서 열기": "Open in Chrome", "여기서 다시 해 보기": "Try again here", "앗, 사진을 못 불러왔어": "Oops, couldn't load the photos",

  // pick, upload, share
  "고른 4장이야. 이대로 올릴까?": "These are your 4. Upload them?", "배경 미리보기": "Background preview", "보정": "Retouch", "이대로 올리기": "Upload these", "고화질 누끼": "HD cutout", "고화질 누끼가 아직 만들어지는 중이야": "The HD cutout is still being made",
  "조금만 기다리기": "Wait a moment", "기다리는 중…": "Waiting…", "지금 올리기": "Upload now", "맘에 드는": "Pick your", "4장": "best 4", "골라 줘": "", "찍은 6장": "Your 6 shots", "고른 순서 미리보기": "Picked order preview",
  "누른 순서대로 위에서부터 들어가 · 다시 누르면 빠져": "Fill from the top in tap order · tap again to remove", "누른 순서대로 왼쪽부터 들어가 · 다시 누르면 빠져": "Fill from the left in tap order · tap again to remove", "전체 다시 찍기 (한 번만)": "Reshoot all (once only)", "이 4장으로": "Use these 4",
  "배경 켜기": "Background on", "배경 끄기": "Background off", "친구 배경": "Friend's background", "내 배경": "My background", "배경 없음": "No background", "4장까지만 골라. 뺄 사진을 먼저 눌러 줘": "4 is the limit. Tap one to remove it first",
  "올리는 중… 0%": "Uploading… 0%", "사진 4장이랑 짧은 영상을 올리고 있어": "Uploading 4 photos and short clips", "앗, 올리다 끊겼어.": "Oops, the upload was cut off.", "앗, 올리다 멈췄어": "Oops, the upload stopped", "다시 올리기": "Upload again", "이 화면을 닫거나 새로고침하면 찍은 사진이 사라져요": "Closing or refreshing this screen loses your photos",
  "파일이 너무 커. 다시 찍어 줘.": "The file is too big. Please shoot again.", "인터넷이 끊겼나 봐. 다시 눌러 줘.": "Your connection dropped. Tap again.", "다 올렸어! 100%": "Uploaded! 100%", "다른 친구가 먼저 찍었어요. 둘이 찍은 네컷이야": "Another friend got there first. Here's the strip you two made",
  "다 올렸어!": "Uploaded!", "친구한테 보내자": "Send it to your friend", "친구가 아직": "Your friend", "안 찍었어요": "hasn't shot yet", "완성됐어!": "It's done!", "친구한테 알려 주자": "Tell your friend",
  "이 링크를 친구한테 보내 줘.": "Send this link to your friend.", "친구가 찍으면 이 링크에서 바로 열려.": "It opens on this link once they shoot.", "이 링크를 보내면 친구도 우리 네컷을 열어 봐.": "Send this link so your friend can open your strip.", "꾸미기는 각자 폰에서 따로, 저장도 각자 해.": "You each decorate and save on your own phone.",
  "친구가 이 링크를 열면 네 옆에 서서 찍어.": "When your friend opens this link, they stand next to you.", "너는 찍은 자리 그대로 들어가.": "You stay right where you shot.", "둘 다 찍으면 사진이랑 영상이 열려.": "Once you've both shot, the photos and videos open.", "친구가 이 링크를 열면 네 옆 빈자리에 서서 찍어.": "When your friend opens this link, they take the spot next to you.",
  "친구가 찍어 올리면": "When your friend uploads,", "둘 다": "you can both", "꾸미러 가기": "Go decorate", "친구한테 링크 보내기": "Send your friend the link", "완성됐어 링크 보내기": "Send the finished link", "친구가 찍었는지 다시 확인": "Check whether your friend shot",
  "혹시 친구가 보낸 링크야? 여기서 찍기 →": "Did a friend send you this? Shoot here →", "직접 찍어 보기(테스트용)": "Shoot it yourself (test)", "링크 복사": "Copy link", "복사가 안 되면 길게 눌러 복사": "If that fails, long-press to copy", "복사했어! ♥": "Copied! ♥", "아래 링크를 길게 눌러 복사해 줘": "Long-press the link below to copy it",
  "내 옆자리 비워 뒀어 · 2분이면 돼 · 가입 없음": "I saved you a spot · takes 2 minutes · no sign-up", "완성됐어! 우리 네컷 열어 봐 · 크롬에서 열면 꾸미고 저장할 수 있어": "It's done! Open our strip · decorate and save it in Chrome", "보냈어! 이제 꾸미러 가자": "Sent! Now go decorate", "보냈어! 친구가 찍으면 이 링크에서 열려": "Sent! It opens on this link once your friend shoots",
  "크롬에서 열어 꾸며 줘": "Open in Chrome to decorate", "이 앱 안에서는 꾸미고 저장하기가 잘 안 돼요.": "Decorating and saving don't work well inside this app.", "크롬에서 같은 링크가 열려요.": "The same link opens in Chrome.", "오른쪽 위(또는 아래) ⋯ 메뉴 → ‘Safari로 열기’": "⋯ menu at the top (or bottom) right → 'Open in Safari'", "(‘다른 브라우저로 열기’)를 눌러 줘.": "(or 'Open in another browser').", "안 보이면 링크를 복사해서 Safari에 붙여 넣어 줘.": "Don't see it? Copy the link and paste it into Safari.",
  "복사했어! 크롬이나 Safari 주소창에 붙여 넣어 줘": "Copied! Paste it into Chrome or Safari",

  // decorate
  "더 보기": "More", "짠, 완성!": "Ta-da, done!", "영상": "Video", "저장하기": "Save", "완성된 네컷": "Finished strip", "붙인 스티커": "Placed stickers", "네컷 만드는 중": "Making your strip", "앗, 네컷을 못 만들었어": "Oops, couldn't make the strip", "누구 보정": "Who to retouch", "내 크기·위치": "My size and position", "바꿀 칸": "Frame to change", "크기": "Size",
  "사진 속 내 칸을 한 손가락으로 끌면 옮겨져 · 두 손가락은 확대": "Drag yourself with one finger · pinch to zoom", "원래대로": "Reset", "배경": "Background", "프레임": "Frame", "필터": "Filter", "필터 종류": "Filter type", "필터 세기": "Filter strength", "테두리": "Outline", "테두리 색": "Outline color", "테두리 모양": "Outline style", "테두리 두께": "Outline width",
  "문구": "Caption", "한 줄 문구": "One-line caption", "예: D+100, 우리 오늘": "e.g. Day 100, us today", "비워 두면 ‘따로 같이 네컷’이 들어가": `Left empty, it says '${NAME}'`, "글씨체": "Font", "스티커": "Stickers", "스티커 종류": "Sticker type", "클린": "Clean", "다이어리": "Diary", "이모지": "Emoji", "끌어서 옮기고": "Drag to move,", "두 손가락으로 크기·각도": "pinch to resize and rotate", "붙일 스티커": "Stickers to add", "없음": "None",
  "보낸 사람": "Sender", "받은 사람": "Receiver", "보낸 사람 배경": "Sender's background", "받은 사람 배경": "Receiver's background", "4칸 모두": "All 4", "내 배경에선 내가 두 번 보여서 배경을 바꿨어": "You'd appear twice on your background, so it was changed", "안 함": "Off", "살짝": "Light", "많이": "Strong", "피부": "Skin", "갸름": "Slim", "눈": "Eyes",
  "보라 부스": "Purple booth", "분홍 부스": "Pink booth", "흰 벽": "White wall", "흑백 부스": "B&W booth", "하늘": "Sky", "흰색": "White", "분홍": "Pink", "노랑": "Yellow", "민트": "Mint", "보라": "Purple", "검정": "Black", "실선": "Solid", "점선": "Dotted", "반짝이": "Glitter", "얇게": "Thin", "보통": "Medium", "두껍게": "Thick",
  "고딕": "Sans", "크롬": "Chrome", "손글씨": "Handwriting", "화이트": "White", "핑크": "Pink", "체크": "Check", "리본": "Ribbon", "딸기": "Strawberry", "필름": "Film", "우리 둘": "Us two", "블랙": "Black", "미니멀": "Minimal", "지명수배": "Wanted", "생일": "Birthday", "라임": "Lime", "모눈": "Grid", "스크랩": "Scrap",
  "★ 지명수배 ★": "★ WANTED ★", "현상금  REWARD": "REWARD", "오늘의 기록": "Today's record",
  "뽀샤시": "Soft glow", "흑백": "B&W", "그레이": "Gray", "따뜻": "Warm", "시원": "Cool", "약": "Low", "중": "Mid", "강": "High",
  "하트": "Heart", "별": "Star", "반짝": "Sparkle", "왕관": "Crown", "꽃": "Flower", "찰칵": "Click", "찰칵!": "Snap!", "최고": "Best", "우정": "BFF", "♥ 말풍선": "♥ bubble", "화살표": "Arrow", "구름": "Cloud", "스마일": "Smile", "클로버": "Clover", "하트 둘": "Two hearts", "고양이 귀": "Cat ears", "볼터치": "Blush",
  "번쩍": "Flash", "별표": "Asterisk", "마스킹 분홍": "Pink tape", "마스킹 민트": "Mint tape", "마스킹 노랑": "Yellow tape", "낙서 하트": "Doodle heart", "낙서 별": "Doodle star", "뱅글 화살표": "Swirl arrow", "도장": "Stamp", "오늘": "Today", "들꽃": "Wildflower", "좋아": "Like",

  // saving
  "영상 2개 받아 가!": "Grab your 2 videos!", "움직이는 네컷": "Moving strip", "네 칸이 살아 움직여요": "All four frames move", "보너스": "Bonus", "메이킹 영상": "Making-of video", "따로 찍은 순간까지": "Including the moments you shot apart",
  "만드는 동안 광고 하나만 봐 줄래?": "Watch one ad while it's being made?", "광고 보는 동안에도 영상 2개를 계속 만들고 있어. 다 되면 바로 보낼 수 있어! 광고가 이 앱을 무료로 지켜 줘요": "Both videos keep rendering during the ad. Ads keep this free.", "좋아, 2개 다 만들어 줘": "OK, make both", "광고가 끝나야 저장할 수 있어요": "You can save after the ad ends", "다시 보기": "Watch again",
  "SNS에 올려서 자랑해 줘 🙌": "Show it off on social 🙌", "링크 복사했어!": "Link copied!", "링크도 붙여 주면 더 좋아": "Add the link too", "크롬에서 열어 저장하기": "Open in Chrome to save", "이 앱 안에서는 저장이 안 돼요. 오른쪽 위 공유 메뉴 → Safari로 열기 → 같은 화면에서 저장해 줘": "Saving doesn't work in this app. Share menu at the top right → Open in Safari → save from the same screen",
  "닫기": "Close", "다른 친구랑도 찍기": "Shoot with another friend", "시켜본 것 더 보기 →": "More experiments →", "영상 만드는 중": "Making video", "프레임 끼우는 중": "Fitting frames", "리본 묶는 중": "Tying ribbons", "반짝이 뿌리는 중": "Sprinkling glitter", "기다리는 중": "Waiting", "보내는 중…": "Sending…", "보내기·저장": "Send/save", "보정 준비 중": "Preparing retouch", "영상 받아 가!": "Grab your video!",
  "이 폰에서는 메이킹 영상을 못 만들었어요": "This phone couldn't make the making-of video", "이 폰에서는 영상 저장이 안 돼요. 사진으로 저장해 줘": "This phone can't save video. Save the photo instead", "만들고 있어! 다 되면 버튼을 눌러 줘": "Making it! Tap the button when it's ready", "보내는 중이야, 조금만 기다려 줘": "Sending, one moment", "저장이 안 됐어요. 다시 눌러 줘": "Not saved. Tap again", "한 번 더 누르면 닫혀요": "Tap again to close",

  // Toss nudges (web shows them only with ?toss=1 or after release)
  "토스": "Toss", "토스 전용": "Toss only", "토스에서 확인하세요": "See it in Toss", "메이킹 영상은 토스에서 확인하세요": "The making-of video is in Toss", "토스에서 열려요": "Opens in Toss", "토스에서 하면 워터마크 없이 받아요": "No watermark in Toss", "메이킹 영상까지": "Making-of video too", "프레임·스티커 전부": "All frames and stickers", "앱 설치 없이, 토스 앱 안에서": "No install, inside the Toss app", "토스 앱에서 「따로 같이 네컷」을 검색해 줘": `Search '${NAME}' in the Toss app`,
};

const RULES = [
  [/^([가세])로 · (\d+)장$/, (_, s, n) => `${s === "가" ? "Landscape" : "Portrait"} · ${shots(n)}`], [/^(\d+)장 → 4장$/, (_, n) => `${n} → 4`], [/^([가세])로 네컷$/, (_, s) => `${s === "가" ? "Landscape" : "Portrait"} strip`],
  [/^(\d+)장 찍었어\. 셔터를 누르면 (\d+)번째부터 이어서 찍어$/, (_, n, m) => `${shots(n)} done. Tap the shutter to continue from #${m}`], [/^(\d+)장 준비 중… (\d+)\/(\d+)$/, (_, n, a, b) => `Preparing ${shots(n)}… ${a}/${b}`],
  [/^누끼 다듬는 중 (\d+)\/4$/, (_, n) => `Refining cutouts ${n}/4`], [/^정밀 누끼 작업 중… (\d+)\/(\d+)$/, (_, a, b) => `Refining cutouts… ${a}/${b}`], [/^움직이는 누끼 (\d)\/4 다듬는 중 · 끝나면 영상이 더 깔끔해져$/, (_, n) => `Refining moving cutouts ${n}/4 · the video gets cleaner when done`],
  [/^(\d+)칸 중 4칸 고르기$/, (_, n) => `Pick 4 of ${n}`],
  [/^(\d+)번째 사진$/, (_, n) => `Photo ${n}`], [/^(\d+)칸$/, (_, n) => `Frame ${n}`], [/^올리는 중… (\d+)%$/, (_, n) => `Uploading… ${n}%`], [/^영상 준비 중 (\d+)\/8$/, (_, n) => `Preparing video ${n}/8`], [/^스티커는 (\d+)개까지 붙일 수 있어$/, (_, n) => `You can add up to ${n} stickers`],
  [/^(움직이는 네컷|메이킹 영상) (저장|저장했어!|완성!|완성! 버튼을 누르면 저장돼|완성! 보내기·저장을 눌러 줘)$/, (_, k, w) => `${en[k]} ${{ "저장": "· save", "저장했어!": "saved!", "완성!": "ready!", "완성! 버튼을 누르면 저장돼": "ready! Tap the button to save", "완성! 보내기·저장을 눌러 줘": "ready! Tap send/save" }[w]}`],
  [/^(.+) 붙이기$/, (_, s) => `Add ${en[s] ?? s}`], [/^(.+) (프레임은|스티커는) 토스에서 열려요$/, (_, s, k) => `${en[s] ?? s} ${k === "프레임은" ? "frame" : "stickers"} open in Toss`],
];

export function tr(s) {
  if (!EN || !/[가-힣]/.test(s)) return s;
  const core = s.trim(), hit = en[core];
  if (hit !== undefined) return s.replace(core, hit);
  for (const [re, fn] of RULES) if (re.test(core)) return s.replace(core, core.replace(re, fn));
  return s;
}

// Text + attributes under one root; English output has no Hangul, so a node is touched once.
const ATTRS = ["aria-label", "alt", "title", "placeholder"];
function sweep(root) {
  const w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  for (let n = root.nodeType === 3 ? root : w.nextNode(); n; n = w.nextNode()) {
    const tag = n.parentElement?.tagName;
    if (tag !== "SCRIPT" && tag !== "STYLE") { const t = tr(n.data); if (t !== n.data) n.data = t; }
  }
  if (root.querySelectorAll) for (const a of ATTRS) {
    for (const e of [root, ...root.querySelectorAll(`[${a}]`)]) { const v = e.getAttribute?.(a); if (v && tr(v) !== v) e.setAttribute(a, tr(v)); }
  }
}

function langButton() {
  const b = Object.assign(document.createElement("button"), { type: "button", textContent: EN ? "KO" : "EN" });
  b.setAttribute("aria-label", EN ? "Korean" : "English");
  b.style.cssText = "position:fixed;z-index:50;top:calc(env(safe-area-inset-top) + 2px);left:50%;transform:translateX(-50%);min-width:44px;min-height:44px;"
    + "border:1.5px solid currentColor;border-radius:8px;background:rgba(255,255,255,.85);color:#111;font:700 13px sans-serif;cursor:pointer";
  b.onclick = () => { const next = EN ? "ko" : "en", u = new URL(location.href); u.searchParams.set("lang", next);
    try { localStorage.setItem("lab.lang", next); } catch {} location.href = u.href; };
  document.body.append(b);
  const paint = () => b.hidden = !/^(theme|landA|landB|mode)$/.test(document.body.dataset.screen || "");   // later screens hold photos a reload would lose
  new MutationObserver(paint).observe(document.body, { attributes: true, attributeFilter: ["data-screen"] }); paint();
}

function start() {
  langButton();
  if (!EN) return;
  document.documentElement.lang = "en"; document.title = NAME;
  document.head.append(Object.assign(document.createElement("style"), { textContent:   // Hangul that lives in app.css content:
    '.pk.pending::after{content:"Refining"}body[data-ui="film"] :is(#pickBg,#revBg)::before{content:"BG"}#stkSets .pill.locked::after{content:"Toss"}' }));
  const h1 = document.querySelector("#s-landA h1 > span");       // one <i> per letter, staggered by --i
  if (h1) { const deco = h1.querySelector("svg"); let k = 0, ch = s => [...s].map(c => c === " " ? " " : `<i style="--i:${k++}">${c}</i>`).join("");
    h1.innerHTML = `${ch("Miles apart,")}<br><em>${ch("one frame")}</em>`; if (deco) h1.append(deco); }
  sweep(document.body);
  new MutationObserver(ms => { for (const m of ms) {
    if (m.type === "attributes") { const v = m.target.getAttribute(m.attributeName); if (v && tr(v) !== v) m.target.setAttribute(m.attributeName, tr(v)); }
    else if (m.type === "characterData") sweep(m.target);
    else m.addedNodes.forEach(sweep);
  } }).observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRS });
}
if (id === "web") { if (document.body) start(); else addEventListener("DOMContentLoaded", start); }
