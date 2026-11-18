"""v3 encode: frames10 (948) + 1.5 s last-frame hold + grain against banding, music3.wav -> StandScreen_reel.mp4."""
import ctypes, subprocess, sys

k32 = ctypes.windll.kernel32
h = k32.GetCurrentProcess()
k32.SetProcessAffinityMask(h, 0xC0)
k32.SetPriorityClass(h, 0x4000)

D = sys.argv[1]
CRF = sys.argv[2] if len(sys.argv) > 2 else "19"
GAIN = sys.argv[3] if len(sys.argv) > 3 else "-0.6"
run = lambda a: subprocess.run(["ffmpeg", "-hide_banner", "-y", "-loglevel", "error", *a], check=True)
run(["-framerate", "30", "-i", f"{D}/frames10/f%04d.jpg", "-i", f"{D}/music3.wav",
     "-vf", "crop=1080:1920:0:0,tpad=stop_mode=clone:stop_duration=1.5,noise=alls=3:allf=t,format=yuv420p",
     "-c:v", "libx264", "-preset", "slow", "-crf", CRF, "-r", "30", "-threads", "2",
     "-af", f"volume={GAIN}dB", "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
     "-t", "33.1", "-movflags", "+faststart", f"{D}/StandScreen_reel.mp4"])
run(["-ss", "3.0", "-i", f"{D}/StandScreen_reel.mp4", "-frames:v", "1", "-q:v", "2", "-threads", "2", f"{D}/poster.jpg"])
print("ok")
