package com.example.tdproject.ai.query;

import com.example.tdproject.ai.dto.DataQueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 中哈贸易历史数据查询。
 *
 * <p><b>为什么需要它：</b>用户问「1月哈萨克斯坦丝绸出口量」时，问的是<b>已经发生</b>的
 * 事实，而 {@code trade_in/trade_out} 里就有真实记录（201501~202503）。此前系统只有
 * 「预测未来」和「新闻检索」两条路，这类问题会被硬塞进预测，再因为凑不齐四键槽位而
 * 永远给不出答案 —— 用户看到的就是一段与问题无关的固定引导语。</p>
 *
 * <p><b>为什么直连 {@code trade} 库：</b>{@code trade_in/trade_out} 不在主数据源
 * ({@code foreign_trade_qa_db}) 里。这里复用同一个 DataSource，SQL 里用
 * {@code trade.trade_out} 的跨库限定名，不额外引入第二套连接配置。</p>
 *
 * <p><b>为什么只对商品名做模糊解析：</b>用户几乎不可能说对 512 字符的商品规范名
 * （如「其他未列名冻鱼」），但贸易伙伴/贸易方式/注册地基本能说对。只解析商品名，
 * 能把「告知候选」这件事做到最有价值，同时不引入含糊的聚合口径。</p>
 */
@Slf4j
@Service
public class TradeDataQueryService {

    private static final int DETAIL_LIMIT = 24;
    private static final int SUGGEST_LIMIT = 8;

    private final JdbcTemplate jdbc;
    private final String db;

    public TradeDataQueryService(DataSource dataSource,
                                 @Value("${ai.data.db:trade}") String db) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(20);
        this.db = db;
    }

    // =================================================================
    // 对外入口
    // =================================================================

    public DataQueryResult query(String tradeType, String target,
                                 Integer year, Integer month,
                                 String partner, String product,
                                 String tradeMode, String register) {

        String tt = normalizeTradeType(tradeType);
        if (tt == null) {
            return DataQueryResult.builder()
                    .tradeType(null).target(target)
                    .summary("这是「历史数据查询」问题。请先说明是**进口**还是**出口**，"
                            + "例如：「2025年1月哈萨克斯坦的出口数量」。")
                    .suggestions(List.of("进口", "出口"))
                    .hitMonths(0)
                    .rows(List.of())
                    .build();
        }

        String table = table(tt);
        String range = dataRange(table);
        int maxYm = maxYm(table);

        // ---- 商品名解析 ----
        String pInput = blankToNull(product);
        String pResolved = null;
        List<String> pSuggest = List.of();
        boolean unresolved = false;
        if (pInput != null) {
            Resolved r = resolveProduct(table, pInput);
            pResolved = r.value;
            pSuggest = r.candidates;
            unresolved = r.value == null;
            if (unresolved) {
                return DataQueryResult.builder()
                        .tradeType(tt).target(target)
                        .partnerName(blankToNull(partner))
                        .productInput(pInput)
                        .dataRange(range)
                        .summary(buildProductNotFound(tt, pInput, year, month, range, r.candidates))
                        .suggestions(r.candidates)
                        .productUnresolved(true)
                        .hitMonths(0)
                        .rows(List.of())
                        .build();
            }
        }

        // ---- 组装过滤条件 ----
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendEq(where, args, "贸易伙伴名称", partner);
        appendEq(where, args, "贸易方式名称", tradeMode);
        appendEq(where, args, "注册地名称", register);
        if (pResolved != null) {
            where.append(" AND 商品名称 = ?");
            args.add(pResolved);
        }

        // ---- 年月范围 ----
        int ymFrom = 0, ymTo = Integer.MAX_VALUE;
        if (year != null && month != null) {
            ymFrom = year * 100 + month;
            ymTo = ymFrom;
        } else if (year != null) {
            ymFrom = year * 100 + 1;
            ymTo = year * 100 + 12;
        }
        if (ymFrom > 0) {
            where.append(" AND 数据年月 BETWEEN ? AND ?");
            args.add(ymFrom);
            args.add(ymTo);
        }

        String sql = "SELECT 数据年月, SUM(数量) AS qty, SUM(人民币) AS rmb, MAX(计量单位) AS unit"
                + " FROM " + table
                + " WHERE 1=1" + where
                + " GROUP BY 数据年月 ORDER BY 数据年月 DESC LIMIT " + DETAIL_LIMIT;

        List<DataQueryResult.DataRow> rows = new ArrayList<>();
        try {
            jdbc.query(sql, rs -> {
                Integer ym = (Integer) rs.getObject("数据年月");
                Double qty = toDouble(rs.getObject("qty"));
                Double rmb = toDouble(rs.getObject("rmb"));
                rows.add(DataQueryResult.DataRow.builder()
                        .ym(ym)
                        .label(formatYm(ym))
                        .quantity(qty)
                        .rmb(rmb)
                        .price((qty != null && qty != 0 && rmb != null) ? rmb / qty : null)
                        .unit(rs.getString("unit"))
                        .build());
            }, args.toArray());
        } catch (Exception e) {
            log.error("历史数据查询失败 table={} sql={}", table, sql, e);
            return DataQueryResult.builder()
                    .tradeType(tt).target(target).productName(pResolved).productInput(pInput)
                    .dataRange(range).hitMonths(0).rows(List.of())
                    .summary("查询历史数据时出错：" + e.getMessage())
                    .build();
        }

        // ---- 未来月份：库里不可能有 ----
        if (rows.isEmpty() && ymFrom > maxYm) {
            String when = cnYm(year, month);
            if (when.isEmpty()) when = "下个月";
            String what = "quantity".equals(target) ? "数量"
                    : ("price".equals(target) ? "单价" : "单价或数量");
            return DataQueryResult.builder()
                    .tradeType(tt).target(target).partnerName(blankToNull(partner))
                    .productName(pResolved).productInput(pInput)
                    .dataRange(range).hitMonths(0).rows(List.of())
                    .summary("**" + formatYm(ymFrom) + " 还没有实际数据**：库中最新数据是 "
                            + formatYm(maxYm) + "（覆盖 " + range + "）。\n"
                            + "如果你想了解这个月的走势，可以换成预测问题，例如：\n"
                            + "  「预测" + blank(partner) + (pResolved != null ? pResolved : "")
                            + "的" + when + ("in".equals(tt) ? "进口" : "出口") + what + "」")
                    .build();
        }

        if (rows.isEmpty()) {
            return DataQueryResult.builder()
                    .tradeType(tt).target(target).partnerName(blankToNull(partner))
                    .productName(pResolved).productInput(pInput)
                    .tradeMode(blankToNull(tradeMode)).registerName(blankToNull(register))
                    .dataRange(range).hitMonths(0).rows(List.of())
                    .summary("库里没有符合这组条件的记录。")
                    .suggestions(alternativeDims(table, partner, pResolved))
                    .build();
        }

        // ---- 正常命中 ----
        // 先判断计量单位是否混杂：不指定商品名时，把「千克」「米」「台」的数量直接相加
        // 会得到一个毫无意义的假数字（实测出现过「501,181,485 米」）。单位不一致时
        // 数量与单价一律置空，只保留单位一致、可以相加的金额。
        List<String> unitSamples = List.of();
        boolean mixedUnit = false;
        if (!rows.isEmpty()) {
            try {
                unitSamples = jdbc.queryForList(
                        "SELECT DISTINCT 计量单位 FROM " + table + " WHERE 1=1" + where
                                + " AND 计量单位 IS NOT NULL LIMIT 8",
                        String.class, args.toArray());
            } catch (Exception e) {
                log.warn("统计计量单位失败: {}", e.getMessage());
            }
            mixedUnit = unitSamples.size() > 1;
            if (mixedUnit) {
                for (DataQueryResult.DataRow r : rows) {
                    r.setQuantity(null);
                    r.setPrice(null);
                    r.setUnit("多种");
                }
            }
        }

        DataQueryResult.DataRow latest = rows.get(0);
        double totalQty = 0, totalRmb = 0;
        for (DataQueryResult.DataRow r : rows) {
            if (r.getQuantity() != null) totalQty += r.getQuantity();
            if (r.getRmb() != null) totalRmb += r.getRmb();
        }

        return DataQueryResult.builder()
                .tradeType(tt).target(target).partnerName(blankToNull(partner))
                .productName(pResolved).productInput(pInput)
                .tradeMode(blankToNull(tradeMode)).registerName(blankToNull(register))
                .dataRange(range).hitMonths(rows.size()).rows(rows)
                .mixedUnit(mixedUnit)
                .unitSamples(unitSamples)
                .summary(buildSummary(tt, target, partner, pInput, pResolved, tradeMode, register,
                        year, month, rows.size(), latest, totalQty, totalRmb, range,
                        mixedUnit, unitSamples))
                .suggestions(List.of())
                .build();
    }

    // =================================================================
    // 数据范围（「有哪些国家的数据可以访问」这类问题）
    //
    // 此前这类问题命中 CHITCHAT，拿到的是 buildCapabilityAnswer() 里一段
    // **写死的**能力说明：它把「覆盖 2015-01 ~ 2025-03」硬编码在字符串里，
    // 又从不提有哪些国家，所以「有什么国家的数据可以访问」得到的回答答非所问。
    //
    // 这里改成从库里现算：伙伴清单 + 时间范围 + 记录数 + 商品数。
    // 代价是 4 条聚合 SQL（实测合计约 6.7s，trade_out 单表 191 万行，
    // COUNT(DISTINCT 商品编码) 就占 4.2s），因此：
    //   ① 结果带 TTL 缓存；
    //   ② 过期时**先返回旧值、后台刷新**，不把 6.7s 甩到某个用户的请求上；
    //   ③ 应用启动后后台预热一次，首个请求即命中。
    // =================================================================

    /** 库是离线批量导入的，半小时粒度足够新，也避免反复全表扫。 */
    private static final long SCOPE_TTL_MS = 30 * 60 * 1000L;

    private volatile ScopeInfo scopeCache;
    private volatile long scopeCacheAt;
    private final AtomicBoolean scopeRefreshing = new AtomicBoolean(false);

    @PostConstruct
    void warmScope() {
        Thread t = new Thread(() -> {
            try {
                loadScope();
                log.info("数据范围缓存已预热：{}", scopeSummary());
            } catch (Exception e) {
                log.warn("数据范围预热失败（不影响启动，首次提问时会重试）: {}", e.getMessage());
            }
        }, "scope-cache-warmup");
        t.setDaemon(true);
        t.start();
    }

    /** 一句话说清「能访问什么数据」，供能力说明复用。 */
    public String scopeSummary() {
        ScopeInfo s = scope();
        if (s == null || s.partners.isEmpty()) return "历史数据当前不可用。";
        return String.join("、", s.partners) + "（共 " + s.partners.size() + " 个贸易伙伴），"
                + "时间范围 " + s.range + "，"
                + "进口 " + num(s.inRows) + " 条 / " + s.inProducts + " 个商品，"
                + "出口 " + num(s.outRows) + " 条 / " + s.outProducts + " 个商品。";
    }

    /** 「有哪些国家的数据可以访问」的完整回答（Markdown）。 */
    public String scopeAnswer() {
        ScopeInfo s = scope();
        if (s == null || s.partners.isEmpty()) {
            return "暂时读不到数据范围信息，请稍后重试。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("目前系统可访问 **").append(s.partners.size())
                .append(" 个贸易伙伴** 的进出口数据：\n\n");
        sb.append("| 贸易伙伴 | 进口记录 | 出口记录 |\n|---|---|---|\n");
        for (String p : s.partners) {
            sb.append("| ").append(p)
                    .append(" | ").append(num(s.inByPartner.getOrDefault(p, 0L)))
                    .append(" | ").append(num(s.outByPartner.getOrDefault(p, 0L)))
                    .append(" |\n");
        }
        sb.append("\n")
                .append("- 时间范围：**").append(s.range).append("**\n")
                .append("- 商品数：进口 **").append(s.inProducts)
                .append("** 个，出口 **").append(s.outProducts).append("** 个\n")
                .append("- 覆盖方向：进口、出口\n\n")
                .append("可以直接问历史数据，例如「2025年1月哈萨克斯坦的出口数量」；")
                .append("要做预测则需把贸易伙伴、商品名称、贸易方式、境内注册地四项说齐，")
                .append("缺哪项我会告诉你库里实际有哪些可选值。");
        return sb.toString();
    }

    /** 取缓存；过期时先返回旧值，同时触发一次后台刷新。 */
    private ScopeInfo scope() {
        ScopeInfo c = scopeCache;
        if (c == null) {
            try {
                return loadScope();
            } catch (Exception e) {
                log.warn("读取数据范围失败: {}", e.getMessage());
                return null;
            }
        }
        long age = System.currentTimeMillis() - scopeCacheAt;
        if (age > SCOPE_TTL_MS && scopeRefreshing.compareAndSet(false, true)) {
            Thread t = new Thread(() -> {
                try {
                    loadScope();
                } catch (Exception e) {
                    log.warn("数据范围后台刷新失败，继续用旧值: {}", e.getMessage());
                } finally {
                    scopeRefreshing.set(false);
                }
            }, "scope-cache-refresh");
            t.setDaemon(true);
            t.start();
        }
        return c;
    }

    private synchronized ScopeInfo loadScope() {
        ScopeInfo s = new ScopeInfo(dataRange(table("in")));

        Map<String, Long> in = partnerCounts(table("in"));
        Map<String, Long> out = partnerCounts(table("out"));
        s.inByPartner.putAll(in);
        s.outByPartner.putAll(out);

        List<String> names = new ArrayList<>(in.keySet());
        for (String k : out.keySet()) {
            if (!names.contains(k)) names.add(k);
        }
        names.sort(Comparator.comparingLong((String k) ->
                in.getOrDefault(k, 0L) + out.getOrDefault(k, 0L)).reversed());
        s.partners.addAll(names);

        s.inRows = sum(in.values());
        s.outRows = sum(out.values());
        s.inProducts = productCount(table("in"));
        s.outProducts = productCount(table("out"));

        scopeCache = s;
        scopeCacheAt = System.currentTimeMillis();
        return s;
    }

    private Map<String, Long> partnerCounts(String table) {
        // 刻意用 queryForList 而不是 jdbc.query(sql, 回调)：
        // query(String, RowCallbackHandler) 与 query(String, ResultSetExtractor) 两个重载
        // 对同一个 lambda 都适用，编译器会报 ambiguous。queryForList 没有这个歧义。
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT 贸易伙伴名称 AS n, COUNT(*) AS c FROM " + table
                        + " GROUP BY 贸易伙伴名称 ORDER BY c DESC");

        Map<String, Long> m = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Object raw = r.get("n");
            String name = raw == null ? "" : String.valueOf(raw).trim();
            Object c = r.get("c");
            m.put(name.isEmpty() ? "（未标注）" : name,
                    c instanceof Number ? ((Number) c).longValue() : 0L);
        }
        return m;
    }

    private int productCount(String table) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT 商品编码) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private static long sum(Collection<Long> vals) {
        long s = 0;
        for (Long v : vals) {
            if (v != null) s += v;
        }
        return s;
    }

    private static String num(long n) {
        return String.format("%,d", n);
    }

    /** 数据范围快照。 */
    private static final class ScopeInfo {
        final String range;
        final List<String> partners = new ArrayList<>();
        final Map<String, Long> inByPartner = new LinkedHashMap<>();
        final Map<String, Long> outByPartner = new LinkedHashMap<>();
        long inRows;
        long outRows;
        int inProducts;
        int outProducts;

        ScopeInfo(String range) {
            this.range = range;
        }
    }

    /**
     * 某方向（in/out）数据的最大「数据年月」（YYYYMM 整数）。
     * 供预测结果追加「目标月远超数据末期」的可靠性警示用。
     * 返回 0 表示查询失败，调用方应视作「未知」而不误报。
     */
    public int maxDataYm(String tradeType) {
        String tt = normalizeTradeType(tradeType);
        if (tt == null) return 0;
        return maxYm(table(tt));
    }

    /** 某个维度在库中的可选值（用于缺失槽位的「可照做」引导）。 */
    public List<String> distinctValues(String column, String tradeType,
                                       String partner, String product, int limit) {
        String tt = normalizeTradeType(tradeType);
        if (tt == null) return List.of();
        String table = table(tt);
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendEq(where, args, "贸易伙伴名称", partner);
        if (blankToNull(product) != null) {
            Resolved r = resolveProduct(table, product);
            if (r.value != null) appendEq(where, args, "商品名称", r.value);
        }
        String sql = "SELECT DISTINCT " + column + " FROM " + table
                + " WHERE 1=1" + where + " AND " + column + " IS NOT NULL LIMIT " + limit;
        try {
            return jdbc.queryForList(sql, String.class, args.toArray());
        } catch (Exception e) {
            log.warn("查询可选值失败 column={} : {}", column, e.getMessage());
            return List.of();
        }
    }

    /** 商品名在库中是否存在（精确）。 */
    public boolean productExists(String tradeType, String product) {
        String tt = normalizeTradeType(tradeType);
        if (tt == null || blankToNull(product) == null) return false;
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM " + table(tt) + " WHERE 商品名称 = ?",
                    Integer.class, product.trim());
            return n != null && n > 0;
        } catch (Exception e) {
            log.warn("商品名存在性检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 商品名的相近候选；精确命中时返回空列表。
     * 供「预测缺槽位」的引导使用 —— 用户写「丝绸」这类俗称时，
     * 与其让他反复猜，不如直接把库里的规范商品名摆出来。
     */
    public List<String> suggestProducts(String tradeType, String input, int limit) {
        String tt = normalizeTradeType(tradeType);
        if (tt == null || blankToNull(input) == null) return List.of();
        Resolved r = resolveProduct(table(tt), input.trim());
        return r.value != null ? List.of() : head(r.candidates, limit);
    }

    // =================================================================
    // 内部工具
    // =================================================================

    private String buildProductNotFound(String tt, String input, Integer year, Integer month,
                                        String range, List<String> cands) {
        String dir = "in".equals(tt) ? "进口" : "出口";
        StringBuilder sb = new StringBuilder();
        sb.append("库里没有名为「").append(input).append("」的")
                .append(dir).append("商品（数据覆盖 ").append(range).append("）。");
        if (!cands.isEmpty()) {
            sb.append("\n相近的规范商品名有：\n");
            for (int i = 0; i < cands.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(cands.get(i)).append("\n");
            }
            // 示例句沿用用户自己说过的年月，别写死一个不相干的日期
            String when = cnYm(year, month);
            sb.append("请把商品名换成上面的写法再问一次，例如：\n")
                    .append("  「").append(when).append("哈萨克斯坦的").append(cands.get(0))
                    .append(dir).append("数量」");
        } else {
            sb.append("\n可以换个说法，或到「信息管理 → 数据管理」里检索商品全称。");
        }
        return sb.toString();
    }

    private String buildSummary(String tt, String target, String partner, String pInput,
                                String pResolved, String tradeMode, String register,
                                Integer year, Integer month, int months,
                                DataQueryResult.DataRow latest, double totalQty, double totalRmb,
                                String range, boolean mixedUnit, List<String> unitSamples) {
        StringBuilder sb = new StringBuilder();
        String dir = "in".equals(tt) ? "进口" : "出口";

        sb.append("这是历史实际数据（非预测）。口径：");
        List<String> dims = new ArrayList<>();
        dims.add(blank(partner));
        if (pResolved != null) dims.add(pResolved);
        if (blankToNull(tradeMode) != null) dims.add(blank(tradeMode));
        else dims.add("全部贸易方式");
        if (blankToNull(register) != null) dims.add(blank(register));
        else dims.add("全部注册地");
        sb.append(dir).append(" / ").append(String.join(" / ", dims)).append("。\n");
        if (pResolved != null && pInput != null && !pInput.equals(pResolved)) {
            sb.append("（你说的是「").append(pInput).append("」，已按库中规范商品名理解。）\n");
        }

        if (month != null && year != null) {
            sb.append(formatYm(year * 100 + month));
        } else if (year != null) {
            sb.append(year).append("年全年");
        } else {
            sb.append("最近 ").append(months).append(" 个月");
        }
        sb.append(" 的数据：\n");

        boolean singleMonth = (year != null && month != null);
        // 注意：这里刻意用 if/else 而不是三元表达式。
        // `singleMonth ? latest.getQuantity() : totalQty` 中前者是 Double、后者是 double，
        // Java 会对整个三元表达式做二元数值提升 → 结果类型变成基本类型 double，
        // 于是 latest.getQuantity() 被强制拆箱；mixedUnit 时该值为 null，直接 NPE。
        Double qty;
        Double rmb;
        Double price;
        if (singleMonth) {
            qty = latest.getQuantity();
            rmb = latest.getRmb();
            price = latest.getPrice();
        } else {
            qty = Double.valueOf(totalQty);
            rmb = Double.valueOf(totalRmb);
            price = totalQty != 0 ? Double.valueOf(totalRmb / totalQty) : null;
        }

        if (mixedUnit) {
            // 单位不一致：数量与单价不可加，只能给金额
            sb.append("· 金额：").append(fmt(rmb)).append(" 人民币\n");
            sb.append("· 数量/单价：**本次未指定商品名称，无法合计** —— 结果涉及 ")
                    .append(unitSamples.size()).append(" 种计量单位（")
                    .append(String.join("、", unitSamples)).append("），")
                    .append("把「千克」和「米」相加没有意义。\n")
                    .append("  请指明商品名称后再查数量，例如：「")
                    .append(cnYm(year, month)).append(blank(partner))
                    .append("的<商品名称>").append(dir).append("数量」。\n");
        } else {
            if (target == null || "quantity".equals(target)) {
                sb.append("· 数量：").append(fmt(qty))
                        .append(" ").append(safe(latest.getUnit())).append("\n");
            }
            if (target == null || "price".equals(target)) {
                sb.append("· 单价：").append(fmt(price))
                        .append(safe(latest.getUnit()).isEmpty() ? "" : " 人民币/" + safe(latest.getUnit()))
                        .append("\n");
            }
            // 金额不只 target==null 时给：问「单价」的用户同样需要「数量×单价=金额」
            // 的落点（如「20250 千克对应多少人民币」被抽成 target=price，
            // 只给 18.1 元/千克而不给 366,557 人民币，等于没答到点上）。
            // 该金额是这组条件下 SUM(人民币) 的库内真实值，不是估算。
            if (target == null || "price".equals(target)) {
                sb.append("· 金额：").append(fmt(rmb)).append(" 人民币\n");
            }
        }

        sb.append("· 最新数据月份：").append(latest.getLabel())
                .append("（库中数据覆盖 ").append(range).append("）");

        if (!singleMonth && months > 1) {
            sb.append("\n· 上述数值为 ").append(months).append(" 个月的")
                    .append(mixedUnit ? "金额合计" : "合计/加权结果").append("；逐月明细见下表。");
        }
        if (!mixedUnit && (blankToNull(tradeMode) == null || blankToNull(register) == null)) {
            sb.append("\n· 提示：");
            if (blankToNull(tradeMode) == null) sb.append("未限定贸易方式");
            if (blankToNull(tradeMode) == null && blankToNull(register) == null) sb.append("、");
            if (blankToNull(register) == null) sb.append("未限定境内注册地");
            sb.append("，以上为汇总值。补上「一般贸易」「新疆维吾尔自治区」这类限定词可得到更精确的单一记录。");
        }
        return sb.toString();
    }

    private List<String> alternativeDims(String table, String partner, String product) {
        List<String> out = new ArrayList<>();
        try {
            if (blankToNull(partner) != null) {
                List<String> partners = jdbc.queryForList(
                        "SELECT DISTINCT 贸易伙伴名称 FROM " + table + " WHERE 贸易伙伴名称 LIKE ? LIMIT "
                                + SUGGEST_LIMIT, String.class, "%" + partner.trim() + "%");
                if (!partners.isEmpty()) out.add("贸易伙伴（库中有）：" + String.join("、", partners));
            }
            if (product != null) {
                List<String> modes = jdbc.queryForList(
                        "SELECT DISTINCT 贸易方式名称 FROM " + table + " WHERE 商品名称 = ? LIMIT " + SUGGEST_LIMIT,
                        String.class, product);
                if (!modes.isEmpty()) out.add("该商品的贸易方式有：" + String.join("、", modes));
            }
            List<String> years = jdbc.queryForList(
                    "SELECT MIN(数据年月) FROM " + table, String.class);
            if (!years.isEmpty()) out.add("数据年月范围：" + years.get(0) + " 起");
        } catch (Exception e) {
            log.warn("构造备选建议失败: {}", e.getMessage());
        }
        return out;
    }

    /** 商品名解析：精确 → 全串 LIKE（唯一则采用）→ 2-gram / 单字兜底给候选。 */
    private Resolved resolveProduct(String table, String raw) {
        String q = raw.trim();
        try {
            List<String> exact = jdbc.queryForList(
                    "SELECT DISTINCT 商品名称 FROM " + table + " WHERE 商品名称 = ? LIMIT 2",
                    String.class, q);
            if (exact.size() == 1) return new Resolved(exact.get(0), List.of());

            List<String> like = jdbc.queryForList(
                    "SELECT DISTINCT 商品名称 FROM " + table + " WHERE 商品名称 LIKE ? LIMIT "
                            + (SUGGEST_LIMIT + 1), String.class, "%" + q + "%");
            if (like.size() == 1) return new Resolved(like.get(0), List.of());
            if (like.size() > 1) return new Resolved(null, head(like, SUGGEST_LIMIT));

            return new Resolved(null, suggestByChars(table, q));
        } catch (Exception e) {
            log.warn("商品名解析失败: {}", e.getMessage());
            return new Resolved(null, List.of());
        }
    }

    /**
     * 用 2-gram 召回候选；仍为空则退化到单字匹配（应对「丝绸」这类俗称）。
     *
     * <p>2026-09-29 补充：纯 2-gram 对「奶制品」这类词会输给「制品」二字 —— 库里
     * 「未列名木制品/瓷制品/塑料制品」大量命中「制品」，把真正想要的乳制品（名称里是
     * 「乳」而非「奶」）挤掉。为此在打分前先做一轮「字符集合重合」召回：把用户输入
     * 与库中商品名都压成字符集合，重合度高的（如「奶」与「乳」同属乳制品语义场景，
     * 字符上虽不同字，但「品」与「制」会命中，配合实体词权重）优先。更可靠的做法是
     * 维护一张俗称→规范名同义表（奶制品→乳），见 {@link #SYNONYMS}。</p>
     */
    private List<String> suggestByChars(String table, String raw) {
        Map<String, Integer> score = new HashMap<>();

        // ① 俗称/同义映射优先命中（「奶制品」→ 乳及奶油…）
        for (Map.Entry<String, String[]> e : SYNONYMS.entrySet()) {
            if (raw.contains(e.getKey())) {
                for (String kw : e.getValue()) {
                    for (String n : jdbc.queryForList(
                            "SELECT DISTINCT 商品名称 FROM " + table + " WHERE 商品名称 LIKE ? LIMIT 10",
                            String.class, "%" + kw + "%")) {
                        score.merge(n, 6, Integer::sum); // 高权重，压过普通 2-gram
                    }
                }
            }
        }

        // ② 常规 2-gram
        LinkedHashSet<String> grams = new LinkedHashSet<>();
        for (int i = 0; i + 2 <= raw.length() && grams.size() < 6; i++) {
            grams.add(raw.substring(i, i + 2));
        }
        try {
            for (String g : grams) {
                for (String n : jdbc.queryForList(
                        "SELECT DISTINCT 商品名称 FROM " + table + " WHERE 商品名称 LIKE ? LIMIT 60",
                        String.class, "%" + g + "%")) {
                    score.merge(n, 1, Integer::sum);
                }
            }
            if (score.isEmpty()) {
                for (String ch : singleChars(raw)) {
                    List<String> names = jdbc.queryForList(
                            "SELECT DISTINCT 商品名称 FROM " + table + " WHERE 商品名称 LIKE ? LIMIT 4",
                            String.class, "%" + ch + "%");
                    for (String n : names) score.merge(n, 1, Integer::sum);
                    if (score.size() >= SUGGEST_LIMIT) break;
                }
            }
        } catch (Exception e) {
            log.warn("相近商品名召回失败: {}", e.getMessage());
        }
        return score.entrySet().stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(b.getValue(), a.getValue());
                    if (c != 0) return c;
                    return Integer.compare(a.getKey().length(), b.getKey().length());
                })
                .map(Map.Entry::getKey)
                .limit(SUGGEST_LIMIT)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 俗称 / 泛称 → 规范名关键词 的同义映射。
     * 只放「库里真实存在、且用户极可能用俗称」的少数高频品类，避免误伤。
     */
    private static final Map<String, String[]> SYNONYMS = Map.of(
            "奶制品", new String[]{"乳及奶油", "固状乳及奶油", "乳及奶油，含脂量"},
            "乳制品", new String[]{"乳及奶油", "固状乳及奶油", "乳及奶油，含脂量"},
            "奶", new String[]{"乳及奶油"},
            "乳", new String[]{"乳及奶油"}
    );

    private List<String> singleChars(String raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c)) continue;
            out.add(String.valueOf(c));
        }
        return new ArrayList<>(out);
    }

    private String table(String tt) {
        return db + ("out".equals(tt) ? ".trade_out" : ".trade_in");
    }

    private String dataRange(String table) {
        try {
            Map<String, Object> m = jdbc.queryForMap(
                    "SELECT MIN(数据年月) AS mn, MAX(数据年月) AS mx FROM " + table);
            return formatYm(toInt(m.get("mn"))) + "~" + formatYm(toInt(m.get("mx")));
        } catch (Exception e) {
            return "未知";
        }
    }

    private int maxYm(String table) {
        try {
            Integer mx = jdbc.queryForObject("SELECT MAX(数据年月) FROM " + table, Integer.class);
            return mx == null ? 0 : mx;
        } catch (Exception e) {
            return 0;
        }
    }

    private void appendEq(StringBuilder where, List<Object> args, String col, String val) {
        String v = blankToNull(val);
        if (v == null) return;
        where.append(" AND ").append(col).append(" = ?");
        args.add(v);
    }

    private String normalizeTradeType(String v) {
        if (v == null) return null;
        String t = v.trim().toLowerCase(Locale.ROOT);
        if (t.contains("进口") || t.equals("in") || t.equals("import")) return "in";
        if (t.contains("出口") || t.equals("out") || t.equals("export")) return "out";
        return null;
    }

    private static String formatYm(Integer ym) {
        if (ym == null) return "-";
        int v = ym;
        return String.format("%04d-%02d", v / 100, v % 100);
    }

    /** 中文年月：2025+1 -> 「2025年1月」；缺一即只写有的那一半。 */
    private static String cnYm(Integer year, Integer month) {
        StringBuilder sb = new StringBuilder();
        if (year != null) sb.append(year).append("年");
        if (month != null) sb.append(month).append("月");
        return sb.toString();
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmt(Double d) {
        if (d == null) return "-";
        if (Math.abs(d) >= 1000) return String.format("%,.2f", d);
        return String.format("%.4f", d);
    }

    private static String blank(String s) {
        return s == null || s.trim().isEmpty() ? "（未限定）" : s.trim();
    }

    private static String safe(String s) {
        return s == null || s.trim().isEmpty() ? "" : s.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static <T> List<T> head(List<T> list, int n) {
        return list.size() <= n ? list : new ArrayList<>(list.subList(0, n));
    }

    private static final class Resolved {
        final String value;
        final List<String> candidates;

        Resolved(String value, List<String> candidates) {
            this.value = value;
            this.candidates = candidates;
        }
    }
}
