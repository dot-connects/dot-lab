# Side-by-side stills of two moving strips (e.g. fast matte vs HQ moving cut): cells 1-2 at a few times -> PNG.
# usage: bash sheet.sh A.mp4 B.mp4 OUT.png
A=$1; B=$2; OUT=$3
T="0.4 1.2 2.0 3.1"
args=(); n=0
for t in $T; do args+=(-ss $t -i "$A" -ss $t -i "$B"); done
f=""; for i in $(seq 0 $(( ${#args[@]} / 4 - 1 ))); do f="$f[$i:v]trim=end_frame=1,crop=552:780:24:24[c$i];"; done
row=""; k=0
for t in $T; do row="$row[c$((k*2))][c$((k*2+1))]hstack=2[r$k];"; k=$((k+1)); done
all=""; for i in $(seq 0 $((k-1))); do all="$all[r$i]"; done
ffmpeg -v error -y "${args[@]}" -filter_complex "$f$row${all}vstack=$k,scale=iw/2:-1" -frames:v 1 "$OUT"
