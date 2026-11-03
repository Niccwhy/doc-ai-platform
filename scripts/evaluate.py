#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
doc-ai-platform 评测脚本

用途：批量上传样本集 + 逐个模板填表，计算平均准确率与平均响应时间。
赛题评分口径：信息提取准确率 > 80%，每个文档响应时间 ≤ 90 秒。

用法：
    python scripts/evaluate.py \
        --base-url http://localhost:8080 \
        --samples /path/to/samples/*.docx /path/to/samples/*.md ... \
        --templates /path/to/templates/t1.xlsx /path/to/templates/t2.xlsx ... \
        --answers /path/to/answers/t1_answer.json ...

依赖：仅标准库（json / urllib / argparse / time / glob），无需 pip install。

输出：控制台打印每模板准确率与耗时，末尾汇总平均准确率 + 平均响应时间。

注意：
  - 样例答案 JSON 格式：{ "字段名": "期望值", ... }，与模板表头字段一一对应。
  - 若某模板无答案文件，则该模板跳过准确率计算（仅记耗时）。
"""

import argparse
import glob
import json
import time
import urllib.request
import urllib.error


def http_json(url, method="GET", data=None, timeout=120):
    """发送 JSON 请求，返回解析后的 dict。"""
    body = None
    headers = {}
    if data is not None:
        body = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def multipart_upload(url, file_path, field="files", timeout=300):
    """上传单个文件（multipart/form-data）。"""
    import uuid
    boundary = "----wb" + uuid.uuid4().hex
    filename = file_path.split("/")[-1].split("\\")[-1]
    with open(file_path, "rb") as f:
        content = f.read()

    parts = []
    parts.append(("--" + boundary + "\r\n").encode())
    parts.append(
        ('Content-Disposition: form-data; name="%s"; filename="%s"\r\n' % (field, filename)).encode()
    )
    parts.append(b"Content-Type: application/octet-stream\r\n\r\n")
    parts.append(content)
    parts.append(("\r\n--" + boundary + "--\r\n").encode())

    body = b"".join(parts)
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "multipart/form-data; boundary=" + boundary},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def wait_task(base_url, task_id, poll_interval=1.0, timeout=600):
    """轮询任务状态直到终态，返回 (status, result_dict, elapsed_seconds)。"""
    start = time.time()
    while True:
        r = http_json("%s/api/tasks/%s" % (base_url, task_id))
        status = r.get("data", {}).get("status")
        if status in ("SUCCESS", "FAILED"):
            return status, r.get("data", {}), time.time() - start
        if time.time() - start > timeout:
            return "TIMEOUT", r.get("data", {}), time.time() - start
        time.sleep(poll_interval)


def main():
    parser = argparse.ArgumentParser(description="doc-ai-platform 评测脚本")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--samples", nargs="+", required=True, help="样本文件（可含通配符）")
    parser.add_argument("--templates", nargs="+", required=True, help="模板表格文件")
    parser.add_argument("--answers", nargs="*", default=[], help="样例答案 JSON，与模板一一对应")
    args = parser.parse_args()

    base = args.base_url.rstrip("/")

    # 展开样本通配符
    samples = []
    for pat in args.samples:
        samples.extend(glob.glob(pat))
    if not samples:
        print("[错误] 未匹配到任何样本文件")
        return 1

    print("=" * 60)
    print("阶段 1：批量上传样本集（%d 个文件）" % len(samples))
    print("=" * 60)

    # 批量上传（逐个上传到 /api/ingest，暂不支持一次多文件时用单文件循环）
    for f in samples:
        r = multipart_upload("%s/api/ingest" % base, f, field="files")
        task_id = r.get("data", {}).get("taskId")
        status, _, elapsed = wait_task(base, task_id)
        print("  [%s] %s  耗时 %.1fs" % (status, f.split("/")[-1].split("\\")[-1], elapsed))

    print()
    print("=" * 60)
    print("阶段 2：逐个模板填表")
    print("=" * 60)

    accuracies = []
    durations = []
    for i, tmpl in enumerate(args.templates):
        name = tmpl.split("/")[-1].split("\\")[-1]
        start = time.time()
        r = multipart_upload("%s/api/fill" % base, tmpl, field="file")
        elapsed = time.time() - start
        durations.append(elapsed)

        fields = r.get("data", {}).get("fields", [])
        filled = {f.get("fieldName"): f.get("value") for f in fields}

        acc = None
        # 有答案则算准确率
        if i < len(args.answers) and args.answers[i]:
            with open(args.answers[i], "r", encoding="utf-8") as fa:
                answer = json.load(fa)
            correct = sum(1 for k, v in answer.items() if filled.get(k) == v)
            acc = correct / len(answer) if answer else 0.0
            accuracies.append(acc)
            print("  [%s] 准确率 %.1f%% (%d/%d)  耗时 %.1fs"
                  % (name, acc * 100, correct, len(answer), elapsed))
        else:
            print("  [%s] 耗时 %.1fs（无答案，跳过准确率）" % (name, elapsed))

    print()
    print("=" * 60)
    print("汇总")
    print("=" * 60)
    if accuracies:
        avg_acc = sum(accuracies) / len(accuracies) * 100
        print("平均准确率：%.1f%%" % avg_acc)
        print("达标（>80%%）：%s" % ("是" if avg_acc > 80 else "否"))
    else:
        print("平均准确率：无（缺答案文件）")
    avg_dur = sum(durations) / len(durations) if durations else 0
    print("平均响应时间：%.1fs" % avg_dur)
    print("达标（≤90s）：%s" % ("是" if avg_dur <= 90 else "否"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
