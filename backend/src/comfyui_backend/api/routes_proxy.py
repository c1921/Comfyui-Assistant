from fastapi import APIRouter, Depends, Request
import httpx

from ..deps import settings, get_http_client
from ..services.proxy_service import ProxyService

router = APIRouter()


def get_proxy_service(client: httpx.AsyncClient = Depends(get_http_client)) -> ProxyService:
    return ProxyService(settings(), client)


@router.api_route("/api/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"])
async def proxy_api(path: str, request: Request, service: ProxyService = Depends(get_proxy_service)):
    return await service.proxy_http(path, request)


@router.api_route("/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"])
async def proxy_root(path: str, request: Request, service: ProxyService = Depends(get_proxy_service)):
    return await service.proxy_http(path, request)
