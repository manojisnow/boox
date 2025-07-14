import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

// Add axios default configs
axios.defaults.withCredentials = true;

export const getModels = async () => {
    const response = await axios.get(`${API_BASE_URL}/api/chat/models`);
    return response.data;
};

export const sendMessage = async (message, model, sessionId, stream) => {
    // Validate input parameters
    if (!message || !message.trim()) {
        throw new Error('Message cannot be empty');
    }
    if (!model || !model.trim()) {
        throw new Error('Model cannot be empty');
    }
    if (!sessionId || !sessionId.trim()) {
        throw new Error('Session ID cannot be empty');
    }

    try {
        // Ensure all values are properly formatted
        const requestBody = {
            message: message.trim(),
            model: model.trim(),
            sessionId: sessionId.trim(),
            stream: Boolean(stream)
        };
        
        console.log('Sending request with body:', requestBody);
        
        // Send the request with proper headers
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
        console.error('Error sending message:', {
            message: error.response?.data?.message || error.message,
            status: error.response?.status,
            validationErrors: error.response?.data?.errors,
            request: {
                message,
                model,
                sessionId,
                stream
            }
        });
        throw error;
    }
};

export const searchWeb = async (query) => {
    try {
        const response = await axios.get(`${API_BASE_URL}/api/chat/search`, { params: { query } });
        return response.data;
    } catch (error) {
        console.error('Error searching the web:', error);
        throw error;
    }
};

export const resetContext = async () => {
    try {
        const response = await fetch(`${API_BASE_URL}/api/chat/reset`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        return await response.json();
    } catch (error) {
        console.error('Error resetting context:', error);
        throw error;
    }
};