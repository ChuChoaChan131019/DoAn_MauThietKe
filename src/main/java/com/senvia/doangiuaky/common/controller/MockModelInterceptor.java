package com.senvia.doangiuaky.common.controller;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class MockModelInterceptor implements HandlerInterceptor {
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        if (modelAndView != null && modelAndView.getViewName() != null
                && !modelAndView.getViewName().startsWith("redirect:")) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String role = authentication == null ? null : authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority.equals("ROLE_USER") || authority.equals("ROLE_ADMIN"))
                    .map(authority -> authority.substring("ROLE_".length()))
                    .findFirst()
                    .orElse(null);
            modelAndView.addObject("role", role);

            // Shop status remains owned by merchant; this value is only used by the current UI mock.
            String shopStatus = request.getParameter("shopStatus");
            modelAndView.addObject("shopStatus", shopStatus);
        }
    }
}
