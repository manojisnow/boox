import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
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
    sessionStorage.clear();
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
    it('prefers a model matching initialModel over the phi4-mini default', async () => {
        getModels.mockResolvedValue([
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

        await user.click(screen.getByRole('checkbox'));
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
    it('restores a previously saved system prompt from sessionStorage', async () => {
        sessionStorage.setItem('boox-system-prompt', 'Be a pirate');
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

        expect(sessionStorage.getItem('boox-system-prompt')).toBe('Be nice');
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
