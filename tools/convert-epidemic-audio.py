"""Convert downloaded WAVs into quiet mono Vorbis cues. No credentials or API requests here."""
from pathlib import Path
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/audio-tools"))
import imageio_ffmpeg

ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
SOURCES = {
    "air": ("073cf199-3d1b-4a67-9128-a5abd13870ff", "Designed, Whoosh, Soft Airy", .9),
    "whisper": ("def40515-46db-4e4b-811b-2d523636f9aa", "Designed, Eerie, Ghostly Voice Reversed 02", 6.272),
    "chime": ("0c1ab706-6671-42b8-b6e1-8afe242e02d5", "Musical, Chime, Koshi Terra Chime, Ringing Bell Wind Chime 01", 7.0),
}
manifest = []
for mod, names in {"limbo": ("air", "whisper", "chime"), "magia": ("air", "whisper")}.items():
    folder = ROOT / f"aurorion-{mod}/src/main/resources/assets/aurorion_{mod}/sounds/epidemic"
    folder.mkdir(parents=True, exist_ok=True)
    for name in names:
        source_id, title, duration = SOURCES[name]
        output = folder / f"{name}.ogg"
        subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i",
                        str(ROOT / f"build/epidemic/{name}.wav"), "-t", str(duration),
                        "-map_metadata", "-1", "-ac", "1", "-ar", "44100",
                        "-af", f"highpass=f=90,lowpass=f=6500,loudnorm=I=-24:TP=-6:LRA=7,afade=t=in:d=0.06,afade=t=out:st={max(0, duration - .5)}:d=0.5",
                        "-c:a", "libvorbis", "-q:a", "4", str(output)], check=True)
        manifest.append({"file": output.relative_to(ROOT).as_posix(), "provider": "Epidemic Sound",
                         "source_id": source_id, "title": title, "duration_seconds": duration,
                         "retrieved": "2026-09-23", "format": "Ogg Vorbis mono 44100 Hz",
                         "processing": "90 Hz high-pass, 6.5 kHz low-pass, -24 LUFS target, fades; chime shortened to 7 s"})
        print(output.relative_to(ROOT))
(ROOT / "docs").mkdir(exist_ok=True)
(ROOT / "docs/audio-sources.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
