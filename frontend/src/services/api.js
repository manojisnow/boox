import axios from 'axios';

// Use the page's origin by default; override for a separately hosted backend.
const API_BASE_URL = import.meta.env.VITE_API_URL || '';

// Add axios default configs
axios.defaults.withCredentials = true;

export const getServers = async () => {
    const response = await axios.get(`${API_BASE_URL}/api/chat/servers`);
    return response.data;
};

export const getModels = async (server) => {
    const response = await axios.get(`${API_BASE_URL}/api/chat/models`, { params: { server } });
    return response.data;
};

export const sendMessage = async (message, server, model, sessionId, stream, systemPrompt, images, generationConfig, selectedMemoryIds) => {
    // A supplied config replaces saved overrides; blank fields use server defaults.
    if (!message || !message.trim()) {
        throw new Error('Message cannot be empty');
    }
    if (!server || !server.trim()) {
        throw new Error('Server cannot be empty');
    }
    if (!model || !model.trim()) {
        throw new Error('Model cannot be empty');
    }
    if (!sessionId || !sessionId.trim()) {
        throw new Error('Session ID cannot be empty');
    }

    try {
        const requestBody = {
            message: message.trim(),
            server: server.trim(),
            model: model.trim(),
            sessionId: sessionId.trim(),
            stream: Boolean(stream),
        };
        if (systemPrompt && systemPrompt.trim()) {
            requestBody.systemPrompt = systemPrompt.trim();
        }
        if (images && images.length > 0) {
            requestBody.images = images;
        }
        if (generationConfig) {
            requestBody.replaceGenerationConfig = true;
            if (generationConfig.temperature != null) requestBody.temperature = generationConfig.temperature;
            if (generationConfig.numCtx != null) requestBody.numCtx = generationConfig.numCtx;
            if (generationConfig.stopSequences && generationConfig.stopSequences.length > 0) {
                requestBody.stopSequences = generationConfig.stopSequences;
            }
        }

        if (selectedMemoryIds?.length) requestBody.selectedMemoryIds = selectedMemoryIds;
        const response = await axios.post(
            `${API_BASE_URL}/api/chat/send`,
            requestBody,
            {
                headers: {
                    'Content-Type': 'application/json'
                }
            }
        );
        return response.data;
    } catch (error) {
        console.error('Send failed:', error.message);
        throw error;
    }
};

export const streamMessage = async (message, server, model, sessionId, systemPrompt, images, generationConfig, selectedMemoryIds) => {
    const body = {
        message: message.trim(),
        server: server.trim(),
        model: model.trim(),
        sessionId: sessionId.trim(),
        stream: true,
    };
    if (systemPrompt && systemPrompt.trim()) {
        body.systemPrompt = systemPrompt.trim();
    }
    if (images && images.length > 0) {
        body.images = images;
    }
    if (generationConfig) {
        body.replaceGenerationConfig = true;
        if (generationConfig.temperature != null) body.temperature = generationConfig.temperature;
        if (generationConfig.numCtx != null) body.numCtx = generationConfig.numCtx;
        if (generationConfig.stopSequences && generationConfig.stopSequences.length > 0) {
            body.stopSequences = generationConfig.stopSequences;
        }
    }
    if (selectedMemoryIds?.length) body.selectedMemoryIds = selectedMemoryIds;
    const response = await fetch(`${API_BASE_URL}/api/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    if (!response.ok) {
        let detail;
        try { detail = await response.json(); } catch { /* The proxy may return a non-JSON error. */ }
        const error = new Error(detail?.error || detail?.message || `Stream request failed: ${response.status}`);
        error.response = { status: response.status, data: detail };
        throw error;
    }
    return response;
};

export const getTools = async () => {
    const response = await axios.get(`${API_BASE_URL}/api/chat/tools`);
    return response.data;
};

export const resetContext = async (server, sessionId) => {
    try {
        const response = await axios.post(`${API_BASE_URL}/api/chat/reset-context`, {
            server: server,
            sessionId: sessionId,
        });
        return response.data;
    } catch (error) {
        console.error('Error resetting context:', error);
        throw error;
    }
};

// --- Agent runs ---

export const createAgentRun = async (goal, conversationId, server, model, systemPrompt, images, generationConfig, attachments, allowWebSearch, clearAttachments = false, selectedMemoryIds) => {
    const chatRequest = {
        message: goal.trim(), sessionId: conversationId.trim(), server: server.trim(), model: model.trim(),
        stream: false, systemPrompt: systemPrompt || '', images: images || [],
        replaceGenerationConfig: Boolean(generationConfig),
        temperature: generationConfig?.temperature ?? null,
        numCtx: generationConfig?.numCtx ?? null,
        stopSequences: generationConfig?.stopSequences || [],
    };
    if (selectedMemoryIds?.length) chatRequest.selectedMemoryIds = selectedMemoryIds;
    const response = await axios.post(`${API_BASE_URL}/api/agent-runs`, {
        goal: goal.trim(), conversationId: conversationId.trim(), server: server.trim(), model: model.trim(),
        chatRequest, attachments: attachments || [], allowWebSearch, clearAttachments,
    });
    return response.data;
};

export const listAgentRuns = async (conversationId) => {
    const response = await axios.get(`${API_BASE_URL}/api/agent-runs`, { params: { conversationId } });
    return response.data;
};

export const getAgentRun = async (runId) => {
    const response = await axios.get(`${API_BASE_URL}/api/agent-runs/${encodeURIComponent(runId)}`);
    return response.data;
};

export const cancelAgentRun = async (runId) => {
    const response = await axios.post(`${API_BASE_URL}/api/agent-runs/${encodeURIComponent(runId)}/cancel`);
    return response.data;
};

// --- Conversation persistence & management ---

export const listConversations = async () => {
    const response = await axios.get(`${API_BASE_URL}/api/conversations`);
    return response.data;
};

export const getConversationMessages = async (id) => {
    const response = await axios.get(
        `${API_BASE_URL}/api/conversations/${encodeURIComponent(id)}/messages`
    );
    return response.data;
};

export const renameConversation = async (id, title) => {
    const response = await axios.patch(
        `${API_BASE_URL}/api/conversations/${encodeURIComponent(id)}`,
        { title }
    );
    return response.data;
};

export const deleteConversation = async (id) => {
    await axios.delete(`${API_BASE_URL}/api/conversations/${encodeURIComponent(id)}`);
};

// Local personalization stays in the backend; no profile text is stored in browser storage.
const resource = async (method, path, data, params) =>
    (await axios.request({ method, url: `${API_BASE_URL}/api${path}`, data, params })).data;
export const getPreferences = () => resource('get', '/preferences');
export const savePreferences = data => resource('put', '/preferences', data);
export const clearPreferences = version => resource('delete', '/preferences', undefined, { version });
export const listProjects = () => resource('get', '/projects');
export const saveProject = data => resource(data.id ? 'put' : 'post', `/projects${data.id ? `/${encodeURIComponent(data.id)}` : ''}`, data);
export const deleteProject = (id, version) => resource('delete', `/projects/${encodeURIComponent(id)}`, undefined, { version });
export const listMemories = projectId => resource('get', `/projects/${encodeURIComponent(projectId)}/memories`);
export const saveMemory = (projectId, data) => resource(data.id ? 'put' : 'post', `/projects/${encodeURIComponent(projectId)}/memories${data.id ? `/${encodeURIComponent(data.id)}` : ''}`, data);
export const deleteMemory = (projectId, id, version) => resource('delete', `/projects/${encodeURIComponent(projectId)}/memories/${encodeURIComponent(id)}`, undefined, { version });
export const getConversationSettings = id => resource('get', `/conversations/${encodeURIComponent(id)}/settings`);
export const saveConversationSettings = (id, data) => resource('patch', `/conversations/${encodeURIComponent(id)}/settings`, data);
export const getConversationExecution = id => resource('get', `/chat/conversations/${encodeURIComponent(id)}/execution`);
export const apiError = error => error.response?.status === 409
    ? 'This data changed or is being used by a running response. Reload and retry after the response finishes.'
    : error.response?.data?.error || error.response?.data?.message || error.message || 'Could not complete this action.';
