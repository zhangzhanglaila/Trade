#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
中哈贸易系统 · 服务器上传包打包脚本（在开发机上运行）

用途
----
把这个项目打包成一个可以直接 scp/rsync 到服务器的归档文件，
自动剔除「上传过去反而会坏事」或「体积大且服务器上会重建」的内容。

为什么需要它（三个坑）
----------------------
1. node_modules（1.08 GB / 10 个 Windows .node 原生二进制）
   在 Windows 上装的依赖，拷到 Linux 后原生模块无法加载，
   表现为 `npm run build` 报 "Cannot find module .../binding.node" 之类。
   正确做法：排除它，到服务器上执行 `npm ci`（或 npm install）。

2. .git 目录
   项目含 10 个 git 仓库；其中 models/qwen3.5-9b-kh-trade-qa/.git 达 234 MB
   （LFS 对象）。服务器只需要工作区文件，不需要历史。

3. 构建产物 target / dist
   在 Windows 上编译的 .class 与 jar 在 Linux 上不可用（且 jar 里可能含
   路径相关的资源）。服务器上重新 `bash deploy/build.sh` 即可。

用法
----
    python deploy/package-for-server.py                    # 打包（不含模型）
    python deploy/package-for-server.py --with-models      # 含 models/（+18.1 GB）
    python deploy/package-for-server.py --with-dist        # 含已构建的前端 dist
    python deploy/package-for-server.py --dry-run          # 只预览，不写文件
    python deploy/package-for-server.py --no-compress      # 输出 .tar 不压缩

输出
----
    默认写到 <项目根的上一级>/_upload/trade-server-<日期>.tar.gz
"""

from __future__ import annotations

import argparse
import datetime
import os
import sys
import tarfile

# ---------------------------------------------------------------- 排除规则

# 任何层级出现这些目录名，整棵子树都排除
EXCLUDE_DIRS = {
    'node_modules',
    'target',
    '__pycache__',
    '.git',
    '.idea',
    '.claude',
    '.mvn',
    '.gradle',
    '.vscode',
    '.vs',
    'venv',
    '.venv',
    'site-packages',
    'logs',
    '.pytest_cache',
    '.mypy_cache',
    'archive',
}

# 默认排除、但可用 --with-dist 放行的目录
OPTIONAL_DIRS = {
    'dist',
}

# 这些文件名（精确匹配）排除
EXCLUDE_FILES = {
    '.DS_Store',
    'Thumbs.db',
    'desktop.ini',
    '.env',              # 真实凭证文件不入包（含 config/.env）；服务器用 config/.env.example 现填
    '._____temp',
}

# 这些后缀排除
EXCLUDE_EXTS = {'.pyc', '.pyo', '.iml', '.log', '.bak'}

# 这些顶层目录可用 --with-models 放行（否则整体跳过）
MODEL_DIRS = {'models'}


def human(n: int) -> str:
    f = float(n)
    for unit in ('B', 'KB', 'MB', 'GB'):
        if f < 1024 or unit == 'GB':
            return '%.2f %s' % (f, unit)
        f /= 1024.0
    return '%.2f GB' % f


def is_excluded(rel: str, name: str, args) -> bool:
    """rel 是相对项目根的 posix 路径，name 是当前条目名。"""
    parts = rel.split('/')
    for p in parts:
        if p in EXCLUDE_DIRS:
            return True
        if p in OPTIONAL_DIRS and not args.with_dist:
            return True
        if p in MODEL_DIRS and not args.with_models:
            return True
    if name in EXCLUDE_FILES:
        return True
    ext = os.path.splitext(name)[1].lower()
    if ext in EXCLUDE_EXTS:
        return True
    return False


def collect(root: str, args):
    """返回 (待打包文件列表, 被排除项统计)。"""
    keep, excluded = [], {}
    for dirpath, dirnames, filenames in os.walk(root):
        rel_dir = os.path.relpath(dirpath, root).replace('\\', '/')
        if rel_dir == '.':
            rel_dir = ''

        kept_dirs = []
        for d in dirnames:
            rel = ('%s/%s' % (rel_dir, d)).lstrip('/')
            if is_excluded(rel, d, args):
                # 记录排除体积
                p = os.path.join(dirpath, d)
                sz = 0
                for dp, _dn, fns in os.walk(p):
                    for f in fns:
                        try:
                            sz += os.path.getsize(os.path.join(dp, f))
                        except OSError:
                            pass
                key = d if d in EXCLUDE_DIRS | OPTIONAL_DIRS | MODEL_DIRS else 'other'
                excluded[key] = excluded.get(key, 0) + sz
            else:
                kept_dirs.append(d)
        dirnames[:] = kept_dirs

        for f in filenames:
            rel = ('%s/%s' % (rel_dir, f)).lstrip('/')
            if is_excluded(rel, f, args):
                try:
                    sz = os.path.getsize(os.path.join(dirpath, f))
                except OSError:
                    sz = 0
                ext = os.path.splitext(f)[1].lower()
                key = 'file:' + (ext if ext else '(无后缀)')
                excluded[key] = excluded.get(key, 0) + sz
                continue
            keep.append(os.path.join(dirpath, f))
    return keep, excluded


def main() -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    default_root = os.path.dirname(here)          # deploy/ 的上一级 = 项目根

    ap = argparse.ArgumentParser(
        description='打包项目以便上传到 Linux 服务器（自动剔除 node_modules/.git/target 等）')
    ap.add_argument('--root', default=default_root, help='项目根目录（默认：脚本上一级）')
    ap.add_argument('--out', default=None,
                    help='输出归档路径（默认 <项目根上级>/_upload/trade-server-<日期>.tar.gz）')
    ap.add_argument('--with-models', action='store_true',
                    help='包含 models/ 目录（约 18.1 GB，仅当要部署大模型推理时需要）')
    ap.add_argument('--with-dist', action='store_true',
                    help='包含已构建的前端 dist/（若想在本地构建后只上传产物）')
    ap.add_argument('--no-compress', action='store_true', help='输出未压缩的 .tar')
    ap.add_argument('--dry-run', action='store_true', help='只统计与预览，不写归档')
    args = ap.parse_args()

    root = os.path.abspath(args.root)
    if not os.path.isdir(root):
        print('错误：项目根不存在 -> %s' % root)
        return 2

    print('项目根 : %s' % root)
    files, excluded = collect(root, args)

    total = 0
    per_top = {}
    for p in files:
        try:
            sz = os.path.getsize(p)
        except OSError:
            continue
        total += sz
        rel = os.path.relpath(p, root).replace('\\', '/')
        top = rel.split('/')[0] if '/' in rel else '(根目录文件)'
        per_top[top] = per_top.get(top, 0) + sz

    print()
    print('=== 将打包的内容 ===')
    for k in sorted(per_top, key=lambda x: -per_top[x]):
        print('  %12s  %s' % (human(per_top[k]), k))
    print('  %12s  合计 %d 个文件' % (human(total), len(files)))

    print()
    print('=== 已排除的内容 ===')
    if not excluded:
        print('  （无）')
    for k in sorted(excluded, key=lambda x: -excluded[x]):
        if k.startswith('file:'):
            print('  %12s  %s' % (human(excluded[k]), k[5:] + ' 后缀文件'))
        else:
            note = ''
            if k == 'models' and not args.with_models:
                note = '  ← 加 --with-models 可包含'
            if k == 'dist' and not args.with_dist:
                note = '  ← 加 --with-dist 可包含'
            if k == 'node_modules':
                note = '  ← 服务器上必须 npm ci 重装（Windows 原生模块在 Linux 不可用）'
            if k in ('target',):
                note = '  ← 服务器上重新 bash deploy/build.sh'
            print('  %12s  %s/%s' % (human(excluded[k]), k, note))

    if args.dry_run:
        print()
        print('--dry-run：未写入任何文件。')
        return 0

    out = args.out
    if not out:
        base = os.path.dirname(root)
        out = os.path.join(base, '_upload',
                           'trade-server-%s.%s' % (
                               datetime.date.today().isoformat(),
                               'tar' if args.no_compress else 'tar.gz'))
    outdir = os.path.dirname(os.path.abspath(out))
    if outdir:
        os.makedirs(outdir, exist_ok=True)

    mode = 'w:tar' if args.no_compress else 'w:gz'
    print()
    print('正在写入 %s ...' % out)
    print('（压缩约需数分钟，2.5 GB 文本数据通常可压到 ~700 MB）')

    try:
        with tarfile.open(out, mode, format=tarfile.PAX_FORMAT) as tf:
            done = 0
            for p in files:
                rel = os.path.relpath(p, root).replace('\\', '/')
                # 归档内统一放在 trade/ 顶层，解包即得 /opt/trade/trade/... 的直观结构
                tf.add(p, arcname='trade/' + rel, recursive=False)
                done += 1
                if done % 5000 == 0:
                    print('  已加入 %d / %d' % (done, len(files)))
    except Exception as e:                                     # noqa: BLE001
        print('打包失败：%s' % e)
        return 1

    asize = os.path.getsize(out)
    print()
    print('=== 完成 ===')
    print('  归档    : %s' % out)
    print('  原始大小: %s' % human(total))
    print('  归档大小: %s  (压缩率 %.0f%%)' % (human(asize), 100.0 * asize / max(total, 1)))
    print()
    print('上传示例（在开发机上执行）：')
    print('  scp "%s" user@server:/tmp/' % out)
    print()
    print('服务器上解包：')
    print('  sudo mkdir -p /opt && cd /opt')
    print('  sudo tar -xzf /tmp/%s' % os.path.basename(out))
    print('  sudo chown -R $USER:$USER /opt/trade')
    print('  cd /opt/trade && chmod +x deploy/*.sh && bash deploy/check-health.sh')
    print()
    print('解包后请继续执行 docs/服务器部署指南.md 的 §4.2 起各步。')
    return 0


if __name__ == '__main__':
    sys.exit(main())
