import os
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

class Config:
    # DeepSeek API配置
    DEEPSEEK_API_KEY = os.getenv('DEEPSEEK_API_KEY', '')
    DEEPSEEK_API_URL = 'https://api.deepseek.com/v1/chat/completions'
    
    # 服务器配置
    HOST = '0.0.0.0'
    PORT = 5000
    DEBUG = True
