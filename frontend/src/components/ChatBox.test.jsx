import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ChatBox from './ChatBox';
import { sendMessage, streamMessage, getModels, getConversationMessages, listAgentRuns, createAgentRun, getAgentRun, cancelAgentRun, getConversationSettings, saveConversationSettings, getConversationExecution } from '../services/api';

vi.mock('../services/api', () => ({
    getConversationSettings: vi.fn(), saveConversationSettings: vi.fn(), getConversationExecution: vi.fn(),
    apiError: error => error.message,
    sendMessage: vi.fn(),
    streamMessage: vi.fn(),
    getModels: vi.fn(),
    getConversationMessages: vi.fn(),
    listAgentRuns: vi.fn(), createAgentRun: vi.fn(), getAgentRun: vi.fn(), cancelAgentRun: vi.fn(),
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
    sessionStorage.clear();
    getConversationSettings.mockResolvedValue({ version: 0, usePreferences: true, useProjectContext: true, systemPrompt: '' });
    saveConversationSettings.mockImplementation(async (id, settings) => ({ ...settings, version: settings.version + 1 }));
    getConversationExecution.mockResolvedValue({ state: 'idle' });
    getConversationMessages.mockResolvedValue([]);
    listAgentRuns.mockResolvedValue([]);
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
    it.each([true, false])('sends cleared saved settings with streaming=%s', async (streaming) => {
        const user = userEvent.setup();
        sendMessage.mockResolvedValue({ content: 'hi', role: 'assistant' });
        streamMessage.mockResolvedValue({
            body: { getReader: () => ({ read: () => Promise.resolve({ done: true }) }) },
        });
        render(
            <ChatBox
                {...baseProps}
                initialGenerationConfig={{ temperature: 0.5, numCtx: 4096, stopSequences: ['END'] }}
            />
        );
        await openGenerationSettings(user);
        await user.clear(screen.getByLabelText(/temperature/i));
        await user.clear(screen.getByLabelText(/context size/i));
        await user.clear(screen.getByLabelText(/stop sequences/i));
        if (!streaming) await user.click(screen.getByRole('checkbox', { name: 'Stream response' }));
        await user.type(screen.getByPlaceholderText('Message...'), 'Use the defaults');
        await user.click(screen.getByRole('button', { name: /send/i }));

        const send = streaming ? streamMessage : sendMessage;
        await waitFor(() => expect(send).toHaveBeenCalledTimes(1));
        expect(send.mock.calls[0][streaming ? 6 : 7]).toEqual({
            temperature: null,
            numCtx: null,
            stopSequences: [],
        });
    });

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
        await user.click(screen.getByRole('checkbox', { name: 'Stream response' }));

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

// Builds a fake fetch Response whose body reader yields one SSE chunk per
// array entry, then signals done. Each chunk should be a complete "event:"/
// "data:" frame ending in a blank line, matching what the backend sends.
const makeStreamResponse = (chunks) => {
    const encoder = new TextEncoder();
    let i = 0;
    return {
        body: {
            getReader: () => ({
                read: () => {
                    if (i < chunks.length) {
                        const value = encoder.encode(chunks[i]);
                        i += 1;
                        return Promise.resolve({ done: false, value });
                    }
                    return Promise.resolve({ done: true, value: undefined });
                },
            }),
        },
    };
};

describe('ChatBox conversation history', () => {
    it('loads and renders persisted history on mount, converting stored images to data URLs', async () => {
        getConversationMessages.mockResolvedValue([
            { role: 'user', content: 'hi', images: [] },
            { role: 'assistant', content: 'hello back', images: ['aGVsbG8='] },
        ]);
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);

        expect(await screen.findByText('hi')).toBeInTheDocument();
        expect(screen.getByText('hello back')).toBeInTheDocument();
        expect(screen.getByAltText('Attachment 1')).toHaveAttribute(
            'src',
            'data:image/png;base64,aGVsbG8='
        );
    });

    it('shows the greeting placeholder and logs an error when history fails to load', async () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
        getConversationMessages.mockRejectedValue(new Error('history down'));
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);

        await waitFor(() =>
            expect(consoleError).toHaveBeenCalledWith(
                'Failed to load conversation history:',
                'history down'
            )
        );
        expect(screen.getByText('Boox')).toBeInTheDocument();
    });
});

describe('ChatBox model selection', () => {
    it('prefers the conversation model over installed defaults', async () => {
        getModels.mockResolvedValue([
            { name: 'granite4:3b', capabilities: ['tools'] },
            { name: 'phi4-mini:latest', capabilities: [] },
            { name: 'qwen2.5:14b', capabilities: [] },
        ]);
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        expect(await screen.findByText('Chat with qwen2.5')).toBeInTheDocument();
    });

    it('falls back to phi4-mini:latest when initialModel is not in the list', async () => {
        getModels.mockResolvedValue([
            { name: 'phi4-mini:latest', capabilities: [] },
            { name: 'llama3', capabilities: [] },
        ]);
        render(<ChatBox {...baseProps} initialModel="not-installed" initialGenerationConfig={null} />);
        expect(await screen.findByText('Chat with phi4-mini')).toBeInTheDocument();
    });

    it('prefers the release default for a new conversation', async () => {
        getModels.mockResolvedValue([
            { name: 'phi4-mini:latest', capabilities: [] },
            { name: 'granite4:3b', capabilities: ['tools'] },
        ]);
        render(<ChatBox {...baseProps} initialModel="" initialGenerationConfig={null} />);
        expect(await screen.findByText('Chat with granite4')).toBeInTheDocument();
    });

    it('selects an installed model when neither default is available', async () => {
        getModels.mockResolvedValue([{ name: 'llama3', capabilities: [] }]);
        render(<ChatBox {...baseProps} initialModel="" initialGenerationConfig={null} />);
        expect(await screen.findByText('Chat with llama3')).toBeInTheDocument();
    });

    it('shows an error toast when fetching models fails', async () => {
        getModels.mockRejectedValue(new Error('down'));
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Failed to fetch models. Please try again.'
        );
    });
});

describe('ChatBox image attachments', () => {
    const visionProps = {
        ...baseProps,
        initialGenerationConfig: null,
    };

    beforeEach(() => {
        getModels.mockResolvedValue([{ name: 'qwen2.5:14b', capabilities: ['vision'] }]);
    });

    it('attaches a valid image and shows a thumbnail', async () => {
        const user = userEvent.setup();
        render(<ChatBox {...visionProps} />);
        const attachButton = await screen.findByLabelText('Attach image');
        await user.click(attachButton);

        const file = new File(['x'.repeat(10)], 'photo.png', { type: 'image/png' });
        const fileInput = document.querySelector('input[type="file"]');
        await user.upload(fileInput, file);

        expect(await screen.findByAltText('Attachment 1')).toBeInTheDocument();
    });

    it('rejects a non-image file with an error toast', async () => {
        render(<ChatBox {...visionProps} />);
        await screen.findByLabelText('Attach image');

        // user.upload() filters files against the input's accept="image/*" the
        // way a real file picker would, so a mismatched type never reaches
        // onChange - fireEvent bypasses that to actually exercise the app's
        // own type check in handleFileSelect.
        const file = new File(['not an image'], 'notes.txt', { type: 'text/plain' });
        const fileInput = document.querySelector('input[type="file"]');
        fireEvent.change(fileInput, { target: { files: [file] } });

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Only image files can be attached.'
        );
    });

    it('rejects an oversized image with an error toast', async () => {
        const user = userEvent.setup();
        render(<ChatBox {...visionProps} />);
        await screen.findByLabelText('Attach image');

        const bigFile = new File(['x'.repeat(10)], 'huge.png', { type: 'image/png' });
        Object.defineProperty(bigFile, 'size', { value: 8_000_000 });
        const fileInput = document.querySelector('input[type="file"]');
        await user.upload(fileInput, bigFile);

        expect(await screen.findByRole('alert')).toHaveTextContent(/too large/);
    });

    it('removes an attached image via its remove button', async () => {
        const user = userEvent.setup();
        render(<ChatBox {...visionProps} />);
        await screen.findByLabelText('Attach image');

        const file = new File(['x'.repeat(10)], 'photo.png', { type: 'image/png' });
        const fileInput = document.querySelector('input[type="file"]');
        await user.upload(fileInput, file);
        await screen.findByAltText('Attachment 1');

        await user.click(screen.getByLabelText('Remove image'));
        expect(screen.queryByAltText('Attachment 1')).not.toBeInTheDocument();
    });

    it('does not show the attach button for a model without vision support', async () => {
        getModels.mockResolvedValue([{ name: 'qwen2.5:14b', capabilities: [] }]);
        render(<ChatBox {...visionProps} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());
        expect(screen.queryByLabelText('Attach image')).not.toBeInTheDocument();
    });
});

describe('ChatBox SSE streaming', () => {
    it('appends plain text tokens as they stream in', async () => {
        const user = userEvent.setup();
        streamMessage.mockResolvedValue(
            makeStreamResponse(['data:Hello\n\n', 'data: world\n\n', 'data:[DONE]\n\n'])
        );
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        await user.type(screen.getByPlaceholderText('Message...'), 'hi');
        await user.click(screen.getByRole('button', { name: /send/i }));

        await waitFor(() => expect(screen.getByText('Hello world')).toBeInTheDocument());
    });

    it('renders a tool_call then updates it to done on tool_result, matched by index', async () => {
        const user = userEvent.setup();
        const toolCall = JSON.stringify({ index: 0, name: 'web_search', arguments: { query: 'cats' } });
        const toolResult = JSON.stringify({ index: 0, result: 'Cats are mammals.' });
        streamMessage.mockResolvedValue(
            makeStreamResponse([
                `event:tool_call\ndata:${toolCall}\n\n`,
                `event:tool_result\ndata:${toolResult}\n\n`,
                'data:[DONE]\n\n',
            ])
        );
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        await user.type(screen.getByPlaceholderText('Message...'), 'search cats');
        await user.click(screen.getByRole('button', { name: /send/i }));

        expect(await screen.findByText('Web Search complete')).toBeInTheDocument();
    });

    it('ignores malformed tool_call JSON without crashing', async () => {
        const user = userEvent.setup();
        const consoleWarn = vi.spyOn(console, 'warn').mockImplementation(() => {});
        streamMessage.mockResolvedValue(
            makeStreamResponse(['event:tool_call\ndata:{not valid json\n\n', 'data:[DONE]\n\n'])
        );
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        await user.type(screen.getByPlaceholderText('Message...'), 'hi');
        await user.click(screen.getByRole('button', { name: /send/i }));

        await waitFor(() => expect(consoleWarn).toHaveBeenCalled());
    });

    it('shows an error toast when the stream request itself fails', async () => {
        const user = userEvent.setup();
        streamMessage.mockRejectedValue(new Error('network down'));
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        await user.type(screen.getByPlaceholderText('Message...'), 'hi');
        await user.click(screen.getByRole('button', { name: /send/i }));

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Failed to stream message. Please try again.'
        );
    });
});

describe('ChatBox non-streaming send errors', () => {
    it('shows an error toast when the non-streaming send fails', async () => {
        const user = userEvent.setup();
        sendMessage.mockRejectedValue(new Error('down'));
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        await user.click(screen.getByRole('checkbox', { name: 'Stream response' }));
        await user.type(screen.getByPlaceholderText('Message...'), 'hi');
        await user.click(screen.getByRole('button', { name: /send/i }));

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Failed to send message. Please try again.'
        );
    });
});

describe('ChatBox keyboard handling', () => {
    it('sends on Enter without Shift', async () => {
        const user = userEvent.setup();
        streamMessage.mockResolvedValue(makeStreamResponse(['data:[DONE]\n\n']));
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        const textbox = screen.getByPlaceholderText('Message...');
        await user.type(textbox, 'hi{Enter}');

        await waitFor(() => expect(streamMessage).toHaveBeenCalledTimes(1));
    });

    it('does not send on Shift+Enter', async () => {
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        const textbox = screen.getByPlaceholderText('Message...');
        await user.type(textbox, 'hi{Shift>}{Enter}{/Shift}');

        expect(streamMessage).not.toHaveBeenCalled();
    });
});

describe('ChatBox system prompt', () => {
    it('restores only this conversation’s persisted system prompt', async () => {
        sessionStorage.setItem('boox-system-prompt', 'Ignore global prompt');
        getConversationSettings.mockResolvedValue({ version: 2, systemPrompt: 'Be a pirate', usePreferences: true, useProjectContext: true });
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        await user.click(screen.getByLabelText('Toggle system prompt'));
        expect(screen.getByPlaceholderText(/Set system behavior/)).toHaveValue('Be a pirate');
    });

    it('persists edits to sessionStorage as the user types', async () => {
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        await user.click(screen.getByLabelText('Toggle system prompt'));
        const textarea = screen.getByPlaceholderText(/Set system behavior/);
        await user.type(textarea, 'Be nice');

        expect(sessionStorage.getItem('boox-system-prompt')).toBeNull();
    });
});

describe('ChatBox error toast', () => {
    it('can be dismissed manually', async () => {
        const user = userEvent.setup();
        getModels.mockRejectedValue(new Error('down'));
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await screen.findByRole('alert');

        await user.click(screen.getByLabelText('Dismiss'));
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('auto-dismisses after 5 seconds', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        getModels.mockRejectedValue(new Error('down'));
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);

        await vi.waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
        vi.advanceTimersByTime(5000);
        await vi.waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
        vi.useRealTimers();
    });
});

describe('ChatBox send button state', () => {
    it('is disabled until a server, a model, and non-blank input are all present', async () => {
        render(<ChatBox {...baseProps} selectedServer="" initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).not.toHaveBeenCalled());
        expect(screen.getByRole('button', { name: /send/i })).toBeDisabled();
    });

    it('enables once server, model, and input are all set', async () => {
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());
        await user.type(screen.getByPlaceholderText('Message...'), 'hi');
        expect(screen.getByRole('button', { name: /send/i })).toBeEnabled();
    });
});

describe('ChatBox Enter-key guard clauses', () => {
    // The Enter-to-send keydown handler calls handleSendMessage() directly,
    // bypassing the submit button's disabled attribute - these guards are the
    // only thing stopping a send with no server/model chosen in that path.
    it('shows an error and does not send when no server is selected', async () => {
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} selectedServer="" initialGenerationConfig={null} />);

        await user.type(screen.getByPlaceholderText('Message...'), 'hi{Enter}');

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Please select a server before sending a message.'
        );
        expect(streamMessage).not.toHaveBeenCalled();
    });

    it('shows an error and does not send when no model is available', async () => {
        getModels.mockResolvedValue([]);
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        await user.type(screen.getByPlaceholderText('Message...'), 'hi{Enter}');

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Please select a model before sending a message.'
        );
        expect(streamMessage).not.toHaveBeenCalled();
    });
});

describe('ChatBox sending with an attached image', () => {
    it('includes the attached image in both the visible message and the outgoing request', async () => {
        getModels.mockResolvedValue([{ name: 'qwen2.5:14b', capabilities: ['vision'] }]);
        streamMessage.mockResolvedValue(makeStreamResponse(['data:[DONE]\n\n']));
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await screen.findByLabelText('Attach image');

        const file = new File(['x'.repeat(10)], 'photo.png', { type: 'image/png' });
        const fileInput = document.querySelector('input[type="file"]');
        await user.upload(fileInput, file);
        await screen.findByAltText('Attachment 1');

        await user.type(screen.getByPlaceholderText('Message...'), 'look at this');
        await user.click(screen.getByRole('button', { name: /send/i }));

        await waitFor(() => expect(streamMessage).toHaveBeenCalledTimes(1));
        const [, , , , , images] = streamMessage.mock.calls[0];
        expect(images).toHaveLength(1);
        // The outgoing wire payload is raw base64, no data: URL prefix - that
        // prefix is only used for the <img src> shown in the message list.
        expect(images[0]).not.toMatch(/^data:/);
        expect(images[0]).toBe('eHh4eHh4eHh4eA==');
    });
});

describe('ChatBox image attachment limit', () => {
    it('refuses a 5th image once 4 are already attached', async () => {
        getModels.mockResolvedValue([{ name: 'qwen2.5:14b', capabilities: ['vision'] }]);
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await screen.findByLabelText('Attach image');
        const fileInput = document.querySelector('input[type="file"]');

        for (let i = 1; i <= 4; i += 1) {
            const file = new File(['x'.repeat(10)], `photo${i}.png`, { type: 'image/png' });
            await user.upload(fileInput, file);
            await screen.findByAltText(`Attachment ${i}`);
        }

        const fifth = new File(['x'.repeat(10)], 'photo5.png', { type: 'image/png' });
        fireEvent.change(fileInput, { target: { files: [fifth] } });

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'You can attach up to 4 images.'
        );
    });
});

describe('ChatBox malformed tool_result', () => {
    it('ignores malformed tool_result JSON without crashing', async () => {
        const consoleWarn = vi.spyOn(console, 'warn').mockImplementation(() => {});
        const user = userEvent.setup();
        streamMessage.mockResolvedValue(
            makeStreamResponse(['event:tool_result\ndata:{not valid json\n\n', 'data:[DONE]\n\n'])
        );
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        await user.type(screen.getByPlaceholderText('Message...'), 'hi');
        await user.click(screen.getByRole('button', { name: /send/i }));

        await waitFor(() =>
            expect(consoleWarn).toHaveBeenCalledWith(
                'Malformed tool_result SSE payload:',
                expect.any(String)
            )
        );
    });
});

describe('ChatBox input change clears a prior error', () => {
    it('clears the error toast as soon as the user starts typing again', async () => {
        getModels.mockRejectedValue(new Error('down'));
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await screen.findByRole('alert');

        await user.type(screen.getByPlaceholderText('Message...'), 'h');

        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
});

describe('ChatBox server and model dropdowns', () => {
    it('calls setSelectedServer when a different server is chosen', async () => {
        const setSelectedServer = vi.fn();
        const user = userEvent.setup();
        render(
            <ChatBox
                {...baseProps}
                servers={['ollama', 'other']}
                setSelectedServer={setSelectedServer}
                initialGenerationConfig={null}
            />
        );
        await waitFor(() => expect(getModels).toHaveBeenCalled());

        await user.selectOptions(screen.getAllByRole('combobox')[0], 'other');

        expect(setSelectedServer).toHaveBeenCalledWith('other');
    });

    it('switching to a non-vision model drops any attached images and hides the attach button', async () => {
        getModels.mockResolvedValue([
            { name: 'vision-model', capabilities: ['vision'] },
            { name: 'text-model', capabilities: [] },
        ]);
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} initialModel="vision-model" initialGenerationConfig={null} />);
        await screen.findByLabelText('Attach image');

        const file = new File(['x'.repeat(10)], 'photo.png', { type: 'image/png' });
        const fileInput = document.querySelector('input[type="file"]');
        await user.upload(fileInput, file);
        await screen.findByAltText('Attachment 1');

        const modelSelect = screen.getAllByRole('combobox')[1];
        await user.selectOptions(modelSelect, 'text-model');

        await waitFor(() => expect(screen.queryByLabelText('Attach image')).not.toBeInTheDocument());
        expect(screen.queryByAltText('Attachment 1')).not.toBeInTheDocument();
    });
});

describe('ChatBox generation settings inputs', () => {
    it('updates context size and stop sequences as the user types', async () => {
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
        await openGenerationSettings(user);

        await user.type(screen.getByLabelText(/context size/i), '4096');
        await user.type(screen.getByLabelText(/stop sequences/i), 'END, ###');

        expect(screen.getByLabelText(/context size/i)).toHaveValue(4096);
        expect(screen.getByLabelText(/stop sequences/i)).toHaveValue('END, ###');
    });
});

describe('Agent tasks', () => {
    it('refreshes history after restoring completion even when the initial history snapshot is stale', async () => {
        let resolveHistory;
        getConversationMessages.mockImplementationOnce(() => new Promise(resolve => { resolveHistory = resolve; }))
            .mockResolvedValue([{ role: 'user', content: 'Explain the total' }, { role: 'assistant', content: 'Committed answer' }]);
        listAgentRuns.mockResolvedValue([{ id: 'restored-complete', status: 'COMPLETED', steps: [] }]);
        render(<ChatBox {...baseProps} />);
        await waitFor(() => expect(listAgentRuns).toHaveBeenCalledTimes(1));
        await act(async () => resolveHistory([{ role: 'user', content: 'Explain the total' }]));
        await screen.findByText('Committed answer');
        expect(getConversationMessages).toHaveBeenCalledTimes(2);
    });

    it('preserves Ask history and retries initial task-status failures before enabling sending', async () => {
        getConversationMessages.mockResolvedValue([{ role: 'assistant', content: 'Saved normal chat' }]);
        listAgentRuns.mockRejectedValueOnce(new Error('temporary status failure')).mockResolvedValue([]);
        render(<ChatBox {...baseProps} />);
        await screen.findByText('Saved normal chat');
        await screen.findByText(/Could not restore task status/);
        expect(screen.getByRole('button', { name: 'Agent', exact: true })).toBeDisabled();
        await waitFor(() => expect(screen.getByRole('button', { name: 'Agent', exact: true })).toBeEnabled(), { timeout: 2000 });
        expect(listAgentRuns).toHaveBeenCalledTimes(2);
        expect(screen.queryByText(/Could not restore task status/)).not.toBeInTheDocument();
        expect(screen.getByText('Saved normal chat')).toBeInTheDocument();
    });

    it('retries terminal history failures rather than marking an unseen answer refreshed', async () => {
        getConversationMessages.mockResolvedValueOnce([{ role: 'user', content: 'Explain the total' }])
            .mockRejectedValueOnce(new Error('temporary history failure'))
            .mockResolvedValue([{ role: 'assistant', content: 'Recovered answer' }]);
        listAgentRuns.mockResolvedValue([{ id: 'retry-history', status: 'COMPLETED', steps: [] }]);
        render(<ChatBox {...baseProps} />);
        await screen.findByText(/Could not refresh conversation history/);
        expect(screen.getByRole('button', { name: 'Ask', exact: true })).toBeDisabled();
        await screen.findByText('Recovered answer', {}, { timeout: 2000 });
        expect(screen.getByRole('button', { name: 'Ask', exact: true })).toBeEnabled();
        expect(getConversationMessages).toHaveBeenCalledTimes(3);
    });

    it('submits settings and selected text files and restores Ask after completion', async () => {
        const user = userEvent.setup();
        createAgentRun.mockResolvedValue({ id: 'run-1', status: 'RUNNING', steps: [] });
        getAgentRun.mockResolvedValue({ id: 'run-1', status: 'COMPLETED', finalOutput: 'The total is 12', steps: [] });
        render(<ChatBox {...baseProps} />);
        await user.click(screen.getByRole('button', { name: 'Agent', exact: true }));
        const fileInput = document.querySelector('input[accept*=".txt"]');
        await user.upload(fileInput, new File(['name,value\na,5\nb,7'], 'data.csv', { type: 'text/csv' }));
        await screen.findByRole('button', { name: 'Remove file data.csv' });
        expect(screen.getByRole('button', { name: 'Ask', exact: true })).toBeDisabled();
        expect(screen.getByRole('checkbox', { name: 'Stream response' })).toBeDisabled();
        await user.type(screen.getByPlaceholderText('Message...'), 'Calculate total');
        await user.click(screen.getByRole('button', { name: 'Send', exact: true }));
        await waitFor(() => expect(createAgentRun).toHaveBeenCalledWith('Calculate total', 'conv-1', 'ollama', 'qwen2.5:14b', '', [], { temperature: null, numCtx: null, stopSequences: [] }, [{ name: 'data.csv', content: 'name,value\na,5\nb,7' }], false, false, []));
        getConversationMessages.mockResolvedValue([{ role: 'user', content: 'Calculate total' }, { role: 'assistant', content: 'The total is 12' }]);
        await screen.findByText('The total is 12');
        await waitFor(() => expect(screen.getByRole('button', { name: 'Ask', exact: true })).toBeEnabled());
        await user.click(screen.getByRole('button', { name: 'Ask', exact: true }));
    });

    it('offers visible web and mixed scopes without requiring tool names', async () => {
        const user = userEvent.setup();
        createAgentRun.mockResolvedValue({ id: 'mixed', status: 'RUNNING', steps: [] });
        render(<ChatBox {...baseProps} />);
        await user.click(screen.getByRole('button', { name: 'Agent', exact: true }));
        expect(screen.getByRole('checkbox', { name: 'Include web sources' })).toBeChecked();
        await user.upload(document.querySelector('input[accept*=".txt"]'), new File(['amount\n7'], 'data.csv', { type: 'text/csv' }));
        await screen.findByRole('button', { name: 'Remove file data.csv' });
        expect(screen.getByRole('checkbox', { name: 'Include web sources' })).not.toBeChecked();
        await user.click(screen.getByRole('checkbox', { name: 'Include web sources' }));
        expect(screen.getByText(/Scope: Files \+ Web/)).toBeInTheDocument();
        await user.type(screen.getByPlaceholderText('Message...'), 'Compare these figures with published figures');
        await user.click(screen.getByRole('button', { name: 'Send', exact: true }));
        await waitFor(() => expect(createAgentRun).toHaveBeenCalledWith(expect.any(String), 'conv-1', 'ollama', 'qwen2.5:14b', '', [], expect.any(Object), expect.any(Array), true, false, []));
    });

    it('restores retained files and scope, submits a follow-up, and supports clearing evidence', async () => {
        const user = userEvent.setup();
        listAgentRuns.mockResolvedValue([{ id: 'previous', status: 'COMPLETED', steps: [], attachedFileNames: ['totals.csv'], allowWebSearch: false }]);
        createAgentRun.mockResolvedValue({ id: 'follow-up', status: 'RUNNING', steps: [], attachedFileNames: [], allowWebSearch: false });
        render(<ChatBox {...baseProps} />);
        await user.click(screen.getByRole('button', { name: 'Agent', exact: true }));
        await screen.findByText(/Available for follow-ups: totals.csv/);
        expect(screen.getByRole('button', { name: 'Agent', exact: true })).toHaveAttribute('aria-pressed', 'true');
        expect(screen.getByRole('checkbox', { name: 'Include web sources' })).not.toBeChecked();
        await user.click(screen.getByRole('button', { name: 'Clear retained files' }));
        await user.type(screen.getByPlaceholderText('Message...'), 'Explain that calculation');
        await user.click(screen.getByRole('button', { name: 'Send', exact: true }));
        await waitFor(() => expect(createAgentRun).toHaveBeenCalledWith('Explain that calculation', 'conv-1', 'ollama', 'qwen2.5:14b', '', [], expect.any(Object), [], false, true, []));
    });

    it('restores an active run after reload and cancels it', async () => {
        const user = userEvent.setup();
        listAgentRuns.mockResolvedValue([{ id: 'resumed', status: 'RUNNING', steps: [] }]);
        cancelAgentRun.mockResolvedValue({ id: 'resumed', status: 'CANCEL_REQUESTED', steps: [] });
        getAgentRun.mockResolvedValue({ id: 'resumed', status: 'CANCELLED', steps: [] });
        render(<ChatBox {...baseProps} />);
        await user.click(await screen.findByRole('button', { name: 'Cancel', exact: true }));
        expect(cancelAgentRun).toHaveBeenCalledWith('resumed');
        await screen.findByText(/Cancellation takes effect/);
        await screen.findByText('Agent · Cancelled');
        await waitFor(() => expect(screen.getByRole('button', { name: 'Ask', exact: true })).toBeEnabled());
    });

    it('restores input when task creation fails', async () => {
        const user = userEvent.setup();
        createAgentRun.mockRejectedValue({ response: { data: { error: 'Conversation already busy' } } });
        render(<ChatBox {...baseProps} />);
        await user.click(screen.getByRole('button', { name: 'Agent', exact: true }));
        await user.type(screen.getByPlaceholderText('Message...'), 'Review my goal');
        await user.click(screen.getByRole('button', { name: 'Send', exact: true }));
        await screen.findByText('Conversation already busy');
        expect(screen.getByPlaceholderText('Message...')).toHaveValue('Review my goal');
    });

    it('retries polling failures without unlocking an unfinished task', async () => {
        listAgentRuns.mockResolvedValue([{ id: 'run-offline', status: 'RUNNING', steps: [] }]);
        getAgentRun.mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ id: 'run-offline', status: 'COMPLETED', steps: [] });
        render(<ChatBox {...baseProps} />);
        await screen.findByText(/Connection interrupted/, {}, { timeout: 2000 });
        expect(screen.getByRole('button', { name: 'Ask', exact: true })).toBeDisabled();
        await screen.findByText('Agent · Response ready', {}, { timeout: 3000 });
        await waitFor(() => expect(screen.getByRole('button', { name: 'Ask', exact: true })).toBeEnabled());
    });

    it('removes files and rejects oversized attachments', async () => {
        const user = userEvent.setup();
        render(<ChatBox {...baseProps} />);
        await user.click(screen.getByRole('button', { name: 'Agent', exact: true }));
        const input = document.querySelector('input[accept*=".txt"]');
        await user.upload(input, new File(['some text'], 'notes.md', { type: 'text/markdown' }));
        await user.click(await screen.findByRole('button', { name: 'Remove file notes.md' }));
        expect(screen.queryByRole('button', { name: 'Remove file notes.md' })).not.toBeInTheDocument();
        await user.upload(input, new File(['x'.repeat(24_001)], 'large.txt', { type: 'text/plain' }));
        await screen.findByText(/24,000 characters total/);
    });
});


describe('ChatBox local context lifecycle', () => {
    it('persists per-chat preferences and project opt-out before a model request', async () => {
        const user = userEvent.setup();
        getConversationSettings.mockResolvedValue({ version: 7, projectId: 'p1', usePreferences: true, useProjectContext: true, systemPrompt: 'Existing instructions' });
        streamMessage.mockResolvedValue({ body: { getReader: () => ({ read: () => Promise.resolve({ done: true }) }) } });
        render(<ChatBox {...baseProps} projects={[{ id: 'p1', name: 'Boox' }]} />);
        await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Use my preferences' })).toBeEnabled());
        await user.click(screen.getByRole('checkbox', { name: 'Use my preferences' }));
        await user.click(screen.getByRole('checkbox', { name: 'Use project context' }));
        await user.type(screen.getByPlaceholderText('Message...'), 'hello');
        await user.click(screen.getByRole('button', { name: 'Send', exact: true }));
        await waitFor(() => expect(streamMessage).toHaveBeenCalled());
        expect(saveConversationSettings).toHaveBeenCalledWith('conv-1', { version: 7, projectId: 'p1', usePreferences: false, useProjectContext: false, systemPrompt: 'Existing instructions' });
    });
    it('does not send a request when settings cannot load, preserving existing data', async () => {
        const user = userEvent.setup(); getConversationSettings.mockRejectedValue(new Error('settings down'));
        render(<ChatBox {...baseProps} />);
        expect(await screen.findByRole('alert')).toHaveTextContent('settings down');
        await user.type(screen.getByPlaceholderText('Message...'), 'hello');
        expect(screen.getByRole('button', { name: 'Send', exact: true })).toBeDisabled();
        expect(saveConversationSettings).not.toHaveBeenCalled(); expect(streamMessage).not.toHaveBeenCalled();
    });
    it('explicitly clears persisted conversation instructions', async () => {
        const user = userEvent.setup();
        getConversationSettings.mockResolvedValue({ version: 4, usePreferences: true, useProjectContext: true, systemPrompt: 'Old prompt' });
        render(<ChatBox {...baseProps} />);
        await user.click(screen.getByLabelText('Toggle system prompt'));
        await waitFor(() => expect(screen.getByLabelText('Conversation instructions')).toHaveValue('Old prompt'));
        await user.click(screen.getByRole('button', { name: 'Clear conversation instructions' }));
        await waitFor(() => expect(saveConversationSettings).toHaveBeenCalledWith('conv-1', { version: 4, systemPrompt: '' }));
        expect(screen.getByLabelText('Conversation instructions')).toHaveValue('');
    });
    it('does not unlock a disconnected Ask response while the worker remains running', async () => {
        const user = userEvent.setup();
        getConversationExecution.mockResolvedValueOnce({ state: 'idle' }).mockResolvedValue({ state: 'running' });
        streamMessage.mockResolvedValue({ body: { getReader: () => ({ read: () => Promise.reject(new Error('connection lost')) }) } });
        render(<ChatBox {...baseProps} />);
        await user.type(screen.getByPlaceholderText('Message...'), 'hello');
        await waitFor(() => expect(screen.getByRole('button', { name: 'Send', exact: true })).toBeEnabled());
        await user.click(screen.getByRole('button', { name: 'Send', exact: true }));
        await waitFor(() => expect(getConversationExecution).toHaveBeenCalledTimes(2));
        await user.type(screen.getByPlaceholderText('Message...'), 'follow up');
        expect(screen.getByRole('button', { name: 'Send', exact: true })).toBeDisabled();
        expect(screen.getByText(/Reconnecting does not cancel its work/)).toBeInTheDocument();
    });
    it('renders SSE context metadata separately from answer tokens', async () => {
        const user = userEvent.setup(); let read = false;
        streamMessage.mockResolvedValue({ body: { getReader: () => ({ read: () => {
            if (read) return Promise.resolve({ done: true }); read = true;
            return Promise.resolve({ done: false, value: new TextEncoder().encode('event: context_used\ndata: {"included":[{"type":"preferences","name":"Response length","version":3}]}\n\ndata: Hello\n\ndata: [DONE]\n\n') });
        } }) } });
        render(<ChatBox {...baseProps} />);
        await user.type(screen.getByPlaceholderText('Message...'), 'hello');
        await user.click(screen.getByRole('button', { name: 'Send', exact: true }));
        expect(await screen.findByText('Response length (v3)')).toBeInTheDocument();
        expect(screen.getByText('Hello')).toBeInTheDocument();
        expect(screen.queryByText(/"included"/)).not.toBeInTheDocument();
    });
    it('offers Save for project only for stable persisted IDs', async () => {
        getConversationSettings.mockResolvedValue({ version: 0, projectId: 'p1', usePreferences: true, useProjectContext: true });
        getConversationMessages.mockResolvedValue([{ id: 55, role: 'assistant', content: 'Persisted answer' }, { role: 'assistant', content: 'No identifier' }]);
        render(<ChatBox {...baseProps} projects={[{ id: 'p1', name: 'Boox' }]} />);
        await screen.findByText('Persisted answer');
        expect(screen.getAllByRole('button', { name: 'Save for project' })).toHaveLength(1);
    });
});

it('inherits an unsaved project chat without creating it until settings are saved or sent', async () => {
    const user = userEvent.setup();
    getConversationSettings.mockRejectedValue({ response: { status: 404 } });
    streamMessage.mockResolvedValue({ body: { getReader: () => ({ read: () => Promise.resolve({ done: true }) }) } });
    render(<ChatBox {...baseProps} initialProjectId="p1" projects={[{ id: 'p1', name: 'Boox' }]} />);
    await waitFor(() => expect(screen.getByLabelText('Chat project')).toBeEnabled());
    expect(screen.getByLabelText('Chat project')).toHaveValue('p1');
    expect(saveConversationSettings).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText('Message'), 'First project message');
    await user.click(screen.getByRole('button', { name: 'Send', exact: true }));
    await waitFor(() => expect(saveConversationSettings).toHaveBeenCalledWith('conv-1', expect.objectContaining({ version: 0, projectId: 'p1' })));
    expect(streamMessage).toHaveBeenCalled();
});

it('blocks sends during project-deletion settings refresh and preserves unrelated prompt edits', async () => {
    const user = userEvent.setup();
    getConversationSettings.mockResolvedValue({ version: 2, projectId: 'p1', usePreferences: true, useProjectContext: true, systemPrompt: 'Saved' });
    const { rerender } = render(<ChatBox {...baseProps} projects={[{ id: 'p1', name: 'Boox' }]} settingsRevision={0} />);
    await user.click(screen.getByLabelText('Toggle system prompt'));
    await waitFor(() => expect(screen.getByLabelText('Conversation instructions')).toHaveValue('Saved'));
    await user.type(screen.getByLabelText('Conversation instructions'), ' draft');
    await user.type(screen.getByLabelText('Message'), 'Ready');
    let finish;
    getConversationSettings.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
    rerender(<ChatBox {...baseProps} projects={[]} settingsRevision={1} />);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Send', exact: true })).toBeDisabled());
    await act(async () => finish({ version: 3, projectId: null, usePreferences: true, useProjectContext: true, systemPrompt: 'Saved' }));
    expect(screen.getByLabelText('Chat project')).toHaveValue('');
    expect(screen.getByLabelText('Conversation instructions')).toHaveValue('Saved draft');
    expect(screen.getByRole('button', { name: 'Send', exact: true })).toBeEnabled();
});

it('recovers the authoritative completed answer after an Ask disconnect', async () => {
    const user = userEvent.setup();
    getConversationExecution.mockResolvedValueOnce({ state: 'idle' }).mockResolvedValueOnce({ state: 'running' }).mockResolvedValue({ state: 'idle' });
    streamMessage.mockResolvedValue({ body: { getReader: () => ({ read: () => Promise.reject(new Error('connection interrupted')) }) } });
    render(<ChatBox {...baseProps} />);
    await user.type(screen.getByLabelText('Message'), 'hello');
    await user.click(screen.getByRole('button', { name: 'Send', exact: true }));
    await waitFor(() => expect(getConversationExecution).toHaveBeenCalledTimes(2));
    getConversationMessages.mockResolvedValue([{ id: 89, role: 'assistant', content: 'Completed while disconnected' }]);
    expect(await screen.findByText('Completed while disconnected', {}, { timeout: 2500 })).toBeInTheDocument();
    await user.type(screen.getByLabelText('Message'), 'Follow up');
    expect(screen.getByRole('button', { name: 'Send', exact: true })).toBeEnabled();
});

it('keeps explicit generation edits when first settings save creates an empty conversation summary', async () => {
    const user = userEvent.setup();
    const { rerender } = render(<ChatBox {...baseProps} initialGenerationConfig={null} />);
    await openGenerationSettings(user);
    await user.type(screen.getByLabelText('Temperature'), '0.7');
    rerender(<ChatBox {...baseProps} initialGenerationConfig={{ temperature: null, numCtx: null, stopSequences: [] }} />);
    expect(screen.getByLabelText('Temperature')).toHaveValue(0.7);
});
