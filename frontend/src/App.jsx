import React, { useState, useEffect } from 'react';
import ChatBox from './components/ChatBox';
import { getServers } from './services/api';
import './App.css';

const App = () => {
    const [servers, setServers] = useState([]);
    const [selectedServer, setSelectedServer] = useState('');
    const [sidebarExpanded, setSidebarExpanded] = useState(false);
    const [chatBoxKey, setChatBoxKey] = useState(0);

    useEffect(() => {
        const fetchServers = async () => {
            try {
                const result = await getServers();
                setServers(result);
            } catch (err) {
                console.error('Failed to fetch servers:', err.message);
            }
        };
        fetchServers();
    }, []);

    const handleNewChat = () => {
        setChatBoxKey(prev => prev + 1);
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
                    <div style={{ padding: 'var(--space-lg)', color: 'var(--color-text-secondary)', fontWeight: 500 }}>
                        Chat History
                    </div>
                )}
            </div>
            <div className="main-chat-section">
                <ChatBox
                    key={chatBoxKey}
                    selectedServer={selectedServer}
                    setSelectedServer={setSelectedServer}
                    servers={servers}
                />
            </div>
        </div>
    );
};

export default App;
