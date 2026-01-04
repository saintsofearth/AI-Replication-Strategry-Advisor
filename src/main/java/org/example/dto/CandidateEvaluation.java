package org.example.dto;

import lombok.Data;
import org.example.constants.Topology;

import java.util.List;
import java.util.Map;

@Data
public class CandidateEvaluation {
    private Topology topology;
    private int totalScore;
    private Map<String, Integer> axisScores;
    private List<String> gatesFired;
    private List<String> warnings;
    private List<String> keyReasons;
}
