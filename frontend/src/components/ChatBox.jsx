import React, { useState, useEffect, useRef, useCallback } from 'react';
import Message from './Message';
import { sendMessage, streamMessage, getModels } from '../services/api';
import './ChatBox.css';

const ChatBox = ({ selectedServer, setSelectedServer, servers }) => {
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [models, setModels] = useState([]);
    const [selectedModel, setSelectedModel] = useState('');
    const [chatLocked, setChatLocked] = useState(false);
    const [isStreaming, setIsStreaming] = useState(false);
    const [streamEnabled, setStreamEnabled] = useState(true);
    const [systemPrompt, setSystemPrompt] = useState(() => sessionStorage.getItem('boox-system-prompt') || '');
    const [showSystemPrompt, setShowSystemPrompt] = useState(false);
    const [error, setError] = useState('');
    const messagesEndRef = useRef(null);
    const sessionIdRef = useRef(null);
    const textareaRef = useRef(null);

    // Generate a sessionId once per ChatBox instance
    useEffect(() => {
        if (!sessionIdRef.current) {
            sessionIdRef.current = Math.random().toString(36).substring(2, 15);
        }
    }, []);

    // Fetch models when server changes
    useEffect(() => {
        setModels([]);
        setSelectedModel('');
        if (!selectedServer) return;
        const fetchModels = async () => {
            try {
                const result = await getModels(selectedServer);
                setModels(result);
                const defaultModel = result.find(m => m.name === 'llama3.2:latest');
                if (defaultModel) {
                    setSelectedModel(defaultModel.name);
                }
            } catch (err) {
                console.error('Failed to fetch models:', err.message);
                setError('Failed to fetch models. Please try again.');
            }
        };
        fetchModels();
    }, [selectedServer]);

    // Reset conversation when model changes mid-chat
    useEffect(() => {
        if (messages.length > 0 && selectedModel) {
            setMessages([]);
            sessionIdRef.current = Math.random().toString(36).substring(2, 15);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedModel]);

    // Auto-scroll to latest message
    useEffect(() => {
        if (messagesEndRef.current) {
            messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
        }
    }, [messages, chatLocked]);

    // Clear error when user selects server/model
    useEffect(() => { setError(''); }, [selectedServer, selectedModel]);

    // Auto-dismiss error after 5 seconds
    useEffect(() => {
        if (error) {
            const timer = setTimeout(() => setError(''), 5000);
            return () => clearTimeout(timer);
        }
    }, [error]);

    // Auto-resize textarea
    const resizeTextarea = useCallback(() => {
        const textarea = textareaRef.current;
        if (textarea) {
            textarea.style.height = 'auto';
            textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px';
        }
    }, []);

    const handleSendMessage = async (e) => {
        if (e) e.preventDefault();
        if (!selectedServer) {
            setError('Please select a server before sending a message.');
            return;
        }
        if (!selectedModel) {
            setError('Please select a model before sending a message.');
            return;
        }
        if (!input.trim()) return;
        if (chatLocked) return;

        setError('');
        setChatLocked(true);
        const userMessage = { text: input, sender: 'user' };
        setMessages((prev) => [...prev, userMessage]);
        const currentInput = input.trim();
        setInput('');

        // Reset textarea height after clearing input
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto';
        }

        if (streamEnabled) {
            try {
                const response = await streamMessage(
                    currentInput,
                    selectedServer.trim(),
                    selectedModel.trim(),
                    sessionIdRef.current.trim(),
                    systemPrompt
                );
                // Add empty bot message that will be filled by streaming
                setMessages((prev) => [...prev, { text: '', sender: 'assistant', toolCalls: [] }]);
                setIsStreaming(true);

                const reader = response.body.getReader();
                const decoder = new TextDecoder();
                let buffer = '';
                let currentEventName = '';

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;

                    buffer += decoder.decode(value, { stream: true });
                    const lines = buffer.split('\n');
                    buffer = lines.pop() || '';

                    for (const line of lines) {
                        if (line.startsWith('event:')) {
                            currentEventName = line.slice(6).trim();
                        } else if (line.startsWith('data:')) {
                            // Use raw slice (no trim) to preserve whitespace tokens from Ollama.
                            // Only trim when comparing against the sentinel value.
                            const rawData = line.slice(5);
                            if (rawData.trim() === '[DONE]') {
                                currentEventName = '';
                                break;
                            }
                            if (currentEventName === 'tool_call') {
                                try {
                                    const parsed = JSON.parse(rawData);
                                    setMessages((prev) => {
                                        const updated = [...prev];
                                        const last = { ...updated[updated.length - 1] };
                                        last.toolCalls = [...(last.toolCalls || []), {
                                            index: parsed.index,
                                            name: parsed.name,
                                            args: parsed.arguments,
                                            result: null,
                                            status: 'searching',
                                        }];
                                        updated[updated.length - 1] = last;
                                        return updated;
                                    });
                                } catch (e) {
                                    console.warn('Malformed tool_call SSE payload:', e.message);
                                }
                            } else if (currentEventName === 'tool_result') {
                                try {
                                    const parsed = JSON.parse(rawData);
                                    setMessages((prev) => {
                                        const updated = [...prev];
                                        const last = { ...updated[updated.length - 1] };
                                        // Match by numeric index so same-named tools called
                                        // multiple times are handled correctly.
                                        last.toolCalls = (last.toolCalls || []).map(tc =>
                                            tc.index === parsed.index
                                                ? { ...tc, result: parsed.result, status: 'done' }
                                                : tc
                                        );
                                        updated[updated.length - 1] = last;
                                        return updated;
                                    });
                                } catch (e) {
                                    console.warn('Malformed tool_result SSE payload:', e.message);
                                }
                            } else {
                                // Plain text token — append raw (untrimmed) to preserve spaces
                                setMessages((prev) => {
                                    const updated = [...prev];
                                    const last = updated[updated.length - 1];
                                    updated[updated.length - 1] = {
                                        ...last,
                                        text: last.text + rawData,
                                    };
                                    return updated;
                                });
                            }
                            currentEventName = '';
                        } else if (line === '') {
                            // Blank line resets event name (SSE spec)
                            currentEventName = '';
                        }
                    }
                }
            } catch (err) {
                console.error('Stream failed:', err.message);
                setError('Failed to stream message. Please try again.');
            } finally {
                setIsStreaming(false);
                setChatLocked(false);
            }
        } else {
            try {
                const response = await sendMessage(
                    currentInput,
                    selectedServer.trim(),
                    selectedModel.trim(),
                    sessionIdRef.current.trim(),
                    false,
                    systemPrompt
                );
                const botMessage = { text: response.content, sender: response.role };
                setMessages((prev) => [...prev, botMessage]);
            } catch (err) {
                console.error('Send failed:', err.message);
                setError('Failed to send message. Please try again.');
            } finally {
                setChatLocked(false);
            }
        }
    };

    // Enter to send, Shift+Enter for newline
    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSendMessage();
        }
    };

    const handleSystemPromptChange = (e) => {
        const val = e.target.value;
        setSystemPrompt(val);
        sessionStorage.setItem('boox-system-prompt', val);
    };

    const handleInputChange = (e) => {
        setInput(e.target.value);
        if (error) setError('');
        resizeTextarea();
    };

    const showPlaceholder = messages.length === 0;

    return (
        <div className="chat-window chat-style-window">
            <div className="chat-controls">
                <select
                    value={selectedServer}
                    onChange={e => setSelectedServer(e.target.value)}
                    disabled={chatLocked}
                    className="inline-select"
                >
                    <option value="" disabled>Select a server</option>
                    {servers.map(server => (
                        <option key={server} value={server}>{server}</option>
                    ))}
                </select>
                {models.length > 0 && (
                    <select
                        value={selectedModel}
                        onChange={e => setSelectedModel(e.target.value)}
                        disabled={chatLocked}
                        className="inline-select"
                    >
                        <option value="" disabled>Select a model</option>
                        {models.map(model => (
                            <option key={model.name} value={model.name}>{model.name}</option>
                        ))}
                    </select>
                )}
            </div>
            <div className="chat-messages">
                {showPlaceholder ? (
                    <div className="chat-greeting">
                        <div className="greeting-title">Boox</div>
                        <div className="greeting-subtitle">
                            {selectedModel
                                ? `Chat with ${selectedModel.split(':')[0]}`
                                : 'Select a server and model to begin'}
                        </div>
                        <div className="greeting-hint">Press Enter to send, Shift+Enter for a new line</div>
                    </div>
                ) : (
                    messages.map((msg, index) => (
                        <Message key={index} text={msg.text} sender={msg.sender} toolCalls={msg.toolCalls} />
                    ))
                )}
                {chatLocked && !isStreaming && (
                    <div className="message-row bot">
                        <div className="message-bubble bot typing-indicator">
                            <span className="dot"></span>
                            <span className="dot"></span>
                            <span className="dot"></span>
                        </div>
                    </div>
                )}
                <div ref={messagesEndRef} />
            </div>
            <form className="input-area" onSubmit={handleSendMessage}>
                <div className="input-bubble">
                    {showSystemPrompt && (
                        <div className="system-prompt-section">
                            <textarea
                                value={systemPrompt}
                                onChange={handleSystemPromptChange}
                                placeholder="Set system behavior... (e.g. You are a helpful coding assistant)"
                                rows={2}
                                className="system-prompt-textarea"
                                disabled={chatLocked}
                            />
                        </div>
                    )}
                    <div className="input-row">
                        <button
                            type="button"
                            className={`system-prompt-toggle${systemPrompt ? ' active' : ''}`}
                            onClick={() => setShowSystemPrompt(v => !v)}
                            title="System prompt"
                            aria-label="Toggle system prompt"
                        >
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <circle cx="12" cy="12" r="3" />
                                <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
                            </svg>
                        </button>
                        <textarea
                            ref={textareaRef}
                            value={input}
                            onChange={handleInputChange}
                            onKeyDown={handleKeyDown}
                            placeholder="Message..."
                            rows={1}
                            className="input-textarea"
                        />
                        <button
                            type="submit"
                            className="send-btn"
                            aria-label="Send"
                            disabled={chatLocked || !input.trim() || !selectedServer || !selectedModel}
                        >
                            <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
                                <circle cx="16" cy="16" r="16" fill="currentColor" style={{ color: 'var(--color-accent)' }}/>
                                <path d="M16 10L16 22M16 10L11 15M16 10L21 15" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                            </svg>
                        </button>
                        <label className="stream-toggle-inline" title="Stream response">
                            <input
                                type="checkbox"
                                checked={streamEnabled}
                                onChange={e => setStreamEnabled(e.target.checked)}
                                disabled={chatLocked}
                            />
                            <span className="stream-icon">&#x2301;</span>
                        </label>
                    </div>
                </div>
            </form>
            {error && (
                <div className="toast" role="alert">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <circle cx="12" cy="12" r="10" />
                        <line x1="15" y1="9" x2="9" y2="15" />
                        <line x1="9" y1="9" x2="15" y2="15" />
                    </svg>
                    <span>{error}</span>
                    <button className="toast-dismiss" onClick={() => setError('')} aria-label="Dismiss">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                            <line x1="18" y1="6" x2="6" y2="18" />
                            <line x1="6" y1="6" x2="18" y2="18" />
                        </svg>
                    </button>
                </div>
            )}
        </div>
    );
};

export default ChatBox;
