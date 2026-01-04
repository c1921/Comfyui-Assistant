from fastapi import APIRouter, Depends
import httpx

from ..deps import get_http_client, settings

router = APIRouter()


@router.get("/health")
async def health():
    return {"status": "ok"}


@router.get("/api/comfy/health")
async def comfy_health(client: httpx.AsyncClient = Depends(get_http_client)):
    url = f"{settings().comfy_http_base}/system_stats"
    try:
        resp = await client.get(url, timeout=httpx.Timeout(5.0))
    except Exception as exc:
        return {"status": "down", "error": str(exc)}
    if resp.status_code >= 200 and resp.status_code < 400:
        return {"status": "ok", "http_status": resp.status_code}
    return {"status": "down", "http_status": resp.status_code}
