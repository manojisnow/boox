#!/usr/bin/env python3
"""Check packaged assets and durable local settings on a disposable validation container."""

import argparse
import json
import subprocess
import time
import urllib.request
from html.parser import HTMLParser


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='http://127.0.0.1:18089')
    parser.add_argument('--container', required=True)
    parser.add_argument('--output', default='/tmp/ullavox-memory-container-results.json')
    args = parser.parse_args()
    if not args.container.startswith('ullavox-memory-validation-'):
        parser.error('Only disposable ullavox-memory-validation-* containers may be restarted')

    def request(path, method='GET', body=None):
        req = urllib.request.Request(args.base + path, method=method,
                                     data=json.dumps(body).encode() if body is not None else None,
                                     headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.load(response)

    class Scripts(HTMLParser):
        def __init__(self):
            super().__init__()
            self.paths = []

        def handle_starttag(self, tag, attrs):
            attrs = dict(attrs)
            if tag == 'script' and attrs.get('type') == 'module' and attrs.get('src', '').startswith('/assets/'):
                self.paths.append(attrs['src'])

    with urllib.request.urlopen(args.base + '/', timeout=10) as response:
        html = response.read().decode()
    assert 'id="root"' in html
    scripts = Scripts()
    scripts.feed(html)
    assert scripts.paths
    with urllib.request.urlopen(args.base + scripts.paths[0], timeout=10) as response:
        bundle = response.read().decode()
    assert 'Preferences' in bundle and 'Project' in bundle
    profile = request('/api/preferences')
    saved = request('/api/preferences', 'PUT', {
        **profile, 'onboardingStatus': 'completed', 'instructions': 'Container restart validation'})
    project = request('/api/projects', 'POST', {
        'name': 'Container persistence', 'description': '', 'instructions': 'Keep this project local.'})
    note = request(f"/api/projects/{project['id']}/memories", 'POST', {
        'title': 'Restart decision', 'content': 'This saved note survives an app restart.',
        'kind': 'decision', 'pinned': True})
    subprocess.run(['docker', 'restart', args.container], check=True, stdout=subprocess.DEVNULL)
    deadline = time.monotonic() + 60
    while True:
        try:
            if request('/actuator/health')['status'] == 'UP':
                break
        except (OSError, ValueError):
            pass
        if time.monotonic() >= deadline:
            raise AssertionError('Container did not become healthy after restart')
        time.sleep(1)
    restored = request('/api/preferences')
    assert restored['version'] == saved['version']
    assert restored['instructions'] == saved['instructions']
    notes = request(f"/api/projects/{project['id']}/memories")
    assert any(row['id'] == note['id'] and row['content'] == note['content'] for row in notes)
    result = {'packagedAssets': 'passed', 'freshStartup': 'passed',
              'preferencesAfterRestart': 'passed', 'projectNotesAfterRestart': 'passed'}
    with open(args.output, 'w') as output:
        json.dump(result, output, indent=2)
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    main()
