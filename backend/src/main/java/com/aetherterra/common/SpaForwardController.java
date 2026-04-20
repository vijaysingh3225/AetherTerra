package com.aetherterra.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/",
            "/auctions",
            "/auctions/{slug}",
            "/login",
            "/register",
            "/verify-email",
            "/account",
            "/admin",
            "/admin/users",
            "/admin/auctions"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
