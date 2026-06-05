import os
from dotenv import load_dotenv

# 加载环境变量（本地开发）
load_dotenv()


class Config:
    # 服务器配置
    HOST = os.getenv("WEATHER_AGENT_HOST", "0.0.0.0")
    PORT = int(os.getenv("WEATHER_AGENT_PORT", "5001"))
    DEBUG = os.getenv("WEATHER_AGENT_DEBUG", "true").lower() in ("1", "true", "yes", "y")

    # 天气服务配置（后续接入）
    WEATHER_API_KEY = os.getenv("WEATHER_API_KEY", "")
    WEATHER_API_BASE_URL = os.getenv("WEATHER_API_BASE_URL", "")

