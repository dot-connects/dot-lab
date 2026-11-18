"""Encode final/frames + music.wav -> StandScreen_reel.mp4 and poster.jpg, BelowNormal + 2 CPUs."""
import ctypes, subprocess, sys

k32 = ctypes.windll.kernel32
h = k32.GetCurrentProcess()
k32.SetProcessAffinityMask(h, 0xC0)
k32.SetPriorityClass(h, 0x4000)

D = sys.argv[1]
CRF = sys.argv[2] if len(sys.argv) > 2 else "18"
run = lambda a: subprocess.run(["ffmpeg", "-hide_banner", "-y", "-loglevel", "error", *a], check=True)
run(["-framerate", "30", "-i", f"{D}/frames/f%04d.jpg", "-i", f"{D}/music.wav",
     "-vf", "crop=1080:1920:0:0,format=yuv420p", "-c:v", "libx264", "-preset", "slow", "-crf", CRF,
     "-r", "30", "-threads", "2", "-af", "volume=-4.5dB", "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
     "-t", "32", "-movflags", "+faststart", f"{D}/StandScreen_reel.mp4"])
run(["-ss", "3.0", "-i", f"{D}/StandScreen_reel.mp4", "-frames:v", "1", "-q:v", "2", "-threads", "2", f"{D}/poster.jpg"])
print("ok")
