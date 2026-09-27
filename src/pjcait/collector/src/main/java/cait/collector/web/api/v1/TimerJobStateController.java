package cait.collector.web.api.v1;

import cait.collector.job.service.DataUpdateJobService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/job/state")
public class TimerJobStateController {

    private final DataUpdateJobService dataUpdateJobService;

    public TimerJobStateController(DataUpdateJobService dataUpdateJobService) {
        this.dataUpdateJobService = dataUpdateJobService;
    }

}
