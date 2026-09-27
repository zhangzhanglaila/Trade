package com.example.tdproject;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.tdb.TDBFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 批量把磁盘上的 RDF 数据装入 Jena TDB 图库。
 *
 * <p>【为什么需要它】
 * 项目里 940 MB 的贸易知识图谱 RDF（275 个文件、约 196 万条三元组）
 * 以及 28 MB 的新闻图谱 RDF 一直只以文件形式躺在 data/ 下，
 * 从未写入 TDB。RdfImportTest 中的 writeToTDB() 是被注释掉的，
 * 因此新部署的环境里图库是空的，图谱可视化与图算法分析都没有数据。
 *
 * <p>【为什么不直接用 Fuseki 的 tdbloader】
 * deliverables/查询工具/apache-jena-fuseki-5.4.0 用的是 TDB2 存储格式，
 * 而本项目的 JenaGraphRepositoryImpl 走的是 Jena 4.10 的
 * org.apache.jena.tdb.TDBFactory（TDB1）。两种格式互不兼容，
 * 必须用与运行时完全相同的 API 写入，否则应用读不到数据。
 *
 * <p>【默认不执行】
 * 本类带 @EnabledIfSystemProperty，常规构建不会跑。显式导入：
 * <pre>
 *   cd src/backend-main
 *   mvn test -Dgraph.import=true -Dtest=RdfBulkImportTest
 * </pre>
 * 可选参数：
 * <pre>
 *   -Dtrade.workspace=/opt/trade         项目根（默认按模块目录上溯两级）
 *   -Dgraph.store=...                    TDB 目录（默认 runtime/ontology-graph-store）
 *   -Dgraph.dir=...                      要导入的目录，多个用逗号分隔
 *   -Dgraph.clear=true                   导入前清空已有数据
 * </pre>
 */
@EnabledIfSystemProperty(named = "graph.import", matches = "true")
public class RdfBulkImportTest {

    private static final Logger log = LoggerFactory.getLogger(RdfBulkImportTest.class);

    /** 项目工作区根目录：由 Maven surefire 注入，默认按模块目录上溯两级（=项目根） */
    private static final String WORKSPACE =
            System.getProperty("trade.workspace", "../..");

    /** TDB 存储目录，与 application.yaml 的 ontology.graph.store-path 保持一致 */
    private static final String TDB_PATH =
            System.getProperty("graph.store", WORKSPACE + "/runtime/ontology-graph-store");

    /** 默认导入这两个目录下的全部 .rdf */
    private static final String DEFAULT_DIRS =
            WORKSPACE + "/data/02_派生_贸易知识图谱RDF,"
                    + WORKSPACE + "/data/03_派生_新闻知识图谱RDF";

    /** 每积累多少个文件提交一次事务，避免单个超大事务耗尽内存 */
    private static final int COMMIT_EVERY = 15;

    /**
     * 导入目标命名图。
     *
     * <p>【为什么必须写命名图】应用侧从不读默认图 —— 整个 backend-main 的
     * src/main 里 {@code getDefaultModel} 出现了 0 次。图谱可视化
     * （{@code GET /ontology/{id}/visualization}）与结构分析
     * （{@code /ontology/{id}/graph/structure}）都只查一个命名图：
     *
     * <pre>
     *   JenaGraphRepositoryImpl.buildNamedGraphUri()
     *       = ontology.graph.named-graph-prefix
     *         + ontology.projectName + "/v" + ontology.versionNumber
     * </pre>
     *
     * 默认值对应 sys 库里 ontology 表唯一那行（projectName=中哈贸易知识图谱本体、
     * versionNumber=v1.0），注意拼接结果里是双 v（"…/v" + "v1.0"）。
     * 实测：只导入默认图时，TDB 有 355 万条，而 /visualization 返回空数组。
     *
     * 用 -Dgraph.named= 覆盖；传空串则退回导入默认图。
     */
    private static final String NAMED_GRAPH =
            System.getProperty("graph.named",
                    "http://example.org/ontology/中哈贸易知识图谱本体/vv1.0");

    @Test
    public void bulkImport() {
        String dirsProp = System.getProperty("graph.dir", DEFAULT_DIRS);
        boolean clear = Boolean.parseBoolean(System.getProperty("graph.clear", "false"));

        List<File> files = new ArrayList<>();
        for (String d : dirsProp.split(",")) {
            File dir = new File(d.trim());
            if (!dir.isDirectory()) {
                log.warn("目录不存在，跳过: {}", dir.getAbsolutePath());
                continue;
            }
            collect(dir, files);
        }
        files.sort(Comparator.comparing(File::getAbsolutePath));

        long totalBytes = files.stream().mapToLong(File::length).sum();
        log.info("========== 批量导入 RDF -> TDB ==========");
        log.info("存储目录 : {}", new File(TDB_PATH).getAbsolutePath());
        log.info("待导入   : {} 个文件, {} MB", files.size(), String.format("%.2f", totalBytes / 1048576.0));
        if (files.isEmpty()) {
            log.warn("没有找到任何 .rdf 文件，退出");
            return;
        }

        // 首次部署时 runtime/ontology-graph-store 尚不存在，而
        // TDBFactory.createDataset 对不存在的路径会走"连接已有库"分支，
        // 直接抛 "Does not exist: ..."，导致文档 §4.3 的导入命令在全新机器上
        // 必然失败。先建出空目录，让它走创建分支。
        new File(TDB_PATH).mkdirs();

        Dataset dataset = TDBFactory.createDataset(TDB_PATH);
        try {
            log.info("目标命名图: {}", NAMED_GRAPH.isEmpty() ? "(默认图)" : NAMED_GRAPH);

            // 可选：清空（默认图与目标命名图一起清，避免残留撑大 TDB）
            if (clear) {
                log.info("graph.clear=true，先清空默认图与目标命名图…");
                dataset.begin(ReadWrite.WRITE);
                try {
                    dataset.getDefaultModel().removeAll();
                    if (!NAMED_GRAPH.isEmpty()) {
                        dataset.getNamedModel(NAMED_GRAPH).removeAll();
                    }
                    dataset.commit();
                } finally {
                    dataset.end();
                }
            }

            long before = countTriples(dataset, NAMED_GRAPH);
            log.info("导入前三元组数: {}", before);

            int done = 0;
            int failed = 0;
            long pending = 0;
            long t0 = System.currentTimeMillis();
            dataset.begin(ReadWrite.WRITE);
            try {
                Model target = NAMED_GRAPH.isEmpty()
                        ? dataset.getDefaultModel()
                        : dataset.getNamedModel(NAMED_GRAPH);
                for (File f : files) {
                    Model tmp = ModelFactory.createDefaultModel();
                    String lang = detectLang(f);
                    try (Reader reader = openReader(f, lang)) {
                        tmp.read(reader, null, lang);
                        target.add(tmp);
                        pending += tmp.size();
                    } catch (Exception e) {
                        failed++;
                        log.error("读取失败 {} : {}", f.getName(), e.getMessage());
                    } finally {
                        tmp.close();
                    }
                    done++;
                    if (done % COMMIT_EVERY == 0 || done == files.size()) {
                        dataset.commit();
                        long sec = (System.currentTimeMillis() - t0) / 1000;
                        log.info("进度 {}/{}  本次新增约 {} 条  用时 {}s",
                                done, files.size(), pending, sec);
                        pending = 0;
                        dataset.begin(ReadWrite.WRITE);
                    }
                }
                // 若最后一次 begin 后没有内容，显式结束
                dataset.commit();
            } catch (Exception e) {
                dataset.abort();
                throw e;
            } finally {
                dataset.end();
            }

            long after = countTriples(dataset, NAMED_GRAPH);
            log.info("========================================");
            log.info("导入完成：处理 {} 个文件，失败 {} 个", done, failed);
            log.info("导入前 {} 条 -> 导入后 {} 条（净增 {}）", before, after, after - before);
            if (after < 1_000_000) {
                log.warn("警告：图库三元组数不足 100 万，请检查数据目录是否完整");
            }
        } finally {
            dataset.close();
        }
    }

    /** 递归收集 .rdf / .owl / .ttl / .nt */
    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            if (c.isDirectory()) {
                collect(c, out);
            } else {
                String n = c.getName().toLowerCase();
                if (n.endsWith(".rdf") || n.endsWith(".owl")
                        || n.endsWith(".ttl") || n.endsWith(".nt")) {
                    out.add(c);
                }
            }
        }
    }

    private static String detectLang(File f) {
        String n = f.getName().toLowerCase();
        if (n.endsWith(".ttl")) {
            return "TURTLE";
        }
        if (n.endsWith(".nt")) {
            return "N-TRIPLE";
        }
        return "RDF/XML";
    }

    // ==================================================================
    // URI 清洗：把 RDF/XML 属性里的非法 IRI 修成合法形式
    //
    // 【为什么需要】这批 mapped_data.rdf 的 rdf:about 直接拿商品名称拼 URI，
    // 商品名里的各种写法让 URI 违反 RFC 3986/3987。Jena 会报 {W002} 并
    // ——关键——立刻中止整个文件的解析（不是跳过该条）。实测最严重的一个文件
    // 有 1183 个 rdf:Description，首个坏 IRI 出现在第 807 行，结果只导入 431 条
    // 就停了。
    //
    // 【四类问题及分布（275 个文件全量统计）】
    //   1. 裸 %（"弹性线≥5%" 这类商品名）      8,930 处 / 254 个文件
    //   2. 字符引用形式的控制字符 &#10; &#13;      293 处 / 123 个文件
    //   3. 裸空格（"西鲱, 整条"）                   若干
    //   4. 路径里的方括号 []（RFC 3986 只允许出现在 host）
    //
    // 【为什么可以一律转义】已核对：这些 % 没有一个是合法的 %XX，不存在二次
    // 转义；空格与方括号在 URI 路径中本就非法。清洗只作用于解析这一侧，
    // data/ 下的原文件保持不动。
    // ==================================================================

    /** RDF/XML 中承载 URI 的属性（实测仅 rdf:about 命中，另两个一并覆盖） */
    private static final Pattern URI_ATTR =
            Pattern.compile("(rdf:(?:about|resource|datatype))=\"([^\"]*)\"");

    /** 数字字符引用，如 &#10; / &#x0D; */
    private static final Pattern CHARREF = Pattern.compile("&#([xX]?)([0-9A-Fa-f]+);");

    /** URI 里必须百分号编码的字符（空格与控制字符另行按码点处理） */
    private static final String URI_ILLEGAL = "<>\"{}|\\^`[]";

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /** 把单个 URI 属性值修成合法 IRI。 */
    private static String escapeUri(String uri) {
        String u = uri;
        // 1) 字符引用形式的控制字符 -> 百分号编码
        if (u.indexOf("&#") >= 0) {
            Matcher cm = CHARREF.matcher(u);
            StringBuilder c = new StringBuilder(u.length());
            while (cm.find()) {
                int code;
                try {
                    code = Integer.parseInt(cm.group(2), cm.group(1).isEmpty() ? 10 : 16);
                } catch (NumberFormatException e) {
                    code = -1;
                }
                if (code >= 0 && (code < 0x20 || code == 0x7f)) {
                    cm.appendReplacement(c, Matcher.quoteReplacement(String.format("%%%02X", code)));
                } else {
                    cm.appendReplacement(c, Matcher.quoteReplacement(cm.group(0)));
                }
            }
            cm.appendTail(c);
            u = c.toString();
        }
        // 2) 逐字符：裸 % -> %25；空格/控制字符/URI 禁用符 -> %XX
        StringBuilder s = new StringBuilder(u.length() + 16);
        for (int i = 0; i < u.length(); i++) {
            char ch = u.charAt(i);
            if (ch == '%') {
                boolean legal = i + 2 < u.length()
                        && isHex(u.charAt(i + 1)) && isHex(u.charAt(i + 2));
                s.append(legal ? "%" : "%25");
            } else if (ch <= 0x20 || ch == 0x7f || URI_ILLEGAL.indexOf(ch) >= 0) {
                s.append(String.format("%%%02X", (int) ch));
            } else {
                s.append(ch);
            }
        }
        return s.toString();
    }

    /** 打开一个 RDF 文件用于解析；RDF/XML 会先做上述 URI 清洗。 */
    private static Reader openReader(File f, String lang) throws Exception {
        if (!"RDF/XML".equals(lang)) {
            return new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8);
        }
        String xml = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        if (xml.indexOf('%') < 0 && xml.indexOf("&#") < 0) {
            return new StringReader(xml);
        }
        Matcher m = URI_ATTR.matcher(xml);
        StringBuilder sb = new StringBuilder(xml.length() + 512);
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    m.group(1) + "=\"" + escapeUri(m.group(2)) + "\""));
        }
        m.appendTail(sb);
        return new StringReader(sb.toString());
    }

    private static long countTriples(Dataset dataset, String graphUri) {
        dataset.begin(ReadWrite.READ);
        try {
            return (graphUri == null || graphUri.isEmpty()
                    ? dataset.getDefaultModel()
                    : dataset.getNamedModel(graphUri)).size();
        } finally {
            dataset.end();
        }
    }
}
