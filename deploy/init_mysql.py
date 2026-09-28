#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
中哈贸易系统 · MySQL 数据库一键初始化

做四件事：
  1. 建库建表（执行 src/backend-main/src/main/resources/db/schema.sql）
  2. 导入新闻语料   data/04_采集_新闻语料/*.csv        -> news_articles
  3. 导入海关明细   data/01_原始_海关进出口明细/**/*.csv -> trade_records
  4. 由真实数据聚合出统计表（而不是用 Math.random() 造假数据）：
       country_monthly_trade / trade_method_stats / corpus_entries / comprehensive_data

用法：
  python init_mysql.py --password 你的密码
  python init_mysql.py --password xxx --skip-trade-records   # 只导语料+统计，跳过 200 万行明细
  python init_mysql.py --password xxx --news-only            # 只导新闻语料

依赖：pymysql
"""
import argparse
import csv
import glob
import io
import itertools
import os
import sys
import time
from collections import defaultdict

try:
    import pymysql
except ImportError:
    sys.exit('缺少依赖：pip install pymysql')

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(HERE, '..'))
DATA_DIR = os.path.join(PROJECT_ROOT, 'data')
RAW_TRADE_DIR = os.path.join(DATA_DIR, '01_原始_海关进出口明细')
NEWS_DIR = os.path.join(DATA_DIR, '04_采集_新闻语料')
SCHEMA_FILE = os.path.join(PROJECT_ROOT, 'src', 'backend-main',
                           'src', 'main', 'resources', 'db', 'schema.sql')
DB_NAME = 'foreign_trade_qa_db'
BATCH = 5000

COUNTRIES = ['哈萨克斯坦', '乌兹别克斯坦', '吉尔吉斯斯坦', '土库曼斯坦', '塔吉克斯坦']


def log(msg):
    print(msg, flush=True)


# ----------------------------------------------------------------------
# 工具函数
# ----------------------------------------------------------------------
class _NoNulFile(object):
    """包装文本文件对象，按行剔除混入的 NUL 字节。

    为什么需要：`data/01_原始_海关进出口明细` 里有 34/295 个 CSV 正文含 0x00。
    多数只是某个字段的值恰好是一个 NUL（`"0","\x00","359,582"`）；另有两个文件
    （哈萨克斯坦/出口/2021.7—8.csv、2021.9—10.csv，二者互为副本）中段有约
    718 KB 的连续零填充，属文件损坏。
    Python 的 csv 模块碰到 NUL 会直接抛 `_csv.Error: line contains NUL`，让整批
    导入中断，所以在这里剥掉：字段值退化为空串（由 to_number() 归为 None），
    整行皆 NUL 的会退化空行并随即被 DictReader 跳过。
    """

    def __init__(self, fh):
        self._fh = fh

    def __iter__(self):
        return self

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return self._fh.__exit__(*exc)

    def __next__(self):
        line = next(self._fh)
        return line.replace('\x00', '') if '\x00' in line else line

    def __getattr__(self, name):
        return getattr(self._fh, name)


def open_csv(path):
    """打开 CSV，自动探测编码与 BOM；返回的句柄已剔除 NUL 字节。"""
    for enc in ('utf-8-sig', 'utf-8', 'gbk', 'gb18030'):
        try:
            with io.open(path, 'r', encoding=enc, newline='') as f:
                f.read(4096)
            return _NoNulFile(io.open(path, 'r', encoding=enc, newline='',
                                      errors='replace'))
        except (UnicodeDecodeError, LookupError):
            continue
    return _NoNulFile(io.open(path, 'r', encoding='utf-8', errors='replace',
                              newline=''))


def to_number(v):
    """'1,234,567.89' / '' / '-' -> Decimal 可用字符串。"""
    if v is None:
        return None
    v = str(v).strip().replace(',', '').replace('，', '')
    if v in ('', '-', '--', 'nan', 'NaN', 'NULL', '?'):
        return None
    try:
        float(v)
        return v
    except ValueError:
        return None


def to_int(v):
    if v is None:
        return None
    v = str(v).strip().replace(',', '')
    if v in ('', '-', '--', 'nan', 'NULL'):
        return None
    try:
        return int(float(v))
    except ValueError:
        return None


# 新闻日期形如：
#   '2025-04-27 12:32'（mofcom）
#   '15:45, 05 六月 2025 | GMT +5'（kazinform，中文月份 + GMT 尾巴）
CN_MONTHS = {'一月': 1, '二月': 2, '三月': 3, '四月': 4, '五月': 5, '六月': 6,
             '七月': 7, '八月': 8, '九月': 9, '十月': 10, '十一月': 11, '十二月': 12}


def parse_news_date(date_str, create_time):
    """尽最大努力解析新闻时间，失败则用 create_time（unix 秒）兜底。返回 'YYYY-MM-DD HH:MM:SS' 或 None。"""
    import re
    from datetime import datetime, timezone, timedelta
    s = (date_str or '').strip()
    if s:
        # 2025-04-27 12:32 / 2025-04-27
        m = re.match(r'(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?', s)
        if m:
            y, mo, d, hh, mm, ss = m.groups()
            return '%s-%s-%s %s:%s:%s' % (y, mo, d, hh or '00', mm or '00', ss or '00')
        # 15:45, 05 六月 2025 | GMT +5
        m = re.search(r'(\d{1,2})\s+([\d一二三四五六七八九十]+月)\s+(\d{4})', s)
        if m:
            day, mon_s, year = m.groups()
            mon = CN_MONTHS.get(mon_s) or CN_MONTHS.get('一' + mon_s[1:]) or None
            if mon is None:
                try:
                    mon = int(mon_s.replace('月', ''))
                except ValueError:
                    mon = 1
            hm = re.search(r'(\d{1,2}):(\d{2})', s)
            return '%s-%02d-%02d %s:%s:00' % (year, mon, int(day),
                                              (hm.group(1) if hm else '00'),
                                              (hm.group(2) if hm else '00'))
        # 2025/04/27
        m = re.match(r'(\d{4})/(\d{1,2})/(\d{1,2})', s)
        if m:
            y, mo, d = m.groups()
            return '%s-%02d-%02d 00:00:00' % (y, int(mo), int(d))
    if create_time:
        try:
            ts = int(float(str(create_time).strip()))
            if ts > 10 ** 12:
                ts //= 1000
            if 946684800 < ts < 4102444800:   # 2000-01-01 ~ 2100-01-01
                return datetime.fromtimestamp(ts, tz=timezone(timedelta(hours=8))) \
                    .strftime('%Y-%m-%d %H:%M:%S')
        except (ValueError, TypeError, OSError):
            pass
    return None


# ----------------------------------------------------------------------
# 步骤 1：建库建表
# ----------------------------------------------------------------------
def run_schema(conn, args):
    log('[1/4] 执行建表脚本: %s' % SCHEMA_FILE)
    with io.open(SCHEMA_FILE, 'r', encoding='utf-8') as f:
        sql = f.read()
    # 去掉整行注释后按分号切分
    lines = [ln for ln in sql.splitlines() if not ln.strip().startswith('--')]
    stmts = [s.strip() for s in '\n'.join(lines).split(';') if s.strip()]
    with conn.cursor() as cur:
        for s in stmts:
            cur.execute(s)
    conn.commit()
    log('      建表完成，共执行 %d 条语句' % len(stmts))


# ----------------------------------------------------------------------
# 步骤 2：导入新闻语料
# ----------------------------------------------------------------------
# 目录里其实混了两种格式的 CSV：
#   ① 爬虫导出格式（有表头）：
#        title,date,desc,tags,url,content_html,content_text,pics,create_time
#        → news_content_03-kazinform.csv / news_content_03-mofcom.csv
#   ② 归档格式（【没有表头】，首行即数据）：
#        id(哈希串), content(正文，标题可能以 **xxx** 内嵌在开头), (空列), date(YYYY-MM-DD)
#        → archived-khnews-related-filtered.csv，27,425 条
#
# 【为什么必须特殊处理 ②】
#   旧实现一律用 csv.DictReader 读取。对无表头文件，DictReader 会把**第一条记录
#   当成表头**，于是后续每一行的 row.get('title') / row.get('content_text') 全是
#   None，被 `if not title and not content: continue` 全部丢弃，最终打印 "0 行"
#   却不算错误——27,425 条归档语料就这样被静默跳过，前端「新闻语料管理」只剩
#   另外两个小 CSV 贡献的 652 条（两次 init 日志里的 200 + 450）。
#   注意 wc -l 数出来的是「行数」不是「记录数」：正文含换行，kazinform 5,917 行
#   其实只有 201 条记录，mofcom 3,621 行只有 451 条。
KNOWN_HEADERS = {'title', 'date', 'content_text', 'content', 'desc', 'url',
                 'content_html', 'tags', 'pics', 'create_time'}


def _split_title(content):
    """无表头归档语料没有独立标题列，尽力从正文里抽一个标题。

    优先级：正文开头的 **加粗标题** > 首行；都抽不出就返回 None。
    """
    import re
    s = (content or '').strip()
    if not s:
        return None
    m = re.match(r'^\*\*(.{2,120}?)\*\*[ \t]*\n?', s)
    if m:
        return m.group(1).strip()
    first = s.split('\n', 1)[0].strip().strip('*')
    if not first:
        return None
    return first[:120]


def _iter_news(p):
    """按行产出 (title, content, date_str, create_time)，兼容带/不带表头两种 CSV。"""
    with open_csv(p) as f:
        rd = csv.reader(f)
        first = next(rd, None)
        if first is None:
            return
        head = [(c or '').strip().lower() for c in first]
        if set(head) & KNOWN_HEADERS:
            # ① 带表头：首行是表头，按列名取值
            def pick(row, *names):
                for nm in names:
                    if nm in head:
                        i = head.index(nm)
                        if i < len(row):
                            return (row[i] or '').strip()
                return ''
            for row in rd:
                yield (pick(row, 'title'),
                       pick(row, 'content_text', 'content'),
                       pick(row, 'date'),
                       pick(row, 'create_time'))
        else:
            # ② 无表头归档：首行也是数据，按列位置取值（0=id 1=正文 2=保留 3=日期）
            for row in itertools.chain([first], rd):
                content = (row[1] if len(row) > 1 else '').strip()
                date_s = (row[3] if len(row) > 3 else '').strip()
                yield (_split_title(content), content, date_s, None)


def import_news(conn, args):
    files = sorted(glob.glob(os.path.join(NEWS_DIR, '*.csv')))
    if not files:
        log('[2/4] 未找到新闻 CSV，跳过')
        return 0
    log('[2/4] 导入新闻语料 -> news_articles')
    total = 0
    with conn.cursor() as cur:
        cur.execute('DELETE FROM news_articles')
        cur.execute('ALTER TABLE news_articles AUTO_INCREMENT = 1')
        for p in files:
            n = 0
            batch = []
            for title, content, date_s, create_time in _iter_news(p):
                if not title and not content:
                    continue
                pub = parse_news_date(date_s, create_time)
                batch.append((title[:512] or None, content or None, pub))
                n += 1
                if len(batch) >= BATCH:
                    cur.executemany(
                        'INSERT INTO news_articles (title, content, publish_time) VALUES (%s,%s,%s)',
                        batch)
                    total += len(batch)
                    batch = []
            if batch:
                cur.executemany(
                    'INSERT INTO news_articles (title, content, publish_time) VALUES (%s,%s,%s)',
                    batch)
                total += len(batch)
            log('      %-52s %6d 行' % (os.path.basename(p), n))
            if n == 0:
                log('        ⚠ %s 解析出 0 条，请检查该文件是否为未知表头/列序'
                    % os.path.basename(p))
    conn.commit()
    log('      新闻语料合计 %d 条' % total)
    return total


# ----------------------------------------------------------------------
# 步骤 3：导入海关明细 + 同步做聚合
# ----------------------------------------------------------------------
def import_trade_and_aggregate(conn, args):
    if not os.path.isdir(RAW_TRADE_DIR):
        log('[3/4] 未找到 %s，跳过' % RAW_TRADE_DIR)
        return
    log('[3/4] 导入海关明细 -> trade_records（同时聚合统计）')

    # 聚合容器
    monthly = defaultdict(lambda: {'eq': 0.0, 'ea': 0.0, 'iq': 0.0, 'ia': 0.0})
    method = defaultdict(float)
    countries_seen = set()

    files = []
    for country in COUNTRIES:
        for direction in ('出口', '进口'):
            d = os.path.join(RAW_TRADE_DIR, country, direction)
            if os.path.isdir(d):
                for f in sorted(glob.glob(os.path.join(d, '*.csv'))):
                    files.append((country, direction, f))
    log('      待处理 CSV：%d 个' % len(files))

    t0 = time.time()
    total = 0
    skipped_no_date = 0          # 因缺「数据年月」被丢弃的行数
    with conn.cursor() as cur:
        if not args.keep_trade_records:
            cur.execute('TRUNCATE TABLE trade_records')
        for idx, (country, direction, path) in enumerate(files, 1):
            inserted = 0
            batch = []
            with open_csv(path) as f:
                rd = csv.DictReader(f)
                if not rd.fieldnames:
                    continue
                for row in rd:
                    ym = to_int(row.get('数据年月'))
                    # 缺「数据年月」的行直接丢弃：没有月份就无法归属到任何时段，
                    # 灌进去只会得到 data_year_month 为空的残缺记录。
                    # 实测：哈萨克斯坦/出口/2021.{7—8,9—10}.csv 这两个损坏文件
                    # 的表头是 9 列（缺「数据年月」「注册地」「贸易方式」），
                    # 曾因此污染 11,600 行。详见该目录 _quarantine/README.md
                    if ym is None:
                        skipped_no_date += 1
                        continue
                    partner = (row.get('贸易伙伴名称') or '').strip() or country
                    place_name = (row.get('注册地名称') or '').strip() or None
                    comm_code = (row.get('商品编码') or '').strip() or None
                    comm_name = (row.get('商品名称') or '').strip() or None
                    meth_code = (row.get('贸易方式编码') or '').strip() or None
                    meth_name = (row.get('贸易方式名称') or '').strip() or None
                    qty = to_number(row.get('第一数量'))
                    unit = (row.get('第一计量单位') or '').strip() or None
                    amt = to_number(row.get('人民币'))

                    # ---- 聚合（按文件所在目录判断进出口方向；表里没有该列）----
                    if ym:
                        y, m = divmod(ym, 100)
                        if 1 <= m <= 12:
                            key = (partner, y, m)
                            if direction == '出口':
                                monthly[key]['eq'] += float(qty or 0)
                                monthly[key]['ea'] += float(amt or 0)
                            else:
                                monthly[key]['iq'] += float(qty or 0)
                                monthly[key]['ia'] += float(amt or 0)
                        if meth_name and amt:
                            method[(partner, meth_name, y, m)] += float(amt)
                    countries_seen.add(partner)

                    if not args.skip_trade_records:
                        batch.append((
                            ym,
                            (row.get('贸易伙伴编码') or '').strip() or None,
                            partner,
                            (row.get('注册地编码') or '').strip() or None,
                            place_name, comm_code, comm_name,
                            meth_code, meth_name, qty, unit, amt,
                        ))
                        if len(batch) >= BATCH:
                            cur.executemany(
                                'INSERT INTO trade_records (data_year_month, partner_code, partner_name,'
                                ' place_code, place_name, commodity_code, commodity_name,'
                                ' method_code, method_name, quantity, unit, amount)'
                                ' VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', batch)
                            inserted += len(batch)
                            total += len(batch)
                            batch = []
            if batch:
                cur.executemany(
                    'INSERT INTO trade_records (data_year_month, partner_code, partner_name,'
                    ' place_code, place_name, commodity_code, commodity_name,'
                    ' method_code, method_name, quantity, unit, amount)'
                    ' VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)', batch)
                inserted += len(batch)
                total += len(batch)
            if idx % 25 == 0 or idx == len(files):
                log('      进度 %3d/%d  %8.2fs  已写 trade_records %d 行'
                    % (idx, len(files), time.time() - t0, total))

        if skipped_no_date:
            log('      已跳过 %d 行（缺「数据年月」，无法归属时段）' % skipped_no_date)

        # ---- 写统计表 ----
        log('      聚合 -> country_monthly_trade')
        cur.execute('TRUNCATE TABLE country_monthly_trade')
        rows = []
        for (c, y, m), v in sorted(monthly.items()):
            rows.append((c, y, m, round(v['eq'], 4), round(v['ea'], 4),
                         round(v['iq'], 4), round(v['ia'], 4),
                         round(v['eq'] + v['iq'], 4), round(v['ea'] + v['ia'], 4)))
        if rows:
            cur.executemany(
                'INSERT INTO country_monthly_trade (country, year, month, export_quantity,'
                ' export_amount, import_quantity, import_amount, total_quantity, total_amount)'
                ' VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)', rows)
        log('      %d 行' % len(rows))

        log('      聚合 -> trade_method_stats')
        cur.execute('TRUNCATE TABLE trade_method_stats')
        rows2 = [(c, meth, round(amt, 4), y, m) for (c, meth, y, m), amt in sorted(method.items())]
        if rows2:
            cur.executemany(
                'INSERT INTO trade_method_stats (country_name, trade_method, trade_amount,'
                ' stat_year, stat_month) VALUES (%s,%s,%s,%s,%s)', rows2)
        log('      %d 行' % len(rows2))
    conn.commit()

    # 记录聚合结果供 comprehensive_data 使用
    import_trade_and_aggregate.monthly = monthly
    import_trade_and_aggregate.countries = countries_seen


# ----------------------------------------------------------------------
# 步骤 4：汇总统计
# ----------------------------------------------------------------------
def build_summary(conn):
    log('[4/4] 汇总 -> corpus_entries / comprehensive_data')
    with conn.cursor() as cur:
        # 历年语料条目
        cur.execute('TRUNCATE TABLE corpus_entries')
        cur.execute("""
            INSERT INTO corpus_entries (year, entry_count, update_time)
            SELECT YEAR(publish_time) AS y, COUNT(*), NOW()
            FROM news_articles
            WHERE publish_time IS NOT NULL
            GROUP BY YEAR(publish_time)
            HAVING y IS NOT NULL
        """)
        cur.execute('SELECT COUNT(*) FROM corpus_entries')
        log('      corpus_entries: %d 行' % cur.fetchone()[0])

        # 综合统计（取最新月份一行）
        cur.execute('TRUNCATE TABLE comprehensive_data')
        cur.execute('SELECT COUNT(*) FROM news_articles')
        news_cnt = cur.fetchone()[0]
        cur.execute('SELECT COUNT(*) FROM trade_records')
        trade_cnt = cur.fetchone()[0]
        cur.execute('SELECT COUNT(DISTINCT partner_name) FROM trade_records')
        country_cnt = cur.fetchone()[0] or 0
        cur.execute('SELECT MAX(year), MAX(month) FROM country_monthly_trade')
        row = cur.fetchone()
        if row and row[0]:
            y, m = row[0], row[1]
            cur.execute("""SELECT MAX(month) FROM country_monthly_trade WHERE year=%s""", (y,))
            m = cur.fetchone()[0] or m
            # 取该年月的合计
            cur.execute("""SELECT COALESCE(SUM(total_quantity),0) FROM country_monthly_trade
                           WHERE year=%s AND month=%s""", (y, m))
            tot = cur.fetchone()[0]
            log('      最新数据月: %s-%02d  进出口总量: %s' % (y, m, tot))
        else:
            y, m = 2025, 3
        cur.execute("""INSERT INTO comprehensive_data
            (corpus_entry_count, data_entry_count, query_visit_count, trade_country_count,
             stat_year, stat_month, update_time)
            VALUES (%s,%s,%s,%s,%s,%s,NOW())""",
                    (news_cnt, trade_cnt, 0, country_cnt, y, m))
        log('      comprehensive_data: 语料 %d / 数据 %d / 国家 %d' % (news_cnt, trade_cnt, country_cnt))
    conn.commit()


# ----------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description='中哈贸易系统 MySQL 一键初始化')
    ap.add_argument('--host', default='127.0.0.1')
    ap.add_argument('--port', type=int, default=3306)
    ap.add_argument('--user', default='root')
    ap.add_argument('--password', default='123456')
    ap.add_argument('--skip-trade-records', action='store_true',
                    help='不写入 trade_records 明细（仍然聚合统计），适合快速起服务')
    ap.add_argument('--keep-trade-records', action='store_true',
                    help='保留 trade_records 已有的数据（默认会清空重导）')
    ap.add_argument('--news-only', action='store_true', help='只导新闻语料与汇总')
    ap.add_argument('--schema-only', action='store_true', help='只建库建表')
    args = ap.parse_args()

    log('=' * 66)
    log('中哈贸易系统 · MySQL 初始化')
    log('  项目根: %s' % PROJECT_ROOT)
    log('  目标库: %s@%s:%d/%s' % (args.user, args.host, args.port, DB_NAME))
    log('=' * 66)

    conn = pymysql.connect(host=args.host, port=args.port, user=args.user,
                           password=args.password, charset='utf8mb4',
                           autocommit=False, local_infile=False)
    try:
        run_schema(conn, args)
        if args.schema_only:
            log('仅建表，结束。')
            return
        if not args.news_only:
            import_trade_and_aggregate(conn, args)
        import_news(conn, args)
        build_summary(conn)
    finally:
        conn.close()

    log('')
    log('=' * 66)
    log('完成。核对数据规模：')
    conn = pymysql.connect(host=args.host, port=args.port, user=args.user,
                           password=args.password, database=DB_NAME, charset='utf8mb4')
    try:
        with conn.cursor() as cur:
            for t in ('sys_user', 'news_articles', 'trade_records',
                      'country_monthly_trade', 'trade_method_stats',
                      'corpus_entries', 'comprehensive_data', 'ontology'):
                cur.execute('SELECT COUNT(*) FROM `%s`' % t)
                log('  %-24s %10d 行' % (t, cur.fetchone()[0]))
    finally:
        conn.close()
    log('=' * 66)


if __name__ == '__main__':
    main()
