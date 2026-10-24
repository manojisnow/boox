import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { getServers, listConversations, renameConversation, deleteConversation, getPreferences, listProjects } from './services/api';

vi.mock('./services/api', () => ({
    getPreferences: vi.fn().mockResolvedValue({ onboardingStatus: 'skipped' }),
    listProjects: vi.fn().mockResolvedValue([]),
    apiError: error => error.message,
    getServers: vi.fn(),
    listConversations: vi.fn(),
    renameConversation: vi.fn(),
    deleteConversation: vi.fn(),
}));

vi.mock('./components/ChatBox', () => ({
    default: (props) => (
        <div data-testid="chat-box">
            {!props.sidebarExpanded && <button aria-label="New Chat" onClick={props.onNewChat}>New chat</button>}
            <span data-testid="conversation-id">{props.conversationId}</span>
            <span data-testid="selected-server">{props.selectedServer}</span>
            <span data-testid="initial-model">{props.initialModel}</span>
            <span data-testid="initial-project">{props.initialProjectId}</span>
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
    window.innerWidth = 700;
    getPreferences.mockResolvedValue({ onboardingStatus: 'skipped' });
    listProjects.mockResolvedValue([]);
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
        expect(localStorage.getItem('ullavox-active-conversation')).toBe(id);
    });

    it('resumes the conversation id and server stored in localStorage', async () => {
        localStorage.setItem('ullavox-active-conversation', '1');
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
        localStorage.setItem('ullavox-active-conversation', '1');
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
        localStorage.setItem('ullavox-active-conversation', '1');
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


describe('project navigation and local installation state', () => {
    it('opens a new project destination with inherited context when it has no conversations', async () => {
        const user = userEvent.setup();
        listProjects.mockResolvedValue([{ id: 'empty', name: 'New idea' }]);
        render(<App />);
        await user.click(screen.getByLabelText('Show chat history'));
        await user.click(await screen.findByRole('button', { name: 'New idea', exact: true }));
        expect(screen.getByTestId('initial-project')).toHaveTextContent('empty');
        expect(screen.queryByRole('dialog', { name: 'Chat navigation' })).not.toBeInTheDocument();
    });

    it('contains keyboard focus in compact navigation and restores its trigger on Escape', async () => {
        const user = userEvent.setup();
        render(<App />);
        const trigger = screen.getByLabelText('Show chat history');
        await user.click(trigger);
        expect(screen.getByRole('dialog', { name: 'Chat navigation' })).toBeInTheDocument();
        await user.click(screen.getByLabelText('Navigation options'));
        screen.getByRole('button', { name: 'Refresh projects' }).focus();
        await user.tab();
        expect(screen.getByLabelText('Hide chat history')).toHaveFocus();
        await user.tab({ shift: true });
        expect(screen.getByRole('button', { name: 'Refresh projects' })).toHaveFocus();
        await user.keyboard('{Escape}');
        expect(screen.queryByRole('dialog', { name: 'Chat navigation' })).not.toBeInTheDocument();
        expect(screen.getByLabelText('Show chat history')).toHaveFocus();
    });

    it('opens navigation by default on desktop and selects the available connection', async () => {
        window.innerWidth = 1280;
        render(<App />);
        expect(screen.getByTestId('conversation-sidebar')).toBeInTheDocument();
        expect(screen.getAllByRole('button', { name: 'New Chat', exact: true })).toHaveLength(1);
        await waitFor(() => expect(screen.getByTestId('selected-server')).toHaveTextContent('ollama'));
        act(() => { window.innerWidth = 500; window.dispatchEvent(new Event('resize')); });
        expect(screen.queryByTestId('conversation-sidebar')).not.toBeInTheDocument();
    });

    it('replaces a deleted stored chat ID without creating a conversation', async () => {
        localStorage.setItem('ullavox-active-conversation', 'deleted-chat');
        render(<App />);
        await waitFor(() => expect(screen.getByTestId('conversation-id').textContent).not.toBe('deleted-chat'));
        expect(localStorage.getItem('ullavox-active-conversation')).toBe(screen.getByTestId('conversation-id').textContent);
    });
    it('restores project navigation and inherits its project for a new chat', async () => {
        const user = userEvent.setup(); localStorage.setItem('ullavox-active-conversation', '1');
        listProjects.mockResolvedValue([{ id: 'p1', name: 'Ullavox' }]);
        listConversations.mockResolvedValue([{ id: '1', title: 'First', server: 'ollama', projectId: 'p1' }, { id: '2', title: 'Standalone', server: 'ollama' }]);
        render(<App />);
        await user.click(screen.getByLabelText('Show chat history'));
        await waitFor(() => expect(screen.getByRole('button', { name: 'Ullavox', exact: true })).toHaveAttribute('aria-current', 'page'));
        expect(screen.getByText('select-1')).toBeInTheDocument(); expect(screen.queryByText('select-2')).not.toBeInTheDocument();
        await user.click(screen.getByLabelText('New Chat'));
        expect(screen.getByTestId('initial-project')).toHaveTextContent('p1');
        await user.click(screen.getByLabelText('Show chat history'));
        await user.click(screen.getByRole('button', { name: 'Standalone chats', exact: true }));
        await user.click(screen.getByLabelText('New Chat'));
        expect(screen.getByTestId('initial-project')).toBeEmptyDOMElement();
    });
});


it('ignores an older initial project response after a newer refresh completes', async () => {
    const user = userEvent.setup(); let initial;
    listProjects.mockImplementationOnce(() => new Promise(resolve => { initial = resolve; })).mockResolvedValue([{ id: 'fresh', name: 'Fresh project' }]);
    render(<App />);
    await user.click(screen.getByLabelText('Show chat history'));
    await user.click(screen.getByLabelText('Navigation options'));
    await user.click(screen.getByRole('button', { name: 'Refresh projects' }));
    expect(await screen.findByRole('button', { name: 'Fresh project' })).toBeInTheDocument();
    await act(async () => initial([]));
    expect(screen.getByRole('button', { name: 'Fresh project' })).toBeInTheDocument();
});

it('does not reopen onboarding from an old bootstrap response after the user visits preferences', async () => {
    const user = userEvent.setup(); let bootstrap;
    getPreferences.mockImplementationOnce(() => new Promise(resolve => { bootstrap = resolve; })).mockResolvedValue({ version: 0, onboardingStatus: 'completed' });
    render(<App />);
    await user.click(screen.getByLabelText('Show chat history'));
    await user.click(screen.getByRole('button', { name: 'Preferences' }));
    expect(await screen.findByRole('heading', { name: 'Preferences' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Close Preferences' }));
    await act(async () => bootstrap({ onboardingStatus: 'not_started' }));
    expect(screen.queryByRole('heading', { name: 'Make Ullavox work for you' })).not.toBeInTheDocument();
});
