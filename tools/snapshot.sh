#!/usr/bin/env bash
# 디버그 빌드를 설치하고, 화면을 띄우지 않는 WidgetSnapshotReceiver로 모든 위젯을
# 런처와 같은 조건(제한된 컨텍스트)에서 렌더링해 build/snapshots/ 로 가져온다.
#   tools/snapshot.sh            (Windows: GRADLEW_EXT=.bat tools/snapshot.sh)
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."
ADB="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}/platform-tools/adb"
OUT=build/snapshots

./gradlew${GRADLEW_EXT:-} :app:installDebug --console=plain -q
"$ADB" logcat -c
"$ADB" shell am broadcast -n com.partkit.widgets/.debug.WidgetSnapshotReceiver >/dev/null
for _ in $(seq 1 24); do
  sleep 5
  if "$ADB" logcat -d -s WidgetSnapshot:* | grep -qE "widgets_dark|Snapshot failed"; then break; fi
done
"$ADB" logcat -d -s WidgetSnapshot:* | grep -E "Wrote|failed|Exception" | tail -3
mkdir -p "$OUT"
"$ADB" pull /sdcard/Android/data/com.partkit.widgets/files/snapshots/. "$OUT/" >/dev/null
python - "$OUT" <<'PY'
import sys
from PIL import Image
out = sys.argv[1]
a, b = Image.open(f"{out}/widgets_light.png"), Image.open(f"{out}/widgets_dark.png")
sheet = Image.new("RGB", (a.width + b.width + 30, max(a.height, b.height)), "white")
sheet.paste(a, (0, 0)); sheet.paste(b, (a.width + 30, 0))
sheet.save(f"{out}/widgets_all.png")
sheet.resize((sheet.width * 9 // 20, sheet.height * 9 // 20)).save(f"{out}/widgets_small.png")
print(f"{out}/widgets_all.png")
PY
