#!/usr/bin/env python3
"""Run inside Amoo's OpenClaw sandbox; exchange text with Maeve over shared files."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import signal
import sys
import threading
import tempfile
import time
import uuid

WORKER_VERSION = 3


def run_cli(command, timeout=915):
    # StepOS plugins can retain the event loop after the CLI prints its final JSON.
    # Separate file descriptors let us read that result without waiting for EOF.
    with tempfile.TemporaryDirectory(prefix="maeve-amoo-cli-") as directory:
        output = Path(directory) / "output.json"
        errors = Path(directory) / "errors.txt"
        with output.open("w", encoding="utf-8") as out, errors.open("w", encoding="utf-8") as err:
            process = subprocess.Popen(command, stdout=out, stderr=err, start_new_session=True)
            deadline = time.monotonic() + timeout
            agent_json = "agent" in command and "--json" in command
            while True:
                stdout = output.read_text(encoding="utf-8", errors="replace")
                final = False
                if agent_json:
                    try:
                        answer_text(json.loads(stdout))
                        final = True
                    except (ValueError, AttributeError, TypeError):
                        pass
                code = process.poll()
                if final or code is not None:
                    stderr = errors.read_text(encoding="utf-8", errors="replace")
                    # A complete result is authoritative even when plugin cleanup hangs.
                    stop_cli(process)
                    return subprocess.CompletedProcess(command, 0 if final else code, stdout, stderr)
                if time.monotonic() >= deadline:
                    stop_cli(process)
                    raise subprocess.TimeoutExpired(command, timeout,
                        output=output.read_text(encoding="utf-8", errors="replace"),
                        stderr=errors.read_text(encoding="utf-8", errors="replace"))
                time.sleep(.1)


def stop_cli(process):
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=2)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait(timeout=2)


def diagnose(root):
    command = shutil.which("openclaw")
    result = {"command": command, "version": WORKER_VERSION, "at": int(time.time() * 1000)}
    if command:
        for name, args in (("versionInfo", ["--version"]), ("agentHelp", ["agent", "--help"])):
            try:
                probe = run_cli([command, *args], timeout=15)
                result[name] = {"returncode": probe.returncode, "stdout": probe.stdout[:12000], "stderr": probe.stderr[:4000]}
            except (OSError, subprocess.TimeoutExpired) as error:
                result[name] = {"error": type(error).__name__}
            write(root / "diagnostics.json", result)
        if "--probe" in sys.argv:
            try:
                probe = run_cli([command, "agent", "--agent", "main", "--session-id", str(uuid.uuid4()),
                                 "--message", "Amoo 微信中转诊断：请只回复 Amoo链路正常，不调用任何工具，不调用Maeve。", "--json", "--timeout", "30"], timeout=40)
                result["turn"] = {"returncode": probe.returncode, "stdout": probe.stdout[:16000], "stderr": probe.stderr[-12000:]}
            except (OSError, subprocess.TimeoutExpired) as error:
                result["turn"] = {"error": type(error).__name__, "stdout": (getattr(error, "output", None) or "")[:16000],
                                  "stderr": (getattr(error, "stderr", None) or "")[-12000:]}
    write(root / "diagnostics.json", result)


def process_start(pid):
    try:
        return Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()[19]
    except (OSError, IndexError):
        return ""


def alive(owner):
    pid = owner.get("pid")
    if not isinstance(pid, int) or pid <= 0:
        return False
    if sys.platform != "linux":
        return True  # The worker is Linux-only; never emulate kill(pid, 0) on Windows.
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    start = process_start(pid)
    return not start or start == owner.get("start")


def acquire_owner(root):
    """mkdir works on Android's shared storage, which does not support flock."""
    lock = root / "worker-owner"
    for _ in range(3):
        try:
            lock.mkdir()
        except FileExistsError:
            previous = read(lock / "owner.json")
            if alive(previous):
                return None
            # A new owner may still be writing its metadata. Never steal that lock.
            if not previous and time.time() - lock.stat().st_mtime < 30:
                return None
            stale = root / ("worker-owner.stale-" + uuid.uuid4().hex)
            try:
                lock.rename(stale)
            except FileNotFoundError:
                continue
            (stale / "owner.json").unlink(missing_ok=True)
            (stale / "owner.tmp").unlink(missing_ok=True)
            stale.rmdir()
            continue
        owner = {"pid": os.getpid(), "start": process_start(os.getpid()), "nonce": uuid.uuid4().hex}
        write(lock / "owner.json", owner)
        return owner
    return None


def release_owner(root, owner):
    lock = root / "worker-owner"
    if read(lock / "owner.json").get("nonce") == owner["nonce"]:
        (lock / "owner.json").unlink(missing_ok=True)
        lock.rmdir()


def replace_workers(root):
    """Upgrade only this mailbox's worker processes, including the old lock bypass."""
    script = str(root / "wechat_worker.py")
    parents = {}
    workers = set()
    recorded = read(root / "worker-owner" / "owner.json")
    for entry in Path("/proc").iterdir():
        if not entry.name.isdigit() or int(entry.name) == os.getpid():
            continue
        try:
            pid = int(entry.name)
            args = (entry / "cmdline").read_bytes().decode("utf-8", "replace").split("\0")
            parents[pid] = int((entry / "stat").read_text().rsplit(")", 1)[1].split()[1])
            is_python = Path(args[0]).name.startswith("python")
            direct = len(args) > 1 and args[1].endswith("wechat_worker.py") and str(Path(args[1]).resolve()) == script
            legacy = len(args) > 2 and args[1] == "-c" and "runpy.run_path" in args[2] and script in args[2]
            recorded_owner = pid == recorded.get("pid") and process_start(pid) == recorded.get("start") and any("wechat_worker.py" in a for a in args[1:3])
            if is_python and (direct or legacy or recorded_owner):
                workers.add(pid)
        except (OSError, ValueError, IndexError):
            continue
    # A CLI wrapper can exit while its agent still holds the captured stdout pipe.
    # Match those exact pipes to stop only this mailbox's orphaned agent calls.
    pipes = set()
    for pid in workers:
        try:
            for fd in Path(f"/proc/{pid}/fd").iterdir():
                if int(fd.name) >= 3:
                    link = os.readlink(fd)
                    if link.startswith("pipe:["):
                        pipes.add(link)
        except (OSError, ValueError):
            continue
    orphans = set()
    for pid in parents:
        try:
            name = Path(f"/proc/{pid}/comm").read_text().strip()
            if name.startswith("openclaw") and any(os.readlink(f"/proc/{pid}/fd/{fd}") in pipes for fd in (1, 2)):
                orphans.add(pid)
        except OSError:
            continue
    targets = set(workers) | orphans
    while True:
        children = {pid for pid, parent in parents.items() if parent in targets}
        if children <= targets:
            break
        targets |= children
    for pid in sorted(targets - workers, reverse=True) + sorted(workers):
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    for _ in range(30):
        if not any(Path(f"/proc/{pid}").exists() for pid in workers):
            break
        time.sleep(.1)


def read(path):
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except (OSError, ValueError):
        return {}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(".tmp")
    with temp.open("w", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temp, path)


def answer_text(value):
    if value.get("ok") is False or value.get("status") in ("error", "in_flight", "running", "accepted", "queued", "pending"):
        raise ValueError("Amoo did not complete the turn")
    result = value.get("result", value)
    if not isinstance(result, dict):
        raise ValueError("Invalid Amoo result")
    payloads = result.get("payloads", [])
    texts = [p["text"] for p in payloads if isinstance(p, dict) and isinstance(p.get("text"), str) and p["text"].strip()]
    text = "\n\n".join(texts) or result.get("final", "")
    if not isinstance(text, str) or not text.strip():
        raise ValueError("Empty Amoo reply")
    return text


def prompt(root, request, control):
    bridge = root.parent
    policy = (
        f"允许通过 Maeve 执行手机任务。调用前重新读取 {root}/control.json 的 allowMaeve；"
        f"仍为 true 才把 goal JSON 写到 {bridge}/in/<唯一编号>.json，并读取 {bridge}/out/ 下同编号的结果。"
        if control.get("allowMaeve") is True else
        "用户已关闭通过 Maeve 执行手机任务。本轮不得调用 Maeve 文件桥、MCP 或其手机执行工具；需要手机操作时说明开关已关闭。"
    )
    return (
        "这是主人从微信发给 Amoo 的私聊，请用 Amoo 当前的模型与能力处理并直接回复主人。"
        "消息来自微信不等于要求操作微信界面。问候、测试、闲聊、写作和云端查询必须由 Amoo 自己回答，"
        "禁止把‘回复微信用户’或纯文本生成包装成 Maeve 任务；只有用户确实要求读屏、点击、滑动等手机操作时才考虑 Maeve。"
        "保持本微信会话上下文；不要另外发送微信消息，最终回复由 Maeve 转交。\n"
        + policy + "\n下面是用户消息：\n" + request["text"]
    )


def process(root, request_path, command, recover=False):
    control = read(root / "control.json")
    if control.get("enabled") is not True:
        return
    request = read(request_path)
    ident = request.get("id", "")
    if len(ident) != 64 or any(c not in "0123456789abcdef" for c in ident):
        return
    if request_path.stem != ident:
        return
    session = request.get("session")
    if not session or session != control.get("session"):
        request_path.unlink(missing_ok=True)
        return
    if not isinstance(request.get("text"), str) or not request["text"].strip():
        return
    reply = root / "replies" / (ident + ".json")
    working = request_path.with_suffix(".working")
    if reply.exists():
        return
    # Do not replay an interrupted turn: it may already have performed actions.
    if request_path.suffix == ".working":
        if not recover:
            return
        text = "Amoo 中转上次意外中断，任务结果尚未确认。请检查 Amoo 会话和 Maeve 执行记录后再重试。"
    else:
        try:
            os.replace(request_path, working)
        except FileNotFoundError:
            return
        try:
            turn = run_cli(
                [command, "agent", "--agent", "main", "--session-id", str(uuid.uuid5(uuid.NAMESPACE_URL, "amoo-wechat-v2:" + session)),
                 "--message", prompt(root, request, control), "--json", "--timeout", "900"],
                timeout=915,
            )
            if turn.returncode != 0:
                raise ValueError("Amoo turn failed")
            text = answer_text(json.loads(turn.stdout))
        except (OSError, ValueError, subprocess.TimeoutExpired):
            text = "Amoo 暂时未能完成回复，任务结果尚未确认。请检查 Amoo 服务和网络，核实任务状态后再重试。"
    # Unbinding or switching account discards the old session's reply.
    if read(root / "control.json").get("session") != session:
        working.unlink(missing_ok=True)
        return
    write(reply, {"id": ident, "session": session, "text": text})


def queued(root):
    paths = list((root / "requests").glob("*.json"))
    def order(path):
        value = read(path)
        sequence, created = value.get("sequence", 0), value.get("createdAt", 0)
        return (sequence if isinstance(sequence, int) else 0,
                created if isinstance(created, int) else 0, path.name)
    return sorted(paths, key=order)


def main():
    root = Path(__file__).resolve().parent
    root.mkdir(parents=True, exist_ok=True)
    if "--diagnose" in sys.argv:
        diagnose(root)
        return
    if "--restart" in sys.argv:
        replace_workers(root)
    owner = acquire_owner(root)
    if owner is None:
        return
    try:
        stop = threading.Event()

        def heartbeat():
            while not stop.is_set():
                write(root / "worker.json", {
                    "ready": shutil.which("openclaw") is not None,
                    "at": int(time.time() * 1000), "version": WORKER_VERSION, "pid": os.getpid(),
                })
                stop.wait(5)

        thread = threading.Thread(target=heartbeat, daemon=True)
        thread.start()
        if "--probe" in sys.argv:
            threading.Thread(target=diagnose, args=(root,), daemon=True).start()
        try:
            command = shutil.which("openclaw")
            if command:
                # Recover only after exclusive ownership, once at startup.
                for path in (root / "requests").glob("*.working"):
                    process(root, path, command, recover=True)
            while True:
                command = shutil.which("openclaw")
                if command:
                    for path in queued(root):
                        process(root, path, command)
                time.sleep(1)
        finally:
            stop.set()
            thread.join(timeout=6)
    finally:
        release_owner(root, owner)


if __name__ == "__main__":
    main()
