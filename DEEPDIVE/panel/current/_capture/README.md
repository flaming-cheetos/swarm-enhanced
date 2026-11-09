# Capture harness (not source)

`server.py` is a throwaway mock of the B1 web contract used to render
`../index.html` + `../app.js` and record what each control actually sends.

It mirrors, in this order:

1. `isAuthorized`: `X-Swarm-Token`, `?token=`, or `swarm_token` cookie.
2. `isOriginAllowed`: when an `Origin` header is present it must equal
   `http://<Host>`.
3. `GET /` returns the extracted HTML, sets `Set-Cookie: swarm_token=...;
   HttpOnly; SameSite=Strict`, and sends the same CSP shape as `SwarmWebHandler`.
4. `GET /app.js` requires the cookie.
5. `GET /ws` requires the cookie on the **handshake**; if the browser drops it,
   the panel's reconnect loop is the observable failure.
6. Every `POST` records `{path, body}` to `requests.jsonl` and answers
   `{"ok":true}` — the same unconditional response the real handler gives.

Only game state is fabricated (`state()` and `LOGS`). Markup, CSS and JS are the
verbatim extracted source. This is for screenshots and payload verification; it
is **not** evidence that the Java server runs.

Run: `python server.py` then open `http://127.0.0.1:8791/?token=b1-test-token`.
