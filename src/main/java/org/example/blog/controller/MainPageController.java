package org.example.blog.controller;

import org.example.blog.service.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class MainPageController {
    @ResponseBody
    @GetMapping("/index")
    public String index() {
        return "Hello World!";
    }

    @GetMapping("/")
    public String mainPage() {
        return "redirect:/index";
    }

    @ResponseBody
    @GetMapping("/index_for_logged_user")
    public String indexForLoggedUser(@AuthenticationPrincipal CustomUserDetails user) {
        return "Hello " + user.getUsername() + "!";
    }
}
