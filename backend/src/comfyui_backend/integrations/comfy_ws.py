import websockets

from ..deps import settings


def build_upstream_url(client_id: str) -> str:
    cfg = settings()
    return f"{cfg.comfy_ws_base}/ws?clientId={client_id}"


def connect(upstream_url: str):
    return websockets.connect(upstream_url)
