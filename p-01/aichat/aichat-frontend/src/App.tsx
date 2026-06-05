import React, { useMemo, useRef, useEffect, useState } from 'react';

interface Message {
  id: string;
  content: string;
  sender: 'user' | 'ai';
}

interface Conversation {
  id: string;
  title: string | null;
  updatedAt: string;
}

function App() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '1',
      content: '你好！我是AI助手，有什么可以帮你的吗？',
      sender: 'ai'
    }
  ]);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [isLoadingConversations, setIsLoadingConversations] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const apiBase = 'http://localhost:8081';
  const [me, setMe] = useState<{ loggedIn: boolean; userId: string | null } | null>(null);
  const [loginState, setLoginState] = useState<string>('');
  const [qrUrl, setQrUrl] = useState<string>('');
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const loadMe = async () => {
    const resp = await fetch(`${apiBase}/api/auth/me`, { credentials: 'include' });
    if (!resp.ok) {
      setMe({ loggedIn: false, userId: null });
      return;
    }
    const data = await resp.json();
    setMe({ loggedIn: Boolean(data?.loggedIn), userId: data?.userId ?? null });
  };

  const loadConversations = async () => {
    setIsLoadingConversations(true);
    try {
      const resp = await fetch(`${apiBase}/api/conversations`, { credentials: 'include' });
      if (resp.status === 401) {
        setConversations([]);
        setActiveConversationId(null);
        return;
      }
      const data = await resp.json();
      setConversations(data);
      if (!activeConversationId && Array.isArray(data) && data.length > 0) {
        setActiveConversationId(data[0].id);
      }
    } finally {
      setIsLoadingConversations(false);
    }
  };

  const loadMessages = async (conversationId: string) => {
    const resp = await fetch(`${apiBase}/api/conversations/${conversationId}/messages?limit=200`, {
      credentials: 'include'
    });
    if (!resp.ok) return;
    const data = await resp.json();
    if (!Array.isArray(data)) return;

    const mapped: Message[] = data.map((m: any) => ({
      id: String(m.id),
      content: String(m.content ?? ''),
      sender: m.role === 'user' ? 'user' : 'ai'
    }));
    setMessages(mapped.length > 0 ? mapped : [
      {
        id: '1',
        content: '你好！我是AI助手，有什么可以帮你的吗？',
        sender: 'ai'
      }
    ]);
  };

  const createConversation = async () => {
    const resp = await fetch(`${apiBase}/api/conversations`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include',
      body: JSON.stringify({ title: '新会话' })
    });
    if (!resp.ok) return;
    const data = await resp.json();
    await loadConversations();
    if (data?.id) {
      setActiveConversationId(data.id);
      await loadMessages(data.id);
    }
  };

  useEffect(() => {
    loadMe().then(() => loadConversations());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (activeConversationId) {
      loadMessages(activeConversationId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeConversationId]);

  const handleSend = async () => {
    if (!inputValue.trim()) return;
    if (!me?.loggedIn) return;
    if (!activeConversationId) {
      await createConversation();
      if (!activeConversationId) return;
    }

    const userMessage: Message = {
      id: Date.now().toString(),
      content: inputValue,
      sender: 'user'
    };

    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setIsLoading(true);

    try {
      const aiMessageId = (Date.now() + 1).toString();
      setMessages(prev => [
        ...prev,
        {
          id: aiMessageId,
          content: '',
          sender: 'ai'
        }
      ]);

      // 调用Spring Boot后端流式API（SSE），使用 fetch 读取流
      const response = await fetch(`${apiBase}/api/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify({ conversationId: activeConversationId, message: inputValue })
      });
      if (!response.ok) {
        const text = await response.text().catch(() => '');
        setMessages(prev =>
          prev.map(m => (m.id === aiMessageId ? { ...m, content: `Error: HTTP ${response.status}${text ? ` - ${text}` : ''}` } : m))
        );
        return;
      }
      if (!response.body) {
        setMessages(prev => prev.map(m => (m.id === aiMessageId ? { ...m, content: 'Error: Streaming not supported by browser.' } : m)));
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      const appendDelta = (delta: string) => {
        if (!delta) return;
        setMessages(prev => prev.map(m => (m.id === aiMessageId ? { ...m, content: m.content + delta } : m)));
      };

      const setError = (error: string) => {
        setMessages(prev => prev.map(m => (m.id === aiMessageId ? { ...m, content: `Error: ${error}` } : m)));
      };

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const parts = buffer.split('\n\n');
        buffer = parts.pop() ?? '';

        for (const block of parts) {
          const lines = block
            .split('\n')
            .map(l => l.trim())
            .filter(Boolean);

          const eventLine = lines.find(l => l.startsWith('event:'));
          const dataLine = lines.find(l => l.startsWith('data:'));
          const event = eventLine ? eventLine.slice('event:'.length).trim() : '';
          const dataStr = dataLine ? dataLine.slice('data:'.length).trim() : '';
          if (!event || !dataStr) continue;

          let payload: any = null;
          try {
            payload = JSON.parse(dataStr);
          } catch {
            continue;
          }

          if (event === 'token') {
            appendDelta(String(payload?.delta ?? ''));
          } else if (event === 'done') {
            // 流结束：刷新会话列表（updatedAt/title 等可能变化）
            loadConversations();
          } else if (event === 'error') {
            setError(String(payload?.error ?? 'Unknown error'));
          }
        }
      }
    } catch (error) {
      const errorMessage: Message = {
        id: (Date.now() + 1).toString(),
        content: 'Failed to connect to server. Please try again.',
        sender: 'ai'
      };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const startLogin = async () => {
    setIsLoggingIn(true);
    try {
      const resp = await fetch(`${apiBase}/api/auth/wechat/qr`, { credentials: 'include' });
      if (!resp.ok) return;
      const data = await resp.json();
      setLoginState(String(data?.state ?? ''));
      setQrUrl(String(data?.qrUrl ?? ''));
    } finally {
      setIsLoggingIn(false);
    }
  };

  const confirmMockLogin = async () => {
    if (!loginState) return;
    setIsLoggingIn(true);
    try {
      const resp = await fetch(`${apiBase}/api/auth/wechat/mock/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ state: loginState })
      });
      if (!resp.ok) return;
      await loadMe();
      await loadConversations();
    } finally {
      setIsLoggingIn(false);
    }
  };

  const logout = async () => {
    await fetch(`${apiBase}/api/auth/logout`, { method: 'POST', credentials: 'include' });
    await loadMe();
    setConversations([]);
    setActiveConversationId(null);
    setMessages([
      {
        id: '1',
        content: '你好！我是AI助手，有什么可以帮你的吗？',
        sender: 'ai'
      }
    ]);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  if (!me?.loggedIn) {
    return (
      <div className="chat-container">
        <div className="chat-header">
          <div className="header-left">AI Chat</div>
          <div className="header-right" />
        </div>
        <div className="chat-messages">
          <div className="message ai-message">请先微信扫码登录后使用聊天功能（当前为本地开发“模拟扫码”）。</div>
          <div className="message ai-message">
            <button className="send-button" onClick={startLogin} disabled={isLoggingIn}>
              生成二维码（模拟）
            </button>
          </div>
          {loginState ? (
            <div className="message ai-message">
              <div style={{ marginBottom: 8 }}>state：{loginState}</div>
              {qrUrl ? (
                <div style={{ marginBottom: 8 }}>
                  <a href={qrUrl} target="_blank" rel="noreferrer">
                    打开微信扫码登录页面
                  </a>
                </div>
              ) : null}
              <button className="send-button" onClick={confirmMockLogin} disabled={isLoggingIn}>
                我已扫码（模拟确认登录）
              </button>
            </div>
          ) : null}
        </div>
        <div className="message-input">
          <input type="text" className="input-field" value="" disabled placeholder="登录后可输入消息..." />
          <button className="send-button" disabled>
            发送
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="chat-container">
      <div className="chat-header">
        <div className="header-left">
          AI Chat
        </div>
        <div className="header-right">
          <button className="new-conversation-button" onClick={logout}>
            退出
          </button>
          <select
            className="conversation-select"
            value={activeConversationId ?? ''}
            onChange={(e) => setActiveConversationId(e.target.value || null)}
            disabled={isLoadingConversations || conversations.length === 0}
          >
            {conversations.length === 0 ? (
              <option value="">无会话</option>
            ) : (
              conversations.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.title || c.id}
                </option>
              ))
            )}
          </select>
          <button className="new-conversation-button" onClick={createConversation} disabled={isLoadingConversations}>
            新建
          </button>
        </div>
      </div>
      <div className="chat-messages">
        {messages.map(message => (
          <div 
            key={message.id} 
            className={`message ${message.sender === 'user' ? 'user-message' : 'ai-message'}`}
          >
            {message.content}
          </div>
        ))}
        {isLoading && (
          <div className="message ai-message">
            思考中...
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>
      <div className="message-input">
        <input
          type="text"
          className="input-field"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder="输入消息..."
          disabled={isLoading}
        />
        <button 
          className="send-button" 
          onClick={handleSend}
          disabled={!inputValue.trim() || isLoading}
        >
          发送
        </button>
      </div>
    </div>
  );
}

export default App;
