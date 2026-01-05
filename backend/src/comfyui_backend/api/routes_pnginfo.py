import json
from typing import Any, Dict, Optional

from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from .routes_album import _get_album_dir, _resolve_album_target

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
            raise HTTPException(status_code=400, detail=f"{key} JSON parse failed: {exc}")
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
        raise HTTPException(status_code=400, detail="pnginfo missing workflow or prompt")

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
        raise HTTPException(status_code=400, detail="only PNG files are supported")
    try:
        with Image.open(file.file) as image:
            return _extract_pnginfo(image)
    except UnidentifiedImageError:
        raise HTTPException(status_code=400, detail="unrecognized image file")
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"failed to read image: {exc}")


@router.get("/api/workflow/pnginfo/album/{filename:path}")
async def parse_pnginfo_from_album(filename: str):
    album_dir = _get_album_dir()
    target = _resolve_album_target(filename, album_dir)
    if not target.is_file() or target.suffix.lower() != ".png":
        raise HTTPException(status_code=404, detail="PNG image not found")
    try:
        with Image.open(target) as image:
            return _extract_pnginfo(image)
    except UnidentifiedImageError:
        raise HTTPException(status_code=400, detail="unrecognized image file")
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"failed to read image: {exc}")
