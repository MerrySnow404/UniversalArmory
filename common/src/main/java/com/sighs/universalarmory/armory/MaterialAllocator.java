package com.sighs.universalarmory.armory;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MaterialAllocator {
    private MaterialAllocator() {
    }

    public static long[] allocate(List<BigInteger> available, long[] required, boolean[][] matches) {
        if (matches.length != available.size()) {
            throw new IllegalArgumentException("Invalid material matrix");
        }
        int sink = available.size() + required.length + 1;
        List<List<Edge>> graph = new ArrayList<>();
        for (int i = 0; i <= sink; i++) {
            graph.add(new ArrayList<>());
        }
        long total = 0;
        for (long count : required) {
            if (count < 0) {
                throw new IllegalArgumentException("Negative material requirement");
            }
            total = Math.addExact(total, count);
        }
        Edge[] supplies = new Edge[available.size()];
        for (int supply = 0; supply < available.size(); supply++) {
            if (available.get(supply).signum() < 0 || matches[supply].length != required.length) {
                throw new IllegalArgumentException("Invalid material supply");
            }
            long capacity = available.get(supply).min(BigInteger.valueOf(total)).longValueExact();
            supplies[supply] = connect(graph, 0, supply + 1, capacity);
            for (int demand = 0; demand < required.length; demand++) {
                if (matches[supply][demand]) {
                    connect(graph, supply + 1, available.size() + demand + 1, required[demand]);
                }
            }
        }
        for (int demand = 0; demand < required.length; demand++) {
            connect(graph, available.size() + demand + 1, sink, required[demand]);
        }
        long supplied = 0;
        while (supplied < total) {
            int[] parents = new int[graph.size()];
            Edge[] path = new Edge[graph.size()];
            Arrays.fill(parents, -1);
            parents[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(0);
            while (!queue.isEmpty() && parents[sink] == -1) {
                int from = queue.removeFirst();
                for (Edge edge : graph.get(from)) {
                    if (edge.capacity > 0 && parents[edge.to] == -1) {
                        parents[edge.to] = from;
                        path[edge.to] = edge;
                        queue.add(edge.to);
                    }
                }
            }
            if (parents[sink] == -1) {
                throw new IllegalArgumentException("库存和背包中的材料不足");
            }
            long amount = total - supplied;
            for (int node = sink; node != 0; node = parents[node]) {
                amount = Math.min(amount, path[node].capacity);
            }
            for (int node = sink; node != 0; node = parents[node]) {
                Edge edge = path[node];
                edge.capacity -= amount;
                edge.reverse.capacity += amount;
            }
            supplied += amount;
        }
        long[] consumed = new long[supplies.length];
        for (int i = 0; i < supplies.length; i++) {
            consumed[i] = supplies[i].reverse.capacity;
        }
        return consumed;
    }

    private static Edge connect(List<List<Edge>> graph, int from, int to, long capacity) {
        Edge forward = new Edge(to, capacity);
        Edge reverse = new Edge(from, 0);
        forward.reverse = reverse;
        reverse.reverse = forward;
        graph.get(from).add(forward);
        graph.get(to).add(reverse);
        return forward;
    }

    private static final class Edge {
        private final int to;
        private long capacity;
        private Edge reverse;

        private Edge(int to, long capacity) {
            this.to = to;
            this.capacity = capacity;
        }
    }
}
