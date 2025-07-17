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
    const lastEnterPressRef = useRef(0);

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
            try {
                const result = await getModels(selectedServer);
                setModels(result);
                // Set default model to llama3.2 if available
                const defaultModel = result.find(m => m.name === 'llama3.2:latest');
                if (defaultModel) {
                    setSelectedModel(defaultModel.name);
                } else {
                    console.error('No llama3.2 model found in:', result);
                }
            } catch (err) {
                console.error('Error fetching models:', err);
                setError('Failed to fetch models. Please try again.');
            }
        };
        fetchModels();
    }, [selectedServer]);

    useEffect(() => {
        if (messagesEndRef.current) {
            messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
        }
    }, [messages]);

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
        setMessages((prevMessages) => [...prevMessages, userMessage]);
        setInput('');

        try {
            // Ensure all values are properly formatted
            const server = selectedServer.trim();
            const model = selectedModel.trim();
            const sessionId = sessionIdRef.current.trim();
            const stream = Boolean(streamEnabled);

            // Log the request details before sending
            console.log('Sending message with:', {
                message: input,
                server,
                model,
                sessionId,
                stream
            });

            const response = await sendMessage(
                input.trim(),
                server,
                model,
                sessionId,
                stream
            );

            console.log('Successfully sent message:', {
                message: input,
                server,
                model,
                sessionId,
                stream
            });
            console.log('Received response:', response);

            const botMessage = { text: response.content, sender: response.role };
            setMessages((prevMessages) => [...prevMessages, botMessage]);
        } catch (err) {
            console.error('Error sending message:', err);
            setError('Failed to send message. Please try again.');
        } finally {
            setChatLocked(false);
        }
    };

    // Clear error when user selects server/model or types
    useEffect(() => { setError(''); }, [selectedServer, selectedModel]);
    
    const handleKeyPress = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            const now = Date.now();
            const timeSinceLastEnter = now - lastEnterPressRef.current;
            
            if (timeSinceLastEnter <= 500) { // 500ms window for double Enter
                handleSendMessage();
                lastEnterPressRef.current = 0; // Reset the timer
            } else {
                lastEnterPressRef.current = now;
            }
        }
    };

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
                            <option key={model.name} value={model.name}>{model.name}</option>
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
                            onKeyPress={handleKeyPress}
                            placeholder="Enter a prompt... (Press Enter twice to send)"
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