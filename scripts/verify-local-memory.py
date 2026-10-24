#!/usr/bin/env python3
"""Verify local-memory APIs on an isolated test installation with a real local model.

This updates global preferences. Point it only at a temporary validation database.
"""

import argparse
import json
import time
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='http://127.0.0.1:18085')
    parser.add_argument('--model', default='qwen2.5:14b')
    parser.add_argument('--output', default='/tmp/ullavox-local-memory-validation.json')
    parser.add_argument('--allow-preferences-update', action='store_true', required=True)
    args = parser.parse_args()
    checks = []
    prefix = 'memory-validation-' + str(time.time_ns())

    def request(method, path, data=None, expected=200):
        body = json.dumps(data).encode() if data is not None else None
        req = urllib.request.Request(args.base + path, data=body, method=method,
                                     headers={'Content-Type': 'application/json'})
        try:
            response = urllib.request.urlopen(req, timeout=240)
        except urllib.error.HTTPError as error:
            if error.code != expected:
                raise AssertionError(f'{method} {path}: HTTP {error.code}') from error
            response = error
        with response:
            assert response.status == expected or response.code == expected
            value = response.read()
            return json.loads(value) if value else None

    def record(name, action):
        result = action()
        checks.append({'name': name, 'passed': True, 'result': result})
        with open(args.output, 'w') as output:
            json.dump({'model': args.model, 'checks': checks}, output, indent=2)
        print('PASS:', name, flush=True)
        return result

    def preferences():
        current = request('GET', '/api/preferences')
        updated = request('PUT', '/api/preferences', {
            'version': current['version'], 'onboardingStatus': 'completed',
            'responseLength': 'brief', 'explanationStyle': 'examples',
            'familiarity': 'intermediate', 'background': '', 'goals': '',
            'instructions': 'Answer the current request directly.'})
        assert updated['responseLength'] == 'brief'
        request('PUT', '/api/preferences', {**updated, 'version': current['version']}, expected=409)
        return updated

    record('preferences save and stale-update conflict', preferences)
    project = request('POST', '/api/projects', {
        'name': prefix, 'description': 'Synthetic validation project',
        'instructions': 'Use saved project decisions when relevant.'}, expected=201)
    note = request('POST', f"/api/projects/{project['id']}/memories", {
        'title': 'Recovery phrase', 'content': 'The project recovery phrase is COBALT ORCHARD.',
        'kind': 'decision', 'pinned': True}, expected=201)
    conversation = prefix + '-ask'
    request('PATCH', f'/api/conversations/{conversation}/settings', {
        'version': 0, 'projectId': project['id'],
        'usePreferences': True, 'useProjectContext': True, 'systemPrompt': ''})

    def ask():
        reply = request('POST', '/api/chat/send', {
            'message': 'What is the project recovery phrase? Reply with the phrase.',
            'server': 'ollama', 'model': args.model, 'sessionId': conversation,
            'stream': False, 'selectedMemoryIds': [note['id']]})
        assert 'COBALT ORCHARD' in reply['content'].upper(), reply
        assert any(item['id'] == note['id'] for item in reply['contextUsed']['included'])
        return reply

    record('real-model recall and context metadata', ask)

    def save_source():
        history = request('GET', f'/api/conversations/{conversation}/messages')
        source = next(row for row in history if row['role'] == 'user')
        saved = request('POST', f"/api/projects/{project['id']}/memories", {
            'title': 'Saved source', 'content': 'A user-reviewed independent decision.',
            'kind': 'decision', 'pinned': False,
            'sourceConversationId': conversation, 'sourceMessageId': source['id']}, expected=201)
        request('POST', '/api/chat/reset-context', {'server': 'ollama', 'sessionId': conversation})
        assert request('GET', f'/api/conversations/{conversation}/messages') == []
        notes = request('GET', f"/api/projects/{project['id']}/memories")
        retained = next(row for row in notes if row['id'] == saved['id'])
        assert retained['content'] == saved['content']
        assert not retained.get('sourceMessageId')
        return {'noteRetained': True, 'sourceUnavailable': True}

    record('reset clears source history and preserves saved note', save_source)

    def isolated():
        reply = request('POST', '/api/chat/send', {
            'message': 'Say hello briefly.', 'server': 'ollama', 'model': args.model,
            'sessionId': prefix + '-standalone', 'stream': False})
        assert not any(item['type'] == 'memory' for item in reply['contextUsed']['included'])
        return {'noProjectMemories': True}

    record('standalone chat does not receive project notes', isolated)

    def agent():
        run = request('POST', '/api/agent-runs', {
            'goal': 'Add the values in numbers.csv and report the total briefly.',
            'conversationId': prefix + '-agent', 'server': 'ollama', 'model': args.model,
            'allowWebSearch': False,
            'attachments': [{'name': 'numbers.csv', 'content': 'value\n3\n5\n7'}]}, expected=202)
        deadline = time.monotonic() + 360
        while run['status'] not in ('COMPLETED', 'FAILED', 'CANCELLED'):
            assert time.monotonic() < deadline, 'Agent validation timed out'
            time.sleep(1)
            run = request('GET', '/api/agent-runs/' + run['id'])
        assert run['status'] == 'COMPLETED', run
        assert '15' in run['finalOutput'], run
        assert isinstance(run.get('contextUsed'), dict)
        return run

    record('fresh Agent conversation and file task with preferences', agent)
    print(f'{len(checks)} checks passed. Results: {args.output}', flush=True)


if __name__ == '__main__':
    main()
