import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ConversationSidebar from './ConversationSidebar';

const conversations = [
    { id: '1', title: 'First chat' },
    { id: '2', title: 'Second chat' },
];

let props;

beforeEach(() => {
    props = {
        conversations,
        activeId: '1',
        onSelect: vi.fn(),
        onNewChat: vi.fn(),
        onRename: vi.fn(),
        onDelete: vi.fn(),
    };
});

describe('ConversationSidebar', () => {
    it('shows an empty state when there are no conversations', () => {
        render(<ConversationSidebar {...props} conversations={[]} />);
        expect(screen.getByText('No conversations yet')).toBeInTheDocument();
    });

    it('renders every conversation and marks the active one', () => {
        const { container } = render(<ConversationSidebar {...props} />);
        expect(screen.getByText('First chat')).toBeInTheDocument();
        expect(screen.getByText('Second chat')).toBeInTheDocument();
        const items = container.querySelectorAll('.conv-item');
        expect(items[0]).toHaveClass('active');
        expect(items[1]).not.toHaveClass('active');
    });

    it('calls onNewChat when the new chat row is clicked', async () => {
        const user = userEvent.setup();
        render(<ConversationSidebar {...props} />);
        await user.click(screen.getByText('New chat'));
        expect(props.onNewChat).toHaveBeenCalledTimes(1);
    });

    it('calls onSelect with the conversation id when its title is clicked', async () => {
        const user = userEvent.setup();
        render(<ConversationSidebar {...props} />);
        await user.click(screen.getByText('Second chat'));
        expect(props.onSelect).toHaveBeenCalledWith('2');
    });

    it('calls onDelete with the conversation id when the delete icon is clicked', async () => {
        const user = userEvent.setup();
        render(<ConversationSidebar {...props} />);
        await user.click(screen.getAllByLabelText('Delete conversation')[0]);
        expect(props.onDelete).toHaveBeenCalledWith('1');
    });

    it('renames a conversation on Enter and exits edit mode', async () => {
        const user = userEvent.setup();
        render(<ConversationSidebar {...props} />);
        await user.click(screen.getAllByLabelText('Rename conversation')[0]);

        const input = screen.getByDisplayValue('First chat');
        await user.clear(input);
        await user.type(input, 'Renamed{Enter}');

        expect(props.onRename).toHaveBeenCalledWith('1', 'Renamed');
        expect(screen.queryByDisplayValue('Renamed')).not.toBeInTheDocument();
    });

    it('cancels the rename on Escape without calling onRename', async () => {
        const user = userEvent.setup();
        render(<ConversationSidebar {...props} />);
        await user.click(screen.getAllByLabelText('Rename conversation')[0]);

        const input = screen.getByDisplayValue('First chat');
        await user.type(input, ' extra{Escape}');

        expect(props.onRename).not.toHaveBeenCalled();
        expect(screen.getByText('First chat')).toBeInTheDocument();
    });

    it('commits the rename on blur', async () => {
        const user = userEvent.setup();
        render(<ConversationSidebar {...props} />);
        await user.click(screen.getAllByLabelText('Rename conversation')[0]);

        const input = screen.getByDisplayValue('First chat');
        await user.clear(input);
        await user.type(input, 'Blurred');
        await user.tab();

        expect(props.onRename).toHaveBeenCalledWith('1', 'Blurred');
    });

    it('does not call onRename when the committed title is blank', async () => {
        const user = userEvent.setup();
        render(<ConversationSidebar {...props} />);
        await user.click(screen.getAllByLabelText('Rename conversation')[0]);

        const input = screen.getByDisplayValue('First chat');
        await user.clear(input);
        await user.type(input, '   {Enter}');

        expect(props.onRename).not.toHaveBeenCalled();
    });
});
