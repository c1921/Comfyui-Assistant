import asyncio
from fastapi import FastAPI, Request, WebSocket, Response
from fastapi.middleware.cors import CORSMiddleware
import httpx
import websockets

COMFY = "http://127.0.0.1:8188"     # ComfyUI 地址（在同一台PC上跑）
COMFY_WS = "ws://127.0.0.1:8188"    # ComfyUI WS

app = FastAPI()

# 允许你网页来源访问（你也可以改成更严格的 origin 白名单）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

client = httpx.AsyncClient(timeout=300)

@app.api_route("/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"])
async def proxy(path: str, request: Request):
    url = f"{COMFY}/{path}"
    headers = dict(request.headers)

    headers.pop("origin", None)
    headers.pop("referer", None)
    headers["origin"] = COMFY
    headers["referer"] = COMFY + "/"
    headers.pop("host", None)

    body = await request.body()
    resp = await client.request(
        request.method,
        url,
        params=request.query_params,
        content=body,
        headers=headers,
    )

    # 过滤掉不该透传/可能冲突的响应头
    hop_by_hop = {"content-encoding", "transfer-encoding", "connection", "keep-alive"}
    out_headers = {k: v for k, v in resp.headers.items() if k.lower() not in hop_by_hop}

    return Response(
        content=resp.content,
        status_code=resp.status_code,
        headers=out_headers,
        media_type=resp.headers.get("content-type"),
    )


@app.websocket("/ws")
async def ws_proxy(websocket: WebSocket):
    # 转发 WebSocket：浏览器连 /ws?clientId=xxx，我们转到 ComfyUI 的 /ws?clientId=xxx
    await websocket.accept()
    client_id = websocket.query_params.get("clientId", "")
    upstream = f"{COMFY_WS}/ws?clientId={client_id}"

    async with websockets.connect(upstream) as ws_up:
        async def from_client():
            while True:
                msg = await websocket.receive_text()
                await ws_up.send(msg)

        async def from_upstream():
            while True:
                msg = await ws_up.recv()
                await websocket.send_text(msg)

        await asyncio.gather(from_client(), from_upstream())
