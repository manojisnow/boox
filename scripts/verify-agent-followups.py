#!/usr/bin/env python3
"""Opt-in live regression: requires Ullavox at localhost:8080 and qwen2.5:14b in Ollama.
Creates a new test conversation and verifies five turns without tool-name instructions.
Results are saved under /tmp for manual inspection; no existing conversation is modified.
"""
import json
import time
import urllib.request
base = 'http://localhost:8080'
conversation = 'agent-followup-live-' + str(int(time.time()))

def request(path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(urllib.request.Request(base + path, data=data, headers={'Content-Type': 'application/json'}), timeout=20) as response:
        return json.load(response)

def task(goal, files=None, clear=False, web=None):
    body = {'goal': goal, 'conversationId': conversation, 'server': 'ollama', 'model': 'qwen2.5:14b', 'attachments': files or [], 'clearAttachments': clear}
    if web is not None:
        body['allowWebSearch'] = web
    run = request('/api/agent-runs', body)
    print('started', goal, run['id'], flush=True)
    start = time.monotonic()
    while run['status'] not in ['COMPLETED', 'FAILED', 'CANCELLED'] and time.monotonic() - start < 240:
        time.sleep(2)
        run = request('/api/agent-runs/' + run['id'])
    print(json.dumps({'goal': goal, 'status': run['status'], 'scope': run.get('allowWebSearch'), 'files': run.get('attachedFileNames'), 'answer': run.get('finalOutput'), 'error': run.get('error')}), flush=True)
    assert run['status'] == 'COMPLETED', run
    history = request('/api/conversations/' + conversation + '/messages')
    assert history[-1]['role'] == 'assistant' and history[-1]['content'] == run['finalOutput'], 'Completed run has no committed answer in history'
    return run
first = task('Calculate the sum of the amount column in totals.csv and show the rows used.', [{'name': 'totals.csv', 'content': 'item,amount\na,5\nb,7\nc,9'}])
second = task('explain that calculation?')
assert '21' in first['finalOutput'], 'Wrong initial total'
assert all((value in second['finalOutput'] for value in ['5', '7', '9', '21'])), 'Explanation lost the calculation'
assert first['finalOutput'] != second['finalOutput'], 'Repeated identical response'
assert second['attachedFileNames'] == ['totals.csv'] and second['allowWebSearch'] is False
third = task('If I change row b from 7 to 10, what is the new total? Explain the adjustment.')
assert '24' in third['finalOutput'], 'Wrong hypothetical total'
fourth = task('What was the original value in row b? Use the actual file, not my hypothetical change.')
assert '7' in fourth['finalOutput']
cleared = task('Can you verify row b directly from the file now?', clear=True)
assert cleared['attachedFileNames'] == []
assert any((phrase in cleared['finalOutput'].lower() for phrase in ['cannot', "can't", 'no longer', 'not available', 'not currently', "don't have", 'do not have', 'unable'])), 'Claimed direct verification after clearing files'
with open('/tmp/ullavox-followup-live-results.json', 'w') as f:
    json.dump({'conversationId': conversation, 'runs': [first, second, third, fourth, cleared]}, f, indent=2)
print('PASS: explanation differs, scope and files retained, hypothetical total 24, original evidence unchanged, honest limitation after clearing', flush=True)
