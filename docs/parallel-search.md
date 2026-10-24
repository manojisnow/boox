# Parallel web search in Ullavox

Set `WEB_SEARCH_PROVIDER=parallel` in the backend environment or Compose `.env`,
then restart the backend. Leave `PARALLEL_API_KEY` empty for anonymous light use,
or supply your own key for authenticated access. Keys stay on the backend; they
are never sent to the frontend or included in tool output. `legacy` is the
default provider. `tools.websearch.enabled=false` disables both web tools.

Ask normally, for example: “Research the latest stable Spring Boot release,
explain its supported Java versions, and cite official source links.” The agent
chooses search and page reading without requiring users to know tool names.

The adapter uses the fixed `https://search.parallel.ai/mcp` endpoint, initializes
MCP, and calls only `web_search` and `web_fetch`. Each conversation receives a
random UUID persisted in SQLite and reused across follow-ups and restarts. The
provider also receives the active model identifier. A nullable `web_session_id`
column is added to existing conversation tables; existing messages are retained.

Search results retain up to five sources. Page reading accepts up to three
public HTTP/HTTPS domain URLs per call and requests focused excerpts, not full
pages. Credentials, IP literals, local hostnames and non-web schemes are rejected.
This is remote provider retrieval, not a local filesystem or network reader.
Returned content is untrusted evidence, including any embedded instructions.
The agent shares a five-call budget between search and page reading. Each result
is limited to 4,000 characters, allocating space per source before shortening
excerpts. Completed search and page evidence can be reused in bounded follow-ups,
prioritizing the most recently retrieved evidence.
Source presence does not prove a claim: current facts still need relevant,
current evidence, and unsupported citations are rejected.

Files-only tasks expose neither web tool. With web enabled, model-selected
queries and requested URLs leave the machine. The backend does not forward file
attachments or the complete conversation to Parallel. The model can still put
information into a query, so use Files only for sensitive material. The UI
explains that web requests go to the configured provider.

## Limits and terms checked on 10 October 2026

Anonymous MCP is documented for exploration/light use with lower, unpublished
rate limits. It is not a production capacity guarantee. Rate limits, denied
access, provider errors and timeouts produce explicit limitations; there is no
automatic retry or switch to another provider. HTTP connection/read timeouts
are 10/30 seconds per exchange. Thread interruption is checked between exchanges; requested cancellation is
checked after a tool returns. An in-flight tool may finish before cancellation.
MCP sessions are closed when the server assigns a transport session ID.

[Parallel's customer terms](https://parallel.ai/customer-terms), effective
11 August 2026, permit application integration for authorized end customers,
subject to the agreement. They restrict cross-customer sharing of outputs,
resale/data products, competitive use and training models with outputs. Public
benchmark/evaluation disclosure requires written consent. Submitted data may be
used to improve/train Parallel's services, and processing personal data requires
a separate DPA. These constraints matter for hosted or commercial Ullavox; do not
infer blanket permission from free MCP access. A personal opt-in integration is
consistent with the light-use documentation, but obtain provider clarification
before offering shared hosted access. This is a terms assessment, not legal
clearance. Distributing Ullavox source does not bundle a Parallel subscription or
permission to redistribute third-party page content.

Keep each installation's provider credentials under its operator's control.
Do not build a shared cross-user evidence cache or publish captured provider
outputs/evaluation results in the repository. Follow the
[acceptable use policy](https://parallel.ai/acceptable-use-policy) and source
website rights. Ullavox's existing unauthenticated API means hosted multi-user
access needs authentication and isolation before using a shared provider account.

References:
- [Search MCP parameters and access](https://docs.parallel.ai/integrations/mcp/search-mcp)
- [Programmatic MCP use](https://docs.parallel.ai/integrations/mcp/programmatic-use)
- [Pricing](https://parallel.ai/pricing)

## Validation

Run backend `mvn -f backend/pom.xml clean verify` with Java 25 and frontend
`npm test -- --coverage` and `npm run build` with Node 24. Mock provider tests
cover protocol, evidence bounds, errors and safe metadata. SQLite integration
tests cover stable random correlation IDs. Agent tests enforce tool scope,
citation checks and page evidence in follow-ups. Then run the app with Parallel
enabled and validate research, a supplied URL, follow-ups, files-only work and
cancellation using an actual model. Use `python3 scripts/verify-parallel-agent.py` for the opt-in live regression;
set `EXPECTED_BOOT_VERSION`, `EXPECTED_JAVA_MIN` and `EXPECTED_JAVA_MAX` to
independently verified values when checking current facts. Captured output stays
under `/tmp`. Do not publish it as a provider evaluation without permission.
User manual validation precedes the PR.
