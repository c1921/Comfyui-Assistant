from fastapi import APIRouter

from .routes_config import router as config_router
from .routes_health import router as health_router
from .routes_proxy import router as proxy_router
from .routes_ws import router as ws_router

router = APIRouter()
router.include_router(health_router)
router.include_router(config_router)
router.include_router(proxy_router)
router.include_router(ws_router)
