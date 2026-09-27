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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            // 可选：清空
            if (clear) {
                log.info("graph.clear=true，先清空默认图…");
                dataset.begin(ReadWrite.WRITE);
                try {
                    dataset.getDefaultModel().removeAll();
                    dataset.commit();
                } finally {
                    dataset.end();
                }
            }

            long before = countTriples(dataset);
            log.info("导入前默认图三元组数: {}", before);

            int done = 0;
            int failed = 0;
            long pending = 0;
            long t0 = System.currentTimeMillis();
            dataset.begin(ReadWrite.WRITE);
            try {
                Model target = dataset.getDefaultModel();
                for (File f : files) {
                    Model tmp = ModelFactory.createDefaultModel();
                    try (InputStreamReader reader = new InputStreamReader(
                            new FileInputStream(f), StandardCharsets.UTF_8)) {
                        tmp.read(reader, null, detectLang(f));
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

            long after = countTriples(dataset);
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

    private static long countTriples(Dataset dataset) {
        dataset.begin(ReadWrite.READ);
        try {
            return dataset.getDefaultModel().size();
        } finally {
            dataset.end();
        }
    }
}
