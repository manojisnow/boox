import React, { useState, useEffect, useRef } from 'react';
import Message from './Message';
import { sendMessage, getModels } from '../services/api';

const ChatBox = ({ selectedServer, setSelectedServer, servers }) => {
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [models, setModels] = useState([]);
    const [selectedModel, setSelectedModel] = useState('');
    const [chatLocked, setChatLocked] = useState(false);
    const [streamEnabled, setStreamEnabled] = useState(true);
    const messagesEndRef = useRef(null);
    const sessionIdRef = useRef(null);
    const [error, setError] = useState('');

    // Generate a sessionId once per ChatBox instance
    useEffect(() => {
        if (!sessionIdRef.current) {
            sessionIdRef.current = Math.random().toString(36).substring(2, 15);
        }
    }, []);

    useEffect(() => {
        setModels([]);
        setSelectedModel('');
        if (!selectedServer) return;
        const fetchModels = async () => {
            const result = await getModels(selectedServer);
            setModels(result);
        };
        fetchModels();
    }, [selectedServer]);

    useEffect(() => {
        if (messagesEndRef.current) {
            messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
        }
    }, [messages]);

    const handleSendMessage = async (e) => {
        e.preventDefault();
        if (!selectedServer) {
            setError('Please select a server before sending a message.');
            return;
        }
        if (!selectedModel) {
            setError('Please select a model before sending a message.');
            return;
        }
        if (!input.trim() || chatLocked) return;
        setError('');
        setChatLocked(true);
        const userMessage = { text: input, sender: 'user' };
        setMessages((prevMessages) => [...prevMessages, userMessage]);
        setInput(''); // Clear input immediately for better UX
        try {
            const response = await sendMessage(input, selectedServer, selectedModel, sessionIdRef.current, streamEnabled);
            const botMessage = { text: response.content, sender: response.role };
            setMessages((prevMessages) => [...prevMessages, botMessage]);
        } catch (err) {
            setMessages((prevMessages) => [...prevMessages, { text: '(Error sending message)', sender: 'assistant' }]);
        } finally {
            setChatLocked(false);
        }
    };

    // Clear error when user selects server/model or types
    useEffect(() => { setError(''); }, [selectedServer, selectedModel]);
    const handleInputChange = (e) => {
        setInput(e.target.value);
        if (error) setError('');
    };

    const showPlaceholder = messages.length === 0;

    return (
        <div className="chat-window chat-style-window">
            {/* Error message */}
            {error && <div className="chat-error-message">{error}</div>}
            {/* New Chat button */}
            <div className="chat-controls">
                <select
                    value={selectedServer}
                    onChange={e => setSelectedServer(e.target.value)}
                    disabled={chatLocked || messages.length > 0}
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
                        disabled={chatLocked || messages.length > 0}
                        className="inline-select"
                    >
                        <option value="" disabled>Select a model</option>
                        {models.map(model => (
                            <option key={model} value={model}>{model}</option>
                        ))}
                    </select>
                )}
            </div>
            <div className="chat-messages">
                {showPlaceholder ? (
                    <div className="chat-greeting">
                        Hello!
                    </div>
                ) : (
                    messages.map((msg, index) => (
                        <Message key={index} text={msg.text} sender={msg.sender} />
                    ))
                )}
                <div ref={messagesEndRef} />
            </div>
            <form className="input-area" onSubmit={handleSendMessage}>
                <div className="input-bubble">
                    <div className="input-row">
                        <textarea
                            value={input}
                            onChange={handleInputChange}
                            placeholder="Enter a prompt..."
                            rows={4}
                            className="input-textarea"
                        />
                        <button type="submit" className="send-btn" aria-label="Send" disabled={chatLocked || !input.trim() || !selectedServer || !selectedModel}>
                            <svg width="28" height="28" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <circle cx="14" cy="14" r="14" fill="#7b61ff"/>
                                <path d="M10 14L18 14M18 14L15 11M18 14L15 17" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                            </svg>
                        </button>
                        <label className="stream-toggle-inline" title="Stream response">
                            <input
                                type="checkbox"
                                checked={streamEnabled}
                                onChange={e => setStreamEnabled(e.target.checked)}
                                disabled={chatLocked}
                            />
                            <span className="stream-icon">🌊</span>
                        </label>
                    </div>
                </div>
            </form>
        </div>
    );
};

export default ChatBox;