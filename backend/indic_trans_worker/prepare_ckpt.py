"""Download the official IndicTrans2 En-Indic distilled zip and locate the CT2 dir."""
from __future__ import annotations

import os
import shutil
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

PRIMARY_URL = (
    "https://indictrans2-public.objectstore.e2enetworks.net/it2_distilled_ckpts/en-indic.zip"
)
FALLBACK_URL = (
    "https://huggingface.co/datasets/ai4bharat/BPCC/resolve/main/additional/en-indic-dist.tar.gz"
)
DEST = Path(os.environ.get("INDIC_TRANS_ROOT", "/models/en-indic"))
MARKER = Path(os.environ.get("INDIC_TRANS_CKPT_FILE", "/models/CKPT_DIR"))


def _download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    print(f"Downloading {url}", flush=True)
    urllib.request.urlretrieve(url, dest)


def _extract(archive: Path, dest: Path) -> None:
    dest.mkdir(parents=True, exist_ok=True)
    if archive.suffixes[-2:] == [".tar", ".gz"] or archive.suffix == ".tgz":
        with tarfile.open(archive, "r:gz") as tar:
            tar.extractall(dest)
        return
    with zipfile.ZipFile(archive) as zf:
        zf.extractall(dest)


def find_ckpt(root: Path) -> Path:
    bins = list(root.rglob("model.bin"))
    srcs = list(root.rglob("model.SRC"))
    if not bins:
        raise FileNotFoundError(f"No CTranslate2 model.bin under {root}")
    vocab_dirs = {p.parent for p in srcs if p.parent.name == "vocab"}
    for model_bin in bins:
        ckpt = model_bin.parent
        if (ckpt / "vocab" / "model.SRC").is_file():
            return ckpt
        for vocab in vocab_dirs:
            if vocab.parent == ckpt or vocab.parent == ckpt.parent:
                target = ckpt / "vocab"
                if not target.exists():
                    try:
                        target.symlink_to(vocab, target_is_directory=True)
                    except OSError:
                        shutil.copytree(vocab, target)
                if (ckpt / "vocab" / "model.SRC").is_file():
                    return ckpt
    raise FileNotFoundError(f"model.bin found but vocab/model.SRC missing under {root}")


def main() -> int:
    DEST.mkdir(parents=True, exist_ok=True)
    archive = Path("/tmp/en-indic.archive")
    try:
        _download(PRIMARY_URL, archive)
    except Exception as exc:
        print(f"Primary download missed ({exc}); trying BPCC fallback", flush=True)
        _download(FALLBACK_URL, archive)
    _extract(archive, DEST)
    archive.unlink(missing_ok=True)
    ckpt = find_ckpt(DEST)
    MARKER.parent.mkdir(parents=True, exist_ok=True)
    MARKER.write_text(str(ckpt), encoding="utf-8")
    print(f"IndicTrans2 CT2 ready at {ckpt}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
