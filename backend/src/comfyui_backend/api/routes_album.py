from pathlib import Path
from urllib.parse import quote
from typing import List, Literal

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel

from ..config import load_ui_config

router = APIRouter()

ALLOWED_IMAGE_SUFFIXES = {
    ".jpg",
    ".jpeg",
    ".png",
    ".gif",
    ".webp",
    ".bmp",
    ".tiff",
    ".tif",
}


class AlbumItem(BaseModel):
    name: str
    url: str


def _get_album_dir() -> Path:
    cfg = load_ui_config()
    album_path = (cfg.albumPath or "").strip()
    if not album_path:
        raise HTTPException(status_code=400, detail="albumPath not configured")
    album_dir = Path(album_path).expanduser().resolve()
    if not album_dir.exists() or not album_dir.is_dir():
        raise HTTPException(status_code=400, detail="albumPath is not a valid directory")
    return album_dir


def _is_allowed_image(path: Path) -> bool:
    return path.is_file() and path.suffix.lower() in ALLOWED_IMAGE_SUFFIXES


def _resolve_album_target(filename: str, album_dir: Path) -> Path:
    if not filename:
        raise HTTPException(status_code=400, detail="invalid filename")
    candidate = Path(filename)
    if candidate.is_absolute() or candidate.drive or candidate.root:
        raise HTTPException(status_code=400, detail="invalid path")
    target = (album_dir / candidate).resolve()
    if album_dir not in target.parents and target != album_dir:
        raise HTTPException(status_code=400, detail="invalid path")
    return target


@router.get("/api/album/list", response_model=List[AlbumItem])
async def list_album_images(order: Literal["asc", "desc"] = "desc") -> List[AlbumItem]:
    album_dir = _get_album_dir()
    items: List[AlbumItem] = []
    reverse = order == "desc"
    candidates = [p for p in album_dir.rglob("*") if _is_allowed_image(p)]
    for entry in sorted(candidates, key=lambda p: p.relative_to(album_dir).as_posix().lower(), reverse=reverse):
        rel_path = entry.relative_to(album_dir).as_posix()
        items.append(
            AlbumItem(
                name=rel_path,
                url=f"/api/album/file/{quote(rel_path)}",
            )
        )
    return items


@router.get("/api/album/file/{filename:path}")
async def get_album_image(filename: str) -> FileResponse:
    album_dir = _get_album_dir()
    target = _resolve_album_target(filename, album_dir)
    if not _is_allowed_image(target):
        raise HTTPException(status_code=404, detail="image not found")
    return FileResponse(target)


@router.delete("/api/album/file/{filename:path}")
async def delete_album_image(filename: str):
    album_dir = _get_album_dir()
    target = _resolve_album_target(filename, album_dir)
    if not _is_allowed_image(target):
        raise HTTPException(status_code=404, detail="image not found")
    try:
        target.unlink()
    except Exception:
        raise HTTPException(status_code=500, detail="delete failed")
    return {"status": "ok"}
