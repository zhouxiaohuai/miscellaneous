# aichat-weather-agent

一个用于“天气查询”的 Agent 服务（当前仅提供最小项目骨架，后续再补充真实天气查询与 Agent 编排逻辑）。

## 目录结构

- `src/app.py`: Flask 服务入口
- `src/config.py`: 环境变量配置
- `requirements.txt`: Python 依赖
- `.env.example`: 环境变量示例

## 本地启动

在 `aichat/aichat-weather-agent` 目录下：

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
python src/app.py
```

默认端口：`5001`

## 接口（占位）

- `GET /api/health`
- `POST /api/weather`：`{"city":"上海"}` / `{"location":"上海"}`

