import React, { useState, useEffect, useRef, useCallback } from 'react';
import Message from './Message';
import AgentRunPanel from './AgentRunPanel';
import Icon from './Icon';
import { EditorPanel, MemoryPanel, ContextUsed } from './Personalization';
import {
    sendMessage,
    streamMessage,
    getModels,
    getConversationMessages,
    createAgentRun,
    getAgentRun,
    listAgentRuns,
    cancelAgentRun,
    getConversationSettings, saveConversationSettings, getConversationExecution, apiError,
} from '../services/api';
import './ChatBox.css';

const ChatBox = ({
    conversationId,
    title = 'New chat',
    sidebarExpanded = true,
    onNewChat,
    onboarding = false,
    onOpenPreferences,
    initialModel,
    initialProjectId = '',
    projects = [],
    settingsRevision = 0,
    initialGenerationConfig,
    onConversationChanged,
    selectedServer,
    setSelectedServer,
    servers,
}) => {
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [models, setModels] = useState([]);
    const [modelsLoading, setModelsLoading] = useState(false);
    const [modelRevision, setModelRevision] = useState(0);
    const [selectedModel, setSelectedModel] = useState('');
    const chosenModels = useRef(new Map());
    const [chatLocked, setChatLocked] = useState(false);
    const [isStreaming, setIsStreaming] = useState(false);
    const [streamEnabled, setStreamEnabled] = useState(true);
    const [systemPrompt, setSystemPrompt] = useState('');
    const [settings, setSettings] = useState(null);
    const [settingsError, setSettingsError] = useState('');
    const [settingsBusy, setSettingsBusy] = useState(false);
    const [projectId, setProjectId] = useState(initialProjectId || '');
    const [usePreferences, setUsePreferences] = useState(true);
    const [useProjectContext, setUseProjectContext] = useState(true);
    const [showContext, setShowContext] = useState(false);
    const [settingsSaved, setSettingsSaved] = useState(false);
    const [showAttachments, setShowAttachments] = useState(false);
    const [showMemory, setShowMemory] = useState(false);
    const [sourceMessage, setSourceMessage] = useState(null);
    const [selectedMemoryIds, setSelectedMemoryIds] = useState([]);
    const [contextUsed, setContextUsed] = useState(null);
    const [executionPending, setExecutionPending] = useState(true);
    const [executionError, setExecutionError] = useState('');
    const [executionCheck, setExecutionCheck] = useState(0);
    const mounted = useRef(true);
    useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
    const [showSystemPrompt, setShowSystemPrompt] = useState(false);
    const [error, setError] = useState('');
    const [attachedImages, setAttachedImages] = useState([]);
    const [showGenerationSettings, setShowGenerationSettings] = useState(false);
    const [interactionMode, setInteractionMode] = useState('ask');
    const [activeAgentRun, setActiveAgentRun] = useState(null);
    const [agentRefreshError, setAgentRefreshError] = useState('');
    const [agentPollAttempt, setAgentPollAttempt] = useState(0);
    const [attachedFiles, setAttachedFiles] = useState([]);
    const [retainedFiles, setRetainedFiles] = useState([]);
    const [clearAttachments, setClearAttachments] = useState(false);
    const [allowWebSearch, setAllowWebSearch] = useState(true);
    const [filesLoading, setFilesLoading] = useState(false);
    const textFileInputRef = useRef(null);
    const [temperature, setTemperature] = useState(
        () => initialGenerationConfig?.temperature ?? ''
    );
    const [numCtx, setNumCtx] = useState(() => initialGenerationConfig?.numCtx ?? '');
    const [stopSequencesText, setStopSequencesText] = useState(
        () => (initialGenerationConfig?.stopSequences || []).join(', ')
    );
    const messagesEndRef = useRef(null);
    const textareaRef = useRef(null);
    const fileInputRef = useRef(null);
    const completedAgentRuns = useRef(new Set());

    const toMessages = history => history.filter(m => ['user', 'assistant'].includes(m.role)).map(m => ({ id: m.id, text: m.content, sender: m.role, images: (m.images || []).map(b64 => `data:image/png;base64,${b64}`) }));
    useEffect(() => {
        let current = true;
        const previous = settings;
        setSettings(null);
        getConversationSettings(conversationId).then(value => {
            if (!current) return;
            setSettings(value);
            // A project deletion refreshes the server version and assignment. Keep
            // unrelated edits the user has not saved yet.
            if (!previous || projectId === (previous.projectId || '') || !projects.some(p => p.id === projectId)) setProjectId(value.projectId || '');
            if (!previous || usePreferences === previous.usePreferences) setUsePreferences(value.usePreferences);
            if (!previous || useProjectContext === previous.useProjectContext) setUseProjectContext(value.useProjectContext);
            if (!previous || systemPrompt === (previous.systemPrompt || '')) setSystemPrompt(value.systemPrompt || '');
            setSelectedMemoryIds([]); setSettingsError(''); setContextUsed(value.contextUsed || null);
        }).catch(e => {
            if (!current) return;
            if (e.response?.status === 404) setSettings({ version: 0 });
            else setSettingsError(apiError(e));
        });
        return () => { current = false; };
    }, [conversationId, settingsRevision]);
    const persistSettings = async () => {
        if (!settings) throw new Error('Conversation settings have not loaded. Reopen this chat and retry.');
        setSettingsBusy(true); setSettingsError(''); setSettingsSaved(false);
        try {
            const saved = await saveConversationSettings(conversationId, { version: settings.version, projectId: projectId || null, usePreferences, useProjectContext, systemPrompt });
            if (mounted.current) { setSettings(saved); setSettingsSaved(true); }
            onConversationChanged?.();
            return saved;
        } catch (e) { if (mounted.current) setSettingsError(apiError(e)); throw e; }
        finally { if (mounted.current) setSettingsBusy(false); }
    };
    useEffect(() => {
        let current = true; let timer;
        const check = async () => {
            try {
                const status = await getConversationExecution(conversationId);
                if (!current) return;
                const pending = ['queued', 'running', 'cancelling', 'cancellation_requested'].includes(status.state?.toLowerCase());
                setExecutionPending(pending); setExecutionError('');
                if (pending) timer = setTimeout(check, 1200);
                else if (executionCheck > 0) {
                    const history = await getConversationMessages(conversationId);
                    if (current && history.length) setMessages(toMessages(history));
                    const savedSettings = await getConversationSettings(conversationId);
                    if (current && savedSettings.contextUsed) setContextUsed(savedSettings.contextUsed);
                }
            } catch (e) {
                if (!current) return;
                if (e.response?.status === 404) { setExecutionPending(false); return; }
                setExecutionPending(true); setExecutionError('Checking response status. Sending stays paused until Ullavox confirms the worker has finished.');
                timer = setTimeout(check, 2000);
            }
        };
        check();
        return () => { current = false; clearTimeout(timer); };
    }, [conversationId, executionCheck]);
    const blocked = chatLocked || executionPending || settingsBusy;

    const MAX_IMAGES = 4;
    const MAX_IMAGE_BYTES = 7_000_000; // matches the backend's ~7MB-raw cap

    const selectedModelInfo = models.find(m => m.name === selectedModel);
    const supportsVision = Boolean(selectedModelInfo?.capabilities?.includes('vision'));

    // The conversation list (and this conversation's saved generation config) loads
    // asynchronously in the parent, so it's often still null on first mount. Restore
    // from it the first time real data arrives, then stop — initialGenerationConfig
    // is a fresh object on every parent re-render (e.g. after each send refreshes the
    // sidebar), so re-syncing on every change would keep clobbering the user's own
    // in-progress edits to these fields.
    const generationConfigRestored = useRef(false);
    useEffect(() => {
        if (!initialGenerationConfig || generationConfigRestored.current) return;
        generationConfigRestored.current = true;
        setTemperature(initialGenerationConfig.temperature ?? '');
        setNumCtx(initialGenerationConfig.numCtx ?? '');
        setStopSequencesText((initialGenerationConfig.stopSequences || []).join(', '));
    }, [initialGenerationConfig]);

    // Load persisted history for this conversation on mount (component is
    // remounted with a new key whenever the active conversation changes).
    useEffect(() => {
        if (!conversationId) return;
        let cancelled = false;
        let retryTimer;
        setChatLocked(true);
        const loadHistory = async () => {
            try {
                const history = await getConversationMessages(conversationId);
                if (!cancelled && history.length > 0) {
                    setMessages(toMessages(history));
                }
            } catch (err) {
                if (err.response?.status !== 404) console.error('Failed to load conversation history:', err.message);
            }
        };
        const historyLoaded = loadHistory();
        const restoreRuns = async (attempt = 0) => {
            try {
                const runs = await listAgentRuns(conversationId);
                // Apply the initial history before allowing terminal refresh to replace it.
                await historyLoaded;
                if (cancelled) return;
                setAgentRefreshError('');
                if (runs.length === 0) {
                    setChatLocked(false);
                    return;
                }
                const latest = runs[0];
                setActiveAgentRun(latest);
                if (typeof latest.allowWebSearch === 'boolean') setAllowWebSearch(latest.allowWebSearch);
                setRetainedFiles(latest.attachedFileNames || []);
                setInteractionMode('agent');
            } catch (err) {
                if (cancelled) return;
                setAgentRefreshError('Could not restore task status. Retrying; sending is paused until status is known.');
                retryTimer = setTimeout(() => restoreRuns(attempt + 1), Math.min(800 + attempt * 500, 5000));
            }
        };
        restoreRuns();
        return () => { cancelled = true; clearTimeout(retryTimer); };
    }, [conversationId]);

    useEffect(() => {
        if (!activeAgentRun) return undefined;
        let cancelled = false;
        let timer;
        const terminal = ['COMPLETED', 'FAILED', 'CANCELLED'].includes(activeAgentRun.status);
        if (terminal) {
            if (!completedAgentRuns.current.has(activeAgentRun.id)) {
                getConversationMessages(conversationId).then(history => {
                    if (cancelled) return;
                    setMessages(toMessages(history));
                    completedAgentRuns.current.add(activeAgentRun.id);
                    setAgentRefreshError('');
                    setChatLocked(false);
                    if (onConversationChanged) onConversationChanged();
                }).catch(() => {
                    if (cancelled) return;
                    setAgentRefreshError('Could not refresh conversation history. Retrying before enabling follow-ups.');
                    timer = setTimeout(() => setAgentPollAttempt(attempt => attempt + 1), Math.min(800 + agentPollAttempt * 500, 5000));
                });
            } else setChatLocked(false);
            return () => { cancelled = true; clearTimeout(timer); };
        }

        timer = setTimeout(async () => {
            try {
                const run = await getAgentRun(activeAgentRun.id);
                if (!cancelled) {
                    setAgentRefreshError('');
                    setActiveAgentRun(run);
                }
            } catch (err) {
                if (!cancelled) {
                    setAgentRefreshError('Connection interrupted. Retrying task status; the agent may still be working.');
                    setAgentPollAttempt(attempt => attempt + 1);
                }
            }
        }, Math.min(800 + agentPollAttempt * 500, 5000));
        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [activeAgentRun, onConversationChanged, conversationId, agentPollAttempt]);

    // Fetch models when server changes; prefer the conversation's own model on resume.
    useEffect(() => {
        setModels([]);
        setSelectedModel('');
        if (!selectedServer) { setModelsLoading(false); return; }
        setModelsLoading(true);
        let current = true;
        const fetchModels = async () => {
            try {
                const result = await getModels(selectedServer);
                if (!current) return;
                setModels(result);
                const preferred =
                    result.find(m => m.name === chosenModels.current.get(selectedServer))
                    || result.find(m => m.name === initialModel)
                    || result.find(m => m.name === 'granite4:3b')
                    || result.find(m => m.name === 'phi4-mini:latest')
                    || result[0];
                if (preferred) {
                    setSelectedModel(preferred.name);
                }
            } catch (err) {
                if (!current) return;
                console.error('Failed to fetch models:', err.message);
                setError('Failed to fetch models. Please try again.');
            } finally {
                if (current) setModelsLoading(false);
            }
        };
        fetchModels();
        return () => { current = false; };
    }, [selectedServer, initialModel, modelRevision]);

    // Auto-scroll to latest message
    useEffect(() => {
        if (messagesEndRef.current) {
            messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
        }
    }, [messages, chatLocked]);

    // Clear error when user selects server/model
    useEffect(() => { setError(''); }, [selectedServer, selectedModel]);

    // Auto-dismiss error after 5 seconds
    useEffect(() => {
        if (error) {
            const timer = setTimeout(() => setError(''), 5000);
            return () => clearTimeout(timer);
        }
    }, [error]);

    // Auto-resize textarea
    const resizeTextarea = useCallback(() => {
        const textarea = textareaRef.current;
        if (textarea) {
            textarea.style.height = 'auto';
            textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px';
        }
    }, []);

    // Drop staged images if the selected model can't see them (e.g. user switched
    // to a text-only model after attaching something).
    useEffect(() => {
        if (!supportsVision && attachedImages.length > 0) {
            setAttachedImages([]);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [supportsVision]);

    const handleTextFileSelect = async (event) => {
        const selected = Array.from(event.target.files || []);
        event.target.value = '';
        if (attachedFiles.length + selected.length > 4) { setError('Attach up to 4 text files.'); return; }
        setFilesLoading(true);
        try {
            const loaded = await Promise.all(selected.map(async file => {
                if (!/\.(txt|md|csv|tsv|json|log|java|py|js|jsx|ts|tsx|html|css|xml|yaml|yml|sql)$/i.test(file.name)) throw new Error('Select text, CSV, JSON, or code files. PDF and binary files are not supported.');
                if (file.size > 100_000) throw new Error(`${file.name} is too large. Select a smaller file or excerpt.`);
                const content = await new Promise((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onload = () => resolve(reader.result);
                    reader.onerror = () => reject(new Error(`Could not read ${file.name}.`));
                    reader.readAsText(file);
                });
                if (content.includes('\0')) throw new Error(`${file.name} appears to be a binary file.`);
                return { name: file.name, content };
            }));
            if (attachedFiles.length === 0 && loaded.length > 0) setAllowWebSearch(false);
            setAttachedFiles(previous => {
                const next = [...previous, ...loaded];
                if (next.length > 4 || next.reduce((sum, file) => sum + file.content.length, 0) > 24_000) {
                    setError('Attach up to 4 files with at most 24,000 characters total. Use excerpts for larger files.');
                    return previous;
                }
                return next;
            });
        } catch (err) { setError(err.message); }
        finally { setFilesLoading(false); }
    };

    const handleFileSelect = (e) => {
        const files = Array.from(e.target.files || []);
        e.target.value = ''; // allow re-selecting the same file later
        if (files.length === 0) return;

        const room = MAX_IMAGES - attachedImages.length;
        if (room <= 0) {
            setError(`You can attach up to ${MAX_IMAGES} images.`);
            return;
        }

        files.slice(0, room).forEach((file) => {
            if (!file.type.startsWith('image/')) {
                setError('Only image files can be attached.');
                return;
            }
            if (file.size > MAX_IMAGE_BYTES) {
                setError(`${file.name} is too large (max ${Math.floor(MAX_IMAGE_BYTES / 1_000_000)}MB).`);
                return;
            }
            const reader = new FileReader();
            reader.onload = () => {
                const dataUrl = reader.result;
                const base64 = dataUrl.slice(dataUrl.indexOf(',') + 1);
                setAttachedImages((prev) =>
                    prev.length >= MAX_IMAGES ? prev : [...prev, { dataUrl, base64 }]
                );
            };
            reader.readAsDataURL(file);
        });
    };

    const removeAttachedImage = (index) => {
        setAttachedImages((prev) => prev.filter((_, i) => i !== index));
    };

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
        if (blocked || filesLoading || !settings) return;

        setError('');
        setChatLocked(true);
        generationConfigRestored.current = true;
        setContextUsed(null);
        try { await persistSettings(); } catch (e) { setError(apiError(e)); setChatLocked(false); return; }
        const imagesBase64 = attachedImages.map((img) => img.base64);
        const generationConfig = {
            temperature: temperature === '' ? null : Number(temperature),
            numCtx: numCtx === '' ? null : Number(numCtx),
            stopSequences: stopSequencesText
                .split(',')
                .map(s => s.trim())
                .filter(Boolean),
        };
        const userMessage = {
            text: input + (interactionMode === 'agent' ? attachedFiles.map(file => `\n\nAttached file: ${file.name}`).join('') : ''),
            sender: 'user',
            images: attachedImages.map((img) => img.dataUrl),
        };
        setMessages((prev) => [...prev, userMessage]);
        const currentInput = input.trim();
        setInput('');
        setAttachedImages([]);

        // Reset textarea height after clearing input
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto';
        }

        if (interactionMode === 'agent') {
            try {
                const run = await createAgentRun(
                    currentInput,
                    conversationId,
                    selectedServer,
                    selectedModel,
                    systemPrompt,
                    imagesBase64,
                    generationConfig,
                    attachedFiles,
                    allowWebSearch,
                    clearAttachments,
                    selectedMemoryIds
                );
                setSelectedMemoryIds([]);
                setAttachedFiles([]);
                setClearAttachments(false);
                setRetainedFiles(run.attachedFileNames || []);
                if (typeof run.allowWebSearch === 'boolean') setAllowWebSearch(run.allowWebSearch);
                setAgentRefreshError('');
                setAgentPollAttempt(0);
                setActiveAgentRun(run);
                if (onConversationChanged) onConversationChanged();
            } catch (err) {
                console.error('Agent run failed to start:', err.message);
                setError(err.response?.data?.error || 'Failed to start agent task. Please try again.');
                setInput(currentInput);
                setMessages(prev => prev.slice(0, -1));
                setChatLocked(false);
            }
            return;
        }

        if (streamEnabled) {
            try {
                const response = await streamMessage(
                    currentInput,
                    selectedServer.trim(),
                    selectedModel.trim(),
                    conversationId.trim(),
                    systemPrompt,
                    imagesBase64,
                    generationConfig,
                    selectedMemoryIds
                );
                setSelectedMemoryIds([]);
                // The backend has now created/updated this conversation, so surface it
                // in the sidebar immediately — before streaming finishes — in case the
                // user navigates away mid-response.
                if (onConversationChanged) onConversationChanged();
                // Add empty bot message that will be filled by streaming
                setMessages((prev) => [...prev, { text: '', sender: 'assistant', toolCalls: [] }]);
                setIsStreaming(true);

                const reader = response.body.getReader();
                const decoder = new TextDecoder();
                let buffer = '';
                let currentEventName = '';

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;

                    buffer += decoder.decode(value, { stream: true });
                    const lines = buffer.split('\n');
                    buffer = lines.pop() || '';

                    for (const line of lines) {
                        if (line.startsWith('event:')) {
                            currentEventName = line.slice(6).trim();
                        } else if (line.startsWith('data:')) {
                            // Use raw slice (no trim) to preserve whitespace tokens from Ollama.
                            // Only trim when comparing against the sentinel value.
                            const rawData = line.slice(5);
                            if (rawData.trim() === '[DONE]') {
                                currentEventName = '';
                                break;
                            }
                            if (currentEventName === 'context_used') {
                                try { setContextUsed(JSON.parse(rawData)); } catch { /* Keep the response usable if metadata is malformed. */ }
                            } else if (currentEventName === 'error') {
                                setError(rawData.trim() || 'The response stopped. Checking worker status.');
                            } else if (currentEventName === 'tool_call') {
                                try {
                                    const parsed = JSON.parse(rawData);
                                    setMessages((prev) => {
                                        const updated = [...prev];
                                        const last = { ...updated[updated.length - 1] };
                                        last.toolCalls = [...(last.toolCalls || []), {
                                            index: parsed.index,
                                            name: parsed.name,
                                            args: parsed.arguments,
                                            result: null,
                                            status: 'searching',
                                        }];
                                        updated[updated.length - 1] = last;
                                        return updated;
                                    });
                                } catch (e) {
                                    console.warn('Malformed tool_call SSE payload:', e.message);
                                }
                            } else if (currentEventName === 'tool_result') {
                                try {
                                    const parsed = JSON.parse(rawData);
                                    setMessages((prev) => {
                                        const updated = [...prev];
                                        const last = { ...updated[updated.length - 1] };
                                        // Match by numeric index so same-named tools called
                                        // multiple times are handled correctly.
                                        last.toolCalls = (last.toolCalls || []).map(tc =>
                                            tc.index === parsed.index
                                                ? { ...tc, result: parsed.result, status: 'done' }
                                                : tc
                                        );
                                        updated[updated.length - 1] = last;
                                        return updated;
                                    });
                                } catch (e) {
                                    console.warn('Malformed tool_result SSE payload:', e.message);
                                }
                            } else {
                                // Plain text token — append raw (untrimmed) to preserve spaces
                                setMessages((prev) => {
                                    const updated = [...prev];
                                    const last = updated[updated.length - 1];
                                    updated[updated.length - 1] = {
                                        ...last,
                                        text: last.text + rawData,
                                    };
                                    return updated;
                                });
                            }
                            currentEventName = '';
                        } else if (line === '') {
                            // Blank line resets event name (SSE spec)
                            currentEventName = '';
                        }
                    }
                }
            } catch (err) {
                console.error('Stream failed:', err.message);
                setError(err.response?.data?.error || err.response?.data?.message || 'Failed to stream message. Please try again.');
            } finally {
                setIsStreaming(false);
                setExecutionPending(true);
                setExecutionCheck(n => n + 1);
                setChatLocked(false);
                if (onConversationChanged) onConversationChanged();
            }
        } else {
            try {
                const response = await sendMessage(
                    currentInput,
                    selectedServer.trim(),
                    selectedModel.trim(),
                    conversationId.trim(),
                    false,
                    systemPrompt,
                    imagesBase64,
                    generationConfig,
                    selectedMemoryIds
                );
                setSelectedMemoryIds([]);
                setContextUsed(response.contextUsed || null);
                const botMessage = { id: response.id, text: response.content, sender: response.role };
                setMessages((prev) => [...prev, botMessage]);
            } catch (err) {
                console.error('Send failed:', err.message);
                setError(err.response?.data?.error || err.response?.data?.message || 'Failed to send message. Please try again.');
            } finally {
                setExecutionPending(true); setExecutionCheck(n => n + 1);
                setChatLocked(false);
                if (onConversationChanged) onConversationChanged();
            }
        }
    };

    // Enter to send, Shift+Enter for newline
    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSendMessage();
        }
    };

    const handleSystemPromptChange = (e) => {
        const val = e.target.value;
        setSystemPrompt(val);

    };

    const handleInputChange = (e) => {
        setInput(e.target.value);
        if (error) setError('');
        resizeTextarea();
    };

    const handleCancelAgentRun = async (runId) => {
        try {
            setActiveAgentRun(await cancelAgentRun(runId));
        } catch (err) {
            console.error('Could not cancel agent run:', err.message);
            setError('Could not cancel agent task.');
        }
    };

    const showPlaceholder = messages.length === 0;
    const connectionName = selectedServer === 'ollama' ? 'Ollama' : selectedServer;
    const settingsDirty = settings && ((projectId || '') !== (settings.projectId || '') || usePreferences !== (settings.usePreferences ?? true) || useProjectContext !== (settings.useProjectContext ?? true) || systemPrompt !== (settings.systemPrompt || ''));


    return (
        <div className="chat-window chat-style-window">
            {showMemory && projectId && <MemoryPanel key={`${projectId}-${sourceMessage?.id || 'manual'}`} projectId={projectId} conversationId={conversationId} sourceMessage={sourceMessage} selectionEnabled={useProjectContext} selectedIds={selectedMemoryIds} onSelectionChange={setSelectedMemoryIds} onClose={() => { setShowMemory(false); setSourceMessage(null); }} />}
            <header className="chat-header">
                <div className="chat-heading">
                    <div className="chat-breadcrumb">{projects.find(p => p.id === projectId)?.name || 'Your space'}</div>
                    <h1>{title}</h1>
                </div>
                <div className="chat-header-actions">
                    {servers.length > 1 && <select aria-label="Server" value={selectedServer} onChange={e => setSelectedServer(e.target.value)} disabled={blocked} className="inline-select"><option value="" disabled>Connection</option>{servers.map(server => <option key={server} value={server}>{server}</option>)}</select>}
                    {selectedServer && <div className="model-picker">
                        <span className="model-source" id="model-source">Models from {connectionName}</span>
                        {models.length > 0 && <select aria-label="Model" aria-describedby="model-source" value={selectedModel} onChange={e => { chosenModels.current.set(selectedServer, e.target.value); setSelectedModel(e.target.value); }} disabled={blocked} className="inline-select"><option value="" disabled>Select a model</option>{models.map(model => <option key={model.name} value={model.name}>{model.name}</option>)}</select>}
                    </div>}
                    {selectedServer && !selectedModel && !modelsLoading && <button type="button" className="connection-retry" onClick={() => setModelRevision(value => value + 1)} disabled={blocked}>Retry models</button>}
                    <button type="button" className="context-trigger" aria-haspopup="dialog" onClick={() => setShowContext(true)}><Icon name="context" />Context</button>
                    {!sidebarExpanded && onNewChat && <button type="button" className="icon-button" aria-label="New Chat" onClick={onNewChat}><Icon name="plus" /></button>}
                </div>
            </header>
            {showContext && <EditorPanel title="Context" onClose={() => setShowContext(false)}>
                <p className="panel-intro">Choose what carries into your next message.</p>
                <div className="context-controls">
                    <label>Chat project<select value={projectId} disabled={blocked || !settings} onChange={e => { setProjectId(e.target.value); setSelectedMemoryIds([]); }}><option value="">Standalone</option>{projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
                    <label><input type="checkbox" checked={usePreferences} disabled={blocked || !settings} onChange={e => setUsePreferences(e.target.checked)} /> Use my preferences</label>
                    <label><input type="checkbox" checked={useProjectContext} disabled={blocked || !settings || !projectId} onChange={e => { setUseProjectContext(e.target.checked); if (!e.target.checked) setSelectedMemoryIds([]); }} /> Use project context</label>
                    <button type="button" disabled={blocked || !settings} onClick={() => persistSettings().catch(() => {})}>{settingsBusy ? 'Saving…' : 'Save chat settings'}</button>
                    {projectId && <button type="button" disabled={blocked} onClick={() => { setSourceMessage(null); setShowContext(false); setShowMemory(true); }}>Project notes{selectedMemoryIds.length ? ` (${selectedMemoryIds.length} selected)` : ''}</button>}
                </div>

                <p className="context-save-hint">Changes are saved when you send. Save now to keep them without sending a message.</p>
                {settingsSaved && !settingsDirty && <p role="status">Chat settings saved.</p>}
                {settingsError && <p role="alert">{settingsError}</p>}
                <div className="context-section"><h3>Connection</h3>
                    {servers.length <= 1 && <label>Server<select aria-label="Server" value={selectedServer} onChange={e => setSelectedServer(e.target.value)} disabled={blocked}><option value="" disabled>Select a server</option>{servers.map(server => <option key={server} value={server}>{server}</option>)}</select></label>}
                    {selectedServer && <p>{selectedServer === 'ollama' ? 'The model list comes from the Ollama service configured for this Ullavox installation. It can include models that run locally or in the cloud.' : `The model list comes from the ${connectionName} connection configured for this Ullavox installation.`}</p>}
                    <p>Context is sent to the selected model. Remote models receive the information you include.</p>
                </div>
                <div className="context-section"><h3>Advanced</h3>
                    <button type="button" className="advanced-toggle" aria-label="Toggle system prompt" aria-expanded={showSystemPrompt} onClick={() => setShowSystemPrompt(value => !value)}>Conversation instructions <span>{showSystemPrompt ? '−' : '+'}</span></button>
                    {showSystemPrompt && (
                        <div className="system-prompt-section">
                            <textarea
                                aria-label="Conversation instructions"
                                maxLength={8000}
                                value={systemPrompt}
                                onChange={handleSystemPromptChange}
                                placeholder="Set system behavior... (e.g. You are a helpful coding assistant)"
                                rows={2}
                                className="system-prompt-textarea"
                                disabled={blocked}
                            />
                            <button type="button" disabled={blocked || !settings} onClick={async () => {
                                setSettingsBusy(true);
                                try { const saved = await saveConversationSettings(conversationId, { version: settings.version, systemPrompt: '' }); setSettings(saved); setSystemPrompt(''); } catch (e) { setSettingsError(apiError(e)); } finally { setSettingsBusy(false); }
                            }}>Clear conversation instructions</button>
                        </div>
                    )}

                    <button type="button" className="advanced-toggle" aria-label="Toggle generation settings" aria-expanded={showGenerationSettings} onClick={() => setShowGenerationSettings(value => !value)}>Generation settings <span>{showGenerationSettings ? '−' : '+'}</span></button>
                    {showGenerationSettings && (
                        <div className="generation-settings-section">
                            <label className="generation-settings-field">
                                <span>Temperature</span>
                                <input
                                    type="number"
                                    min="0"
                                    max="2"
                                    step="0.1"
                                    value={temperature}
                                    onChange={e => { generationConfigRestored.current = true; setTemperature(e.target.value); }}
                                    placeholder="server default"
                                    disabled={blocked}
                                />
                            </label>
                            <label className="generation-settings-field">
                                <span>Context size</span>
                                <input
                                    type="number"
                                    min="1"
                                    step="1"
                                    value={numCtx}
                                    onChange={e => { generationConfigRestored.current = true; setNumCtx(e.target.value); }}
                                    placeholder="server default"
                                    disabled={blocked}
                                />
                            </label>
                            <label className="generation-settings-field generation-settings-field-wide">
                                <span>Stop sequences</span>
                                <input
                                    type="text"
                                    value={stopSequencesText}
                                    onChange={e => { generationConfigRestored.current = true; setStopSequencesText(e.target.value); }}
                                    placeholder="comma-separated, e.g. END, ###"
                                    disabled={blocked}
                                />
                            </label>
                        </div>
                    )}
                        <label className="stream-toggle-inline" title={interactionMode === 'agent' ? 'Agent responses appear after evidence review' : 'Stream response'}>
                            <input
                                type="checkbox"
                                aria-label="Stream response"
                                checked={streamEnabled}
                                disabled={interactionMode === 'agent' || blocked}
                                onChange={e => setStreamEnabled(e.target.checked)}
                            />
                            <span>Stream responses</span>
                        </label>
                    <p>Generation overrides apply when you send your next message.</p>
                </div>
            </EditorPanel>}
            {settingsError && <div role="alert">{settingsError} <button type="button" disabled={blocked} onClick={async () => {
                try { const value = await getConversationSettings(conversationId); setSettings(value); setSystemPrompt(value.systemPrompt || ''); setProjectId(value.projectId || ''); setUsePreferences(value.usePreferences); setUseProjectContext(value.useProjectContext); setSelectedMemoryIds([]); setSettingsError(''); } catch (e) { setSettingsError(apiError(e)); }
            }}>Reload chat settings</button></div>}
            {executionError && <p role="status">{executionError}</p>}
            {executionPending && <p role="status">Ullavox is checking or completing this conversation’s response. Reconnecting does not cancel its work.</p>}
            <div className="chat-messages">
                {showPlaceholder ? (
                    <div className="chat-greeting">
                        <div className="greeting-wordmark">ullavox<span>.</span></div>
                        <h2 className="greeting-title">A little space to think.</h2>
                        <div className="greeting-subtitle">
                            {selectedModel ? (projectId ? 'Pick up an idea, or start something new in this project.' : 'Ask a question. Explore an idea. Make a little progress.') : (modelsLoading ? 'Connecting to your model…' : selectedServer ? 'No models available. Check your connection and installed models, then retry.' : 'Open Context to choose a connection and get started.')}
                        </div>
                        {onboarding && onOpenPreferences && <button type="button" className="onboarding-link" onClick={onOpenPreferences}>Make Ullavox feel more like you →</button>}
                    </div>
                ) : (
                    messages.map((msg, index) => (
                        <React.Fragment key={msg.id != null ? `message-${msg.id}` : `draft-${index}`}><Message text={msg.text} sender={msg.sender} toolCalls={msg.toolCalls} images={msg.images} />
                        {projectId && msg.id != null && !blocked && <button type="button" className="save-project-message" disabled={settings?.projectId !== projectId} title={settings?.projectId !== projectId ? 'Save chat settings before saving a message to this project' : 'Save this completed message as an editable project note'} onClick={() => { setSourceMessage(msg); setShowMemory(true); }}><Icon name="bookmark" size={14} />Save to project notes</button>}</React.Fragment>
                    ))
                )}
                {activeAgentRun && <AgentRunPanel run={activeAgentRun} onCancel={handleCancelAgentRun} />}
                {agentRefreshError && <p role="status" className="agent-connection-message">{agentRefreshError}</p>}
                {chatLocked && interactionMode === 'ask' && !isStreaming && (
                    <div className="message-row bot">
                        <div className="message-bubble bot typing-indicator">
                            <span className="dot"></span>
                            <span className="dot"></span>
                            <span className="dot"></span>
                        </div>
                    </div>
                )}
                {!showPlaceholder && <ContextUsed context={interactionMode === 'agent' ? activeAgentRun?.contextUsed : contextUsed} />}
                <div ref={messagesEndRef} />
            </div>
            <form className="input-area" onSubmit={handleSendMessage}>
                <div className="input-bubble">
                    {interactionMode === 'agent' && (
                        <div className="agent-mode-help">
                            <p>Scope: {allowWebSearch ? (attachedFiles.length || retainedFiles.length ? 'Files + Web' : 'Web') : 'Files only'}. Research uses the sources you allow.</p>
                            <label><input type="checkbox" checked={allowWebSearch} onChange={event => setAllowWebSearch(event.target.checked)} disabled={blocked} /> Include web sources</label>
                            <details className="research-details"><summary>About research & attachments</summary><p>Attaching text files defaults to Files only. Enable web sources to compare files with external information. Web queries and requested page URLs are sent to the configured search provider.</p><p>Up to 4 text, Markdown, CSV, JSON, or code files; 24,000 characters combined. PDF and Office files are not supported.</p></details>
                            {retainedFiles.length > 0 && <p>Available for follow-ups: {retainedFiles.join(', ')}. <button type="button" disabled={blocked} onClick={() => { setRetainedFiles([]); setClearAttachments(true); }}>Clear retained files</button></p>}
                            {filesLoading && <p role="status">Reading selected files…</p>}
                            {attachedFiles.length > 0 && <ul>{attachedFiles.map((file, index) => (
                                <li key={index}>{file.name} <button type="button" aria-label={`Remove file ${file.name}`} onClick={() => setAttachedFiles(prev => prev.filter((_, i) => i !== index))} disabled={blocked}>Remove</button></li>
                            ))}</ul>}
                        </div>
                    )}
                    {attachedImages.length > 0 && (
                        <div className="attached-images-row">
                            {attachedImages.map((img, index) => (
                                <div className="attached-image-thumb" key={index}>
                                    <img src={img.dataUrl} alt={`Attachment ${index + 1}`} />
                                    <button
                                        type="button"
                                        className="attached-image-remove"
                                        onClick={() => removeAttachedImage(index)}
                                        aria-label="Remove image"
                                    >
                                        ×
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                        <textarea
                            ref={textareaRef}
                            aria-label="Message"
                            value={input}
                            onChange={handleInputChange}
                            onKeyDown={handleKeyDown}
                            placeholder={interactionMode === 'agent' ? 'What would you like to investigate?' : 'Ask anything, or pick up an idea…'}
                            rows={1}
                            className="input-textarea"
                        />
                    <div className="input-row">
                        <div className="interaction-mode-toggle" role="group" aria-label="Response mode">
                            <button
                                type="button"
                                className={interactionMode === 'ask' ? 'active' : ''}
                                onClick={() => setInteractionMode('ask')}
                                aria-pressed={interactionMode === 'ask'}
                                disabled={blocked || filesLoading || attachedFiles.length > 0}
                            >
                                Chat
                            </button>
                            <button
                                type="button"
                                className={interactionMode === 'agent' ? 'active' : ''}
                                onClick={() => setInteractionMode('agent')}
                                aria-pressed={interactionMode === 'agent'}
                                disabled={blocked}
                            >
                                Research
                            </button>
                        </div>
                        <button type="button" className="attachment-trigger" aria-expanded={showAttachments} onClick={() => setShowAttachments(value => !value)}><Icon name="plus" size={16} />Attach</button>
                        <div className="attachment-options" hidden={!showAttachments}>
                        {interactionMode === 'agent' && <>
                            <input ref={textFileInputRef} type="file" multiple accept=".txt,.md,.csv,.tsv,.json,.log,.java,.py,.js,.jsx,.ts,.tsx,.html,.css,.xml,.yaml,.yml,.sql" onChange={handleTextFileSelect} style={{ display: 'none' }} />
                            <button type="button" className="attach-image-toggle" onClick={() => textFileInputRef.current?.click()} disabled={blocked || filesLoading || attachedFiles.length >= 4} aria-label="Attach text files" title="Attach text, CSV, JSON, or code files">Files</button>
                        </>}
                        {supportsVision && (
                            <>
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept="image/*"
                                    multiple
                                    onChange={handleFileSelect}
                                    style={{ display: 'none' }}
                                />
                                <button
                                    type="button"
                                    className="attach-image-toggle"
                                    onClick={() => fileInputRef.current && fileInputRef.current.click()}
                                    disabled={blocked || attachedImages.length >= MAX_IMAGES}
                                    title="Attach image"
                                    aria-label="Attach image"
                                >
                                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                        <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                                        <circle cx="8.5" cy="8.5" r="1.5" />
                                        <polyline points="21 15 16 10 5 21" />
                                    </svg>
                                </button>
                            </>
                        )}
                        {interactionMode !== 'agent' && <span className="attachment-hint">Choose Research to attach text files.</span>}
                        {!supportsVision && <span className="attachment-hint">Images need a vision-capable model.</span>}
                        </div>
                        <button
                            type="submit"
                            className="send-btn"
                            aria-label="Send"
                            disabled={blocked || !settings || filesLoading || !input.trim() || !selectedServer || !selectedModel}
                        >
                            <Icon name="arrow" size={20} />
                        </button>

                    </div>
                </div>
                <p className="composer-caption">{selectedModel || 'Choose a model in Context'}<span>Enter to send · Shift + Enter for a new line</span></p>
            </form>
            {error && (
                <div className="toast" role="alert">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <circle cx="12" cy="12" r="10" />
                        <line x1="15" y1="9" x2="9" y2="15" />
                        <line x1="9" y1="9" x2="15" y2="15" />
                    </svg>
                    <span>{error}</span>
                    <button className="toast-dismiss" onClick={() => setError('')} aria-label="Dismiss">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                            <line x1="18" y1="6" x2="6" y2="18" />
                            <line x1="6" y1="6" x2="18" y2="18" />
                        </svg>
                    </button>
                </div>
            )}
        </div>
    );
};

export default ChatBox;
