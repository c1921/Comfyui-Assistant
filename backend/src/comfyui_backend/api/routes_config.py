from fastapi import APIRouter
from pydantic import BaseModel

from ..config import UiConfig, load_ui_config, save_ui_config

router = APIRouter()


class BackendConfig(BaseModel):
    pcIp: str = ""
    pcPort: str = "8000"


@router.get("/api/config", response_model=BackendConfig)
async def get_config() -> BackendConfig:
    cfg = load_ui_config()
    return BackendConfig(pcIp=cfg.pcIp, pcPort=cfg.pcPort)


@router.put("/api/config", response_model=BackendConfig)
async def update_config(payload: BackendConfig) -> BackendConfig:
    cfg = UiConfig(pcIp=payload.pcIp, pcPort=payload.pcPort)
    save_ui_config(cfg)
    return payload
