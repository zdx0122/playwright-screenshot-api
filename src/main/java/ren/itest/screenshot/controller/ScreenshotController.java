package ren.itest.screenshot.controller;

import ren.itest.screenshot.service.ScreenshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

@RestController
public class ScreenshotController {

    @Autowired
    private ScreenshotService screenshotService;

    @Async
    @GetMapping("/api/screenshot")
    public CompletableFuture<ResponseEntity<byte[]>> getScreenshot(@RequestParam String url) {
        // 异步调用 ScreenshotService
        return screenshotService.takeScreenshot(url).thenApply(screenshot -> {
            try {
                // 将截图文件转换为字节数组
                byte[] imageBytes = Files.readAllBytes(screenshot.toPath());

                // 设置响应头为 image/png
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.CONTENT_TYPE, "image/png");

                // 返回截图数据
                return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
            } catch (IOException e) {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });
    }
}
