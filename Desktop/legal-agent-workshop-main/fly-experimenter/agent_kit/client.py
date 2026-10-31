"""Model client (OpenAI-compatible). Third-party import stays inside the class."""


class ModelClient:
    def __init__(self, settings, model=None):
        import openai
        self.client = openai.OpenAI(api_key=settings["MODEL_API_KEY"],
                                    base_url=settings["MODEL_API_BASE_URL"])
        self.model = model or settings["MODEL_API_MODEL"]

    def complete(self, messages, tools, timeout=120):
        msg = self.client.chat.completions.create(
            model=self.model, messages=messages, tools=tools, timeout=timeout,
        ).choices[0].message
        return to_dict(msg)


def to_dict(message):
    if isinstance(message, dict):
        return message
    data = {"role": "assistant", "content": message.content or ""}
    calls = getattr(message, "tool_calls", None)
    if calls:
        data["tool_calls"] = [
            {"id": c.id, "type": "function",
             "function": {"name": c.function.name, "arguments": c.function.arguments}}
            for c in calls
        ]
    return data
