import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';
import {
    getServers,
    getModels,
    sendMessage,
    streamMessage,
    getTools,
    resetContext,
    listConversations,
    getConversationMessages,
    renameConversation,
    deleteConversation,
    createAgentRun, listAgentRuns, getAgentRun, cancelAgentRun,
    getPreferences, savePreferences, clearPreferences, listProjects, saveProject, deleteProject,
    listMemories, saveMemory, deleteMemory, getConversationSettings, saveConversationSettings, getConversationExecution,
} from './api';

vi.mock('axios', () => ({
    default: {
        defaults: {},
        request: vi.fn(),
        get: vi.fn(),
        post: vi.fn(),
        patch: vi.fn(),
        delete: vi.fn(),
    },
}));

beforeEach(() => {
    vi.clearAllMocks();
});

describe('getServers / getModels / getTools', () => {
    it('getServers returns response.data from GET /api/chat/servers', async () => {
        axios.get.mockResolvedValue({ data: ['ollama'] });
        const result = await getServers();
        expect(axios.get).toHaveBeenCalledWith('/api/chat/servers');
        expect(result).toEqual(['ollama']);
    });

    it('getModels passes server as a query param', async () => {
        axios.get.mockResolvedValue({ data: [{ name: 'phi4-mini' }] });
        const result = await getModels('ollama');
        expect(axios.get).toHaveBeenCalledWith('/api/chat/models', {
            params: { server: 'ollama' },
        });
        expect(result).toEqual([{ name: 'phi4-mini' }]);
    });

    it('getTools returns response.data from GET /api/chat/tools', async () => {
        axios.get.mockResolvedValue({ data: ['web_search'] });
        const result = await getTools();
        expect(axios.get).toHaveBeenCalledWith('/api/chat/tools');
        expect(result).toEqual(['web_search']);
    });
});

describe('sendMessage validation', () => {
    it.each([
        ['message', ['', 'ollama', 'phi4-mini', 'sid'], 'Message cannot be empty'],
        ['message (whitespace)', ['   ', 'ollama', 'phi4-mini', 'sid'], 'Message cannot be empty'],
        ['server', ['hi', '', 'phi4-mini', 'sid'], 'Server cannot be empty'],
        ['model', ['hi', 'ollama', '', 'sid'], 'Model cannot be empty'],
        ['sessionId', ['hi', 'ollama', 'phi4-mini', ''], 'Session ID cannot be empty'],
    ])('rejects a missing %s before calling axios', async (_label, args, expectedMessage) => {
        await expect(sendMessage(...args)).rejects.toThrow(expectedMessage);
        expect(axios.post).not.toHaveBeenCalled();
    });
});

describe('sendMessage request body', () => {
    it('sends the minimal body when optional fields are omitted', async () => {
        axios.post.mockResolvedValue({ data: { content: 'hi', role: 'assistant' } });
        const result = await sendMessage('hello', 'ollama', 'phi4-mini', 'sid', true);

        expect(axios.post).toHaveBeenCalledWith(
            '/api/chat/send',
            {
                message: 'hello',
                server: 'ollama',
                model: 'phi4-mini',
                sessionId: 'sid',
                stream: true,
            },
            { headers: { 'Content-Type': 'application/json' } }
        );
        expect(result).toEqual({ content: 'hi', role: 'assistant' });
        expect(axios.post.mock.calls[0][1]).not.toHaveProperty('replaceGenerationConfig');
    });

    it('trims message/server/model/sessionId and coerces stream to boolean', async () => {
        axios.post.mockResolvedValue({ data: {} });
        await sendMessage('  hi  ', ' ollama ', ' phi4-mini ', ' sid ', undefined);
        const [, body] = axios.post.mock.calls[0];
        expect(body).toMatchObject({
            message: 'hi',
            server: 'ollama',
            model: 'phi4-mini',
            sessionId: 'sid',
            stream: false,
        });
    });

    it('includes systemPrompt only when non-blank', async () => {
        axios.post.mockResolvedValue({ data: {} });
        await sendMessage('hi', 'ollama', 'phi4-mini', 'sid', false, '   ');
        expect(axios.post.mock.calls[0][1]).not.toHaveProperty('systemPrompt');

        await sendMessage('hi', 'ollama', 'phi4-mini', 'sid', false, 'be nice');
        expect(axios.post.mock.calls[1][1]).toHaveProperty('systemPrompt', 'be nice');
    });

    it('includes images only when non-empty', async () => {
        axios.post.mockResolvedValue({ data: {} });
        await sendMessage('hi', 'ollama', 'phi4-mini', 'sid', false, '', []);
        expect(axios.post.mock.calls[0][1]).not.toHaveProperty('images');

        await sendMessage('hi', 'ollama', 'phi4-mini', 'sid', false, '', ['b64data']);
        expect(axios.post.mock.calls[1][1]).toHaveProperty('images', ['b64data']);
    });

    it('includes only the set fields of generationConfig', async () => {
        axios.post.mockResolvedValue({ data: {} });
        await sendMessage('hi', 'ollama', 'phi4-mini', 'sid', false, '', [], {
            temperature: 0.1,
            numCtx: null,
            stopSequences: [],
        });
        expect(axios.post.mock.calls[0][1]).toMatchObject({ temperature: 0.1, replaceGenerationConfig: true });
        expect(axios.post.mock.calls[0][1]).not.toHaveProperty('numCtx');
        expect(axios.post.mock.calls[0][1]).not.toHaveProperty('stopSequences');

        await sendMessage('hi', 'ollama', 'phi4-mini', 'sid', false, '', [], {
            temperature: null,
            numCtx: 4096,
            stopSequences: ['END'],
        });
        expect(axios.post.mock.calls[1][1]).toMatchObject({ numCtx: 4096, stopSequences: ['END'] });
        expect(axios.post.mock.calls[1][1]).not.toHaveProperty('temperature');
    });

    it('explicitly replaces saved settings when every field has been cleared', async () => {
        axios.post.mockResolvedValue({ data: {} });
        await sendMessage('hi', 'ollama', 'phi4-mini', 'sid', false, '', [], {
            temperature: null,
            numCtx: null,
            stopSequences: [],
        });
        const body = axios.post.mock.calls[0][1];
        expect(body).toHaveProperty('replaceGenerationConfig', true);
        expect(body).not.toHaveProperty('temperature');
        expect(body).not.toHaveProperty('numCtx');
        expect(body).not.toHaveProperty('stopSequences');
    });

    it('logs and rethrows when the request fails', async () => {
        const error = new Error('network down');
        axios.post.mockRejectedValue(error);
        await expect(sendMessage('hi', 'ollama', 'phi4-mini', 'sid', false)).rejects.toThrow('network down');
    });
});

describe('streamMessage', () => {
    beforeEach(() => {
        global.fetch = vi.fn();
    });

    it('POSTs to /api/chat/stream with stream: true and returns the response', async () => {
        const fakeResponse = { ok: true, body: {} };
        global.fetch.mockResolvedValue(fakeResponse);

        const result = await streamMessage('hi', 'ollama', 'phi4-mini', 'sid', 'be nice', ['b64'], {
            temperature: 0.5,
            numCtx: null,
            stopSequences: [],
        });

        expect(global.fetch).toHaveBeenCalledWith(
            '/api/chat/stream',
            expect.objectContaining({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
            })
        );
        const body = JSON.parse(global.fetch.mock.calls[0][1].body);
        expect(body).toMatchObject({
            message: 'hi',
            server: 'ollama',
            model: 'phi4-mini',
            sessionId: 'sid',
            stream: true,
            systemPrompt: 'be nice',
            images: ['b64'],
            temperature: 0.5,
            replaceGenerationConfig: true,
        });
        expect(body).not.toHaveProperty('numCtx');
        expect(body).not.toHaveProperty('stopSequences');
        expect(result).toBe(fakeResponse);
    });

    it('includes stopSequences when non-empty', async () => {
        global.fetch.mockResolvedValue({ ok: true, body: {} });
        await streamMessage('hi', 'ollama', 'phi4-mini', 'sid', '', [], {
            temperature: null,
            numCtx: null,
            stopSequences: ['END', '###'],
        });
        const body = JSON.parse(global.fetch.mock.calls[0][1].body);
        expect(body).toMatchObject({ stopSequences: ['END', '###'] });
    });

    it('throws when the response is not ok', async () => {
        global.fetch.mockResolvedValue({ ok: false, status: 500 });
        await expect(streamMessage('hi', 'ollama', 'phi4-mini', 'sid')).rejects.toThrow(
            'Stream request failed: 500'
        );
    });

    it('explicitly resets cleared settings but preserves them when config is omitted', async () => {
        global.fetch.mockResolvedValue({ ok: true, body: {} });
        await streamMessage('hi', 'ollama', 'phi4-mini', 'sid', '', [], {
            temperature: null,
            numCtx: null,
            stopSequences: [],
        });
        const resetBody = JSON.parse(global.fetch.mock.calls[0][1].body);
        expect(resetBody).toHaveProperty('replaceGenerationConfig', true);
        expect(resetBody).not.toHaveProperty('temperature');
        expect(resetBody).not.toHaveProperty('numCtx');
        expect(resetBody).not.toHaveProperty('stopSequences');

        await streamMessage('hi', 'ollama', 'phi4-mini', 'sid');
        const untouchedBody = JSON.parse(global.fetch.mock.calls[1][1].body);
        expect(untouchedBody).not.toHaveProperty('replaceGenerationConfig');
    });
});

describe('resetContext', () => {
    it('posts server/sessionId and returns response.data', async () => {
        axios.post.mockResolvedValue({ data: { ok: true } });
        const result = await resetContext('ollama', 'sid');
        expect(axios.post).toHaveBeenCalledWith('/api/chat/reset-context', {
            server: 'ollama',
            sessionId: 'sid',
        });
        expect(result).toEqual({ ok: true });
    });

    it('logs and rethrows on failure', async () => {
        axios.post.mockRejectedValue(new Error('boom'));
        await expect(resetContext('ollama', 'sid')).rejects.toThrow('boom');
    });
});

describe('conversation persistence endpoints', () => {
    it('listConversations GETs /api/conversations', async () => {
        axios.get.mockResolvedValue({ data: [{ id: '1' }] });
        const result = await listConversations();
        expect(axios.get).toHaveBeenCalledWith('/api/conversations');
        expect(result).toEqual([{ id: '1' }]);
    });

    it('getConversationMessages GETs /api/conversations/:id/messages with id encoded', async () => {
        axios.get.mockResolvedValue({ data: [] });
        await getConversationMessages('id with space');
        expect(axios.get).toHaveBeenCalledWith(
            '/api/conversations/id%20with%20space/messages'
        );
    });

    it('renameConversation PATCHes /api/conversations/:id with the new title', async () => {
        axios.patch.mockResolvedValue({ data: { id: '1', title: 'New' } });
        const result = await renameConversation('1', 'New');
        expect(axios.patch).toHaveBeenCalledWith('/api/conversations/1', {
            title: 'New',
        });
        expect(result).toEqual({ id: '1', title: 'New' });
    });

    it('deleteConversation DELETEs /api/conversations/:id', async () => {
        axios.delete.mockResolvedValue({});
        await deleteConversation('1');
        expect(axios.delete).toHaveBeenCalledWith('/api/conversations/1');
    });
});


describe('agent run API', () => {
    it('sends task settings/files and supports resume and cancellation', async () => {
        axios.post.mockResolvedValue({ data: { id: 'r' } });
        axios.get.mockResolvedValue({ data: [] });
        expect(await createAgentRun(' goal ', ' c ', ' ollama ', ' model ', 'Be concise', [], { temperature: 0.3, numCtx: 8192, stopSequences: [] }, [{ name: 'a.txt', content: 'Evidence' }])).toEqual({ id: 'r' });
        expect(axios.post.mock.calls[0][1]).toMatchObject({ goal: 'goal', conversationId: 'c', chatRequest: { systemPrompt: 'Be concise', replaceGenerationConfig: true, temperature: 0.3, numCtx: 8192 }, attachments: [{ name: 'a.txt', content: 'Evidence' }] });
        await createAgentRun('goal', 'c', 'ollama', 'model');
        expect(axios.post.mock.calls[1][1].chatRequest.replaceGenerationConfig).toBe(false);
        expect(await listAgentRuns('c')).toEqual([]);
        expect(axios.get).toHaveBeenCalledWith('/api/agent-runs', { params: { conversationId: 'c' } });
        await getAgentRun('a/b');
        expect(axios.get).toHaveBeenCalledWith('/api/agent-runs/a%2Fb');
        await cancelAgentRun('a/b');
        expect(axios.post).toHaveBeenCalledWith('/api/agent-runs/a%2Fb/cancel');
    });
});


describe('local context APIs', () => {
    it('preserves versions and explicit empty prompt values in settings PATCH', async () => {
        axios.request.mockResolvedValue({ data: { version: 3, systemPrompt: '' } });
        await saveConversationSettings('a/b', { version: 2, systemPrompt: '', projectId: null, usePreferences: false });
        expect(axios.request).toHaveBeenCalledWith({ method: 'patch', url: '/api/conversations/a%2Fb/settings', data: { version: 2, systemPrompt: '', projectId: null, usePreferences: false }, params: undefined });
    });
    it('uses versioned preference and project resources', async () => {
        axios.request.mockResolvedValue({ data: {} });
        await getPreferences(); await savePreferences({ version: 1, instructions: '' }); await clearPreferences(2);
        await listProjects(); await saveProject({ name: 'Ullavox' }); await saveProject({ id: 'p1', version: 3, name: 'Ullavox' }); await deleteProject('p1', 4);
        expect(axios.request).toHaveBeenCalledWith(expect.objectContaining({ method: 'put', url: '/api/preferences', data: { version: 1, instructions: '' } }));
        expect(axios.request).toHaveBeenCalledWith(expect.objectContaining({ method: 'delete', url: '/api/preferences', params: { version: 2 } }));
        expect(axios.request).toHaveBeenCalledWith(expect.objectContaining({ method: 'post', url: '/api/projects' }));
        expect(axios.request).toHaveBeenCalledWith(expect.objectContaining({ method: 'put', url: '/api/projects/p1' }));
        expect(axios.request).toHaveBeenCalledWith(expect.objectContaining({ method: 'delete', url: '/api/projects/p1', params: { version: 4 } }));
    });
    it('uses scoped note routes and worker execution status', async () => {
        axios.request.mockResolvedValue({ data: {} });
        await listMemories('p1'); await saveMemory('p1', { title: 'Decision', sourceMessageId: 71 });
        await saveMemory('p1', { id: 'n1', version: 2 }); await deleteMemory('p1', 'n1', 2);
        await getConversationSettings('c1'); await getConversationExecution('c1');
        expect(axios.request).toHaveBeenCalledWith(expect.objectContaining({ method: 'post', url: '/api/projects/p1/memories', data: { title: 'Decision', sourceMessageId: 71 } }));
        expect(axios.request).toHaveBeenCalledWith(expect.objectContaining({ method: 'put', url: '/api/projects/p1/memories/n1' }));
        expect(axios.request).toHaveBeenCalledWith(expect.objectContaining({ method: 'get', url: '/api/chat/conversations/c1/execution' }));
    });
    it('sends selected note IDs in Ask and Agent requests', async () => {
        axios.post.mockResolvedValue({ data: {} });
        await sendMessage('hello', 'ollama', 'model', 'chat', false, '', [], null, ['n1']);
        expect(axios.post).toHaveBeenCalledWith('/api/chat/send', expect.objectContaining({ selectedMemoryIds: ['n1'] }), expect.anything());
        await createAgentRun('goal', 'chat', 'ollama', 'model', '', [], null, [], false, false, ['n1']);
        expect(axios.post).toHaveBeenCalledWith('/api/agent-runs', expect.objectContaining({ chatRequest: expect.objectContaining({ selectedMemoryIds: ['n1'] }) }));
    });
});
