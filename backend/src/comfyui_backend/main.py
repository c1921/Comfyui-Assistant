from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import httpx

from .api.router import router as api_router
from .config import get_settings
from .logging import setup_logging


def create_app() -> FastAPI:
    setup_logging()
    settings = get_settings()

    app = FastAPI()
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.allowed_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(api_router)

    @app.on_event("startup")
    async def startup() -> None:
        app.state.http_client = httpx.AsyncClient(timeout=settings.http_timeout_seconds)

    @app.on_event("shutdown")
    async def shutdown() -> None:
        client = app.state.http_client
        await client.aclose()

    return app


app = create_app()
