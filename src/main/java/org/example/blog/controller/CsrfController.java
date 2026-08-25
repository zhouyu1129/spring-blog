package org.example.blog.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CSRF Token 端点
 * 前端访问此接口后，Spring Security 会通过 Cookie 设置 XSRF-TOKEN，
 * 后续非 GET 请求前端需通过 X-XSRF-TOKEN 头携带回来
 */
@RestController
@RequestMapping("/api")
public class CsrfController {

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
            "parameterName", token.getParameterName(),
            "headerName", token.getHeaderName(),
            "token", token.getToken()
        );
    }
}
