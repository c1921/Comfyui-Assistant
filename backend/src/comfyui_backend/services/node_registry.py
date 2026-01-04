import asyncio
import logging
import time
from typing import Optional, Set

import httpx

from ..config import Settings


class NodeRegistry:
    def __init__(self, ttl_seconds: int = 30) -> None:
        self._ttl_seconds = ttl_seconds
        self._lock = asyncio.Lock()
        self._last_fetch = 0.0
        self._node_types: Optional[Set[str]] = None
        self._logger = logging.getLogger(__name__)

    async def get_supported_types(
        self,
        client: httpx.AsyncClient,
        settings: Settings,
    ) -> Optional[Set[str]]:
        now = time.monotonic()
        if self._node_types is not None and now - self._last_fetch < self._ttl_seconds:
            return self._node_types
        if self._node_types is None and now - self._last_fetch < self._ttl_seconds:
            return None

        async with self._lock:
            now = time.monotonic()
            if self._node_types is not None and now - self._last_fetch < self._ttl_seconds:
                return self._node_types
            if self._node_types is None and now - self._last_fetch < self._ttl_seconds:
                return None

            url = f"{settings.comfy_http_base}/object_info"
            try:
                resp = await client.get(url, timeout=httpx.Timeout(5.0))
                if resp.status_code < 400:
                    data = resp.json()
                    if isinstance(data, dict):
                        self._node_types = {str(k) for k in data.keys()}
                    else:
                        self._node_types = None
                else:
                    self._logger.warning("ComfyUI object_info returned HTTP %s", resp.status_code)
                    self._node_types = None
            except Exception as exc:
                self._logger.warning("Failed to fetch ComfyUI object_info: %s", exc)
                self._node_types = None
            self._last_fetch = time.monotonic()
            return self._node_types
