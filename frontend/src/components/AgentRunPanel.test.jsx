import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AgentRunPanel from './AgentRunPanel';

describe('AgentRunPanel', () => {
    it('renders actual evidence, cancellation and review limits', () => {
        const cancel = vi.fn();
        const { rerender } = render(<AgentRunPanel run={{ id: 'r', status: 'RUNNING', steps: [{ sequence: 1, status: 'COMPLETED', summary: 'Search result', detail: 'Source text' }] }} onCancel={cancel} />);
        fireEvent.click(screen.getByText('View evidence'));
        expect(screen.getByText('Source text')).toBeVisible();
        fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
        expect(cancel).toHaveBeenCalledWith('r');
        rerender(<AgentRunPanel run={{ id: 'r', status: 'COMPLETED', steps: [] }} onCancel={cancel} />);
        expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();
        expect(screen.getByText(/Draft reviewed by the same model/)).toBeVisible();
        rerender(<AgentRunPanel run={{ id: 'r', status: 'FAILED', error: 'Model offline', steps: [{ sequence: 2, status: 'FAILED', summary: 'Failed' }] }} onCancel={cancel} />);
        expect(screen.getByText('Model offline')).toBeVisible();
        rerender(<AgentRunPanel run={null} />);
        expect(screen.queryByLabelText('Agent task activity')).not.toBeInTheDocument();
    });
});
