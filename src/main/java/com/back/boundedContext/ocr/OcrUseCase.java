package com.back.boundedContext.ocr;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrUseCase {
    @Value("${ocr.default-lang}")
    private String defaultLang;

    @Value("${ocr.tessdata-path}")
    private String tessdataPath;

    private static final Kernel SHARPEN_KERNEL = new Kernel(3, 3, new float[]{
            0f, -1f,  0f,
            -1f,  5f, -1f,
            0f, -1f,  0f
    });

    @PostConstruct
    void init() {
        System.setProperty("jna.library.path", "/opt/homebrew/lib");
    }

    public String extract(MultipartFile image) {
        BufferedImage bufferedImage;
        try {
            bufferedImage = ImageIO.read(image.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        BufferedImage preprocess = preprocess(bufferedImage);

        String bestText = "";
        for (int psm : new int[]{3, 6}) {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessdataPath);
            tesseract.setLanguage(defaultLang);
            tesseract.setPageSegMode(psm);
            tesseract.setOcrEngineMode(1);
            tesseract.setVariable("preserve_interword_spaces", "1");

            try {
                String text = tesseract.doOCR(preprocess).strip();
                if (text.length() > bestText.length()) {
                    bestText = text;
                }

            } catch (TesseractException e) {
                throw new RuntimeException(e);
            }
        }

        return bestText;
    }


    /**
     * 이미지 전처리 파이프라인 (한글+영문 혼합 최적화)
     * <p>
     * 전처리 과도 적용 시 역효과 실측 결과:
     * - 샤프닝 + 대비 1.8: 한글 ↑ but 특수문자(`.` `_` `+`)가 왜곡됨
     * - Median Filter: 한글 획 훼손
     * - UnsharpMask: 한글 인식률 하락
     * - 이진화(threshold 128): 한글 얇은 획 소실
     * <p>
     * 최종 전략:
     * 1. 저해상도(≤1200px) 이미지만 2배 확대
     * 2. 그레이스케일 변환
     * 3. 저해상도 이미지만 샤프닝 (고해상도는 스킵 — 특수문자 왜곡 방지)
     * 4. 부드러운 명암 대비 (scale=1.5, offset=-10)
     */
    private BufferedImage preprocess(BufferedImage src) {
        int     w        = src.getWidth();
        int     h        = src.getHeight();
        int     minDim   = Math.min(w, h);
        boolean isLowRes = minDim <= 1200;

        // 1. 적응형 확대 (저해상도만)
        BufferedImage scaled = src;
        if (isLowRes) {
            int factor = minDim <= 300 ? 3 : 2;
            int newW   = w * factor;
            int newH   = h * factor;
            scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, newW, newH, null);
            g.dispose();
        }

        // 2. 그레이스케일
        BufferedImage gray = new BufferedImage(scaled.getWidth(), scaled.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D    g2   = gray.createGraphics();
        g2.drawImage(scaled, 0, 0, null);
        g2.dispose();

        // 3. 조건부 샤프닝 (저해상도만 — 고해상도는 `.` `_` 등 특수문자 왜곡 방지)
        BufferedImage sharpened = gray;
        if (isLowRes) {
            sharpened = new ConvolveOp(SHARPEN_KERNEL, ConvolveOp.EDGE_NO_OP, null).filter(gray, null);
        }

        // 4. 부드러운 명암 대비 (특수문자 보존 + 한글 인식 밸런스)
        return new RescaleOp(1.5f, -10f, null).filter(sharpened, null);
    }
}
