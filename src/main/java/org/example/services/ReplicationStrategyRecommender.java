package org.example.services;

import org.example.constants.*;
import org.example.dto.CandidateEvaluation;
import org.example.dto.ReplicationRecommendation;
import org.example.dto.ReplicationRequirements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReplicationStrategyRecommender {
    /*
        We need to think about core logic at 2 levels:
        1. Hard constraints
        2. A weighted scoring matrix.

        Hard constraint: Given our input what is an obviously a bad choice.

    */

    private static final int BIG_PENALTY = -60;
    private static final int MED_PENALTY = -30;
    private static final int SMALL_PENALTY = -15;

    public ReplicationRecommendation advise(ReplicationRequirements requirements) {


        List<CandidateEvaluation> evals = new ArrayList<>();
        evals.add(evaluate(Topology.LEADER_FOLLOWER, requirements));
    }

    private CandidateEvaluation evaluate(Topology topology, ReplicationRequirements requirements) {
        Map<String, Integer> axis = new LinkedHashMap<>();
        List<String> gates = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        int gatePenalty = applyGates(topology, requirements, gates, warnings, reasons);

        axis.put("consistencyFit", scoreConsistencyFit(topology, requirements));
        axis.put("availabilityFit", scoreAvailabilityFit(topology, requirements));
        axis.put("latencyFit", scoreLatencyFit(topology, requirements));
        axis.put("conflictRisk", scoreConflictRisk(topology, requirements));
    }

    private int applyGates(Topology topology, ReplicationRequirements req, List<String> gates, List<String> warnings, List<String> reasons) {
        int penalty = 0;

        if (req.getRegions() == Regions.MULTI_WRITE) {
            if (topology == Topology.LEADER_FOLLOWER) {
                gates.add("MULTI_WRITE penalizes LEADER_FOLLOWER (single write region assumption)");
                warnings.add("Multi-region writes are difficult with Leader-follower unless you centralize writes to one region");
                penalty += BIG_PENALTY;
            }
        }

        if (req.getRegions() == Regions.MULTI_WRITE && req.getConflictTolerance() == ConflictTolerance.NONE) {
            if (topology == Topology.MULTI_LEADER) {
                gates.add("CONFLICT_NONE + MULTI_WRITE penalizes MULTI_LEADER");
                warnings.add("Multi-leader with multi-region writes can create conflicts; conflict-free data model or centralized writes may be required.");
                penalty += BIG_PENALTY;
            }
            if (topology == Topology.LEADERLESS) {
                gates.add("CONFLICT_NONE + MULTI_WRITE penalizes LEADERLESS");
                warnings.add("Leaderless setup need conflict resolution (versioning/repair). If conflicts are unacceptable, consider centralizing writes.");
                penalty += MED_PENALTY;
            }
        }

        if (req.getDataLossTolerance() == DataLossTolerance.ZERO) {
            warnings.add("Zero data loss tolerance typically requires synchronous/majority acknowledegment; async replication increases risk under failures.");
            if (topology == Topology.LEADERLESS || topology == Topology.MULTI_LEADER) penalty += SMALL_PENALTY;
        }

        if (req.getConsistency() == Consistency.STRONG && req.getRegions() != Regions.SINGLE) {
            warnings.add("Strong consistency across regions increases write latency and/or reduces availability under partitions");
            if (topology == Topology.MULTI_LEADER) penalty += MED_PENALTY;
            if (topology == Topology.LEADERLESS) penalty += SMALL_PENALTY;
        }

        Integer latency = req.getLatencyTargetMsP99();
        if (latency != null && latency <= 50 && req.getRegions() != Regions.SINGLE && req.getConsistency() == Consistency.STRONG) {
            warnings.add("P99 <= 50ms with strong consistency across multiple regions is usually unrealistic without relaxing constraints.");
            penalty += SMALL_PENALTY;
        }

        if (!gates.isEmpty()) {
            reasons.add("Gate penalties applied: " + String.join("; ", gates));
        }

        return penalty;
    }

    private int scoreConsistencyFit(Topology t, ReplicationRequirements req) {
        return switch (req.getConsistency()) {
            case STRONG -> switch(t) {
                case LEADER_FOLLOWER -> 10;
                case MULTI_LEADER -> 3;
                case LEADERLESS -> 6;
            };
            case SESSION -> switch(t) {
                case LEADER_FOLLOWER -> 8;
                case MULTI_LEADER -> 6;
                case LEADERLESS -> 7;
            };
            case EVENTUAL -> switch(t) {
                case LEADER_FOLLOWER -> 6;
                case MULTI_LEADER -> 8;
                case LEADERLESS -> 9;
            };
        };
    }

    private int scoreAvailabilityFit(Topology t, ReplicationRequirements req) {
        int base = switch(t) {
            case LEADER_FOLLOWER -> 6;
            case MULTI_LEADER -> 8;
            case LEADERLESS -> 9;
        };

        return switch(req.getAvailabilityPriority()) {
            case HIGH -> base + switch(t) {
                case LEADER_FOLLOWER -> -2;
                case MULTI_LEADER, LEADERLESS -> +1;
            };
            case MED -> base;
            case LOW -> base - 1;
        };
    }

    private int scoreLatencyFit(Topology t, ReplicationRequirements req) {
        int p99 = req.getLatencyTargetMsP99() == null ? 150 : req.getLatencyTargetMsP99();
        boolean tight = p99 <= 80;

        if (req.getRegions() == Regions.SINGLE) {
            return switch(t) {
                case LEADER_FOLLOWER -> tight ? 9 : 8;
                case MULTI_LEADER -> 7;
                case LEADERLESS -> tight ? 7 : 8;
            };
        }

        return switch(t) {
            case LEADER_FOLLOWER -> (req.getConsistency() == Consistency.STRONG) ? (tight ? 4 : 5) : (tight ? 5 : 6);
            case MULTI_LEADER -> (req.getRegions() == Regions.MULTI_WRITE) ? (8) : (7);
            case LEADERLESS -> 7;
        };
    }

    private int scoreConflictRisk(Topology t, ReplicationRequirements req) {
        return switch(t) {
            case LEADER_FOLLOWER -> 10;
            case MULTI_LEADER -> switch(req.getConflictTolerance()) {
                case NONE -> 2;
                case LOW -> 4;
                case HIGH -> 6;
            };
            case LEADERLESS -> switch(req.getConflictTolerance()) {
                case NONE -> 4;
                case LOW -> 6;
                case HIGH -> 7;
            };
        };
    }


}
