import React from 'react';
import './ChatControls.css';

const ChatControls = ({
    selectedServer,
    setSelectedServer,
    servers,
    selectedModel,
    setSelectedModel,
    models,
    disabled
}) => {
    return (
        <div className="chat-controls glass">
            <div className="control-group">
                <label htmlFor="server-select">Server</label>
                <select
                    id="server-select"
                    value={selectedServer}
                    onChange={(e) => setSelectedServer(e.target.value)}
                    disabled={disabled}
                    className="elegant-select"
                >
                    <option value="" disabled>Select Server</option>
                    {servers.map((server) => (
                        <option key={server} value={server}>{server}</option>
                    ))}
                </select>
            </div>

            {models.length > 0 && (
                <div className="control-group animate-fade-in">
                    <label htmlFor="model-select">Model</label>
                    <select
                        id="model-select"
                        value={selectedModel}
                        onChange={(e) => setSelectedModel(e.target.value)}
                        disabled={disabled}
                        className="elegant-select"
                    >
                        <option value="" disabled>Select Model</option>
                        {models.map((model) => (
                            <option key={model.name} value={model.name}>{model.name}</option>
                        ))}
                    </select>
                </div>
            )}
        </div>
    );
};

export default ChatControls;
