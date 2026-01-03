import httpx

from ..deps import settings


def create_client() -> httpx.AsyncClient:
    cfg = settings()
    return httpx.AsyncClient(timeout=cfg.http_timeout_seconds)
