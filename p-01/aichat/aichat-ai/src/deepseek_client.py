import requests
import json
from typing import Iterable, List, Dict, Optional
from config import Config

class DeepSeekClient:
    def __init__(self):
        self.api_key = Config.DEEPSEEK_API_KEY
        self.api_url = Config.DEEPSEEK_API_URL
        self.headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {self.api_key}'
        }
    
    def chat_completion(self, messages: List[Dict[str, str]], model: str = 'deepseek-chat') -> Optional[str]:
        """
        调用DeepSeek API进行聊天完成
        
        Args:
            messages: 消息列表，格式为[{"role": "user", "content": "消息内容"}]
            model: 使用的模型，默认为deepseek-chat
            
        Returns:
            生成的回复内容，如果失败则返回None
        """
        if not self.api_key:
            print("Error: DeepSeek API key is not set")
            return None
        
        payload = {
            "model": model,
            "messages": messages,
            "temperature": 0.7,
            "max_tokens": 1024,
            "top_p": 0.95
        }
        
        try:
            response = requests.post(self.api_url, headers=self.headers, json=payload, timeout=30)
            print(f"DeepSeek API Response Status: {response.status_code}")
            # Avoid console encoding issues on Windows terminals (e.g. GBK) by
            # escaping non-ASCII characters before printing.
            safe_text = response.text.encode("ascii", errors="backslashreplace").decode("ascii")
            print(f"DeepSeek API Response Content: {safe_text}")
            response.raise_for_status()
            result = response.json()
            return result['choices'][0]['message']['content']
        except requests.RequestException as e:
            print(f"Error calling DeepSeek API: {e}")
            return None

    def chat_completion_stream(
        self,
        messages: List[Dict[str, str]],
        model: str = 'deepseek-chat',
    ) -> Iterable[str]:
        """
        调用 DeepSeek API 的流式输出（SSE）。

        Yields:
            每次 yield 一段增量文本（delta content）。
        """
        if not self.api_key:
            raise RuntimeError("DeepSeek API key is not set")

        payload = {
            "model": model,
            "messages": messages,
            "temperature": 0.7,
            "max_tokens": 1024,
            "top_p": 0.95,
            "stream": True,
        }

        # Use a connect timeout + a generous read timeout for long responses.
        resp = requests.post(
            self.api_url,
            headers=self.headers,
            json=payload,
            stream=True,
            timeout=(10, 300),
        )
        resp.raise_for_status()

        for raw_line in resp.iter_lines(decode_unicode=True):
            if not raw_line:
                continue
            # SSE keep-alive/comments
            if raw_line.startswith(":"):
                continue
            if not raw_line.startswith("data:"):
                continue

            data = raw_line[len("data:"):].strip()
            if data == "[DONE]":
                break

            try:
                chunk = json.loads(data)
            except Exception:
                # Ignore malformed chunks (should be rare); upstream may send transient lines.
                continue

            try:
                choices = chunk.get("choices") or []
                if not choices:
                    continue
                delta = choices[0].get("delta") or {}
                content = delta.get("content")
                if isinstance(content, str) and content:
                    yield content
            except Exception:
                continue
