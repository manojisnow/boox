import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Message from './Message';

describe('Message', () => {
    it('renders a user message as plain text with no sender label', () => {
        const { container } = render(<Message text="Hello there" sender="user" />);
        expect(screen.getByText('Hello there')).toBeInTheDocument();
        expect(container.querySelector('.message-sender')).toBeNull();
        expect(container.querySelector('.message-row.user')).not.toBeNull();
    });

    it('labels assistant messages "AI" and renders markdown', () => {
        const { container } = render(<Message text="**bold text**" sender="assistant" />);
        expect(screen.getByText('AI')).toBeInTheDocument();
        expect(container.querySelector('strong')).not.toBeNull();
        expect(container.querySelector('.message-row.bot')).not.toBeNull();
    });

    it('shows the raw sender name for non-assistant, non-user senders', () => {
        render(<Message text="note" sender="system" />);
        expect(screen.getByText('system')).toBeInTheDocument();
    });

    it('renders attached images as thumbnails with numbered alt text', () => {
        const images = ['data:image/png;base64,aaa', 'data:image/png;base64,bbb'];
        render(<Message text="see attached" sender="user" images={images} />);
        expect(screen.getByAltText('Attachment 1')).toBeInTheDocument();
        expect(screen.getByAltText('Attachment 2')).toBeInTheDocument();
    });

    it('renders no image block when images is empty or absent', () => {
        const { container } = render(<Message text="hi" sender="user" images={[]} />);
        expect(container.querySelector('.message-images')).toBeNull();
    });

    it('renders tool call cards for assistant messages with toolCalls', () => {
        const toolCalls = [
            { name: 'web_search', args: { query: 'cats' }, result: null, status: 'searching' },
        ];
        render(<Message text="Let me check." sender="assistant" toolCalls={toolCalls} />);
        expect(screen.getByText('Web Search: "cats"')).toBeInTheDocument();
    });

    it('does not render tool call cards for user messages even if toolCalls is set', () => {
        const toolCalls = [
            { name: 'web_search', args: { query: 'cats' }, result: null, status: 'searching' },
        ];
        const { container } = render(<Message text="hi" sender="user" toolCalls={toolCalls} />);
        expect(container.querySelector('.tool-calls-container')).toBeNull();
    });

    it('renders inline code as a plain <code> element', () => {
        const { container } = render(<Message text="Use `npm test` to run." sender="assistant" />);
        const code = container.querySelector('code');
        expect(code).not.toBeNull();
        expect(code.textContent).toBe('npm test');
    });

    it('renders a fenced code block with a language tag via the syntax highlighter', () => {
        const text = '```js\nconst x = 1;\n```';
        const { container } = render(<Message text={text} sender="assistant" />);
        expect(container.textContent).toContain('const x = 1;');
    });

    it('renders a fenced multi-line code block without a language tag', () => {
        const text = '```\nline one\nline two\n```';
        const { container } = render(<Message text={text} sender="assistant" />);
        expect(container.textContent).toContain('line one');
        expect(container.textContent).toContain('line two');
    });

    it('renders an empty assistant message without crashing', () => {
        const { container } = render(<Message text="" sender="assistant" />);
        expect(container.querySelector('.message-bubble.bot')).not.toBeNull();
    });
});
