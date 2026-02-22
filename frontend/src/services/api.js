import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

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

export const sendMessage = async (message, server, model, sessionId, stream, systemPrompt) => {
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

export const streamMessage = async (message, server, model, sessionId, systemPrompt) => {
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