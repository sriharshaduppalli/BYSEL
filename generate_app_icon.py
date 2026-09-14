"""Compatibility wrapper — launcher icons are built by android/scripts/rebuild_launcher_icons.py."""
from pathlib import Path
import runpy

runpy.run_path(str(Path(__file__).resolve().parent / "android" / "scripts" / "rebuild_launcher_icons.py"), run_name="__main__")
