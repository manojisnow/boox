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

export const sendMessage = async (message, server, model, sessionId, stream, systemPrompt, images, generationConfig) => {
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

export const streamMessage = async (message, server, model, sessionId, systemPrompt, images, generationConfig) => {
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
    const response = await fetch(`${API_BASE_URL}/api/chat/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    if (!response.ok) {
        throw new Error(`Stream request failed: ${response.status}`);
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

export const createAgentRun = async (goal, conversationId, server, model, systemPrompt, images, generationConfig, attachments, allowWebSearch) => {
    const chatRequest = {
        message: goal.trim(), sessionId: conversationId.trim(), server: server.trim(), model: model.trim(),
        stream: false, systemPrompt: systemPrompt || '', images: images || [],
        replaceGenerationConfig: Boolean(generationConfig),
        temperature: generationConfig?.temperature ?? null,
        numCtx: generationConfig?.numCtx ?? null,
        stopSequences: generationConfig?.stopSequences || [],
    };
    const response = await axios.post(`${API_BASE_URL}/api/agent-runs`, {
        goal: goal.trim(), conversationId: conversationId.trim(), server: server.trim(), model: model.trim(),
        chatRequest, attachments: attachments || [], allowWebSearch,
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
