import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PreferencesPanel, ProjectEditor, MemoryPanel, ContextUsed } from './Personalization';
import { getPreferences, savePreferences, clearPreferences, listMemories, saveMemory, deleteMemory, saveProject, deleteProject } from '../services/api';
vi.mock('../services/api', () => ({
    getPreferences: vi.fn(), savePreferences: vi.fn(), clearPreferences: vi.fn(), listMemories: vi.fn(), saveMemory: vi.fn(), deleteMemory: vi.fn(), saveProject: vi.fn(), deleteProject: vi.fn(),
    apiError: e => e.response?.status === 409 ? 'This data changed or is being used by a running response. Reload and retry after the response finishes.' : e.message,
}));
const profile = { version: 2, onboardingStatus: 'not_started', responseLength: 'brief', background: 'Developer', instructions: '' };
beforeEach(() => { vi.clearAllMocks(); getPreferences.mockResolvedValue(profile); listMemories.mockResolvedValue([]); });
describe('local preferences', () => {
    it('skips using the latest saved version, preserving saved preferences and discarding draft edits', async () => {
        const user = userEvent.setup(); const close = vi.fn();
        savePreferences.mockResolvedValue({ ...profile, version: 3, onboardingStatus: 'skipped' });
        render(<PreferencesPanel onboarding onClose={close} />);
        await user.type(await screen.findByLabelText('How should Ullavox help you?'), 'Do not retain this draft');
        getPreferences.mockResolvedValue({ ...profile, version: 5, background: 'Updated elsewhere' });
        await user.click(screen.getByRole('button', { name: 'Skip onboarding' }));
        await waitFor(() => expect(savePreferences).toHaveBeenCalledWith({ ...profile, version: 5, onboardingStatus: 'skipped', background: 'Updated elsewhere' }));
        expect(close).toHaveBeenCalled();
    });
    it('closes stale onboarding without overwriting preferences already completed elsewhere', async () => {
        const user = userEvent.setup(); const close = vi.fn();
        render(<PreferencesPanel onboarding onClose={close} />);
        await screen.findByRole('button', { name: 'Skip onboarding' });
        getPreferences.mockResolvedValue({ ...profile, version: 3, onboardingStatus: 'completed' });
        await user.click(screen.getByRole('button', { name: 'Skip onboarding' }));
        await waitFor(() => expect(close).toHaveBeenCalledOnce());
        expect(savePreferences).not.toHaveBeenCalled();
    });
    it('reports a failed skip instead of closing or overwriting saved data', async () => {
        const user = userEvent.setup(); const close = vi.fn();
        render(<PreferencesPanel onboarding onClose={close} />);
        await screen.findByRole('button', { name: 'Skip onboarding' });
        getPreferences.mockRejectedValue(new Error('Offline'));
        await user.click(screen.getByRole('button', { name: 'Skip onboarding' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Offline');
        expect(close).not.toHaveBeenCalled();
        expect(savePreferences).not.toHaveBeenCalled();
    });
    it('allows editing and clearing with versions, without browser profile storage', async () => {
        const user = userEvent.setup();
        savePreferences.mockImplementation(async p => ({ ...p, version: 3 })); clearPreferences.mockResolvedValue({ version: 4, onboardingStatus: 'skipped' });
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        render(<PreferencesPanel onClose={() => {}} />);
        await user.type(await screen.findByLabelText('How should Ullavox help you?'), 'Explain tradeoffs');
        await user.click(screen.getByRole('button', { name: 'Save preferences' }));
        await waitFor(() => expect(savePreferences).toHaveBeenCalledWith(expect.objectContaining({ version: 2, instructions: 'Explain tradeoffs', onboardingStatus: 'completed' })));
        await user.click(screen.getByRole('button', { name: 'Clear preferences' }));
        await waitFor(() => expect(clearPreferences).toHaveBeenCalledWith(3));
        expect(screen.getByLabelText('Background (optional)')).toHaveValue('');
        expect(localStorage.getItem('background')).toBeNull();
    });
    it('never enables saving when a profile load failed', async () => {
        getPreferences.mockRejectedValue(new Error('Offline'));
        render(<PreferencesPanel onClose={() => {}} />);
        expect(await screen.findByRole('alert')).toHaveTextContent('Offline');
        expect(screen.queryByRole('button', { name: 'Save preferences' })).not.toBeInTheDocument();
        expect(savePreferences).not.toHaveBeenCalled();
    });
    it('keeps edits visible after version conflict', async () => {
        const user = userEvent.setup(); savePreferences.mockRejectedValue({ response: { status: 409 } });
        render(<PreferencesPanel onClose={() => {}} />);
        await user.type(await screen.findByLabelText('How should Ullavox help you?'), 'One step at a time');
        await user.click(screen.getByRole('button', { name: 'Save preferences' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Reload and retry');
        expect(screen.getByLabelText('How should Ullavox help you?')).toHaveValue('One step at a time');
    });
});
describe('project memory', () => {
    it('saves exact persisted provenance from a completed message with edited content', async () => {
        const user = userEvent.setup(); saveMemory.mockResolvedValue({ id: 'note-1' });
        render(<MemoryPanel projectId="p1" conversationId="c1" sourceMessage={{ id: 73, text: 'Use SQLite' }} onClose={() => {}} onSelectionChange={() => {}} />);
        await user.type(screen.getByLabelText('Note title'), 'Storage decision');
        await user.selectOptions(screen.getByLabelText('Kind'), 'decision');
        await user.click(screen.getByRole('button', { name: 'Save note' }));
        await waitFor(() => expect(saveMemory).toHaveBeenCalledWith('p1', expect.objectContaining({ sourceMessageId: 73, sourceConversationId: 'c1', title: 'Storage decision', kind: 'decision', content: 'Use SQLite' })));
    });
    it('preserves notes without sources, supports selection/pin/delete with version', async () => {
        const user = userEvent.setup(); const selection = vi.fn();
        const note = { id: 'n1', version: 4, title: 'Offline', content: 'Keep local', kind: 'constraint', origin: 'message', pinned: false };
        listMemories.mockResolvedValue([note]); vi.spyOn(window, 'confirm').mockReturnValue(true);
        render(<MemoryPanel projectId="p1" selectedIds={[]} onClose={() => {}} onSelectionChange={selection} />);
        expect(await screen.findByText(/Source unavailable/)).toBeInTheDocument();
        await user.click(screen.getByLabelText('Use for next message')); expect(selection).toHaveBeenCalledWith(['n1']);
        await user.click(screen.getByRole('button', { name: 'Pin' }));
        await waitFor(() => expect(saveMemory).toHaveBeenCalledWith('p1', { ...note, pinned: true }));
        await user.click(screen.getByRole('button', { name: 'Delete note' }));
        await waitFor(() => expect(deleteMemory).toHaveBeenCalledWith('p1', 'n1', 4));
    });
    it('creates projects and explains delete impact before preserving transcripts', async () => {
        const user = userEvent.setup(); const saved = vi.fn();
        render(<ProjectEditor project={{ id: 'p1', version: 8, name: 'Ullavox', description: '', instructions: '' }} chatCount={3} onSaved={saved} onClose={() => {}} />);
        await user.type(screen.getByLabelText('Project instructions'), 'Work offline');
        await user.click(screen.getByRole('button', { name: 'Save project' }));
        await waitFor(() => expect(saveProject).toHaveBeenCalledWith(expect.objectContaining({ id: 'p1', version: 8, instructions: 'Work offline' })));
        vi.spyOn(window, 'confirm').mockReturnValue(true);
        await user.click(screen.getByRole('button', { name: 'Delete project' }));
        await waitFor(() => expect(deleteProject).toHaveBeenCalledWith('p1', 8));
        expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('3 chats will become standalone; transcripts are preserved'));
    });
});
it('shows execution metadata without presenting current settings as historical snapshots', () => {
    render(<ContextUsed context={{ included: [{ type: 'preferences', name: 'Response length', version: 2 }], omitted: [{ type: 'memory', reason: 'budget' }], estimatedTokens: 30 }} />);
    expect(screen.getByText('Response length (v2)')).toBeInTheDocument(); expect(screen.getByText('Saved note: context budget')).toBeInTheDocument();
});

it('explains an empty saved-note search and restores results when the query is cleared', async () => {
    const user = userEvent.setup();
    listMemories.mockResolvedValue([{ id: 'n1', title: 'Project decision', content: 'Keep SQLite', kind: 'decision' }]);
    render(<MemoryPanel projectId="p1" onClose={() => {}} onSelectionChange={() => {}} />);
    expect(await screen.findByRole('heading', { name: 'Project decision' })).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Project notes' })).toBeInTheDocument();
    await user.type(screen.getByLabelText('Search saved notes'), 'unmatched');
    expect(screen.getByRole('status')).toHaveTextContent('No matching notes. Try a different search.');
    expect(screen.queryByRole('heading', { name: 'Project decision' })).not.toBeInTheDocument();
    await user.clear(screen.getByLabelText('Search saved notes'));
    expect(screen.getByRole('heading', { name: 'Project decision' })).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
});

it('refreshes preserved saved notes after their source chat is reset', async () => {
    const user = userEvent.setup();
    const note = { id: 'n1', version: 1, title: 'Keep decision', content: 'SQLite', kind: 'decision', origin: 'message', sourceMessageId: 5, sourceConversationId: 'c1' };
    listMemories.mockResolvedValueOnce([note]).mockResolvedValue([{ ...note, sourceMessageId: null, sourceConversationId: null }]);
    render(<MemoryPanel projectId="p1" onClose={() => {}} onSelectionChange={() => {}} />);
    expect(await screen.findByText(/From a saved message/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Refresh notes' }));
    expect(await screen.findByText(/Source unavailable/)).toBeInTheDocument();
    expect(screen.getByText('Keep decision')).toBeInTheDocument();
    expect(screen.getByText('SQLite')).toBeInTheDocument();
});

it('does not leave a new note creation form available to duplicate a committed save after refresh fails', async () => {
    const user = userEvent.setup();
    listMemories.mockResolvedValueOnce([]).mockRejectedValue(new Error('refresh offline'));
    saveMemory.mockResolvedValue({ id: 'created-note', version: 0 });
    render(<MemoryPanel projectId="p1" onClose={() => {}} onSelectionChange={() => {}} />);
    await user.click(screen.getByRole('button', { name: 'Add note' }));
    await user.type(screen.getByLabelText('Note title'), 'Keep'); await user.type(screen.getByLabelText('Content'), 'Local');
    await user.click(screen.getByRole('button', { name: 'Save note' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Change saved. Could not refresh notes');
    expect(screen.queryByRole('button', { name: 'Save note' })).not.toBeInTheDocument();
    expect(saveMemory).toHaveBeenCalledTimes(1);
});

it('retains a newly assigned project ID when save succeeds but refreshing navigation fails', async () => {
    const user = userEvent.setup(); const onSaved = vi.fn().mockRejectedValue(new Error('refresh offline'));
    saveProject.mockResolvedValue({ id: 'created-project', version: 0, name: 'New', description: '', instructions: '' });
    render(<ProjectEditor onSaved={onSaved} onClose={() => {}} />);
    await user.type(screen.getByLabelText('Project name'), 'New');
    await user.click(screen.getByRole('button', { name: 'Save project' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Change saved. Could not refresh projects');
    await user.click(screen.getByRole('button', { name: 'Save project' }));
    await waitFor(() => expect(saveProject).toHaveBeenLastCalledWith(expect.objectContaining({ id: 'created-project', version: 0 })));
});

it('uses backend field bounds and requires shortening an overlong source message', async () => {
    const user = userEvent.setup();
    const { unmount } = render(<PreferencesPanel onClose={() => {}} />);
    expect(await screen.findByLabelText('Response length')).toHaveAttribute('maxlength', '120');
    expect(screen.getByLabelText('Background (optional)')).toHaveAttribute('maxlength', '1000');
    expect(screen.getByLabelText('How should Ullavox help you?')).toHaveAttribute('maxlength', '2000');
    unmount();
    render(<MemoryPanel projectId="p1" conversationId="c1" sourceMessage={{ id: 8, text: 'x'.repeat(2001) }} onClose={() => {}} onSelectionChange={() => {}} />);
    expect(screen.getByRole('button', { name: 'Save note' })).toBeDisabled();
    expect(screen.getByText(/shorten this message/)).toBeInTheDocument();
    await user.clear(screen.getByLabelText('Content')); await user.type(screen.getByLabelText('Content'), 'Short decision');
    expect(screen.getByRole('button', { name: 'Save note' })).toBeEnabled();
});
