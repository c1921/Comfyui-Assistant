import asyncio
from fastapi import Request, WebSocket
import httpx

from ..config import Settings
from ..integrations import comfy_ws
from ..utils.headers import rewrite_request_headers, filter_response_headers
from ..utils.response import build_response


class ProxyService:
    def __init__(self, settings: Settings, client: httpx.AsyncClient | None) -> None:
        self._settings = settings
        self._client = client

    async def proxy_http(self, path: str, request: Request):
        if not self._client:
            raise RuntimeError("HTTP client is not initialized")
        url = f"{self._settings.comfy_http_base}/{path}"
        headers = rewrite_request_headers(dict(request.headers), self._settings.comfy_http_base)
        body = await request.body()

        resp = await self._client.request(
            request.method,
            url,
            params=request.query_params,
            content=body,
            headers=headers,
        )

        out_headers = filter_response_headers(dict(resp.headers))
        return build_response(
            content=resp.content,
            status_code=resp.status_code,
            headers=out_headers,
            media_type=resp.headers.get("content-type"),
        )

    async def proxy_ws(self, websocket: WebSocket) -> None:
        await websocket.accept()
        client_id = websocket.query_params.get("clientId", "")
        upstream = comfy_ws.build_upstream_url(client_id)

        async with comfy_ws.connect(upstream) as ws_up:
            async def from_client():
                while True:
                    msg = await websocket.receive_text()
                    await ws_up.send(msg)

            async def from_upstream():
                while True:
                    msg = await ws_up.recv()
                    await websocket.send_text(msg)

            await asyncio.gather(from_client(), from_upstream())
