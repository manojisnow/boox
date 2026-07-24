import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import ToolCall from './ToolCall';
import './Message.css';

// react-markdown v10 removed the `inline` prop from the `code` renderer.
// Distinguish block vs inline via a language class or a multi-line body.
const CodeBlock = ({ node, className, children, ...props }) => {
    const match = /language-(\w+)/.exec(className || '');
    const content = String(children).replace(/\n$/, '');
    if (match) {
        return (
            <SyntaxHighlighter
                style={oneDark}
                language={match[1]}
                PreTag="div"
                {...props}
            >
                {content}
            </SyntaxHighlighter>
        );
    }
    if (content.includes('\n')) {
        return (
            <SyntaxHighlighter
                style={oneDark}
                PreTag="div"
                {...props}
            >
                {content}
            </SyntaxHighlighter>
        );
    }
    return <code className={className} {...props}>{children}</code>;
};

const Message = ({ text, sender, toolCalls }) => {
    const isUser = sender === 'user';
    let displayName = '';
    if (!isUser) {
        displayName = sender === 'assistant' ? 'AI' : sender;
    }
    const hasToolCalls = !isUser && toolCalls && toolCalls.length > 0;
    return (
        <div className={`message-row ${isUser ? 'user' : 'bot'}`}>
            {!isUser && <div className="message-sender">{displayName}</div>}
            <div className={`message-bubble ${isUser ? 'user' : 'bot'}`}>
                {hasToolCalls && (
                    <div className="tool-calls-container">
                        {toolCalls.map((tc, i) => (
                            <ToolCall
                                key={i}
                                name={tc.name}
                                args={tc.args}
                                result={tc.result}
                                status={tc.status}
                            />
                        ))}
                    </div>
                )}
                {isUser ? (
                    <span className="message-text">{text}</span>
                ) : (
                    <ReactMarkdown
                        remarkPlugins={[remarkGfm]}
                        components={{ code: CodeBlock }}
                    >
                        {text || ''}
                    </ReactMarkdown>
                )}
            </div>
        </div>
    );
};

export default Message;
