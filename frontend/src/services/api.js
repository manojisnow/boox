import axios from 'axios';

const API_URL = 'http://localhost:8080/api/chat';

export const getServers = async () => {
    const response = await axios.get(`${API_URL}/servers`);
    return response.data;
};

export const getModels = async (server) => {
    const response = await axios.get(`${API_URL}/models`, { params: { server } });
    return response.data;
};

export const sendMessage = async (message, server, model, sessionId, stream) => {
    try {
        const response = await axios.post(`${API_URL}/send`, { message, server, model, sessionId, stream });
        return response.data;
    } catch (error) {
        console.error('Error sending message:', error);
        throw error;
    }
};

export const searchWeb = async (query) => {
    try {
        const response = await axios.get(`${API_URL}/search`, { params: { query } });
        return response.data;
    } catch (error) {
        console.error('Error searching the web:', error);
        throw error;
    }
};

export const resetContext = async (server, sessionId) => {
    await axios.post(`${API_URL}/reset-context`, { server, sessionId });
};