from functools import lru_cache
from fastapi import Request
import httpx

from .config import Settings, get_settings


@lru_cache
def settings() -> Settings:
    return get_settings()


def get_http_client(request: Request) -> httpx.AsyncClient:
    return request.app.state.http_client
