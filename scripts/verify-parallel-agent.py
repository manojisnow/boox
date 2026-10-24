#!/usr/bin/env python3
"""Opt-in Ullavox integration checks; requires Parallel enabled and an installed tool model.
Creates test conversations. Captured provider/model output stays under /tmp.
Set ULLAVOX_TEST_URL and ULLAVOX_TEST_MODEL.
Set EXPECTED_BOOT_VERSION, EXPECTED_JAVA_MIN and EXPECTED_JAVA_MAX to validate independently verified current version facts.
This is an application regression check, not a provider performance benchmark.
"""
import json
import os
import time
import urllib.request
import uuid

BASE = os.environ.get("ULLAVOX_TEST_URL", "http://localhost:8080")
MODEL = os.environ.get("ULLAVOX_TEST_MODEL", "gpt-oss:120b-cloud")
CONVERSATION = "parallel-validation-" + str(uuid.uuid4())
RESULTS = []


def request(path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(urllib.request.Request(
        BASE + path, data=data, headers={"Content-Type": "application/json"}
    ), timeout=20) as response:
        return json.load(response)


def task(goal, conversation=CONVERSATION, **extra):
    run = request("/api/agent-runs", {
        "goal": goal, "conversationId": conversation, "server": "ollama",
        "model": MODEL, "allowWebSearch": True, **extra,
    })
    print("Started", run["id"], flush=True)
    deadline = time.monotonic() + 300
    while run["status"] not in ("COMPLETED", "FAILED", "CANCELLED") and time.monotonic() < deadline:
        time.sleep(2)
        run = request("/api/agent-runs/" + run["id"])
    RESULTS.append(run)
    with open("/tmp/ullavox-parallel-validation.json", "w") as output:
        json.dump({"conversationId": CONVERSATION, "runs": RESULTS}, output, indent=2)
    assert run["status"] == "COMPLETED", run.get("error", run["status"])
    assert "†source" not in run["finalOutput"], "Invented citation marker"
    tool_calls = [step for step in run["steps"] if step["summary"].startswith(("Searching:", "Reading web pages:"))]
    assert len(tool_calls) <= 5, "Tool budget exceeded"
    history = request("/api/conversations/" + conversation + "/messages")
    assert history[-1]["content"] == run["finalOutput"], "Answer was not committed to chat history"
    print("Completed", run["id"], flush=True)
    return run


research = task("Research the latest stable Spring Boot release, explain its supported Java versions, and cite official source links.")
assert "https://docs.spring.io" in research["finalOutput"], "Missing official documentation"
for name in ("EXPECTED_BOOT_VERSION", "EXPECTED_JAVA_MIN", "EXPECTED_JAVA_MAX"):
    if os.environ.get(name):
        assert os.environ[name] in research["finalOutput"], "Incorrect current fact: " + name
followup = task("Explain why Java 25 works with that release. Be specific and cite the evidence.")
assert "25" in followup["finalOutput"] and "https://docs.spring.io" in followup["finalOutput"]
assert followup["finalOutput"] != research["finalOutput"], "Follow-up repeated the same answer"
assert "framework 6" not in followup["finalOutput"].lower(), "Invented framework-version explanation"
assert "same class-file version" not in followup["finalOutput"].lower(), "Invented bytecode explanation"
page = task("Read https://docs.spring.io/spring-boot/system-requirements.html and tell me the supported Java range. Cite that page.", conversation=CONVERSATION + "-page")
assert any(step["summary"].startswith("Reading web pages:") for step in page["steps"]), "Supplied URL did not use page reading"
assert "https://docs.spring.io/spring-boot/system-requirements.html" in page["finalOutput"]
files = task("Calculate the total in totals.csv and explain the calculation.", conversation=CONVERSATION + "-files", allowWebSearch=False, attachments=[{"name": "totals.csv", "content": "item,amount\na,5\nb,7\nc,9"}])
assert "21" in files["finalOutput"]
assert not any(step["summary"].startswith(("Searching:", "Reading web pages:")) for step in files["steps"]), "Files-only task used external tools"
print("PASS: research, explanation follow-up, supplied page, files-only scope; inspect answers in the app.")
