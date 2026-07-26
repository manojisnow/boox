import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ToolCall from './ToolCall';

describe('ToolCall', () => {
    it('maps web_search to "Web Search" and shows the query while searching', () => {
        render(<ToolCall name="web_search" args={{ query: 'cats' }} result={null} status="searching" />);
        expect(screen.getByText('Web Search: "cats"')).toBeInTheDocument();
    });

    it('shows the raw tool name for non-web_search tools', () => {
        render(<ToolCall name="calculator" args={{ query: '2+2' }} result={null} status="searching" />);
        expect(screen.getByText('calculator: "2+2"')).toBeInTheDocument();
    });

    it('falls back to JSON.stringify(args) when args has no query field', () => {
        render(<ToolCall name="lookup" args={{ id: 42 }} result={null} status="searching" />);
        expect(screen.getByText('lookup: "{"id":42}"')).toBeInTheDocument();
    });

    it('shows "complete" label once status is no longer searching', () => {
        render(<ToolCall name="web_search" args={{ query: 'cats' }} result="Cats are mammals." status="done" />);
        expect(screen.getByText('Web Search complete')).toBeInTheDocument();
    });

    it('hides the result until expanded, then shows it on click, then hides again', async () => {
        const user = userEvent.setup();
        render(<ToolCall name="web_search" args={{ query: 'cats' }} result="Cats are mammals." status="done" />);

        expect(screen.queryByText('Cats are mammals.')).not.toBeInTheDocument();

        await user.click(screen.getByText('Web Search complete'));
        expect(screen.getByText('Cats are mammals.')).toBeInTheDocument();

        await user.click(screen.getByText('Web Search complete'));
        expect(screen.queryByText('Cats are mammals.')).not.toBeInTheDocument();
    });

    it('renders no result block when expanded but there is no result yet', async () => {
        const user = userEvent.setup();
        const { container } = render(
            <ToolCall name="web_search" args={{ query: 'cats' }} result={null} status="searching" />
        );
        await user.click(screen.getByText('Web Search: "cats"'));
        expect(container.querySelector('.tool-call-result')).toBeNull();
    });
});
