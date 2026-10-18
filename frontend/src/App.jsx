import React, { useState, useEffect, useCallback, useRef } from 'react';
import ChatBox from './components/ChatBox';
import ConversationSidebar from './components/ConversationSidebar';
import { PreferencesPanel, ProjectEditor } from './components/Personalization';
import {
    getServers,
    listConversations,
    renameConversation,
    deleteConversation,
    getPreferences, listProjects, apiError,
} from './services/api';
import './App.css';

const ACTIVE_KEY = 'boox-active-conversation';

const newConversationId = () =>
    (window.crypto && window.crypto.randomUUID)
        ? window.crypto.randomUUID()
        : Math.random().toString(36).substring(2, 15);

const App = () => {
    const [showPreferences, setShowPreferences] = useState(false);
    const [onboarding, setOnboarding] = useState(false);
    const [projects, setProjects] = useState([]);
    const [projectsRevision, setProjectsRevision] = useState(0);
    const [selectedProject, setSelectedProject] = useState('');
    const [draftProject, setDraftProject] = useState(undefined);
    const [appError, setAppError] = useState('');
    const initialStoredId = useRef(localStorage.getItem(ACTIVE_KEY));
    const initialNavigationRestored = useRef(false);
    const preferencesInteracted = useRef(false);
    const projectRequestId = useRef(0);
    const conversationRequestId = useRef(0);
    const [servers, setServers] = useState([]);
    const [selectedServer, setSelectedServer] = useState('');
    const [sidebarExpanded, setSidebarExpanded] = useState(false);
    const [conversations, setConversations] = useState([]);
    const [activeConversationId, setActiveConversationId] = useState(
        () => localStorage.getItem(ACTIVE_KEY) || newConversationId()
    );

    useEffect(() => {
        let current = true;
        getPreferences().then(p => { if (current && !preferencesInteracted.current && p.onboardingStatus === 'not_started') { setOnboarding(true); setShowPreferences(true); } }).catch(e => { if (current) setAppError(apiError(e)); });
        const requestId = ++projectRequestId.current;
        listProjects().then(p => { if (current && requestId === projectRequestId.current) setProjects(p); }).catch(e => { if (current && requestId === projectRequestId.current) setAppError(apiError(e)); });
        return () => { current = false; };
    }, []);
    const refreshProjects = async () => {
        const requestId = ++projectRequestId.current;
        const list = await listProjects();
        if (requestId !== projectRequestId.current) return;
        if (projects.some(project => !list.some(next => next.id === project.id))) setProjectsRevision(v => v + 1);
        setProjects(list);
        setSelectedProject(current => current && !list.some(p => p.id === current) ? '' : current);
        await refreshConversations();
    };

    useEffect(() => {
        localStorage.setItem(ACTIVE_KEY, activeConversationId);
    }, [activeConversationId]);

    const refreshConversations = useCallback(async () => {
        const requestId = ++conversationRequestId.current;
        try {
            const list = await listConversations();
            if (requestId !== conversationRequestId.current) return;
            setConversations(list);
            if (initialStoredId.current) {
                if (!list.some(c => c.id === initialStoredId.current)) setActiveConversationId(newConversationId());
                initialStoredId.current = null;
            }
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
        if (activeConversation && !initialNavigationRestored.current) {
            initialNavigationRestored.current = true;
            setSelectedProject(activeConversation.projectId || '');
        }
    }, [activeConversation]);

    const handleNewChat = () => {
        setActiveConversationId(newConversationId());
        refreshConversations();
    };

    const handleSelectConversation = (id) => {
        const summary = conversations.find(c => c.id === id);
        setSelectedProject(summary?.projectId || '');
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
            console.error('Failed to rename conversation:', err.message); setAppError(apiError(err));
        }
    };

    const handleDelete = async (id) => {
        // eslint-disable-next-line no-alert
        if (!window.confirm('Delete this conversation? Saved project notes remain; manage them in Project memory.')) return;
        try {
            await deleteConversation(id);
            if (id === activeConversationId) {
                setActiveConversationId(newConversationId());
            }
            await refreshConversations();
        } catch (err) {
            console.error('Failed to delete conversation:', err.message); setAppError(apiError(err));
        }
    };

    return (
        <div className="app-root">
            {showPreferences && <PreferencesPanel onboarding={onboarding} onClose={() => { preferencesInteracted.current = true; setShowPreferences(false); setOnboarding(false); }} />}
            {draftProject !== undefined && <ProjectEditor project={draftProject} onClose={() => setDraftProject(undefined)} onSaved={refreshProjects} chatCount={conversations.filter(c => c.projectId === draftProject?.id).length} />}
            {appError && <div className="toast" role="alert">{appError}<button onClick={() => setAppError('')} aria-label="Dismiss application error">Dismiss</button></div>}
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
                    <>
                    <nav className="project-navigation" aria-label="Projects">
                        <button onClick={() => { preferencesInteracted.current = true; setOnboarding(false); setShowPreferences(true); }}>Preferences</button>
                        <label htmlFor="project-filter">Project</label>
                        <select id="project-filter" value={selectedProject} onChange={e => setSelectedProject(e.target.value)}><option value="">Standalone chats</option>{projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select>
                        <div className="panel-actions"><button onClick={() => setDraftProject(null)}>Create project</button><button onClick={() => refreshProjects().catch(e => setAppError(apiError(e)))}>Refresh projects</button>{selectedProject && <button onClick={() => setDraftProject(projects.find(p => p.id === selectedProject))}>Edit project</button>}</div>
                    </nav>
                    <ConversationSidebar
                        conversations={conversations.filter(c => (c.projectId || '') === selectedProject)}
                        activeId={activeConversationId}
                        onSelect={handleSelectConversation}
                        onNewChat={handleNewChat}
                        onRename={handleRename}
                        onDelete={handleDelete}
                    />
                    </>
                )}
            </div>
            <div className="main-chat-section">
                <ChatBox
                    key={activeConversationId}
                    conversationId={activeConversationId}
                    initialProjectId={activeConversation?.projectId ?? selectedProject}
                    projects={projects}
                    settingsRevision={projectsRevision}
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
