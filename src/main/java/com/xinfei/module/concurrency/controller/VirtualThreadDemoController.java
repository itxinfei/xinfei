package com.xinfei.module.concurrency.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * <p>
 * JDK 25 并发特性演示端点（升级验证用，非业务功能）。
 * </p>
 *
 * <p>三个端点分别回答三个问题：</p>
 * <ol>
 *     <li>{@code /current}：{@code spring.threads.virtual.enabled=true} 之后，
 *         Tomcat 的请求线程到底是不是虚拟线程。</li>
 *     <li>{@code /scale}：同样一批「阻塞等待型」任务，固定平台线程池与虚拟线程池的耗时差多少。</li>
 *     <li>{@code /context}：本项目用 {@link BaseContext}（ThreadLocal）存登录态，
 *         换成虚拟线程后上下文会不会丢；以及 ScopedValue 的行为差异。</li>
 * </ol>
 *
 * <p>本类刻意使用 JDK 25 语法（record / var / ScopedValue）作为新特性示例。</p>
 *
 * @author 心飞为你飞
 * @since 2026-10-20
 */
@Tag(name = "JDK 25 并发特性演示", description = "虚拟线程与上下文传播的可观测演示端点")
@RestController
@RequestMapping("/demo/threads")
public class VirtualThreadDemoController {

    /**
     * 演示用作用域值。ScopedValue 在 JDK 25 已是正式 API（无需 --enable-preview），
     * 但实测语义与早期预览版不同：<b>绑定只在当前线程的 where().run() 作用域内可见，
     * 不会传播到任何子线程</b>——Thread.startVirtualThread、newVirtualThreadPerTaskExecutor、
     * 平台线程池里取都会抛 NoSuchElementException: ScopedValue not bound。
     * 跨线程传播需要结构化并发 StructuredTaskScope，而它在 JDK 25 仍是 preview API。
     */
    private static final ScopedValue<Long> SCOPED_USER_ID = ScopedValue.newInstance();

    /** 平台线程池大小：故意远小于任务数，让「线程不够用」的排队成本显形。 */
    private static final int PLATFORM_POOL_SIZE = 200;

    /**
     * 容器上下文，用于按名探测各 executor 实际使用的线程类型。
     */
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext context;

    /** 当前请求线程信息。 */
    public record ThreadInfo(String threadName, boolean virtual, long threadId) {
    }

    /** 平台线程池 vs 虚拟线程的耗时对比（毫秒）。 */
    public record ScaleComparison(int tasks,
                                  long blockMillisPerTask,
                                  int platformPoolSize,
                                  long platformMillis,
                                  long virtualMillis,
                                  double speedup) {
    }

    /** 上下文传播五种情形的实测结果。 */
    public record ContextPropagation(Object threadLocalInScope,
                                     Object threadLocalInChildThread,
                                     Object scopedValueInScope,
                                     Object scopedValueInChildThread,
                                     Object threadLocalManuallyCopied,
                                     String conclusion) {
    }

    @Operation(summary = "查看当前请求线程是否为虚拟线程")
    @GetMapping("/current")
    public R<ThreadInfo> current() {
        var thread = Thread.currentThread();
        return R.success(new ThreadInfo(thread.getName(), thread.isVirtual(), thread.threadId()));
    }

    /** 某个 executor 的探测结果。 */
    public record ExecutorProbe(String beanName, String beanType, String threadName, boolean virtual, String note) {
    }

    @Operation(summary = "实测各 executor 跑在平台线程还是虚拟线程")
    @GetMapping("/executors")
    public R<java.util.List<ExecutorProbe>> executors() {
        // 覆盖三类：Boot 自动配置的默认异步执行器、Boot 调度器、本项目自定义线程池
        var beanNames = java.util.List.of(
                "applicationTaskExecutor", "taskScheduler",
                "cacheDoubleDeleteExecutor", "recommendExecutor", "aiHealthProbeExecutor",
                "eventListenerExecutor");
        var probes = new ArrayList<ExecutorProbe>(beanNames.size());
        for (var beanName : beanNames) {
            probes.add(probeExecutor(beanName));
        }
        return R.success(probes);
    }

    /**
     * 往指定 executor 投一个探测任务，回报它实际落在哪种线程上。
     *
     * <p>结论性事实：{@code spring.threads.virtual.enabled=true} 只替换 Boot 自动配置的
     * {@code applicationTaskExecutor} 与 {@code taskScheduler}；项目里自己 new 出来的
     * {@code ThreadPoolTaskExecutor} 和 {@code Executors.newScheduledThreadPool(3)} 不受影响，
     * 仍然是平台线程。想全量虚拟线程化必须逐个改造这些自定义池。</p>
     */
    private ExecutorProbe probeExecutor(String beanName) {
        if (!context.containsBean(beanName)) {
            return new ExecutorProbe(beanName, "-", "-", false, "容器内无此 bean");
        }
        var bean = context.getBean(beanName);
        var typeName = bean.getClass().getSimpleName();
        var threadName = new AtomicReference<String>();
        var virtual = new java.util.concurrent.atomic.AtomicBoolean();
        var latch = new java.util.concurrent.CountDownLatch(1);
        Runnable probeTask = () -> {
            var thread = Thread.currentThread();
            threadName.set(thread.getName());
            virtual.set(thread.isVirtual());
            latch.countDown();
        };
        try {
            if (bean instanceof java.util.concurrent.Executor executor) {
                executor.execute(probeTask);
            } else if (bean instanceof org.springframework.scheduling.TaskScheduler scheduler) {
                scheduler.schedule(probeTask, java.time.Instant.now());
            } else {
                return new ExecutorProbe(beanName, typeName, "-", false, "类型不支持投递探测任务");
            }
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                return new ExecutorProbe(beanName, typeName, "-", false, "探测任务 5s 内未执行");
            }
        } catch (Exception e) {
            return new ExecutorProbe(beanName, typeName, "-", false, "探测异常: " + e.getMessage());
        }
        return new ExecutorProbe(beanName, typeName, threadName.get(), virtual.get(), "ok");
    }

    @Operation(summary = "固定平台线程池与虚拟线程执行同一批阻塞任务的耗时对比")
    @GetMapping("/scale")
    public R<ScaleComparison> scale(@RequestParam(defaultValue = "2000") int tasks,
                                    @RequestParam(defaultValue = "50") long blockMillisPerTask) {
        var platformMillis = measure(() -> Executors.newFixedThreadPool(PLATFORM_POOL_SIZE), tasks, blockMillisPerTask);
        var virtualMillis = measure(Executors::newVirtualThreadPerTaskExecutor, tasks, blockMillisPerTask);
        var speedup = virtualMillis == 0 ? 0d : Math.round((double) platformMillis / virtualMillis * 10) / 10d;
        return R.success(new ScaleComparison(tasks, blockMillisPerTask, PLATFORM_POOL_SIZE,
                platformMillis, virtualMillis, speedup));
    }

    @Operation(summary = "ThreadLocal 与 ScopedValue 在虚拟线程下的上下文传播实测")
    @GetMapping("/context")
    public R<ContextPropagation> context() {
        var previousUserId = BaseContext.getCurrentId();
        try {
            BaseContext.setCurrentId(1001L);

            // 1) ThreadLocal 同一线程内可读
            var threadLocalInScope = BaseContext.getCurrentId();
            // 2) ThreadLocal 跨到子虚拟线程即丢失
            var threadLocalInChild = callInNewVirtualThread(BaseContext::getCurrentId);

            var scopedInScope = new AtomicReference<Object>();
            var scopedInChild = new AtomicReference<Object>();
            ScopedValue.where(SCOPED_USER_ID, 1001L).run(() -> {
                // 3) ScopedValue 在作用域内可读
                scopedInScope.set(read(SCOPED_USER_ID::get));
                // 4) 跨到子虚拟线程同样拿不到（实测 JDK 25 不做线程间传播）
                scopedInChild.set(callInNewVirtualThread(() -> read(SCOPED_USER_ID::get)));
            });

            // 5) ThreadLocal 的补救写法：提交前捕获，子线程内重放，用完必须清
            var captured = BaseContext.getCurrentId();
            var manuallyCopied = callInNewVirtualThread(() -> {
                BaseContext.setCurrentId(captured);
                try {
                    return BaseContext.getCurrentId();
                } finally {
                    BaseContext.remove();
                }
            });

            return R.success(new ContextPropagation(threadLocalInScope, threadLocalInChild,
                    scopedInScope.get(), scopedInChild.get(), manuallyCopied,
                    "同线程内两者都能读到；跨到子虚拟线程都拿不到——ThreadLocal 返回 null，"
                            + "ScopedValue 抛 ScopedValue not bound（JDK 25 正式版不做线程间传播，"
                            + "要传播得用仍是 preview 的 StructuredTaskScope）。"
                            + "所以 BaseContext 若只在请求线程内使用可平迁 ScopedValue；"
                            + "凡有把任务 submit 到别的线程池再读上下文的代码，两者都得手动捕获-重放。"));
        } finally {
            // 恢复本次请求原有的登录态，别把演示代码写进上下文的值留给后续处理链
            BaseContext.remove();
            if (previousUserId != null) {
                BaseContext.setCurrentId(previousUserId);
            }
        }
    }

    /**
     * 用指定执行器跑 tasks 个「纯阻塞等待」任务，返回总耗时毫秒。
     *
     * @param executorFactory 执行器工厂，调用方决定是平台线程池还是虚拟线程池
     */
    private long measure(Supplier<ExecutorService> executorFactory, int tasks, long blockMillisPerTask) {
        try (var executor = executorFactory.get()) {
            var start = System.nanoTime();
            var futures = new ArrayList<Future<?>>(tasks);
            for (var i = 0; i < tasks; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        // 模拟等下游 IO：平台线程会占住自己，虚拟线程会在此卸载载体线程
                        Thread.sleep(Duration.ofMillis(blockMillisPerTask));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            for (var future : futures) {
                future.get();
            }
            return (System.nanoTime() - start) / 1_000_000L;
        } catch (Exception e) {
            throw new IllegalStateException("并发演示执行失败: " + e.getMessage(), e);
        }
    }

    /** 在一个新虚拟线程里求值并取回结果（阻塞等待完成）。 */
    private <T> T callInNewVirtualThread(Supplier<T> supplier) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return executor.submit(supplier::get).get();
        } catch (Exception e) {
            throw new IllegalStateException("虚拟线程求值失败: " + e.getMessage(), e);
        }
    }

    /** 求值并把异常转成可读文本，让端点能直接展示「拿不到」这个事实而不是 500。 */
    private static Object read(Supplier<?> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
