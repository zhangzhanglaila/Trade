package cait.collector.datasource;

import cait.collector.configure.datasource.GdeltDatasourceConfiguration;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class GDELTDatasource {

    private static final DateTimeFormatter DateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String GkgFileNameFormat = "%s.gkg.csv.zip";
    private static final String GkgFileUrlFormat = "http://data.gdeltproject.org/gkg/%s";

    private final OkHttpClient okHttpClient;
    private final GdeltDatasourceConfiguration gdeltDatasourceConfiguration;

    public GDELTDatasource(OkHttpClient okHttpClient, GdeltDatasourceConfiguration gdeltDatasourceConfiguration) {
        this.okHttpClient = okHttpClient;
        this.gdeltDatasourceConfiguration = gdeltDatasourceConfiguration;
    }

    @Data
    @Builder
    public static class GdeltEventItem {
        private String date;
        private String themes;
        private String locations;
        private String url;
    }

    public List<String> downloadGkgFile(LocalDate start, LocalDate end) {
        var saveDir = gdeltDatasourceConfiguration.getDownloadCacheDir();

        List<String> filePaths = new ArrayList<>();
        for (var date = start; date.isBefore(end) || date.isEqual(end); date = date.plusDays(1)) {
            var filename = String.format(GkgFileNameFormat, date.format(DateFormatter));
            var url = GkgFileUrlFormat.formatted(filename);
            try {
                filePaths.add(download(url, filename, saveDir));
            } catch (Exception e) {
                log.warn("Exception happened when downloading GDELT GKG zip file: {}, skip", filename, e);
            }
        }

        return filePaths;
    }

    public String downloadGkgFile(LocalDate date) throws IOException {
        var saveDir = gdeltDatasourceConfiguration.getDownloadCacheDir();

        var filename = String.format(GkgFileNameFormat, date.format(DateFormatter));
        var url = GkgFileUrlFormat.formatted(filename);

        return download(url, filename, saveDir);
    }

    public String downloadGkgFileToday() throws IOException {
        return downloadGkgFile(LocalDate.now());
    }

    public List<GdeltEventItem> filterEvent(String file, boolean zip) throws IOException, CsvValidationException {
        if (zip) {
            try (InputStream fis = new FileInputStream(file);
                 ZipArchiveInputStream zis = new ZipArchiveInputStream(fis, "UTF-8")) {

                if (zis.getNextEntry() != null) {
                    return filterEvent(fis);
                }
            }
        }

        return filterEvent(new FileInputStream(file));
    }

    private List<GdeltEventItem> filterEvent(InputStream inputStream) throws IOException, CsvValidationException {
        try (var csvReader = new CSVReaderBuilder(new InputStreamReader(inputStream))
                .withCSVParser(new CSVParserBuilder().withSeparator('\t').build())
                .build()
        ) {
            return filterEvent(csvReader);
        }
    }

    private List<GdeltEventItem> filterEvent(CSVReader csvReader) throws IOException, CsvValidationException {
        var result = new ArrayList<GdeltEventItem>();

        var themeKeyWords = gdeltDatasourceConfiguration.getThemeFilterKeywords().toArray(new String[0]);
        var locationKeyWords = gdeltDatasourceConfiguration.getLocationFilterKeywords().toArray(new String[0]);

        String[] firstRow = csvReader.readNext();

        if (firstRow == null) {
            return new ArrayList<>();
        }

        var dateRowIdx = -1;
        var themeRowIdx = -1;
        var locationRowIdx = -1;
        var urlRowIdx = -1;
        for (int i = 0; i < firstRow.length; i++) {
            if ("THEME".equals(firstRow[i])) {
                themeRowIdx = i;
            } else if ("LOCATION".equals(firstRow[i])) {
                locationRowIdx = i;
            } else if ("SOURCEURLS".equals(firstRow[i])) {
                urlRowIdx = i;
            } else if ("DATE".equals(firstRow[i])) {
                dateRowIdx = i;
            }
        }

        if (checkAnyone(dateRowIdx, themeRowIdx, locationRowIdx, urlRowIdx)) {
            return new ArrayList<>();
        }

        int resultCnt = 0;
        String[] row;
        while ((row = csvReader.readNext()) != null) {
            var date = row[dateRowIdx];
            var theme = row[themeRowIdx];
            var location = row[locationRowIdx];
            var url = row[urlRowIdx].split("<UDIV>")[0];

            var keywordCountMap = countKeywords(theme, themeKeyWords);
            var ratio = computeKeywordRatio(theme, keywordCountMap);
//
//            if (ratio >= gdeltDatasourceConfiguration.getThemeKeywordsMinRatio() &&
            if (StringUtils.containsAnyIgnoreCase(theme, themeKeyWords) &&
                    StringUtils.containsAnyIgnoreCase(location, locationKeyWords)
            ) {
                resultCnt++;
                if (resultCnt > 10) {
                    break;
                }

                result.add(GdeltEventItem.builder()
                        .date(date)
                        .themes(theme)
                        .locations(location)
                        .url(url)
                        .build()
                );
            }
        }

        return result;
    }

    private static Map<String, Integer> countKeywords(String text, String[] keywords) {
        Map<String, Integer> keywordCounts = new HashMap<>();
        for (String keyword : keywords) {
            int count = 0;
            int index = 0;

            while ((index = text.indexOf(keyword, index)) != -1) {
                count++;
                index += keyword.length();
            }

            keywordCounts.put(keyword, count);
        }

        return keywordCounts;
    }

    private static float computeKeywordRatio(String text, Map<String, Integer> keywordCounts) {
        var total = 0;
        for (String keyword : keywordCounts.keySet()) {
            int count = keywordCounts.getOrDefault(keyword, 0);
            total += count*keyword.length();
        }

        return (float) total / (float) text.length();
    }

    private boolean checkAnyone(int... values) {
        for (int val : values) {
            if (val == -1) {
                return true;
            }
        }

        return false;
    }

    private String download(String url, String filename, String saveDir) throws IOException {
        var saveDirPath = Paths.get(saveDir);
        if (!Files.exists(saveDirPath)) {
            Files.createDirectories(saveDirPath);
        }

        Path saveFile = Paths.get(saveDir, filename);
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    return null;
                }
                throw new IOException("Failed to download file: " + response.code());
            }

            if (response.body() == null) {
                throw new IOException("File data null");
            }

            if (!Files.exists(saveFile)) {
                Files.createFile(saveFile);
            }

            try (InputStream inputStream = response.body().byteStream()) {
                Files.copy(inputStream, saveFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return saveFile.toAbsolutePath().toString();
    }
}
