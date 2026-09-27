"""
UN Comtrade API - 获取中国与哈萨克斯坦双边贸易数据
使用官方 comtradeapicall 库

国家代码:
- 中国 (China): 156
- 哈萨克斯坦 (Kazakhstan): 398

注: UN Comtrade 年度数据通常有1-2年延迟
2025年数据预计在2026年下半年发布
"""

import comtradeapicall
import pandas as pd
import json
import os
from datetime import datetime

# API 配置（真实值见项目根 config/.env 的 COMTRADE_API_KEY，该文件不入库）
# 运行前先加载配置：set -a; . ./config/.env; set +a
API_KEY = os.getenv('COMTRADE_API_KEY', '')

# 国家代码
CHINA_CODE = "156"
KAZAKHSTAN_CODE = "398"


def get_bilateral_trade(
    reporter_code: str,
    partner_code: str,
    period: str,
    flow_code: str = "X"
) -> pd.DataFrame:
    """获取双边贸易数据"""
    try:
        result = comtradeapicall.previewFinalData(
            typeCode='C',
            freqCode='A',
            clCode='HS',
            period=period,
            reporterCode=reporter_code,
            partnerCode=partner_code,
            cmdCode='TOTAL',
            flowCode=flow_code,
            partner2Code='',
            customsCode='',
            motCode=''
        )

        if result is not None and not result.empty:
            result['reporter_code'] = reporter_code
            result['partner_code'] = partner_code
            result['flow_code'] = flow_code

        return result
    except Exception as e:
        print(f"获取数据失败: {e}")
        return pd.DataFrame()


def get_commodity_breakdown(
    reporter_code: str,
    partner_code: str,
    period: str,
    top_n: int = 20
) -> pd.DataFrame:
    """获取主要商品类别 (HS 2位章节)"""
    all_commodities = []

    print(f"获取 {period} 年商品类别数据...")

    for chapter in range(1, 100):
        cmd_code = f"{chapter:02d}"

        try:
            result = comtradeapicall.previewFinalData(
                typeCode='C',
                freqCode='A',
                clCode='HS',
                period=period,
                reporterCode=reporter_code,
                partnerCode=partner_code,
                cmdCode=cmd_code,
                flowCode='X',
                partner2Code='',
                customsCode='',
                motCode=''
            )

            if result is not None and not result.empty:
                val = result['primaryValue'].iloc[0]
                if val > 0:
                    desc = result['cmdDesc'].iloc[0] if 'cmdDesc' in result.columns else cmd_code
                    all_commodities.append({
                        'hs_code': cmd_code,
                        'hs_desc': desc,
                        'export_value': val,
                        'net_wgt': result['netWgt'].iloc[0] if 'netWgt' in result.columns else 0
                    })
        except:
            continue

        if chapter % 20 == 0:
            print(f"  进度: {chapter}/99")

    if not all_commodities:
        return pd.DataFrame()

    df = pd.DataFrame(all_commodities)
    df = df.sort_values('export_value', ascending=False).head(top_n)
    return df


def get_yearly_summary(years: list = None) -> pd.DataFrame:
    """获取年度贸易汇总"""
    if years is None:
        years = [2020, 2021, 2022, 2023, 2024]

    all_data = []

    for year in years:
        print(f"\n获取 {year} 年数据...")

        china_export = get_bilateral_trade(CHINA_CODE, KAZAKHSTAN_CODE, str(year), 'X')
        if china_export is not None and not china_export.empty:
            china_export['trade_direction'] = '中国出口到哈萨克斯坦'
            all_data.append(china_export)
            val = china_export['primaryValue'].iloc[0]
            print(f"  中国出口到哈萨克斯坦: ${val:,.0f}")

        kaz_export = get_bilateral_trade(KAZAKHSTAN_CODE, CHINA_CODE, str(year), 'X')
        if kaz_export is not None and not kaz_export.empty:
            kaz_export['trade_direction'] = '哈萨克斯坦出口到中国'
            all_data.append(kaz_export)
            val = kaz_export['primaryValue'].iloc[0]
            print(f"  哈萨克斯坦出口到中国: ${val:,.0f}")

    if not all_data:
        return pd.DataFrame()

    return pd.concat(all_data, ignore_index=True)


def check_data_availability():
    """检查各年份数据可用性"""
    print("\n" + "=" * 60)
    print("检查 UN Comtrade 数据可用性")
    print("=" * 60)

    for year in [2020, 2021, 2022, 2023, 2024, 2025, 2026]:
        try:
            count = comtradeapicall.previewCountFinalData(
                typeCode='C',
                freqCode='A',
                clCode='HS',
                period=str(year),
                reporterCode='156',
                partnerCode='398',
                cmdCode='TOTAL',
                flowCode='X',
                partner2Code='',
                customsCode='',
                motCode=''
            )
            cnt = count.iloc[0,0] if hasattr(count, 'iloc') else 0
            status = "[OK]" if cnt > 0 else "[NO]"
            print(f"  {year}年: {status} ({cnt} 条记录)")
        except Exception as e:
            print(f"  {year}年: ❌ 检查失败")


def save_to_json(data, filename: str):
    """保存数据到 JSON 文件"""
    if isinstance(data, pd.DataFrame):
        data.to_json(filename, orient='records', force_ascii=False, indent=2)
    else:
        with open(filename, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"数据已保存到: {filename}")


def save_to_csv(df: pd.DataFrame, filename: str):
    """保存 DataFrame 到 CSV 文件"""
    df.to_csv(filename, index=False, encoding="utf-8-sig")
    print(f"数据已保存到: {filename}")


def print_summary(df: pd.DataFrame):
    """打印贸易摘要"""
    if df.empty:
        print("\n没有数据可显示")
        return

    print("\n" + "=" * 80)
    print("中国-哈萨克斯坦双边贸易数据摘要 (数据来源: UN Comtrade)")
    print("=" * 80)

    for year in sorted(df['refYear'].unique()):
        year_data = df[df['refYear'] == year]
        print(f"\n【{year}年】")

        for _, row in year_data.iterrows():
            direction = row.get('trade_direction', '未知')
            value = row['primaryValue']
            print(f"  {direction}: ${value:,.0f} (约 {value/1e8:.2f} 亿美元)")

        total = year_data['primaryValue'].sum()
        print(f"  双边贸易总额: ${total:,.0f} (约 {total/1e8:.2f} 亿美元)")


if __name__ == "__main__":
    print("=" * 60)
    print("UN Comtrade API - 中哈贸易数据获取")
    print("=" * 60)
    print(f"时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    # 1. 检查数据可用性
    check_data_availability()

    # 2. 获取2020-2024年数据
    print("\n" + "=" * 60)
    print("【获取年度贸易数据 (2020-2024)】")
    print("=" * 60)

    yearly_df = get_yearly_summary([2020, 2021, 2022, 2023, 2024])

    if not yearly_df.empty:
        print_summary(yearly_df)
        save_to_csv(yearly_df, "china_kazakhstan_yearly_trade.csv")
        save_to_json(yearly_df.to_dict(orient='records'), "china_kazakhstan_yearly_trade.json")
    else:
        print("\n未能获取年度汇总数据")

    # 3. 获取2024年主要商品类别
    print("\n" + "=" * 60)
    print("【获取2024年主要出口商品类别】")
    print("=" * 60)

    china_exports = get_commodity_breakdown(CHINA_CODE, KAZAKHSTAN_CODE, "2024", top_n=20)
    if not china_exports.empty:
        print("\n中国出口到哈萨克斯坦前20大商品类别 (HS 2位):")
        print(china_exports.to_string(index=False))
        save_to_csv(china_exports, "china_exports_kazakhstan_2024_commodities.csv")
    else:
        print("未能获取商品类别数据")

    # 4. 2025年数据说明
    print("\n" + "=" * 60)
    print("[About 2025 Data]")
    print("=" * 60)
    print("""
According to our tests, UN Comtrade currently (April 2026):
- 2024 and earlier: [OK] Available
- 2025 data: [NO] Not yet published (0 records)

Reason: UN Comtrade data comes from official customs declarations,
with typically 1-2 years delay. 2025 data is expected to be released
in the second half of 2026.

Alternative data sources:
1. China Customs: http://www.customs.gov.cn/ (registration required)
2. CEIC Database: https://www.ceicdata.com/ (paid)
3. Trade news: Regular bilateral trade statistics

Known 2025 data (from news):
- 2024 China-Kazakhstan bilateral trade: $43.8 billion (9% YoY growth)
- Jan-Feb 2025 Kazakhstan imports from China share: 28.8%
    """)

    print("=" * 60)
    print("程序结束")
    print("=" * 60)