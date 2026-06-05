from flask import Flask, request, jsonify
from flask_cors import CORS
from config import Config

app = Flask(__name__)
CORS(app)


@app.route("/api/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


@app.route("/api/weather", methods=["POST"])
def weather():
    """
    天气查询接口（骨架）
    约定输入（暂定，后续可调整）：
      - {"city": "上海"} 或 {"location": "上海"}
    当前仅返回占位响应，不调用第三方天气服务。
    """
    data = request.json or {}
    city = (data.get("city") or data.get("location") or "").strip()
    if not city:
        return jsonify({"error": "Missing city parameter"}), 400

    return jsonify(
        {
            "city": city,
            "weather": None,
            "hint": "基础模块已就绪；后续在此接入真实天气API与Agent逻辑。",
        }
    )


if __name__ == "__main__":
    app.run(host=Config.HOST, port=Config.PORT, debug=Config.DEBUG)

