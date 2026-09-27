package cait.collector.job.service;

import cait.collector.configure.CaitCollectorConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class DataUpdateJobService {

    private final Scheduler scheduler;
    private final CaitCollectorConfiguration caitCollectorConfiguration;
    private final DataUpdateJob dataUpdateJob;

    public DataUpdateJobService(Scheduler scheduler, CaitCollectorConfiguration caitCollectorConfiguration, DataUpdateJob dataUpdateJob) {
        this.scheduler = scheduler;
        this.caitCollectorConfiguration = caitCollectorConfiguration;
        this.dataUpdateJob = dataUpdateJob;
    }

    private static final String JOB_NAME = "dataUpdateJob";
    private static final String TRIGGER_NAME = "dataUpdateTrigger";

    private static final String JOB_GROUP = "cait_job_group";
    private static final String TRIGGER_GROUP = "cait_trigger_group";

    private static final JobKey UpdateTimerJobKey = JobKey.jobKey(JOB_NAME, JOB_GROUP);
    private static final TriggerKey UpdateTimerTriggerKey = TriggerKey.triggerKey(TRIGGER_NAME, TRIGGER_GROUP);

    @PostConstruct
    public void init() throws SchedulerException {
        switch (caitCollectorConfiguration.getRunMode()) {
            case "all", "job" -> {
            }
            default -> {
                return;
            }
        }

        if (!scheduler.checkExists(UpdateTimerJobKey)) {
            JobDetail jobDetail = JobBuilder.newJob(DataUpdateJob.class)
                    .withIdentity(UpdateTimerJobKey)
                    .build();

            var dataUpdateJobCron = caitCollectorConfiguration.getDataUpdateJobCron();
            log.info("Data update job cron: '{}'", dataUpdateJobCron);

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(UpdateTimerTriggerKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(dataUpdateJobCron))
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
        }
    }

    public void update(String cronExpression) throws SchedulerException {
        if (scheduler.checkExists(UpdateTimerTriggerKey)) {
            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_NAME, TRIGGER_GROUP)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .build();

            scheduler.rescheduleJob(UpdateTimerTriggerKey, newTrigger);
        }
    }

    public String getCron() throws SchedulerException {
        if (scheduler.checkExists(UpdateTimerTriggerKey)) {
            var trigger = scheduler.getTrigger(UpdateTimerTriggerKey);
            if (trigger instanceof CronTrigger cronTrigger) {
                return cronTrigger.getCronExpression();
            }
        }

        return "";
    }

    public void executeNow() throws SchedulerException {
        if (scheduler.checkExists(UpdateTimerJobKey)) {
            scheduler.triggerJob(UpdateTimerJobKey);
        }
    }

    public enum DatasourceType {
        Custom, Easyspider, GDELT
    }

    public void executeNow(DatasourceType datasourceType) {
        switch (datasourceType) {
            case Custom -> dataUpdateJob.executeCustomDatasource();
            case Easyspider -> dataUpdateJob.executeEasyspiderDatasource();
            case GDELT -> dataUpdateJob.executeGDELTDatasource();
        }
    }

    public void pause() throws SchedulerException {
        if (scheduler.checkExists(UpdateTimerJobKey)) {
            scheduler.pauseJob(UpdateTimerJobKey);
        }
    }

    public void resume() throws SchedulerException {
        if (scheduler.checkExists(UpdateTimerJobKey)) {
            scheduler.resumeJob(UpdateTimerJobKey);
        }
    }

    @Data
    @Builder
    public static class JobState {

        private Trigger.TriggerState state;

        /**
         * 任务开始时间：
         * 触发器首次允许触发任务的时间。
         */
        private Date startTime;

        /**
         * 任务结束时间：
         * 触发器不再触发任务的截止时间。
         * 如果为 null，表示任务无限期执行。
         */
        private Date endTime;

        /**
         * 最后一次预计触发时间：
         * 根据调度规则、开始时间和结束时间计算出的最后一次计划触发时间。
         */
        private Date finalFireTime;

        /**
         * 下次触发时间：
         * Quartz 根据当前时间和调度计划，计算出的下一次执行时间。
         * 如果为 null，表示没有下一次执行（例如任务已完成或被暂停）。
         */
        private Date nextFireTime;

        /**
         * 上次触发时间：
         * 记录该任务最近一次实际触发执行的时间。
         * 如果任务还未执行过，则为 null。
         */
        private Date previousFireTime;

        private Date previousExecuteTime;
    }

    public JobState state() throws SchedulerException {
        if (!scheduler.checkExists(UpdateTimerTriggerKey)) {
            return null;
        }

        var trigger = scheduler.getTrigger(UpdateTimerTriggerKey);

        return JobState.builder()
                .state(scheduler.getTriggerState(UpdateTimerTriggerKey))
                .startTime(trigger.getStartTime())
                .endTime(trigger.getEndTime())
                .finalFireTime(trigger.getFinalFireTime())
                .nextFireTime(trigger.getNextFireTime())
                .previousFireTime(trigger.getPreviousFireTime())
                .build();
    }
}
