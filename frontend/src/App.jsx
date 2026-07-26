import React, { useState, useEffect, useCallback } from 'react';
import ChatBox from './components/ChatBox';
import ConversationSidebar from './components/ConversationSidebar';
import {
    getServers,
    listConversations,
    renameConversation,
    deleteConversation,
} from './services/api';
import './App.css';

const ACTIVE_KEY = 'boox-active-conversation';

const newConversationId = () =>
    (window.crypto && window.crypto.randomUUID)
        ? window.crypto.randomUUID()
        : Math.random().toString(36).substring(2, 15);

const App = () => {
    const [servers, setServers] = useState([]);
    const [selectedServer, setSelectedServer] = useState('');
    const [sidebarExpanded, setSidebarExpanded] = useState(false);
    const [conversations, setConversations] = useState([]);
    const [activeConversationId, setActiveConversationId] = useState(
        () => localStorage.getItem(ACTIVE_KEY) || newConversationId()
    );

    useEffect(() => {
        localStorage.setItem(ACTIVE_KEY, activeConversationId);
    }, [activeConversationId]);

    const refreshConversations = useCallback(async () => {
        try {
            setConversations(await listConversations());
        } catch (err) {
            console.error('Failed to load conversations:', err.message);
        }
    }, []);

    useEffect(() => {
        const fetchServers = async () => {
            try {
                setServers(await getServers());
            } catch (err) {
                console.error('Failed to fetch servers:', err.message);
            }
        };
        fetchServers();
        refreshConversations();
    }, [refreshConversations]);

    const activeConversation = conversations.find(c => c.id === activeConversationId);

    // Restore the server the active conversation was using (e.g. after a page
    // reload) so its model can be preselected too.
    useEffect(() => {
        if (activeConversation && activeConversation.server) {
            setSelectedServer(activeConversation.server);
        }
    }, [activeConversation]);

    const handleNewChat = () => {
        setActiveConversationId(newConversationId());
        refreshConversations();
    };

    const handleSelectConversation = (id) => {
        const summary = conversations.find(c => c.id === id);
        if (summary && summary.server) {
            setSelectedServer(summary.server);
        }
        setActiveConversationId(id);
        refreshConversations();
    };

    const handleRename = async (id, title) => {
        try {
            await renameConversation(id, title);
            await refreshConversations();
        } catch (err) {
            console.error('Failed to rename conversation:', err.message);
        }
    };

    const handleDelete = async (id) => {
        // eslint-disable-next-line no-alert
        if (!window.confirm('Delete this conversation?')) return;
        try {
            await deleteConversation(id);
            if (id === activeConversationId) {
                setActiveConversationId(newConversationId());
            }
            await refreshConversations();
        } catch (err) {
            console.error('Failed to delete conversation:', err.message);
        }
    };

    return (
        <div className="app-root">
            <div className="top-left-controls">
                <button
                    className="sidebar-toggle-btn"
                    onClick={() => setSidebarExpanded(exp => !exp)}
                    aria-label={sidebarExpanded ? 'Hide chat history' : 'Show chat history'}
                >
                    {sidebarExpanded ? (
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <polyline points="15 18 9 12 15 6" />
                        </svg>
                    ) : (
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <line x1="3" y1="6" x2="21" y2="6" />
                            <line x1="3" y1="12" x2="21" y2="12" />
                            <line x1="3" y1="18" x2="21" y2="18" />
                        </svg>
                    )}
                </button>
            </div>
            <div className="bottom-left-controls">
                <button
                    className="sidebar-toggle-btn new-chat-inline-btn"
                    onClick={handleNewChat}
                    aria-label="New Chat"
                >
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <line x1="12" y1="5" x2="12" y2="19" />
                        <line x1="5" y1="12" x2="19" y2="12" />
                    </svg>
                </button>
            </div>
            <div className={`sidebar${sidebarExpanded ? ' expanded' : ' collapsed'}`}>
                {sidebarExpanded && (
                    <ConversationSidebar
                        conversations={conversations}
                        activeId={activeConversationId}
                        onSelect={handleSelectConversation}
                        onNewChat={handleNewChat}
                        onRename={handleRename}
                        onDelete={handleDelete}
                    />
                )}
            </div>
            <div className="main-chat-section">
                <ChatBox
                    key={activeConversationId}
                    conversationId={activeConversationId}
                    initialModel={activeConversation ? activeConversation.model : ''}
                    initialGenerationConfig={activeConversation ? {
                        temperature: activeConversation.temperature,
                        numCtx: activeConversation.numCtx,
                        stopSequences: activeConversation.stopSequences,
                    } : null}
                    onConversationChanged={refreshConversations}
                    selectedServer={selectedServer}
                    setSelectedServer={setSelectedServer}
                    servers={servers}
                />
            </div>
        </div>
    );
};

export default App;
