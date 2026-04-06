package com.apitestagent.service;

import com.apitestagent.web.dto.MethodSourceQueryRequest;
import com.apitestagent.web.dto.MethodSourceView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodSourceServiceTests {

    private final MethodSourceService methodSourceService = new MethodSourceService();

    @TempDir
    Path tempDir;

    @Test
    void shouldFindMethodWhenPackageAndParameterCountMatch() throws IOException {
        Path sourceRoot = createJavaFile("demo/order", "OrderService", sampleSource());

        MethodSourceQueryRequest request = new MethodSourceQueryRequest();
        request.setClassName("OrderService");
        request.setMethodName("createOrder");
        request.setPackageName("demo.order");
        request.setParameterCount(2);
        request.setSearchRoots(Collections.singletonList(sourceRoot.getParent().getParent().getParent().toString()));

        MethodSourceView result = methodSourceService.findMethodSource(request);
        assertTrue(result.getFilePath().contains("OrderService.java"));
        assertTrue(result.getMatchedSignature().contains("createOrder"));
        assertTrue(result.getSourceCode().contains("return orderId + count;"));
    }

    @Test
    void shouldRejectWhenPackageDoesNotMatch() throws IOException {
        Path sourceRoot = createJavaFile("demo/order", "OrderService", sampleSource());

        MethodSourceQueryRequest request = new MethodSourceQueryRequest();
        request.setClassName("OrderService");
        request.setMethodName("createOrder");
        request.setPackageName("demo.payment");
        request.setParameterCount(2);
        request.setSearchRoots(Collections.singletonList(sourceRoot.getParent().getParent().getParent().toString()));

        NoSuchElementException exception = assertThrows(NoSuchElementException.class, () -> methodSourceService.findMethodSource(request));
        assertEquals("找到类文件，但未定位到匹配的方法实现", exception.getMessage());
    }

    @Test
    void shouldRejectWhenParameterCountDoesNotMatch() throws IOException {
        Path sourceRoot = createJavaFile("demo/order", "OrderService", sampleSource());

        MethodSourceQueryRequest request = new MethodSourceQueryRequest();
        request.setClassName("OrderService");
        request.setMethodName("createOrder");
        request.setPackageName("demo.order");
        request.setParameterCount(1);
        request.setSearchRoots(Collections.singletonList(sourceRoot.getParent().getParent().getParent().toString()));

        NoSuchElementException exception = assertThrows(NoSuchElementException.class, () -> methodSourceService.findMethodSource(request));
        assertEquals("找到类文件，但未定位到匹配的方法实现", exception.getMessage());
    }

    private Path createJavaFile(String relativeDir, String className, String content) throws IOException {
        Path directory = tempDir.resolve(relativeDir);
        Files.createDirectories(directory);
        Path file = directory.resolve(className + ".java");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String sampleSource() {
        return "package demo.order;\n"
            + "public class OrderService {\n"
            + "    public String createOrder(String orderId, Integer count) {\n"
            + "        return orderId + count;\n"
            + "    }\n"
            + "}\n";
    }
}