package com.relayops.assistant.adapter.out.persistence;

/** Row of a vector similarity query. */
public class MemoryMatchRow {

    private Long factId;
    private Double distance;

    public Long getFactId() { return factId; }
    public void setFactId(Long factId) { this.factId = factId; }
    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }
}
