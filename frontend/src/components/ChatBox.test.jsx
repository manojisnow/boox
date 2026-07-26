import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ChatBox from './ChatBox';
import { sendMessage, streamMessage, getModels, getConversationMessages } from '../services/api';

vi.mock('../services/api', () => ({
    sendMessage: vi.fn(),
    streamMessage: vi.fn(),
    getModels: vi.fn(),
    getConversationMessages: vi.fn(),
}));

const baseProps = {
    conversationId: 'conv-1',
    initialModel: 'qwen2.5:14b',
    onConversationChanged: vi.fn(),
    selectedServer: 'ollama',
    setSelectedServer: vi.fn(),
    servers: ['ollama'],
};

beforeEach(() => {
    vi.clearAllMocks();
    getConversationMessages.mockResolvedValue([]);
    getModels.mockResolvedValue([
        { name: 'qwen2.5:14b', capabilities: [] },
    ]);
});

// Restores the generation settings panel's toggle button.
const openGenerationSettings = async (user) => {
    const toggle = await screen.findByRole('button', { name: /toggle generation settings/i });
    await user.click(toggle);
};

describe('ChatBox generation config restore', () => {
    it('restores fields once initialGenerationConfig arrives asynchronously, without clobbering later user edits', async () => {
        const user = userEvent.setup();
        // Mirrors App.jsx: the conversation list (and its saved config) is still
        // null on ChatBox's first mount because it loads asynchronously in the parent.
        const { rerender } = render(
            <ChatBox {...baseProps} initialGenerationConfig={null} />
        );

        await openGenerationSettings(user);
        expect(screen.getByLabelText(/temperature/i)).toHaveValue(null);

        // Parent's conversations list finishes loading — a fresh object arrives.
        rerender(
            <ChatBox
                {...baseProps}
                initialGenerationConfig={{ temperature: 0.5, numCtx: 2048, stopSequences: ['END'] }}
            />
        );

        await waitFor(() => expect(screen.getByLabelText(/temperature/i)).toHaveValue(0.5));
        expect(screen.getByLabelText(/context size/i)).toHaveValue(2048);
        expect(screen.getByLabelText(/stop sequences/i)).toHaveValue('END');

        // User edits temperature themselves.
        const temperatureInput = screen.getByLabelText(/temperature/i);
        await user.clear(temperatureInput);
        await user.type(temperatureInput, '0.9');
        expect(temperatureInput).toHaveValue(0.9);

        // Parent re-renders again (e.g. after a send refreshes the sidebar) with a
        // brand-new object reference carrying the same saved values. This must NOT
        // clobber the user's in-progress edit.
        rerender(
            <ChatBox
                {...baseProps}
                initialGenerationConfig={{ temperature: 0.5, numCtx: 2048, stopSequences: ['END'] }}
            />
        );

        expect(screen.getByLabelText(/temperature/i)).toHaveValue(0.9);
    });
});

describe('ChatBox send with generation config', () => {
    it('sends the current temperature/numCtx/stopSequences fields with the message', async () => {
        const user = userEvent.setup();
        streamMessage.mockResolvedValue({
            body: { getReader: () => ({ read: () => Promise.resolve({ done: true, value: undefined }) }) },
        });

        render(
            <ChatBox
                {...baseProps}
                initialGenerationConfig={{ temperature: 0.1, numCtx: null, stopSequences: ['4'] }}
            />
        );

        await waitFor(() => expect(getModels).toHaveBeenCalled());

        const textbox = screen.getByPlaceholderText('Message...');
        await user.type(textbox, 'Count from 1 to 10.');
        await user.click(screen.getByRole('button', { name: /send/i }));

        await waitFor(() => expect(streamMessage).toHaveBeenCalledTimes(1));
        const [, , , , , , generationConfig] = streamMessage.mock.calls[0];
        expect(generationConfig).toEqual({
            temperature: 0.1,
            numCtx: null,
            stopSequences: ['4'],
        });
    });

    it('sends null temperature/numCtx and an empty stopSequences array when settings are untouched', async () => {
        const user = userEvent.setup();
        sendMessage.mockResolvedValue({ content: 'hi', role: 'assistant' });

        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        // Turn off streaming so the non-streaming sendMessage path is exercised.
        await user.click(screen.getByRole('checkbox'));

        const textbox = screen.getByPlaceholderText('Message...');
        await user.type(textbox, 'hello');
        await user.click(screen.getByRole('button', { name: /send/i }));

        await waitFor(() => expect(sendMessage).toHaveBeenCalledTimes(1));
        const [, , , , , , , generationConfig] = sendMessage.mock.calls[0];
        expect(generationConfig).toEqual({
            temperature: null,
            numCtx: null,
            stopSequences: [],
        });
    });
});
