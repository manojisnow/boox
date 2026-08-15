import React from 'react';
import './AgentRunPanel.css';

const STATUS_LABELS = {
    QUEUED: 'Preparing task',
    RUNNING: 'Working',
    WAITING_FOR_APPROVAL: 'Needs approval',
    CANCEL_REQUESTED: 'Cancelling after current step',
    COMPLETED: 'Completed',
    FAILED: 'Could not complete',
    CANCELLED: 'Cancelled',
};

const AgentRunPanel = ({ run, onCancel }) => {
    if (!run) return null;

    const active = ['QUEUED', 'RUNNING', 'WAITING_FOR_APPROVAL', 'CANCEL_REQUESTED'].includes(run.status);
    const canCancel = active && run.status !== 'CANCEL_REQUESTED';

    return (
        <section className="agent-run-panel" aria-label="Agent task activity">
            <div className="agent-run-header">
                <span className={`agent-run-status ${active ? 'active' : ''}`}>
                    <span className="agent-run-status-dot" />
                    Agent · {STATUS_LABELS[run.status] || run.status}
                </span>
                {canCancel && (
                    <button type="button" className="agent-run-cancel" onClick={() => onCancel(run.id)}>
                        Cancel
                    </button>
                )}
            </div>
            <ol className="agent-run-steps">
                {(run.steps || []).map(step => (
                    <li key={step.sequence} className={`agent-run-step ${step.status.toLowerCase()}`}>
                        <span className="agent-run-step-mark">
                            {step.status === 'COMPLETED' ? '✓' : step.status === 'FAILED' ? '!' : '·'}
                        </span>
                        <span>{step.summary}</span>
                    </li>
                ))}
            </ol>
            {run.error && <p className="agent-run-error">{run.error}</p>}
        </section>
    );
};

export default AgentRunPanel;
