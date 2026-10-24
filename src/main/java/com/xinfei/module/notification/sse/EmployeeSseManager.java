package com.xinfei.module.notification.sse;

import com.xinfei.common.ObjectMapperHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 员工端 SSE 连接管理器（后台管理壳层的实时通道，O-25 任务完成推送首用）。
 * <p>
 * 与 {@link SseEmitterManager}（C 端用户通道）刻意分开建类：员工 ID 与 C 端用户 ID
 * 分属两张表，数值可撞（员工 1 与顾客 1），共用同一张 Long 键连接表会跨主体串台。
 * 员工事件统一走本管理器，C 端事件仍走 {@code SseEmitterManager}。
 * </p>
 * <p>语义与 C 端管理器一致：多连接并存、终态/超时/出错摘除、建链即发 connected、
 * 定时心跳防网关回收空闲连接；总开关复用 {@code xinfei.sse.enabled}。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@Component
public class EmployeeSseManager {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = ObjectMapperHolder.getDefault();

    /** SSE 总开关（与 C 端通道共用，关闭时停止心跳保活） */
    @Value("${xinfei.sse.enabled:true}")
    private boolean enabled;

    /** 单条连接超时时间（毫秒），与 C 端通道共用配置 */
    @Value("${xinfei.sse.timeout:1800000}")
    private long timeout;

    /**
     * 注册一个员工的 SSE 连接。
     *
     * @param employeeId 员工ID（BaseContext.getCurrentId()，employee 表主键）
     * @return 交由 Spring MVC 挂起的 SseEmitter
     */
    public SseEmitter subscribe(Long employeeId) {
        SseEmitter emitter = new SseEmitter(timeout);
        CopyOnWriteArrayList<SseEmitter> list =
                emitters.computeIfAbsent(employeeId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        // 连接结束/超时/出错时务必从集合移除，避免内存泄漏与向死连接写数据
        emitter.onCompletion(() -> remove(employeeId, emitter));
        emitter.onTimeout(() -> {
            remove(employeeId, emitter);
            emitter.complete();
        });
        emitter.onError(ex -> remove(employeeId, emitter));

        // 建链后立即下发 connected 事件，供前端确认通道可用
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"status\":\"ok\"}"));
        } catch (IOException e) {
            remove(employeeId, emitter);
        }
        log.debug("[员工SSE] 员工订阅 employeeId={}, 当前连接数={}", employeeId, list.size());
        return emitter;
    }

    /**
     * 向某员工的所有在线连接推送事件；员工离线（无连接）时静默返回，不抛异常。
     *
     * @param employeeId 目标员工ID
     * @param eventName  SSE 事件名
     * @param data       载荷（将被 JSON 序列化）
     */
    public void sendToUser(Long employeeId, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(employeeId);
        if (list == null || list.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            log.warn("[员工SSE] 载荷序列化失败 employeeId={}", employeeId, e);
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (Exception e) {
                // 单条连接写入失败（客户端已断开）即摘除并结束，不影响同员工其他连接
                list.remove(emitter);
                emitter.complete();
            }
        }
    }

    /**
     * 定时心跳：向所有连接下发 heartbeat，防止 Nginx/网关回收空闲连接，并剔除死连接。
     */
    @Scheduled(fixedDelayString = "${xinfei.sse.heartbeat:30000}")
    public void broadcastHeartbeat() {
        if (!enabled) {
            return;
        }
        for (Map.Entry<Long, CopyOnWriteArrayList<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("{}"));
                } catch (Exception e) {
                    entry.getValue().remove(emitter);
                    emitter.complete();
                }
            }
        }
    }

    /**
     * 从集合中移除指定连接；若员工已无任何连接则移除整个键。
     */
    private void remove(Long employeeId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(employeeId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(employeeId, list);
        }
    }
}
