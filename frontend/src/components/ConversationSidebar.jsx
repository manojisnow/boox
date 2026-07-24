import React, { useState } from 'react';
import './ConversationSidebar.css';

const ConversationSidebar = ({ conversations, activeId, onSelect, onNewChat, onRename, onDelete }) => {
    const [editingId, setEditingId] = useState(null);
    const [draft, setDraft] = useState('');

    const startEdit = (conversation) => {
        setEditingId(conversation.id);
        setDraft(conversation.title);
    };

    const cancelEdit = () => {
        setEditingId(null);
        setDraft('');
    };

    const commitEdit = () => {
        const trimmed = draft.trim();
        if (editingId && trimmed) {
            onRename(editingId, trimmed);
        }
        cancelEdit();
    };

    return (
        <div className="conversation-sidebar">
            <button className="new-chat-row" onClick={onNewChat}>
                <span className="plus">+</span> New chat
            </button>
            <div className="conversation-list">
                {conversations.length === 0 && (
                    <div className="conv-empty">No conversations yet</div>
                )}
                {conversations.map(conversation => (
                    <div
                        key={conversation.id}
                        className={`conv-item${conversation.id === activeId ? ' active' : ''}`}
                    >
                        {editingId === conversation.id ? (
                            <input
                                className="conv-rename-input"
                                value={draft}
                                autoFocus
                                onChange={e => setDraft(e.target.value)}
                                onKeyDown={e => {
                                    if (e.key === 'Enter') commitEdit();
                                    if (e.key === 'Escape') cancelEdit();
                                }}
                                onBlur={commitEdit}
                            />
                        ) : (
                            <button
                                className="conv-title"
                                onClick={() => onSelect(conversation.id)}
                                title={conversation.title}
                            >
                                {conversation.title}
                            </button>
                        )}
                        <div className="conv-actions">
                            <button
                                className="conv-action"
                                onClick={() => startEdit(conversation)}
                                aria-label="Rename conversation"
                                title="Rename"
                            >
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <path d="M12 20h9" />
                                    <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
                                </svg>
                            </button>
                            <button
                                className="conv-action"
                                onClick={() => onDelete(conversation.id)}
                                aria-label="Delete conversation"
                                title="Delete"
                            >
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <polyline points="3 6 5 6 21 6" />
                                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                                </svg>
                            </button>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default ConversationSidebar;
