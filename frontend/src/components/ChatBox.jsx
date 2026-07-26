import React, { useState, useEffect, useRef, useCallback } from 'react';
import Message from './Message';
import { sendMessage, streamMessage, getModels, getConversationMessages } from '../services/api';
import './ChatBox.css';

const ChatBox = ({
    conversationId,
    initialModel,
    initialGenerationConfig,
    onConversationChanged,
    selectedServer,
    setSelectedServer,
    servers,
}) => {
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
    const [attachedImages, setAttachedImages] = useState([]);
    const [showGenerationSettings, setShowGenerationSettings] = useState(false);
    const [temperature, setTemperature] = useState(
        () => initialGenerationConfig?.temperature ?? ''
    );
    const [numCtx, setNumCtx] = useState(() => initialGenerationConfig?.numCtx ?? '');
    const [stopSequencesText, setStopSequencesText] = useState(
        () => (initialGenerationConfig?.stopSequences || []).join(', ')
    );
    const messagesEndRef = useRef(null);
    const textareaRef = useRef(null);
    const fileInputRef = useRef(null);

    const MAX_IMAGES = 4;
    const MAX_IMAGE_BYTES = 7_000_000; // matches the backend's ~7MB-raw cap

    const selectedModelInfo = models.find(m => m.name === selectedModel);
    const supportsVision = Boolean(selectedModelInfo?.capabilities?.includes('vision'));

    // The conversation list (and this conversation's saved generation config) loads
    // asynchronously in the parent, so it's often still null on first mount. Restore
    // from it the first time real data arrives, then stop — initialGenerationConfig
    // is a fresh object on every parent re-render (e.g. after each send refreshes the
    // sidebar), so re-syncing on every change would keep clobbering the user's own
    // in-progress edits to these fields.
    const generationConfigRestored = useRef(false);
    useEffect(() => {
        if (!initialGenerationConfig || generationConfigRestored.current) return;
        generationConfigRestored.current = true;
        setTemperature(initialGenerationConfig.temperature ?? '');
        setNumCtx(initialGenerationConfig.numCtx ?? '');
        setStopSequencesText((initialGenerationConfig.stopSequences || []).join(', '));
    }, [initialGenerationConfig]);

    // Load persisted history for this conversation on mount (component is
    // remounted with a new key whenever the active conversation changes).
    useEffect(() => {
        if (!conversationId) return;
        let cancelled = false;
        const loadHistory = async () => {
            try {
                const history = await getConversationMessages(conversationId);
                if (!cancelled && history.length > 0) {
                    setMessages(history.map(m => ({
                        text: m.content,
                        sender: m.role,
                        // Stored images have no known MIME type; browsers decode a
                        // data: URL correctly regardless of the declared type here.
                        images: (m.images || []).map(b64 => `data:image/png;base64,${b64}`),
                    })));
                }
            } catch (err) {
                console.error('Failed to load conversation history:', err.message);
            }
        };
        loadHistory();
        return () => { cancelled = true; };
    }, [conversationId]);

    // Fetch models when server changes; prefer the conversation's own model on resume.
    useEffect(() => {
        setModels([]);
        setSelectedModel('');
        if (!selectedServer) return;
        const fetchModels = async () => {
            try {
                const result = await getModels(selectedServer);
                setModels(result);
                const preferred =
                    result.find(m => m.name === initialModel)
                    || result.find(m => m.name === 'phi4-mini:latest');
                if (preferred) {
                    setSelectedModel(preferred.name);
                }
            } catch (err) {
                console.error('Failed to fetch models:', err.message);
                setError('Failed to fetch models. Please try again.');
            }
        };
        fetchModels();
    }, [selectedServer, initialModel]);

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

    // Drop staged images if the selected model can't see them (e.g. user switched
    // to a text-only model after attaching something).
    useEffect(() => {
        if (!supportsVision && attachedImages.length > 0) {
            setAttachedImages([]);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [supportsVision]);

    const handleFileSelect = (e) => {
        const files = Array.from(e.target.files || []);
        e.target.value = ''; // allow re-selecting the same file later
        if (files.length === 0) return;

        const room = MAX_IMAGES - attachedImages.length;
        if (room <= 0) {
            setError(`You can attach up to ${MAX_IMAGES} images.`);
            return;
        }

        files.slice(0, room).forEach((file) => {
            if (!file.type.startsWith('image/')) {
                setError('Only image files can be attached.');
                return;
            }
            if (file.size > MAX_IMAGE_BYTES) {
                setError(`${file.name} is too large (max ${Math.floor(MAX_IMAGE_BYTES / 1_000_000)}MB).`);
                return;
            }
            const reader = new FileReader();
            reader.onload = () => {
                const dataUrl = reader.result;
                const base64 = dataUrl.slice(dataUrl.indexOf(',') + 1);
                setAttachedImages((prev) =>
                    prev.length >= MAX_IMAGES ? prev : [...prev, { dataUrl, base64 }]
                );
            };
            reader.readAsDataURL(file);
        });
    };

    const removeAttachedImage = (index) => {
        setAttachedImages((prev) => prev.filter((_, i) => i !== index));
    };

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
        const imagesBase64 = attachedImages.map((img) => img.base64);
        const generationConfig = {
            temperature: temperature === '' ? null : Number(temperature),
            numCtx: numCtx === '' ? null : Number(numCtx),
            stopSequences: stopSequencesText
                .split(',')
                .map(s => s.trim())
                .filter(Boolean),
        };
        const userMessage = {
            text: input,
            sender: 'user',
            images: attachedImages.map((img) => img.dataUrl),
        };
        setMessages((prev) => [...prev, userMessage]);
        const currentInput = input.trim();
        setInput('');
        setAttachedImages([]);

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
                    conversationId.trim(),
                    systemPrompt,
                    imagesBase64,
                    generationConfig
                );
                // The backend has now created/updated this conversation, so surface it
                // in the sidebar immediately — before streaming finishes — in case the
                // user navigates away mid-response.
                if (onConversationChanged) onConversationChanged();
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
                if (onConversationChanged) onConversationChanged();
            }
        } else {
            try {
                const response = await sendMessage(
                    currentInput,
                    selectedServer.trim(),
                    selectedModel.trim(),
                    conversationId.trim(),
                    false,
                    systemPrompt,
                    imagesBase64,
                    generationConfig
                );
                const botMessage = { text: response.content, sender: response.role };
                setMessages((prev) => [...prev, botMessage]);
            } catch (err) {
                console.error('Send failed:', err.message);
                setError('Failed to send message. Please try again.');
            } finally {
                setChatLocked(false);
                if (onConversationChanged) onConversationChanged();
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
                        <Message key={index} text={msg.text} sender={msg.sender} toolCalls={msg.toolCalls} images={msg.images} />
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
                    {showGenerationSettings && (
                        <div className="generation-settings-section">
                            <label className="generation-settings-field">
                                <span>Temperature</span>
                                <input
                                    type="number"
                                    min="0"
                                    max="2"
                                    step="0.1"
                                    value={temperature}
                                    onChange={e => setTemperature(e.target.value)}
                                    placeholder="server default"
                                    disabled={chatLocked}
                                />
                            </label>
                            <label className="generation-settings-field">
                                <span>Context size</span>
                                <input
                                    type="number"
                                    min="1"
                                    step="1"
                                    value={numCtx}
                                    onChange={e => setNumCtx(e.target.value)}
                                    placeholder="server default"
                                    disabled={chatLocked}
                                />
                            </label>
                            <label className="generation-settings-field generation-settings-field-wide">
                                <span>Stop sequences</span>
                                <input
                                    type="text"
                                    value={stopSequencesText}
                                    onChange={e => setStopSequencesText(e.target.value)}
                                    placeholder="comma-separated, e.g. END, ###"
                                    disabled={chatLocked}
                                />
                            </label>
                        </div>
                    )}
                    {attachedImages.length > 0 && (
                        <div className="attached-images-row">
                            {attachedImages.map((img, index) => (
                                <div className="attached-image-thumb" key={index}>
                                    <img src={img.dataUrl} alt={`Attachment ${index + 1}`} />
                                    <button
                                        type="button"
                                        className="attached-image-remove"
                                        onClick={() => removeAttachedImage(index)}
                                        aria-label="Remove image"
                                    >
                                        ×
                                    </button>
                                </div>
                            ))}
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
                        <button
                            type="button"
                            className={`system-prompt-toggle${(temperature !== '' || numCtx !== '' || stopSequencesText !== '') ? ' active' : ''}`}
                            onClick={() => setShowGenerationSettings(v => !v)}
                            title="Generation settings"
                            aria-label="Toggle generation settings"
                        >
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <line x1="4" y1="21" x2="4" y2="14" />
                                <line x1="4" y1="10" x2="4" y2="3" />
                                <line x1="12" y1="21" x2="12" y2="12" />
                                <line x1="12" y1="8" x2="12" y2="3" />
                                <line x1="20" y1="21" x2="20" y2="16" />
                                <line x1="20" y1="12" x2="20" y2="3" />
                                <line x1="1" y1="14" x2="7" y2="14" />
                                <line x1="9" y1="8" x2="15" y2="8" />
                                <line x1="17" y1="16" x2="23" y2="16" />
                            </svg>
                        </button>
                        {supportsVision && (
                            <>
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept="image/*"
                                    multiple
                                    onChange={handleFileSelect}
                                    style={{ display: 'none' }}
                                />
                                <button
                                    type="button"
                                    className="attach-image-toggle"
                                    onClick={() => fileInputRef.current && fileInputRef.current.click()}
                                    disabled={chatLocked || attachedImages.length >= MAX_IMAGES}
                                    title="Attach image"
                                    aria-label="Attach image"
                                >
                                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                        <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                                        <circle cx="8.5" cy="8.5" r="1.5" />
                                        <polyline points="21 15 16 10 5 21" />
                                    </svg>
                                </button>
                            </>
                        )}
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
