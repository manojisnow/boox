import React, { useState } from 'react';
import './ToolCall.css';

const ToolCall = ({ name, args, result, status }) => {
    const [expanded, setExpanded] = useState(false);

    const displayName = name === 'web_search' ? 'Web Search' : name;
    const query = args?.query || JSON.stringify(args);

    return (
        <div className={`tool-call ${status}`} onClick={() => setExpanded(v => !v)}>
            <div className="tool-call-header">
                <span className="tool-call-icon">
                    {status === 'searching' ? '🔍' : '✓'}
                </span>
                <span className="tool-call-label">
                    {status === 'searching'
                        ? `${displayName}: "${query}"`
                        : `${displayName} complete`}
                </span>
                <span className={`tool-call-chevron ${expanded ? 'open' : ''}`}>▸</span>
            </div>
            {expanded && result && (
                <pre className="tool-call-result">{result}</pre>
            )}
        </div>
    );
};

export default ToolCall;
