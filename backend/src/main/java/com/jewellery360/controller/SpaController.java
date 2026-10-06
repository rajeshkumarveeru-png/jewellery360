package com.jewellery360.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Lets the web app be opened on any of its screen addresses (for example /sales or /billing/new),
 * also after a browser refresh: those addresses are not files, so they are handed to index.html and
 * the React router shows the right screen. Addresses starting with /api and real files
 * (anything with a dot, such as /assets/app.js) are not touched.
 */
@Controller
public class SpaController {

    @GetMapping({
            "/{p1:^(?!api$)[^.]*$}",
            "/{p1:^(?!api$)[^.]*$}/{p2:[^.]*}",
            "/{p1:^(?!api$)[^.]*$}/{p2:[^.]*}/{p3:[^.]*}",
            "/{p1:^(?!api$)[^.]*$}/{p2:[^.]*}/{p3:[^.]*}/{p4:[^.]*}"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
