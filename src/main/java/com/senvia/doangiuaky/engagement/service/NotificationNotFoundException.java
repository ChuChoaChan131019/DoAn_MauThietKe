package com.senvia.doangiuaky.engagement.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("Không tìm thấy thông báo.");
    }
}
