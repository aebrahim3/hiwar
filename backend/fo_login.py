"""One-time Fo sign-in. Run from the hiwar-backend folder:

    python fo_login.py

Your browser opens Wajo's sign-in page. After you approve, the token is saved
to fo_tokens.json and the backend uses it (and refreshes it) automatically.
"""

import secrets
import sys
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

from app import fo_auth

result: dict = {}


class Callback(BaseHTTPRequestHandler):
    def do_GET(self):
        query = parse_qs(urlparse(self.path).query)
        result.update({k: v[0] for k, v in query.items()})
        ok = "code" in query
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        msg = "Signed in to Fo. You can close this tab." if ok else f"Sign-in failed: {result}"
        self.wfile.write(f"<h2>{msg}</h2>".encode())

    def log_message(self, *args):
        pass


def open_callback_server() -> HTTPServer:
    """Listen for Wajo's redirect. Port 0 = let Windows pick any free port, so a
    busy or reserved port (WinError 10013) can't break sign-in."""
    try:
        return HTTPServer(("127.0.0.1", 0), Callback)
    except OSError as exc:
        sys.exit(f"Couldn't open a local port for the sign-in callback: {exc}")


def main() -> None:
    # Open the port BEFORE registering, so the redirect address we give Wajo is one we own.
    server = open_callback_server()
    redirect_uri = f"http://localhost:{server.server_port}/callback"

    print("Registering Hiwar with Fo...")
    client_id = fo_auth.register(redirect_uri)
    verifier, challenge = fo_auth.make_pkce()
    state = secrets.token_urlsafe(16)
    url = fo_auth.authorize_url(client_id, redirect_uri, challenge, state)

    print("\nOpening your browser to sign in to Wajo. If it doesn't open, visit:\n")
    print(url, "\n")
    webbrowser.open(url)

    print("Waiting for you to finish signing in...")
    while "code" not in result and "error" not in result:
        server.handle_request()

    if "error" in result:
        sys.exit(f"Sign-in failed: {result}")
    if result.get("state") != state:
        sys.exit("Sign-in failed: state mismatch (possible tampering). Try again.")

    server.server_close()
    fo_auth.exchange_code(client_id, result["code"], verifier, redirect_uri)
    print(f"Done. Saved to {fo_auth.TOKENS_FILE.name}. The backend picks it up automatically.")


if __name__ == "__main__":
    main()
