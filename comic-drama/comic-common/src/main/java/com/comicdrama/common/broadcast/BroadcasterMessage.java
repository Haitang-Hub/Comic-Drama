package com.comicdrama.common.broadcast;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 广播消息载体（ApplicationEvent 包装）。
 */
@Getter
public class BroadcasterMessage extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String channel;
    private final Object payload;

    public BroadcasterMessage(String channel, Object payload) {
        super(channel);
        this.channel = channel;
        this.payload = payload;
    }
}
