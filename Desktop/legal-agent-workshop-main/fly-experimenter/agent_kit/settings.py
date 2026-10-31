"""Settings from the environment. Never prints values."""
import os

DEFAULTS = {
    "MODEL_API_KEY": "",
    "MODEL_API_BASE_URL": "https://api.meta.ai/v1",
    "MODEL_API_MODEL": "muse-spark-1.3",
    "LAB_URL": "ws://localhost:8765",
    "VERIFICATION": "off",
}


def load_settings(env=None):
    env = os.environ if env is None else env
    return {k: env.get(k, v) for k, v in DEFAULTS.items()}
