package cait.collector.web.api.v1;

import cait.collector.job.service.DataUpdateJobService;
import cait.collector.web.data.code.ServiceCode;
import cait.collector.web.exception.ServiceException;
import cait.collector.web.model.request.JobUpdateRequest;
import cait.collector.web.model.response.Response;
import cait.collector.web.model.response.TimerState;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@Slf4j
@RestController
@RequestMapping("/timer")
public class TimerController {

    private final DataUpdateJobService dataUpdateJobService;

    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    public TimerController(DataUpdateJobService dataUpdateJobService,
                           ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        this.dataUpdateJobService = dataUpdateJobService;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    }

    @GetMapping("/cron")
    public Response<String> getCron() {
        try {
            var cron = dataUpdateJobService.getCron();
            return Response.success(cron);
        } catch (Exception e) {
            return Response.error(ServiceCode.UnknownErr, e.toString());
        }
    }

    @PostMapping("/update")
    public Response<String> updateJob(@RequestBody JobUpdateRequest request) {
        try {
            dataUpdateJobService.update(request.getCron());
        } catch (SchedulerException e) {
            return Response.error(ServiceCode.ParamWrong, String.format("参数错误：%s", e.getMessage()));
        }

        return Response.success(request.getCron());
    }

    @PostMapping("/execute-now")
    public Response<Object> executeNow(@RequestParam(name = "datasource", required = false) String datasource) {
        threadPoolTaskExecutor.execute(() -> {
            try {
                if (datasource == null) {
                    dataUpdateJobService.executeNow();
                } else {
                    var type = switch (datasource) {
                        case "custom" -> DataUpdateJobService.DatasourceType.Custom;
                        case "easyspider" -> DataUpdateJobService.DatasourceType.Easyspider;
                        case "gdelt" -> DataUpdateJobService.DatasourceType.GDELT;
                        default -> throw new ServiceException(-1, "错误的数据源请求类型: " + datasource);
                    };

                    dataUpdateJobService.executeNow(type);
                }
            } catch (Exception e) {
                log.error("error when execute datasource update job", e);
            }
        });

        return Response.success();
    }

    @PostMapping("/pause")
    public Response<Object> pause() throws SchedulerException {
        dataUpdateJobService.pause();
        return Response.success();
    }

    @PostMapping("/resume")
    public Response<Object> resume() throws SchedulerException {
        dataUpdateJobService.resume();
        return Response.success();
    }

    @GetMapping("/state")
    public Response<TimerState> state() throws SchedulerException {
        var state = dataUpdateJobService.state();

        var triggerState = switch (state.getState()) {
            case NONE -> 0;
            case NORMAL -> 1;
            case PAUSED -> 2;
            case COMPLETE -> 3;
            case ERROR -> 4;
            case BLOCKED -> 5;
        };

        var timerState = TimerState.builder()
                .state(triggerState)
                .startTime(timestamp(state.getStartTime()))
                .endTime(timestamp(state.getEndTime()))
                .finalFireTime(timestamp(state.getFinalFireTime()))
                .nextFireTime(timestamp(state.getNextFireTime()))
                .previousFireTime(timestamp(state.getPreviousFireTime()))
                .build();

        return Response.success(timerState);
    }

    private static long timestamp(Date date) {
        if (date == null) {
            return 0;
        }

        return date.getTime();
    }
}
