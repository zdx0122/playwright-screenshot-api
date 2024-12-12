package ren.itest.screenshot.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ren.itest.screenshot.enums.FormatEnum;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;

import static com.microsoft.playwright.options.LoadState.NETWORKIDLE;

@Service
public class ScreenshotService {
    private static final Logger logger= LoggerFactory.getLogger(ScreenshotService.class);

    private Playwright playwright;
    private Browser browser;
    private final ExecutorService executorService;
    private S3Client s3Client;

    // 设置最大并发数，确保它至少为1
    @Value("${screenshot.max-concurrent-requests:5}")
    private int maxConcurrentRequests;

    @Value("${cloudflare.r2.access-key}")
    private String r2AccessKey;

    @Value("${cloudflare.r2.secret-key}")
    private String r2SecretKey;

    @Value("${cloudflare.r2.bucket-name}")
    private String r2BucketName;

    @Value("${cloudflare.r2.endpoint}")
    private String r2Endpoint;

    @Value("${cloudflare.r2.region:auto}")
    private String r2Region;

    @Value("${cloudflare.r2.domain}")
    private String r2Domain;

    @Value("${domain}")
    private String domain;

    public ScreenshotService() {
        // 创建一个固定大小的线程池，用于限制并发截图请求的数量
        this.executorService = Executors.newFixedThreadPool(10);
    }

    @PostConstruct
    public void init() {
        // 初始化 Playwright 和浏览器实例
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    /**
     * 截图并返回文件对象
     * @param url
     * @return
     */
    public CompletableFuture<File> takeScreenshot(String url) {
        // 异步执行截图任务，提交到线程池
        return CompletableFuture.supplyAsync(() -> {
            // 创建一个新的页面
            try(Page page = browser.newPage();) {
                page.navigate(url);

                page.waitForLoadState(NETWORKIDLE);

                String modifiedUrl = url.replaceAll("https?://", "");
                // 生成截图文件
                Path screenshotPath = Paths.get("screenshot-" + modifiedUrl + "-"+ System.currentTimeMillis() + ".png");
                page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(true));

                // 关闭页面
                page.close();

                return screenshotPath.toFile();
            }
        }, executorService);
    }

    /**
     * 截图并上传到 Cloudflare R2
     * @param url
     * @return
     */
    public CompletableFuture<String> takeScreenshotAndUpload(String url, String format, Boolean fullPage, Integer quality,
                                                             Boolean uploadToR2) {

        return CompletableFuture.supplyAsync(() -> {
            try (Page page = browser.newPage()) {
                // Take Screenshot
                page.navigate(url);
                page.waitForLoadState(NETWORKIDLE);
                String title = page.title();
                // 去掉空格和制表符
                String siteTitle = StrUtil.replace(StrUtil.replace(FileNameUtil.cleanInvalid(title), " ", "_"), "\t", "");
                Path screenshotPath = setScreenshotPath(siteTitle, format);
                page.screenshot(setScreenshotOptions(screenshotPath, format, fullPage, quality));

                // Upload to Cloudflare R2
                if (uploadToR2){
                    String uploadedUrl = uploadToR2(screenshotPath);
                    return uploadedUrl;
                }
                logger.info("Screenshot saved to: " + screenshotPath.toUri());
                String screenshotUrl = domain + "/images?imageName=" + screenshotPath.toString();
                logger.info("Screenshot URL: " + screenshotUrl);
                return screenshotUrl;
            }
        }, executorService);
    }

    /**
     * 上传文件到 Cloudflare R2
     * @param screenshotPath
     * @return
     */
    private String uploadToR2(Path screenshotPath) {
        // Initialize S3Client for Cloudflare R2
        this.s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2AccessKey, r2SecretKey)
                ))
                .region(Region.of(r2Region))
                .endpointOverride(URI.create(r2Endpoint))
                .build();

        // Upload the screenshot to R2
        String objectKey = screenshotPath.getFileName().toString();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(r2BucketName)
                .key(objectKey)
                .build();

        s3Client.putObject(putObjectRequest, screenshotPath);

        logger.info("Uploaded screenshot to R2: " + r2Domain + "/" + objectKey);

        // Return the URL of the uploaded object
        return String.format("%s/%s", r2Domain, objectKey);
    }

    public Page.ScreenshotOptions setScreenshotOptions(Path screenshotPath,String format, Boolean fullPage, Integer quality){
        if (fullPage == null){
            fullPage = false;
        }
        if (quality == null){
            quality = 100;
        }
        if (format == FormatEnum.JPG.getCode()){
            return new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage).setQuality(quality);
        }else {
            return new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage);
        }

    }

    public Path setScreenshotPath(String siteTitle, String format){
        if (format == null){
            format = FormatEnum.JPG.getCode();
        }
        Path screenshotPath = Paths.get("screenshot-" + siteTitle + "-" + DateUtil.today() + "-" + System.currentTimeMillis() + "-" + RandomUtil.randomInt(1000, 10000) + format);
        return screenshotPath;
    }

    @PreDestroy
    public void cleanup() {
        try {
            // 关闭浏览器
            if (browser != null) {
                browser.close();
            }
            // 关闭 Playwright
            if (playwright != null) {
                playwright.close();
            }
        } finally {
            // 关闭线程池
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (s3Client != null) {
            s3Client.close();
        }
    }
}
