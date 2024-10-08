package ren.itest.screenshot.service;

import com.microsoft.playwright.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;

import static com.microsoft.playwright.options.LoadState.NETWORKIDLE;

@Service
public class ScreenshotService {

    private Playwright playwright;
    private Browser browser;
    private final ExecutorService executorService;

    // 设置最大并发数，确保它至少为1
    @Value("${screenshot.max-concurrent-requests:5}")
    private int maxConcurrentRequests;

    public ScreenshotService() {
//        System.out.println("maxConcurrentRequests数值为：" + maxConcurrentRequests);
//        // 验证 maxConcurrentRequests 是否为正数，如果小于 1 则设置为 1
//        if (maxConcurrentRequests < 1) {
//            throw new IllegalArgumentException("maxConcurrentRequests must be at least 1");
//        }

        // 创建一个固定大小的线程池，用于限制并发截图请求的数量
        this.executorService = Executors.newFixedThreadPool(10);
    }

    @PostConstruct
    public void init() {
        // 初始化 Playwright 和浏览器实例
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    public CompletableFuture<File> takeScreenshot(String url) {
        // 异步执行截图任务，提交到线程池
        return CompletableFuture.supplyAsync(() -> {
            // 创建一个新的页面
            Page page = browser.newPage();
            page.navigate(url);

            page.waitForLoadState(NETWORKIDLE);

            String modifiedUrl = url.replaceAll("https?://", "");
            // 生成截图文件
            Path screenshotPath = Paths.get("screenshot-" + modifiedUrl + "-"+ System.currentTimeMillis() + ".png");
            page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath));

            // 关闭页面
            page.close();

            return screenshotPath.toFile();
        }, executorService);
    }

    @PreDestroy
    public void cleanup() {
        // 关闭浏览器和 Playwright
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        // 关闭线程池
        executorService.shutdown();
    }
}
