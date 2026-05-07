package com.utkarsh.web_crawler.frontier;

public enum Priority {

    HIGH(0.6),
    MEDIUM(0.3),
    LOW(0.1);

    private final double weight;

    Priority(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }
}
