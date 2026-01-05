import json
from pathlib import Path
from typing import Any, Dict, Optional

from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from .routes_album import _get_album_dir

router = APIRouter()


def _parse_json_field(key: str, value: Any) -> Optional[Dict[str, Any]]:
    if value is None:
        return None
    if isinstance(value, dict):
        return value
    if isinstance(value, (bytes, bytearray)):
        value = value.decode("utf-8", errors="replace")
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError as exc:
            raise HTTPException(status_code=400, detail=f"{key} JSON 解析失败：{exc}")
        if isinstance(parsed, dict):
            return parsed
        return parsed  # allow non-dict JSON payloads
    return None


def _extract_pnginfo(image: Image.Image) -> Dict[str, Any]:
    info = image.info or {}
    workflow_raw = info.get("workflow")
    prompt_raw = info.get("prompt")

    workflow = _parse_json_field("workflow", workflow_raw)
    prompt = _parse_json_field("prompt", prompt_raw)

    if workflow is None and prompt is None:
        raise HTTPException(status_code=400, detail="pnginfo 未发现 workflow 或 prompt")

    source = "workflow" if workflow is not None else "prompt"
    payload = workflow if source == "workflow" else prompt
    return {
        "source": source,
        "payload": payload,
        "workflow": workflow,
        "prompt": prompt,
        "keys": list(info.keys()),
    }


@router.post("/api/workflow/pnginfo")
async def parse_pnginfo_upload(file: UploadFile = File(...)):
    filename = file.filename or ""
    if not filename.lower().endswith(".png"):
        raise HTTPException(status_code=400, detail="仅支持 PNG 图片")
    try:
        with Image.open(file.file) as image:
            return _extract_pnginfo(image)
    except UnidentifiedImageError:
        raise HTTPException(status_code=400, detail="无法识别图片文件")
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"读取图片失败：{exc}")


@router.get("/api/workflow/pnginfo/album/{filename}")
async def parse_pnginfo_from_album(filename: str):
    if not filename or Path(filename).name != filename:
        raise HTTPException(status_code=400, detail="非法文件名")
    album_dir = _get_album_dir()
    target = (album_dir / filename).resolve()
    if album_dir not in target.parents and target != album_dir:
        raise HTTPException(status_code=400, detail="非法路径")
    if not target.is_file() or target.suffix.lower() != ".png":
        raise HTTPException(status_code=404, detail="PNG 图片不存在")
    try:
        with Image.open(target) as image:
            return _extract_pnginfo(image)
    except UnidentifiedImageError:
        raise HTTPException(status_code=400, detail="无法识别图片文件")
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"读取图片失败：{exc}")
