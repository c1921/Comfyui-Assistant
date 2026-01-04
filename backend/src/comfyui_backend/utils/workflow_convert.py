import logging
from typing import Any, Dict, Iterable, List, Tuple

logger = logging.getLogger(__name__)

UNSUPPORTED_NODE_TYPES = {"MarkdownNote"}
NODE_WIDGET_MAPPINGS = {
    "KSampler": ["seed", "seed_control", "steps", "cfg", "sampler_name", "scheduler", "denoise"],
    "CLIPTextEncode": ["text"],
    "EmptyLatentImage": ["width", "height", "batch_size"],
    "EmptySD3LatentImage": ["width", "height", "batch_size"],
    "CheckpointLoaderSimple": ["ckpt_name"],
    "SaveImage": ["filename_prefix"],
    "VAELoader": ["vae_name"],
    "CLIPLoader": ["clip_name", "type", "device"],
    "UnetLoaderGGUF": ["unet_name"],
}


def _build_link_map(links: Iterable[List[Any]]) -> Dict[int, Tuple[str, int]]:
    link_map: Dict[int, Tuple[str, int]] = {}
    for link in links:
        if not isinstance(link, list) or len(link) < 4:
            continue
        link_id = link[0]
        source_node = link[1]
        source_slot = link[2]
        if not isinstance(link_id, int):
            continue
        try:
            link_map[link_id] = (str(source_node), int(source_slot))
        except Exception:
            continue
    return link_map


def _wrap_array_value(value: Any) -> Any:
    if isinstance(value, list):
        return {"__value__": value}
    return value


def _map_widgets_by_defs(node: Dict[str, Any], inputs: Dict[str, Any]) -> bool:
    widgets = node.get("widgets")
    values = node.get("widgets_values")
    if not isinstance(widgets, list) or not isinstance(values, list):
        return False

    mapped_any = False
    for idx, widget in enumerate(widgets):
        if not isinstance(widget, dict):
            continue
        name = widget.get("name")
        options = widget.get("options") if isinstance(widget.get("options"), dict) else {}
        if not name or options.get("serialize") is False:
            continue
        if idx >= len(values):
            continue
        inputs[name] = _wrap_array_value(values[idx])
        mapped_any = True
    return mapped_any


def _map_widgets_by_inputs(node: Dict[str, Any], inputs: Dict[str, Any]) -> None:
    values = node.get("widgets_values")
    if not isinstance(values, list) or not values:
        return

    input_defs = node.get("inputs")
    if not isinstance(input_defs, list):
        return

    candidates: List[str] = []
    for input_def in input_defs:
        if not isinstance(input_def, dict):
            continue
        name = input_def.get("name")
        if not name or name in inputs:
            continue
        if input_def.get("link") is not None:
            continue
        candidates.append(name)

    if not candidates:
        return

    for value, name in zip(values, candidates):
        inputs[name] = _wrap_array_value(value)


def _map_widgets_by_type(node: Dict[str, Any], inputs: Dict[str, Any]) -> bool:
    node_type = node.get("type")
    if not node_type:
        return False
    param_names = NODE_WIDGET_MAPPINGS.get(node_type)
    values = node.get("widgets_values")
    if not isinstance(param_names, list) or not isinstance(values, list):
        return False
    mapped_any = False
    for idx, param_name in enumerate(param_names):
        if idx >= len(values):
            continue
        value = values[idx]
        if node_type == "KSampler" and param_name == "seed_control" and value == "randomize":
            continue
        inputs[param_name] = _wrap_array_value(value)
        mapped_any = True
    return mapped_any


def convert_workflow_to_api(workflow: Dict[str, Any]) -> Dict[str, Any]:
    nodes = workflow.get("nodes")
    links = workflow.get("links")
    if not isinstance(nodes, list) or not isinstance(links, list):
        return workflow

    link_map = _build_link_map(links)
    output: Dict[str, Any] = {}

    for node in nodes:
        if not isinstance(node, dict):
            continue
        node_id = node.get("id")
        node_type = node.get("type")
        if node_type in UNSUPPORTED_NODE_TYPES:
            continue
        if node_id is None or not node_type:
            continue

        inputs: Dict[str, Any] = {}

        for input_def in node.get("inputs", []) or []:
            if not isinstance(input_def, dict):
                continue
            link_id = input_def.get("link")
            if link_id is None:
                continue
            name = input_def.get("name")
            if not name:
                continue
            linked = link_map.get(link_id)
            if not linked:
                continue
            inputs[name] = [linked[0], linked[1]]

        mapped_by_defs = _map_widgets_by_defs(node, inputs)
        mapped_by_type = False
        if not mapped_by_defs:
            mapped_by_type = _map_widgets_by_type(node, inputs)
        if not mapped_by_defs and not mapped_by_type:
            _map_widgets_by_inputs(node, inputs)

        output[str(node_id)] = {
            "inputs": inputs,
            "class_type": node_type,
            "_meta": {"title": node.get("title") or node_type},
        }

    # Remove inputs linked to removed nodes
    for node_data in output.values():
        node_inputs = node_data.get("inputs")
        if not isinstance(node_inputs, dict):
            continue
        for key, value in list(node_inputs.items()):
            if (
                isinstance(value, list)
                and len(value) == 2
                and isinstance(value[0], str)
                and value[0] not in output
            ):
                del node_inputs[key]

    logger.info("Converted workflow nodes: %s", len(output))
    return output
