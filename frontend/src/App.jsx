import React, { useState, useEffect, useCallback, useRef } from 'react';
import ChatBox from './components/ChatBox';
import ConversationSidebar from './components/ConversationSidebar';
import Icon from './components/Icon';
import { PreferencesPanel, ProjectEditor } from './components/Personalization';
import {
    getServers,
    listConversations,
    renameConversation,
    deleteConversation,
    getPreferences, listProjects, apiError,
} from './services/api';
import './App.css';

const ACTIVE_KEY = 'ullavox-active-conversation';

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
    const [sidebarExpanded, setSidebarExpanded] = useState(() => window.innerWidth >= 900);
    const [compact, setCompact] = useState(() => window.innerWidth < 900);
    const sidebarRef = useRef(null);
    const navigationToggleRef = useRef(null);
    const [conversations, setConversations] = useState([]);
    const [activeConversationId, setActiveConversationId] = useState(
        () => localStorage.getItem(ACTIVE_KEY) || newConversationId()
    );

    useEffect(() => {
        let current = true;
        getPreferences().then(p => { if (current && !preferencesInteracted.current && p.onboardingStatus === 'not_started') { setOnboarding(true); } }).catch(e => { if (current) setAppError(apiError(e)); });
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
                const available = await getServers();
                setServers(available);
                setSelectedServer(current => current || available[0] || '');
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

    useEffect(() => {
        const resize = () => { const next = window.innerWidth < 900; if (next !== compact) { setCompact(next); setSidebarExpanded(!next); } };
        window.addEventListener('resize', resize);
        return () => window.removeEventListener('resize', resize);
    }, [compact]);

    useEffect(() => {
        if (!sidebarExpanded || !compact) return;
        const panel = sidebarRef.current;
        const previous = document.activeElement;
        panel?.querySelector('button')?.focus();
        const handleKey = event => {
            if (event.key === 'Escape') { event.preventDefault(); setSidebarExpanded(false); }
            if (event.key !== 'Tab') return;
            const targets = [navigationToggleRef.current, ...panel.querySelectorAll('button, summary, input')].filter(el => el && !el.disabled && (!el.closest('details:not([open])') || el.tagName === 'SUMMARY'));
            const first = targets[0], last = targets[targets.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        };
        document.addEventListener('keydown', handleKey);
        return () => { document.removeEventListener('keydown', handleKey); if (previous?.isConnected) previous.focus(); };
    }, [sidebarExpanded, compact]);

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
        if (window.innerWidth < 900) setSidebarExpanded(false);
        refreshConversations();
    };

    const handleSelectProject = (id) => {
        setSelectedProject(id);
        const latest = conversations.find(chat => (chat.projectId || '') === id);
        if (latest) handleSelectConversation(latest.id);
        else {
            setActiveConversationId(newConversationId());
            if (compact) setSidebarExpanded(false);
        }
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
        if (!window.confirm('Delete this conversation? Saved project notes remain; manage them in Project notes.')) return;
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
            <button ref={navigationToggleRef} className="navigation-toggle icon-button" onClick={() => setSidebarExpanded(value => !value)} aria-label={sidebarExpanded ? 'Hide chat history' : 'Show chat history'} aria-expanded={sidebarExpanded} aria-controls="ullavox-sidebar"><Icon name="panel" /></button>
            {sidebarExpanded && <button className="navigation-scrim" aria-label="Close navigation" onClick={() => setSidebarExpanded(false)} />}
            <aside id="ullavox-sidebar" ref={sidebarRef} className={`sidebar${sidebarExpanded ? ' expanded' : ' collapsed'}`} aria-label="Chat navigation" role={compact && sidebarExpanded ? 'dialog' : undefined} aria-modal={compact && sidebarExpanded ? true : undefined} inert={!sidebarExpanded}>
                {sidebarExpanded && <>
                    <div className="sidebar-brand">ullavox<span>.</span></div>
                    <button className="new-chat-row" aria-label="New Chat" onClick={() => { handleNewChat(); if (window.innerWidth < 900) setSidebarExpanded(false); }}><Icon name="plus" />New chat</button>
                    <nav className="project-navigation" aria-label="Projects">
                        <button className={!selectedProject ? 'selected' : ''} aria-current={!selectedProject ? 'page' : undefined} onClick={() => handleSelectProject('')}><Icon name="chat" />Standalone chats</button>
                        <div className="navigation-heading"><span>Projects</span><button className="icon-button" aria-label="Create project" onClick={() => setDraftProject(null)}><Icon name="plus" size={16} /></button></div>
                        {projects.map(project => <button key={project.id} className={selectedProject === project.id ? 'selected' : ''} aria-current={selectedProject === project.id ? 'page' : undefined} onClick={() => handleSelectProject(project.id)}><Icon name="folder" /><span>{project.name}</span></button>)}
                        {!projects.length && <p className="navigation-empty">A home for related chats and ideas.</p>}
                        {selectedProject && <button className="edit-project" onClick={() => setDraftProject(projects.find(p => p.id === selectedProject))}>Edit project</button>}
                    </nav>
                    <div className="navigation-heading recent-heading">Recent chats</div>
                    <ConversationSidebar
                        conversations={conversations.filter(c => (c.projectId || '') === selectedProject)}
                        activeId={activeConversationId}
                        onSelect={handleSelectConversation}
                        onRename={handleRename}
                        onDelete={handleDelete}
                    />
                    <div className="sidebar-footer">
                        <button onClick={() => { preferencesInteracted.current = true; setOnboarding(false); setShowPreferences(true); }}><Icon name="settings" />Preferences</button>
                        <details><summary aria-label="Navigation options">•••</summary><button onClick={() => refreshProjects().catch(e => setAppError(apiError(e)))}>Refresh projects</button></details>
                    </div>
                </>}
            </aside>
            <div className="main-chat-section">
                <ChatBox
                    key={activeConversationId}
                    conversationId={activeConversationId}
                    title={activeConversation?.title || 'New chat'}
                    sidebarExpanded={sidebarExpanded}
                    onNewChat={handleNewChat}
                    onboarding={onboarding}
                    onOpenPreferences={() => { preferencesInteracted.current = true; setOnboarding(false); setShowPreferences(true); }}
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
