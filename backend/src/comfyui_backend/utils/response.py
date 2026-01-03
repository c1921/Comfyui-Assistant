from typing import Dict, Optional
from fastapi import Response


def build_response(content: bytes, status_code: int, headers: Dict[str, str], media_type: Optional[str]) -> Response:
    return Response(
        content=content,
        status_code=status_code,
        headers=headers,
        media_type=media_type,
    )
