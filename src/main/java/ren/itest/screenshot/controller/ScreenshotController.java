package ren.itest.screenshot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import ren.itest.screenshot.entity.ResponseDto;
import ren.itest.screenshot.enums.FormatEnum;
import ren.itest.screenshot.enums.ResponseCodeEnum;
import ren.itest.screenshot.service.ScreenshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

@RestController
public class ScreenshotController {
    private static final Logger logger= LoggerFactory.getLogger(ScreenshotController.class);
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

    @GetMapping("/api/screenshot/take")
    public ResponseEntity takeScreenshot(@RequestParam String url,
                                      @RequestParam(defaultValue = ".jpg") String format,
                                      @RequestParam(defaultValue = "false") String fullPage,
                                      @RequestParam(defaultValue = "100") String quality,
                                      @RequestParam(defaultValue = "false") boolean uploadToR2
                                      ) {
        logger.info("入参：" + url + " " + format + " " + fullPage + " " + quality + " " + uploadToR2);
        format = format.toLowerCase();
        if (!format.equals(FormatEnum.JPG.getCode()) && !format.equals(FormatEnum.PNG.getCode()) && !format.equals(FormatEnum.webp.getCode())) {
            return ResponseEntity.badRequest().body(new ResponseDto(ResponseCodeEnum.PARAM_ERROR));
        }
        Boolean fullPageBool = false;
        if (fullPage.equals("true")) {
            fullPageBool = true;
        } else {
            fullPageBool = false;
        }

        if (quality == null){
            quality = "100";
        }
        Integer qualityInt = Integer.parseInt(quality);
        if (qualityInt < 0 || qualityInt > 100) {
            return ResponseEntity.badRequest().body(new ResponseDto(ResponseCodeEnum.PARAM_ERROR));
        }

        return screenshotService.takeScreenshotAndUpload(url, format, fullPageBool, qualityInt, uploadToR2).thenApply(screenshotUrl -> {
            return ResponseEntity.ok().body(new ResponseDto(ResponseCodeEnum.SUCCESS.getCode(), screenshotUrl));
        }).join();
    }


    @GetMapping(value = "/images",produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<InputStreamResource> getImage(@RequestParam String imageName) throws IOException {
        logger.info(imageName);

        File file = new File(imageName);

        // 检查资源是否存在
        if (!file.exists()) {
            logger.info("资源不存在：" + file.exists());
            return ResponseEntity.notFound().build();
        }

        InputStreamResource isr = new InputStreamResource(new FileInputStream(file));
        // 设置HTTP响应头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG); // 根据实际图片类型设置

        // 返回InputStreamResource作为HTTP响应体
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(file.length())
                .body(isr);
    }
}
