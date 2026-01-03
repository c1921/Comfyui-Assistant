from fastapi import APIRouter, Depends, WebSocket

from ..deps import settings
from ..services.proxy_service import ProxyService

router = APIRouter()


def get_proxy_service() -> ProxyService:
    return ProxyService(settings(), None)


@router.websocket("/ws")
async def ws_proxy(websocket: WebSocket, service: ProxyService = Depends(get_proxy_service)):
    await service.proxy_ws(websocket)
