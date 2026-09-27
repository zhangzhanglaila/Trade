package cait.collector.web.model.response;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class TimerState {

    private int state;

    /**
     * 任务开始时间：
     * 触发器首次允许触发任务的时间。
     */
    private long startTime;

    /**
     * 任务结束时间：
     * 触发器不再触发任务的截止时间。
     * 如果为 null，表示任务无限期执行。
     */
    private long endTime;

    /**
     * 最后一次预计触发时间：
     * 根据调度规则、开始时间和结束时间计算出的最后一次计划触发时间。
     */
    private long finalFireTime;

    /**
     * 下次触发时间：
     * Quartz 根据当前时间和调度计划，计算出的下一次执行时间。
     * 如果为 null，表示没有下一次执行（例如任务已完成或被暂停）。
     */
    private long nextFireTime;

    /**
     * 上次触发时间：
     * 记录该任务最近一次实际触发执行的时间。
     * 如果任务还未执行过，则为 null。
     */
    private long previousFireTime;
}
