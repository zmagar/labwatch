#!/usr/bin/env python3
"""Run real Firefox DOM and outage checks against the packaged app; stdlib only.

Build with mvn package first. Requires Java 21+, Python 3 and Firefox.
All servers bind loopback; fixtures and dummy credentials replace real infrastructure.
"""
import argparse
import http.client
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[3]
WEB = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--java", default="java")
parser.add_argument("--firefox", default="firefox")
parser.add_argument("--output", type=Path, default=ROOT / "target/browser-verification")
args = parser.parse_args()
args.output = args.output.resolve()
args.output.mkdir(parents=True, exist_ok=True)
state = {"docker": "online", "proxmox": "online", "api": "online"}
stalls = {"docker": 0, "proxmox": 0, "api": 0}
stop = threading.Event()
finished = threading.Event()
result = {}
app_port = None
fixtures = {
    "docker": (ROOT / "src/test/resources/docker-containers.json").read_bytes(),
    "proxmox": (ROOT / "src/test/resources/proxmox-resources.json").read_bytes(),
}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def reply(self, status, body, content_type="application/json"):
        if isinstance(body, str):
            body = body.encode()
        try:
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        if self.server is upstream:
            source = "docker" if self.path.startswith("/containers/json") else "proxmox"
            if state[source] == "hang":
                stalls[source] += 1
                stop.wait()  # Receive the request but send no headers or body.
                return
            if state[source] == "forbidden":
                return self.reply(403, "<html>Forbidden</html>", "text/html")
            return self.reply(200, fixtures[source])
        if self.path == "/control":
            return self.reply(200, json.dumps({"state": state, "stalls": stalls}))
        if self.path in ("/tests.html", "/tests.js"):
            file = WEB / self.path[1:]
            mime = "text/html" if file.suffix == ".html" else "text/javascript"
            return self.reply(200, file.read_bytes(), mime)
        if self.path == "/api/status":
            if state["api"] == "error":
                return self.reply(503, "temporarily unavailable", "text/plain")
            if state["api"] == "drop":
                self.connection.shutdown(socket.SHUT_RDWR)
                self.connection.close()
                return
            if state["api"] == "hang":
                stalls["api"] += 1
                stop.wait()
                return
        connection = http.client.HTTPConnection("127.0.0.1", app_port, timeout=5)
        try:
            connection.request("GET", self.path)
            response = connection.getresponse()
            self.reply(response.status, response.read(), response.getheader("Content-Type", "text/plain"))
        except OSError as error:
            self.reply(502, str(error), "text/plain")
        finally:
            connection.close()

    def do_POST(self):
        data = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        if self.path == "/control":
            for key, value in data.items():
                if key not in state or value not in {"online", "hang", "error", "drop", "forbidden"}:
                    return self.reply(400, "invalid test control", "text/plain")
            state.update(data)
            return self.reply(200, json.dumps(state))
        if self.path == "/results":
            result.update(data)
            (args.output / "results.json").write_text(json.dumps(data, indent=2))
            self.reply(200, "{}")
            finished.set()
            return
        if self.path == "/progress":
            print(data["message"], flush=True)
            return self.reply(200, "{}")
        self.reply(404, "{}")


def terminate(process):
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


upstream = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
proxy = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
for server in (upstream, proxy):
    threading.Thread(target=server.serve_forever, daemon=True).start()
app = browser = None
try:
    with tempfile.TemporaryDirectory(prefix="labwatch-browser-") as tmp:
        work = Path(tmp)
        shutil.copy(ROOT / "src/test/resources/proxmox-config.yaml", work / "config.yaml")
        env = os.environ.copy()
        env.update({
            "LABWATCH_PROFILE": "private", "LABWATCH_ADDR": "127.0.0.1:0",
            "LABWATCH_POLL_INTERVAL": "1s", "LABWATCH_CONNECT_TIMEOUT": "5s",
            "LABWATCH_REQUEST_TIMEOUT": "10s",
            "DOCKER_HOST": f"http://127.0.0.1:{upstream.server_port}",
            "PROXMOX_URL": f"http://127.0.0.1:{upstream.server_port}",
            "PROXMOX_TOKEN_ID": "test@pve!test", "PROXMOX_TOKEN_SECRET": "fixture-secret",
            "PROXMOX_INSECURE_TLS": "false",
        })
        with (args.output / "app.log").open("w") as app_log, (args.output / "firefox.log").open("w") as browser_log:
            app = subprocess.Popen([args.java, "-jar", str(ROOT / "target/labwatch.jar")],
                                   cwd=work, env=env, stdout=app_log, stderr=subprocess.STDOUT)
            # Jetty reports the actual ephemeral listen port in its startup log.
            import re
            for _ in range(100):
                log = (args.output / "app.log").read_text()
                match = re.search(r"Listening on http://[^:]+:(\d+)", log)
                if match:
                    app_port = int(match[1])
                    break
                if app.poll() is not None:
                    raise RuntimeError("labwatch exited; see app.log")
                time.sleep(0.1)
            if app_port is None:
                raise RuntimeError("could not find labwatch port; see app.log")
            profile = work / "firefox-profile"
            profile.mkdir()
            (profile / "user.js").write_text('user_pref("browser.shell.checkDefaultBrowser", false);\n'
                                            'user_pref("browser.startup.page", 0);\n'
                                            'user_pref("datareporting.policy.dataSubmissionEnabled", false);\n')
            url = f"http://127.0.0.1:{proxy.server_port}/tests.html"
            print(f"Running browser checks at {url}; app port {app_port}", flush=True)
            browser = subprocess.Popen([args.firefox, "--headless", "--no-remote", "--profile", str(profile), url],
                                       stdout=browser_log, stderr=subprocess.STDOUT)
            if not finished.wait(150):
                raise RuntimeError("browser checks did not finish; see firefox.log and app.log")
            print(json.dumps(result, indent=2), flush=True)
            # Stop before removing temporary working directories and browser profile.
            terminate(browser)
            terminate(app)
finally:
    terminate(browser)
    terminate(app)
    stop.set()
    for server in (upstream, proxy):
        server.shutdown()
        server.server_close()
raise SystemExit(0 if result.get("ok") else 1)
