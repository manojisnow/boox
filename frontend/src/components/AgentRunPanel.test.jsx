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
    it('collapses on completion, preserves expandable evidence, and opens the next task', () => {
        const steps = [{ sequence: 1, status: 'COMPLETED', summary: 'Search result', detail: 'Retrieved source' }];
        const { rerender } = render(<AgentRunPanel run={{ id: 'one', status: 'RUNNING', steps }} />);
        expect(screen.getByRole('button', { name: 'Hide activity' })).toHaveAttribute('aria-expanded', 'true');
        rerender(<AgentRunPanel run={{ id: 'one', status: 'COMPLETED', steps }} />);
        expect(screen.getByRole('button', { name: 'Show activity (1 step)' })).toHaveAttribute('aria-expanded', 'false');
        expect(screen.queryByText('Search result')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Show activity (1 step)' }));
        expect(screen.getByText('Search result')).toBeVisible();
        fireEvent.click(screen.getByText('View evidence'));
        expect(screen.getByText('Retrieved source')).toBeVisible();
        fireEvent.click(screen.getByRole('button', { name: 'Hide activity' }));
        rerender(<AgentRunPanel run={{ id: 'two', status: 'RUNNING', steps }} />);
        expect(screen.getByText('Search result')).toBeVisible();
    });

    it('starts saved terminal tasks collapsed but leaves failure messages visible', () => {
        render(<AgentRunPanel run={{ id: 'saved', status: 'FAILED', error: 'Model unavailable', steps: [{ sequence: 1, status: 'FAILED', summary: 'Failed step' }] }} />);
        expect(screen.getByRole('button', { name: 'Show activity (1 step)' })).toHaveAttribute('aria-expanded', 'false');
        expect(screen.getByText('Model unavailable')).toBeVisible();
        expect(screen.queryByText('Failed step')).not.toBeInTheDocument();
    });

});
