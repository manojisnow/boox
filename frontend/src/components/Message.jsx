import React from 'react';
import './Message.css';

const Message = ({ text, sender }) => {
    const isUser = sender === 'user';
    let displayName = '';
    if (!isUser) {
        displayName = sender === 'assistant' ? 'AI' : sender;
    }
    return (
        <div className={`message-row ${isUser ? 'user' : 'bot'}`}>
            {!isUser && <div className="message-sender">{displayName}</div>}
            <div className={`message-bubble ${isUser ? 'user' : 'bot'}`}>
                <span className="message-text">{text}</span>
            </div>
        </div>
    );
};

export default Message;