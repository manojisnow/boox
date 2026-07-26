import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { getServers, listConversations, renameConversation, deleteConversation } from './services/api';

vi.mock('./services/api', () => ({
    getServers: vi.fn(),
    listConversations: vi.fn(),
    renameConversation: vi.fn(),
    deleteConversation: vi.fn(),
}));

vi.mock('./components/ChatBox', () => ({
    default: (props) => (
        <div data-testid="chat-box">
            <span data-testid="conversation-id">{props.conversationId}</span>
            <span data-testid="selected-server">{props.selectedServer}</span>
            <span data-testid="initial-model">{props.initialModel}</span>
        </div>
    ),
}));

vi.mock('./components/ConversationSidebar', () => ({
    default: (props) => (
        <div data-testid="conversation-sidebar">
            {props.conversations.map((c) => (
                <button key={c.id} onClick={() => props.onSelect(c.id)}>
                    select-{c.id}
                </button>
            ))}
            <button onClick={props.onNewChat}>sidebar-new-chat</button>
            <button onClick={() => props.onRename('1', 'Renamed')}>trigger-rename</button>
            <button onClick={() => props.onDelete(props.activeId)}>trigger-delete</button>
        </div>
    ),
}));

beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    getServers.mockResolvedValue(['ollama']);
    listConversations.mockResolvedValue([
        { id: '1', title: 'First', server: 'ollama', model: 'phi4-mini' },
    ]);
    renameConversation.mockResolvedValue({});
    deleteConversation.mockResolvedValue({});
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe('App', () => {
    it('fetches servers and conversations on mount and renders ChatBox', async () => {
        render(<App />);
        await waitFor(() => expect(getServers).toHaveBeenCalledTimes(1));
        expect(listConversations).toHaveBeenCalledTimes(1);
        expect(screen.getByTestId('chat-box')).toBeInTheDocument();
    });

    it('persists a newly generated conversation id to localStorage', async () => {
        render(<App />);
        await waitFor(() => expect(listConversations).toHaveBeenCalled());
        const id = screen.getByTestId('conversation-id').textContent;
        expect(id).toBeTruthy();
        expect(localStorage.getItem('boox-active-conversation')).toBe(id);
    });

    it('resumes the conversation id and server stored in localStorage', async () => {
        localStorage.setItem('boox-active-conversation', '1');
        render(<App />);
        await waitFor(() => expect(screen.getByTestId('conversation-id')).toHaveTextContent('1'));
        await waitFor(() => expect(screen.getByTestId('selected-server')).toHaveTextContent('ollama'));
        expect(screen.getByTestId('initial-model')).toHaveTextContent('phi4-mini');
    });

    it('toggles the sidebar open and closed', async () => {
        const user = userEvent.setup();
        render(<App />);
        await waitFor(() => expect(listConversations).toHaveBeenCalled());

        expect(screen.queryByTestId('conversation-sidebar')).not.toBeInTheDocument();

        await user.click(screen.getByLabelText('Show chat history'));
        expect(screen.getByTestId('conversation-sidebar')).toBeInTheDocument();

        await user.click(screen.getByLabelText('Hide chat history'));
        expect(screen.queryByTestId('conversation-sidebar')).not.toBeInTheDocument();
    });

    it('starts a new chat with a fresh conversation id and refreshes the list', async () => {
        const user = userEvent.setup();
        render(<App />);
        await waitFor(() => expect(listConversations).toHaveBeenCalledTimes(1));
        const originalId = screen.getByTestId('conversation-id').textContent;

        await user.click(screen.getByLabelText('New Chat'));

        await waitFor(() => expect(listConversations).toHaveBeenCalledTimes(2));
        expect(screen.getByTestId('conversation-id').textContent).not.toBe(originalId);
    });

    it('selects a conversation from the sidebar and adopts its server', async () => {
        const user = userEvent.setup();
        listConversations.mockResolvedValue([
            { id: '1', title: 'First', server: 'ollama', model: 'phi4-mini' },
            { id: '2', title: 'Second', server: 'other-server', model: 'llama3' },
        ]);
        render(<App />);
        await waitFor(() => expect(listConversations).toHaveBeenCalled());
        await user.click(screen.getByLabelText('Show chat history'));

        await user.click(screen.getByText('select-2'));

        await waitFor(() => expect(screen.getByTestId('conversation-id')).toHaveTextContent('2'));
        expect(screen.getByTestId('selected-server')).toHaveTextContent('other-server');
    });

    it('renames a conversation via the sidebar callback', async () => {
        const user = userEvent.setup();
        render(<App />);
        await waitFor(() => expect(listConversations).toHaveBeenCalledTimes(1));
        await user.click(screen.getByLabelText('Show chat history'));

        await user.click(screen.getByText('trigger-rename'));

        await waitFor(() => expect(renameConversation).toHaveBeenCalledWith('1', 'Renamed'));
        await waitFor(() => expect(listConversations).toHaveBeenCalledTimes(2));
    });

    it('deletes a conversation after confirmation and replaces the active id if it was active', async () => {
        localStorage.setItem('boox-active-conversation', '1');
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        const user = userEvent.setup();
        render(<App />);
        await waitFor(() => expect(screen.getByTestId('conversation-id')).toHaveTextContent('1'));
        await user.click(screen.getByLabelText('Show chat history'));

        await user.click(screen.getByText('trigger-delete'));

        await waitFor(() => expect(deleteConversation).toHaveBeenCalledWith('1'));
        await waitFor(() =>
            expect(screen.getByTestId('conversation-id').textContent).not.toBe('1')
        );
    });

    it('does not delete when the user cancels the confirmation', async () => {
        localStorage.setItem('boox-active-conversation', '1');
        vi.spyOn(window, 'confirm').mockReturnValue(false);
        const user = userEvent.setup();
        render(<App />);
        await waitFor(() => expect(screen.getByTestId('conversation-id')).toHaveTextContent('1'));
        await user.click(screen.getByLabelText('Show chat history'));

        await user.click(screen.getByText('trigger-delete'));

        expect(deleteConversation).not.toHaveBeenCalled();
    });

    it('logs when renaming a conversation fails', async () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
        renameConversation.mockRejectedValue(new Error('rename down'));
        const user = userEvent.setup();
        render(<App />);
        await waitFor(() => expect(listConversations).toHaveBeenCalledTimes(1));
        await user.click(screen.getByLabelText('Show chat history'));

        await user.click(screen.getByText('trigger-rename'));

        await waitFor(() =>
            expect(consoleError).toHaveBeenCalledWith('Failed to rename conversation:', 'rename down')
        );
    });

    it('logs when deleting a conversation fails', async () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
        deleteConversation.mockRejectedValue(new Error('delete down'));
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        const user = userEvent.setup();
        render(<App />);
        await waitFor(() => expect(listConversations).toHaveBeenCalledTimes(1));
        await user.click(screen.getByLabelText('Show chat history'));

        await user.click(screen.getByText('trigger-delete'));

        await waitFor(() =>
            expect(consoleError).toHaveBeenCalledWith('Failed to delete conversation:', 'delete down')
        );
    });

    it('logs and recovers when fetching servers or conversations fails', async () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
        getServers.mockRejectedValue(new Error('servers down'));
        listConversations.mockRejectedValue(new Error('list down'));

        render(<App />);

        await waitFor(() =>
            expect(consoleError).toHaveBeenCalledWith('Failed to fetch servers:', 'servers down')
        );
        expect(consoleError).toHaveBeenCalledWith('Failed to load conversations:', 'list down');
    });
});
