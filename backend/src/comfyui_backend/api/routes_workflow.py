from fastapi import APIRouter, HTTPException

from ..utils.workflow_convert import convert_workflow_to_api

router = APIRouter()


@router.post("/api/workflow/convert")
async def convert_workflow(payload: dict):
    raw = payload.get("workflow") or payload.get("prompt") or payload
    if not isinstance(raw, dict):
        raise HTTPException(status_code=400, detail="workflow payload must be an object")
    try:
        return convert_workflow_to_api(raw)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"workflow convert failed: {exc}")
