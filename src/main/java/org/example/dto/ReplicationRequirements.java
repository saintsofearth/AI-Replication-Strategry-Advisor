package org.example.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.constants.*;

@Data
@NoArgsConstructor
public class ReplicationRequirements {
    private Workload workload;
    private Regions regions;
    private Consistency consistency;
    private AvailabilityPriority availabilityPriority;
    private Integer latencyTargetMsP99;
    private DataLossTolerance dataLossTolerance;
    private ConflictTolerance conflictTolerance;
}
