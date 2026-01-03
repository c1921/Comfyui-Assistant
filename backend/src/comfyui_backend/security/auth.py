from typing import Set


def is_allowed_path(path: str, allowlist: Set[str]) -> bool:
    return not allowlist or path in allowlist
