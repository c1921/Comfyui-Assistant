from typing import Dict


HOP_BY_HOP = {"content-encoding", "transfer-encoding", "connection", "keep-alive"}


def rewrite_request_headers(headers: Dict[str, str], target_base: str) -> Dict[str, str]:
    out = dict(headers)
    out.pop("origin", None)
    out.pop("referer", None)
    out["origin"] = target_base
    out["referer"] = target_base + "/"
    out.pop("host", None)
    return out


def filter_response_headers(headers: Dict[str, str]) -> Dict[str, str]:
    return {k: v for k, v in headers.items() if k.lower() not in HOP_BY_HOP}
