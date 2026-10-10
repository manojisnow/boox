import React, { useRef, useEffect } from 'react';
import './ChatInput.css';

const ChatInput = ({
    input,
    setInput,
    handleSendMessage,
    chatLocked,
    streamEnabled,
    setStreamEnabled,
    disabled
}) => {
    const textareaRef = useRef(null);

    // Auto-resize textarea
    useEffect(() => {
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto';
            textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
        }
    }, [input]);

    const onKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            if (!chatLocked && input.trim()) {
                handleSendMessage();
            }
        }
    };

    return (
        <div className="chat-input-container glass-heavy">
            <textarea
                ref={textareaRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={onKeyDown}
                placeholder="Ask anything..."
                rows={1}
                disabled={disabled || chatLocked}
                className="chat-textarea"
            />
            <div className="input-actions">
                <label className="stream-toggle" title="Stream response">
                    <input
                        type="checkbox"
                        checked={streamEnabled}
                        onChange={(e) => setStreamEnabled(e.target.checked)}
                        disabled={chatLocked}
                    />
                    <span className="stream-indicator">
                        {streamEnabled ? '🌊' : '💧'}
                    </span>
                </label>
                <button
                    onClick={handleSendMessage}
                    disabled={disabled || chatLocked || !input.trim()}
                    className="send-button"
                    aria-label="Send message"
                >
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <line x1="22" y1="2" x2="11" y2="13"></line>
                        <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
                    </svg>
                </button>
            </div>
        </div>
    );
};

export default ChatInput;
