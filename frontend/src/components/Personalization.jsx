import React, { useEffect, useRef, useState } from 'react';
import { getPreferences, savePreferences, clearPreferences, saveProject, deleteProject, listMemories, saveMemory, deleteMemory, apiError } from '../services/api';
import './Personalization.css';

export function EditorPanel({ title, onClose, children }) {
    const ref = useRef(null);
    useEffect(() => {
        const previous = document.activeElement;
        const panel = ref.current;
        if (typeof panel?.showModal === 'function') panel.showModal();
        else if (panel) panel.open = true;
        panel?.focus();
        return () => { if (typeof panel?.close === 'function') panel.close(); if (previous?.isConnected) previous.focus(); };
    }, []);
    return <dialog ref={ref} tabIndex={-1} className="personalization-panel" aria-label={title} onCancel={e => { e.preventDefault(); onClose(); }} onKeyDown={e => { if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); onClose(); } }}>
        <header><h2>{title}</h2><button type="button" onClick={onClose} aria-label={`Close ${title}`}>Close</button></header>{children}
    </dialog>;
}

const profileFields = [['responseLength', 'Response length'], ['explanationStyle', 'Explanation style'], ['familiarity', 'Assumed familiarity'], ['background', 'Background (optional)', 'User-provided background'], ['goals', 'Goals', 'User goals'], ['instructions', 'How should Ullavox help you?', 'Working preferences']];
export function PreferencesPanel({ onClose, onboarding = false }) {
    const [profile, setProfile] = useState(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const [saved, setSaved] = useState(false);
    const [dirty, setDirty] = useState(false);
    useEffect(() => { let current = true; getPreferences().then(p => { if (current) setProfile(p); }).catch(e => { if (current) setError(apiError(e)); }); return () => { current = false; }; }, []);
    const apply = async (operation) => {
        setBusy(true); setError('');
        try { const p = await operation(); setProfile(p); setSaved(true); setDirty(false); if (onboarding) onClose(); }
        catch (e) { setError(apiError(e)); } finally { setBusy(false); }
    };
    const skipOnboarding = async () => {
        setBusy(true); setError('');
        try {
            // Skip discards this draft, never preferences saved by another panel or tab.
            const current = await getPreferences();
            if (current.onboardingStatus === 'not_started') {
                await savePreferences({ ...current, onboardingStatus: 'skipped' });
            }
            onClose();
        } catch (e) {
            setError(apiError(e));
        } finally { setBusy(false); }
    };
    const preview = profile && profileFields.filter(([key]) => profile[key]).map(([key, label, renderedLabel]) => `${renderedLabel || label}: ${profile[key]}`).join('\n');
    return <EditorPanel title={onboarding ? 'Make Ullavox work for you' : 'Preferences'} onClose={onClose}>
        <p>Optional preferences are stored on this device. Included preferences and project context are sent to your selected model; a remote model receives that information. Saving uses no model call.</p>
        <p>Changes apply when the next response starts. Running work keeps the version it started with.</p>
        {error && <><p role="alert">{error}</p><button type="button" disabled={busy} onClick={async () => { setBusy(true); try { setProfile(await getPreferences()); setError(''); setSaved(false); setDirty(false); } catch (e) { setError(apiError(e)); } finally { setBusy(false); } }}>Reload saved preferences</button></>}
        {!profile ? <p role="status">{error ? 'Preferences could not be loaded. Close and reopen to retry.' : 'Loading preferences…'}</p> : <form onSubmit={e => { e.preventDefault(); apply(() => savePreferences({ ...profile, onboardingStatus: 'completed' })); }}>
            <fieldset disabled={busy}>
                {profileFields.map(([key, label]) => <label key={key}>{label}<textarea rows={key === 'instructions' ? 3 : 2} maxLength={key === 'instructions' ? 2000 : ['background', 'goals'].includes(key) ? 1000 : 120} value={profile[key] || ''} onChange={e => { setSaved(false); setDirty(true); setProfile({ ...profile, [key]: e.target.value }); }} /></label>)}
                <details><summary>{dirty ? 'Instruction preview (current edits)' : 'Saved instruction preview'}</summary><pre>{(!dirty ? profile.renderedInstructions : null) || preview || 'No preferences will be included.'}</pre><p>This preview describes current settings, not the exact context used by an earlier response.</p></details>
                <div className="panel-actions"><button type="submit">Save preferences</button>
                    {onboarding && <button type="button" onClick={skipOnboarding}>Skip onboarding</button>}
                    <button type="button" onClick={() => { if (window.confirm('Clear your saved preferences? Earlier replies and backups may retain information.')) apply(() => clearPreferences(profile.version)); }}>Clear preferences</button>
                </div>
            </fieldset>
            {saved && <p role="status">Preferences saved.</p>}
        </form>}
    </EditorPanel>;
}

export function ProjectEditor({ project, onClose, onSaved, chatCount = 0 }) {
    const [draft, setDraft] = useState(project || { name: '', description: '', instructions: '' });
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const perform = async action => {
        setBusy(true); setError(''); let committed = false;
        try {
            const result = await action(); committed = true;
            if (result?.id) setDraft(result);
            await onSaved(); onClose();
        } catch (e) { setError((committed ? 'Change saved. Could not refresh projects: ' : '') + apiError(e)); }
        finally { setBusy(false); }
    };
    return <EditorPanel title={project ? 'Edit project' : 'Create project'} onClose={onClose}>
        {error && <p role="alert">{error}</p>}
        <form onSubmit={e => { e.preventDefault(); perform(() => saveProject(draft)); }}><fieldset disabled={busy}>
            <label>Project name<input required maxLength={160} value={draft.name} onChange={e => setDraft({ ...draft, name: e.target.value })} /></label>
            <label>Description<textarea maxLength={2000} value={draft.description || ''} onChange={e => setDraft({ ...draft, description: e.target.value })} /></label>
            <label>Project instructions<textarea rows={5} maxLength={4000} value={draft.instructions || ''} onChange={e => setDraft({ ...draft, instructions: e.target.value })} /></label>
            <p>Instructions apply to chats assigned to this project when project context is enabled.</p>
            <div className="panel-actions"><button type="submit">Save project</button>{project && <button type="button" onClick={() => { if (window.confirm(`Delete project and all its saved notes? ${chatCount} chats will become standalone; transcripts are preserved.`)) perform(() => deleteProject(project.id, project.version)); }}>Delete project</button>}</div>
        </fieldset></form>
    </EditorPanel>;
}

export function MemoryPanel({ projectId, conversationId, sourceMessage, selectedIds = [], selectionEnabled = true, onSelectionChange, onClose }) {
    const [notes, setNotes] = useState([]);
    const [draft, setDraft] = useState(sourceMessage ? { title: '', content: sourceMessage.text, kind: 'note', pinned: false, sourceConversationId: conversationId, sourceMessageId: sourceMessage.id } : null);
    const [query, setQuery] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [loaded, setLoaded] = useState(false);
    const requestId = useRef(0);
    const refresh = async () => {
        const id = ++requestId.current;
        const data = await listMemories(projectId);
        if (id === requestId.current) { setNotes(data); setLoaded(true); }
    };
    useEffect(() => {
        let current = true; const id = ++requestId.current;
        listMemories(projectId).then(data => { if (current && id === requestId.current) { setNotes(data); setLoaded(true); } }).catch(e => { if (current && id === requestId.current) setError(apiError(e)); });
        return () => { current = false; };
    }, [projectId]);
    const perform = async action => {
        setBusy(true); setError(''); let committed = false;
        try { await action(); committed = true; setDraft(null); await refresh(); }
        catch (e) { setError((committed ? 'Change saved. Could not refresh notes: ' : '') + apiError(e)); }
        finally { setBusy(false); }
    };
    const filteredNotes = notes.filter(n => `${n.title} ${n.content}`.toLowerCase().includes(query.toLowerCase()));
    return <EditorPanel title="Project notes" onClose={onClose}>
        <p>Save only what you want carried across chats in this project. Saved notes remain when their source chat is deleted or moved. Manage them here.</p>
        <p>Select up to five notes for the next message. Selection clears when Ullavox accepts the request. Pinned notes and relevant matches may also be included within the context budget.</p>
        {error && <p role="alert">{error}</p>}
        <div className="panel-actions"><button type="button" disabled={busy} onClick={() => setDraft({ title: '', content: '', kind: 'note', pinned: false })}>Add note</button>
        <button type="button" disabled={busy} onClick={async () => { setBusy(true); try { await refresh(); setError(''); } catch (e) { setError(apiError(e)); } finally { setBusy(false); } }}>Refresh notes</button></div>
        {draft && <form onSubmit={e => { e.preventDefault(); perform(() => saveMemory(projectId, draft)); }}><fieldset disabled={busy}>
            <label>Note title<input required maxLength={160} value={draft.title} onChange={e => setDraft({ ...draft, title: e.target.value })} /></label>
            <label>Kind<select value={draft.kind} onChange={e => setDraft({ ...draft, kind: e.target.value })}><option value="note">Note</option><option value="decision">Decision</option><option value="constraint">Constraint</option></select></label>
            <label>Content<textarea required rows={6} maxLength={2000} value={draft.content} onChange={e => setDraft({ ...draft, content: e.target.value })} /></label>
            <p>{draft.content.length}/2,000 characters{draft.content.length > 2000 ? ' — shorten this message before saving it as a note.' : ''}</p>
            <label><input type="checkbox" checked={draft.pinned} onChange={e => setDraft({ ...draft, pinned: e.target.checked })} /> Pinned</label>
            <div className="panel-actions"><button type="submit" disabled={draft.content.length > 2000}>Save note</button><button type="button" onClick={() => setDraft(null)}>Cancel editing</button></div>
        </fieldset></form>}
        <label>Search saved notes<input type="search" value={query} onChange={e => setQuery(e.target.value)} /></label>
        {!loaded && !error && <p role="status">Loading notes…</p>}
        {loaded && notes.length === 0 && <p>No saved notes yet.</p>}
        {loaded && notes.length > 0 && filteredNotes.length === 0 && <p role="status">No matching notes. Try a different search.</p>}
        {filteredNotes.map(note => <article className="memory-note" key={note.id}>
            <h3>{note.title}</h3><p className="preserve-lines">{note.content}</p><small>{note.kind} · v{note.version}{note.pinned ? ' · Pinned' : ''}{note.origin === 'message' ? (note.sourceMessageId ? ' · From a saved message' : ' · Source unavailable') : ' · Manual note'}</small>
            <div className="panel-actions"><label><input type="checkbox" checked={selectedIds.includes(note.id)} disabled={!selectionEnabled || busy || (!selectedIds.includes(note.id) && selectedIds.length >= 5)} onChange={e => onSelectionChange(e.target.checked ? [...selectedIds, note.id] : selectedIds.filter(id => id !== note.id))} /> Use for next message</label>
                <button type="button" disabled={busy} onClick={() => setDraft(note)}>Edit</button>
                <button type="button" disabled={busy} onClick={() => perform(() => saveMemory(projectId, { ...note, pinned: !note.pinned }))}>{note.pinned ? 'Unpin' : 'Pin'}</button>
                <button type="button" disabled={busy} onClick={() => { if (window.confirm('Delete this saved note?')) perform(async () => { await deleteMemory(projectId, note.id, note.version); onSelectionChange(selectedIds.filter(id => id !== note.id)); }); }}>Delete note</button>
            </div>
        </article>)}
    </EditorPanel>;
}

export function ContextUsed({ context }) {
    if (!context || (!context.included && !context.omitted && context.estimatedTokens == null)) return null;
    const label = item => profileFields.find(([key]) => key === item.name)?.[1] || item.name || ({ preference: 'Preferences', project: 'Project instructions', memory: 'Saved note' }[item.type]) || item.type;
    return <details className="context-used"><summary>Context used</summary>
        <p>Metadata for this execution. Current settings may have changed.</p>
        {context.included?.length ? <ul>{context.included.map((item, index) => <li key={index}>{label(item)}{item.version != null ? ` (v${item.version})` : ''}</li>)}</ul> : <p>No personalization included.</p>}
        {context.omitted?.length > 0 && <ul>{context.omitted.map((item, index) => <li key={index}>{label(item)}: {({ disabled: 'turned off for this chat', budget: 'context budget', not_relevant: 'not relevant to this request' }[item.reason]) || item.reason}</li>)}</ul>}
        {context.estimatedTokens != null && <small>Estimated personalization tokens: {context.estimatedTokens}</small>}
    </details>;
}
