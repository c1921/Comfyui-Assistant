import asyncio
import json
import logging
from fastapi import Request, WebSocket
from fastapi.responses import JSONResponse
import httpx

from ..config import Settings
from ..integrations import comfy_ws
from ..utils.headers import rewrite_request_headers, filter_response_headers
from ..utils.response import build_response
from ..utils.workflow_convert import convert_workflow_to_api, UNSUPPORTED_NODE_TYPES


class ProxyService:
    def __init__(self, settings: Settings, client: httpx.AsyncClient | None) -> None:
        self._settings = settings
        self._client = client
        self._logger = logging.getLogger(__name__)
        self._unsupported_node_types = set(UNSUPPORTED_NODE_TYPES)
        self._node_registry_attr = "node_registry"

    def _looks_like_api_prompt(self, prompt: dict) -> bool:
        for value in prompt.values():
            if isinstance(value, dict) and "class_type" in value and "inputs" in value:
                return True
            break
        return False

    def _looks_like_workflow_json(self, prompt: dict) -> bool:
        return isinstance(prompt.get("nodes"), list) and isinstance(prompt.get("links"), list)

    def _strip_unsupported_nodes_workflow(
        self,
        prompt: dict,
        supported_types: set[str] | None,
    ) -> dict:
        nodes = prompt.get("nodes")
        links = prompt.get("links")
        if not isinstance(nodes, list) or not isinstance(links, list):
            return prompt

        removed_ids = set()
        removed_types = set()
        kept_nodes = []
        for node in nodes:
            node_type = node.get("type")
            node_id = node.get("id")
            if not node_type:
                continue
            if supported_types is not None:
                if node_type not in supported_types:
                    removed_types.add(node_type)
                    removed_ids.add(node_id)
                    removed_ids.add(str(node_id))
                    continue
            elif node_type in self._unsupported_node_types:
                removed_types.add(node_type)
                removed_ids.add(node_id)
                removed_ids.add(str(node_id))
                continue
            kept_nodes.append(node)

        if not removed_ids:
            return prompt

        def _link_has_removed(link: list) -> bool:
            if len(link) < 4:
                return False
            return link[1] in removed_ids or link[3] in removed_ids

        kept_links = [link for link in links if not _link_has_removed(link)]
        cleaned = dict(prompt)
        cleaned["nodes"] = kept_nodes
        cleaned["links"] = kept_links
        if removed_types:
            self._logger.info(
                "Removed unsupported workflow node types: %s",
                sorted(removed_types),
            )
        self._logger.info(
            "Stripped unsupported nodes from workflow: %s",
            sorted(removed_ids, key=lambda v: str(v)),
        )
        return cleaned

    def _strip_unsupported_nodes_api(
        self,
        prompt: dict,
        supported_types: set[str] | None,
    ) -> dict:
        removed_keys = []
        removed_types = set()
        cleaned = {}
        for key, value in prompt.items():
            if isinstance(value, dict):
                node_type = value.get("class_type")
                if supported_types is not None:
                    if node_type and node_type not in supported_types:
                        removed_types.add(node_type)
                        removed_keys.append(key)
                        continue
                elif node_type in self._unsupported_node_types:
                    removed_types.add(node_type)
                    removed_keys.append(key)
                    continue
            cleaned[key] = value
        if removed_keys:
            if removed_types:
                self._logger.info("Removed unsupported API node types: %s", sorted(removed_types))
            self._logger.info("Stripped unsupported nodes from API prompt: %s", sorted(removed_keys))
            return cleaned
        return prompt

    def _maybe_convert_prompt(
        self,
        payload: dict,
        supported_types: set[str] | None,
    ) -> dict:
        prompt = payload.get("prompt")
        if not isinstance(prompt, dict):
            return payload
        if self._looks_like_api_prompt(prompt):
            prompt = self._strip_unsupported_nodes_api(prompt, supported_types)
            if prompt is payload.get("prompt"):
                return payload
            updated = dict(payload)
            updated["prompt"] = prompt
            return updated
        if not self._looks_like_workflow_json(prompt):
            return payload
        prompt = self._strip_unsupported_nodes_workflow(prompt, supported_types)
        try:
            converted = convert_workflow_to_api(prompt)
        except Exception:
            self._logger.exception("Workflow conversion failed; using original payload.")
            return payload
        if isinstance(converted, dict):
            updated = dict(payload)
            updated["prompt"] = self._strip_unsupported_nodes_api(converted, supported_types)
            self._logger.info("Workflow converted to API format for /prompt request.")
            return updated
        return payload

    async def _get_supported_node_types(self, request: Request) -> set[str] | None:
        if not self._client:
            return None
        registry = getattr(request.app.state, self._node_registry_attr, None)
        if not registry:
            return None
        try:
            return await registry.get_supported_types(self._client, self._settings)
        except Exception:
            self._logger.exception("Failed to resolve ComfyUI node registry.")
            return None

    async def proxy_http(self, path: str, request: Request):
        if not self._client:
            raise RuntimeError("HTTP client is not initialized")
        url = f"{self._settings.comfy_http_base}/{path}"
        headers = rewrite_request_headers(dict(request.headers), self._settings.comfy_http_base)
        body = await request.body()
        if request.method.upper() == "POST" and path == "prompt":
            try:
                payload = json.loads(body.decode("utf-8"))
            except Exception:
                payload = None
            if isinstance(payload, dict):
                supported_types = await self._get_supported_node_types(request)
                payload = self._maybe_convert_prompt(payload, supported_types)
                body = json.dumps(payload, ensure_ascii=False).encode("utf-8")

        try:
            resp = await self._client.request(
                request.method,
                url,
                params=request.query_params,
                content=body,
                headers=headers,
            )
        except httpx.RequestError as exc:
            self._logger.warning("ComfyUI request failed %s %s: %s", request.method, url, exc)
            return JSONResponse(
                {
                    "error": {
                        "type": "upstream_unreachable",
                        "message": "ComfyUI not reachable",
                        "details": str(exc),
                    }
                },
                status_code=502,
            )
        if path == "prompt" and resp.status_code >= 400:
            try:
                err_text = resp.text
            except Exception:
                err_text = ""
            if len(err_text) > 2000:
                err_text = err_text[:2000] + "..."
            self._logger.warning("ComfyUI /prompt error %s: %s", resp.status_code, err_text)

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
