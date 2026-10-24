#!/usr/bin/env python3
"""Opt-in packaged smoke test. Uses the shipped model and creates new conversations.
Raw responses stay in a user-selected /tmp output, never in the repository.
"""
import argparse
import json
import time
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base', default='http://localhost:8085')
parser.add_argument('--model', default='granite4:3b')
parser.add_argument('--output', default='/tmp/ullavox-release-package-results.json')
args = parser.parse_args()
conversation = 'release-validation-' + str(time.time_ns())
results = []


def request(path, body=None, timeout=240):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(args.base + path, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return json.load(response)


def record(name, action):
    try:
        value = action()
        results.append({'name': name, 'passed': True, 'result': value})
        print('PASS:', name, flush=True)
    except Exception as error:
        results.append({'name': name, 'passed': False, 'error': str(error)})
        print('FAIL:', name, str(error), flush=True)
    with open(args.output, 'w') as output:
        json.dump({'base': args.base, 'model': args.model, 'conversationId': conversation, 'checks': results}, output, indent=2)


def ordinary():
    reply = request('/api/chat/send', {'message': 'What is 2 + 3? Answer briefly.', 'server': 'ollama', 'model': args.model, 'sessionId': conversation + '-ask', 'stream': False})
    assert '5' in reply.get('content', ''), reply
    return reply


def task(goal, files=None, web=False, clear=False):
    run = request('/api/agent-runs', {'goal': goal, 'conversationId': conversation, 'server': 'ollama', 'model': args.model, 'attachments': files or [], 'allowWebSearch': web, 'clearAttachments': clear})
    deadline = time.monotonic() + 600
    print('RUN:', run['id'], flush=True)
    while run['status'] not in ['COMPLETED', 'FAILED', 'CANCELLED'] and time.monotonic() < deadline:
        time.sleep(2)
        run = request('/api/agent-runs/' + run['id'], timeout=20)
    if run['status'] not in ['COMPLETED', 'FAILED', 'CANCELLED']:
        request('/api/agent-runs/' + run['id'] + '/cancel', {})
    assert run['status'] == 'COMPLETED', run
    history = request('/api/conversations/' + conversation + '/messages')
    assert history[-1]['role'] == 'assistant' and history[-1]['content'] == run['finalOutput'], run
    return run


def files():
    run = task('Calculate the total amount in totals.csv. Show the three values and sum briefly.', [{'name': 'totals.csv', 'content': 'item,amount\na,5\nb,7\nc,9'}])
    assert '21' in run['finalOutput'], run
    assert run['allowWebSearch'] is False, run
    assert not any('Searching' in step.get('summary', '') or 'Reading web' in step.get('summary', '') for step in run.get('steps', [])), run
    return run


def followup():
    run = task('Explain why that total is correct. Show the addition briefly.')
    assert all(value in run['finalOutput'] for value in ['5', '7', '9', '21']), run
    assert run['attachedFileNames'] == ['totals.csv'] and run['allowWebSearch'] is False, run
    if len(results) > 1 and results[1]['passed']:
        assert run['finalOutput'] != results[1]['result']['finalOutput'], 'Repeated the initial answer'
    return run


def web():
    run = task('Research what photosynthesis is using web sources and cite a source URL. Keep the answer brief.', web=True, clear=True)
    assert any(step.get('summary', '').startswith('Searching:') for step in run.get('steps', [])), run
    assert run['attachedFileNames'] == [], run
    assert 'https://' in run['finalOutput'] and 'photosynth' in run['finalOutput'].lower(), run
    return run


record('ordinary chat', ordinary)
record('file agent calculation', files)
record('retained-file explanation', followup)
record('natural-language web research', web)
raise SystemExit(0 if all(result['passed'] for result in results) else 1)
