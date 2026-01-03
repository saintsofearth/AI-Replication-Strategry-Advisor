package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.constants.ReadPolicy;
import org.example.constants.ReplicationMode;
import org.example.constants.Topology;
import org.example.constants.WritePolicy;

import java.util.List;

@Data
@NoArgsConstructor
public class ReplicationRecommendation {
    private Topology topology;
    private ReplicationMode replicationMode;
    private ReadPolicy readPolicy;
    private WritePolicy writePolicy;
    private Quorum quorum;
    private List<String> warnings;
    private List<String> tradeoffs;
    private String failureBehaviour;
    private String explanation;

    @Data
    @AllArgsConstructor
    private class Quorum {
        Integer n;
        Integer r;
        Integer w;
    }
}
