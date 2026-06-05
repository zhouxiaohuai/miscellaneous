from flask import Flask, request, jsonify, Response, stream_with_context
from flask_cors import CORS
from deepseek_client import DeepSeekClient
from config import Config
import json

app = Flask(__name__)
CORS(app)  # 允许跨域请求

client = DeepSeekClient()

@app.route('/api/chat', methods=['POST'])
def chat():
    """
    聊天API接口
    接收前端/后端发送的消息，调用DeepSeek API生成回复

    兼容两种入参：
    1) {"message": "..."}  单轮消息（向后兼容）
    2) {"messages": [{"role": "...", "content": "..."}, ...]} 多轮上下文（推荐）
    """
    try:
        data = request.json
        if not data:
            return jsonify({'error': 'Missing request body'}), 400

        messages = data.get('messages')
        if isinstance(messages, list) and len(messages) > 0:
            # Basic validation to avoid sending malformed payloads to the vendor API
            for i, m in enumerate(messages):
                if not isinstance(m, dict) or 'role' not in m or 'content' not in m:
                    return jsonify({'error': f'Invalid messages[{i}]'}), 400
        else:
            message = data.get('message')
            if not message:
                return jsonify({'error': 'Missing message parameter'}), 400
            messages = [{"role": "user", "content": message}]
        
        # 调用DeepSeek API
        response = client.chat_completion(messages)
        
        if response:
            return jsonify({'response': response})
        else:
            return jsonify({'error': 'Failed to get response from DeepSeek API'}), 500
            
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/chat/stream', methods=['POST'])
def chat_stream():
    """
    流式聊天API接口（SSE）
    - 输入：同 /api/chat，推荐 {"messages":[...]} 形式
    - 输出：text/event-stream，事件：token/done/error
    """
    data = request.json
    if not data:
        return jsonify({'error': 'Missing request body'}), 400

    messages = data.get('messages')
    model = data.get('model') or 'deepseek-chat'

    if isinstance(messages, list) and len(messages) > 0:
        for i, m in enumerate(messages):
            if not isinstance(m, dict) or 'role' not in m or 'content' not in m:
                return jsonify({'error': f'Invalid messages[{i}]'}), 400
    else:
        message = data.get('message')
        if not message:
            return jsonify({'error': 'Missing message parameter'}), 400
        messages = [{"role": "user", "content": message}]

    @stream_with_context
    def generate():
        full = ""
        try:
            for delta in client.chat_completion_stream(messages, model=model):
                full += delta
                yield "event: token\n"
                yield f"data: {json.dumps({'delta': delta}, ensure_ascii=False)}\n\n"

            yield "event: done\n"
            yield f"data: {json.dumps({'full': full}, ensure_ascii=False)}\n\n"
        except Exception as e:
            yield "event: error\n"
            yield f"data: {json.dumps({'error': str(e)}, ensure_ascii=False)}\n\n"

    headers = {
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
        # Some proxies buffer unless this header is present (nginx). Safe to include.
        "X-Accel-Buffering": "no",
    }
    return Response(generate(), mimetype="text/event-stream", headers=headers)

@app.route('/api/health', methods=['GET'])
def health():
    """
    健康检查接口
    """
    return jsonify({'status': 'ok'})

if __name__ == '__main__':
    app.run(host=Config.HOST, port=Config.PORT, debug=Config.DEBUG)
