from pathlib import Path
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
        raise HTTPException(status_code=400, detail="albumPath 未配置")
    album_dir = Path(album_path).expanduser().resolve()
    if not album_dir.exists() or not album_dir.is_dir():
        raise HTTPException(status_code=400, detail="albumPath 不是有效目录")
    return album_dir


def _is_allowed_image(path: Path) -> bool:
    return path.is_file() and path.suffix.lower() in ALLOWED_IMAGE_SUFFIXES


@router.get("/api/album/list", response_model=List[AlbumItem])
async def list_album_images(order: Literal["asc", "desc"] = "desc") -> List[AlbumItem]:
    album_dir = _get_album_dir()
    items: List[AlbumItem] = []
    reverse = order == "desc"
    for entry in sorted(album_dir.iterdir(), key=lambda p: p.name.lower(), reverse=reverse):
        if not _is_allowed_image(entry):
            continue
        items.append(AlbumItem(name=entry.name, url=f"/api/album/file/{entry.name}"))
    return items


@router.get("/api/album/file/{filename}")
async def get_album_image(filename: str) -> FileResponse:
    if not filename or Path(filename).name != filename:
        raise HTTPException(status_code=400, detail="非法文件名")
    album_dir = _get_album_dir()
    target = (album_dir / filename).resolve()
    if album_dir not in target.parents and target != album_dir:
        raise HTTPException(status_code=400, detail="非法路径")
    if not _is_allowed_image(target):
        raise HTTPException(status_code=404, detail="图片不存在")
    return FileResponse(target)


@router.delete("/api/album/file/{filename}")
async def delete_album_image(filename: str):
    if not filename or Path(filename).name != filename:
        raise HTTPException(status_code=400, detail="非法文件名")
    album_dir = _get_album_dir()
    target = (album_dir / filename).resolve()
    if album_dir not in target.parents and target != album_dir:
        raise HTTPException(status_code=400, detail="非法路径")
    if not _is_allowed_image(target):
        raise HTTPException(status_code=404, detail="图片不存在")
    try:
        target.unlink()
    except Exception:
        raise HTTPException(status_code=500, detail="删除失败")
    return {"status": "ok"}
