/*
 * (C) Copyright 2017-2017, by Assaf Mizrachi and Contributors.
 *
 * JGraphT : a free Java graph-theory library
 *
 * This program and the accompanying materials are dual-licensed under
 * either
 *
 * (a) the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation, or (at your option) any
 * later version.
 *
 * or (per the licensee's choosing)
 *
 * (b) the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 */
package org.jgrapht.alg.scoring;

import java.util.*;
import java.util.concurrent.*;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.util.*;

/**
 * Brandes betweenes centrality.
 * 
 * <p>
 * Computes the betweenes centrality of each vertex of a graph. The betweenness centrality of a node 
 * $v$ v is given by the expression: $g(x)= . For more details see
 * <a href="https://en.wikipedia.org/wiki/Betweenness_centrality">wikipedia</a>.
 *
 * <p>
 * For further reference, see <a href="http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.11.2024&rep=rep1&type=pdf">Ulrik Brandes - 
 * "A Faster Algorithm for Betweenness Centrality"</a>. 
 *
 * 
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 * 
 * @author Assaf Mizrachi
 * @since December 2017
 */
public class BetweenesCentrality<V, E> implements VertexScoringAlgorithm<V, Double>
{

    //TODO
    //test and compare to other techniques
    //complete class documentation.
    //add negative weights support
    
    /**
     * Underlying graph
     */
    private final Graph<V, E> graph;
    /**
     * The actual scores
     */
    private Map<V, Double> scores;
    
    /**
     * Construct a new instance.
     * 
     * @param graph the input graph
     */
    public BetweenesCentrality(Graph<V, E> graph)
    {
        this.graph = Objects.requireNonNull(graph, "Graph cannot be null");        
                
        this.scores = null;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Map<V, Double> getScores()
    {
        if (scores == null) {
            compute();
        }
        return Collections.unmodifiableMap(scores);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Double getVertexScore(V v)
    {
        if (!graph.containsVertex(v)) {
            throw new IllegalArgumentException("Cannot return score of unknown vertex");
        }
        if (scores == null) {
            compute();
        }
        return scores.get(v);
    }
    
    /**
     * Compute the centrality index
     */
    private void compute()
    {
        // initialize result container
        initScores();

        // compute for each source
        this.graph.vertexSet().forEach(s -> compute(s));
        
        //For undirected graph, divide scores by two as each shortest path
        //considered twice.
        if (! this.graph.getType().isDirected()) {
            this.scores.forEach((v, score) -> this.scores.put(v, score = score / 2));
        }
    }
    
    private void compute(V s)
    {
      //initialize
        Stack<V> stack = new Stack<>();
        Map<V, List<V>> predecessors = new HashMap<>();
        this.graph.vertexSet().forEach(w -> predecessors.put(w, new ArrayList<>()));
        
        //Number of shortest paths from s to v
        Map<V, Double> sigma = new HashMap<>();
        this.graph.vertexSet().forEach(t -> sigma.put(t, 0.0));
        sigma.put(s, 1.0);
        
        //Distance (Weight) of the shortest path from s to v
        Map<V, Double> distance = new HashMap<>();
        this.graph.vertexSet().forEach(t -> distance.put(t, Double.POSITIVE_INFINITY));
        distance.put(s, 0.0);

        MyQueue<V, Double> queue =
            this.graph.getType().isWeighted() ? new WeightedQueue() : new UnweightedQueue();
        queue.insert(s, 0.0);
        
        //1. compute the length and the number of shortest paths between all s to v
        while (! queue.isEmpty()) {
            V v = queue.remove();
            stack.push(v);
            
            for (E e : this.graph.outgoingEdgesOf(v)) {
                V w = Graphs.getOppositeVertex(this.graph, e, v);
                double eWeight = graph.getEdgeWeight(e);
                if (eWeight < 0.0) {
                    throw new IllegalArgumentException("Negative edge weight not allowed");
                }
                double d = distance.get(v) + eWeight;
                //w found for the first time?
                if (distance.get(w) == Double.POSITIVE_INFINITY) {                    
                    queue.insert(w, d);
                    distance.put(w, d);
                }                
                //shortest path to w via v?
                if (distance.get(w) >= d) {
                    queue.update(w, d);
                    sigma.put(w, sigma.get(w) + sigma.get(v));
                    predecessors.get(w).add(v);
                }
            }
        }
        
        //2. sum all pair dependencies.
        //The pair-dependency of s and v in w
        Map<V, Double> dependency = new HashMap<>();
        this.graph.vertexSet().forEach(v -> dependency.put(v, 0.0));
        //S returns vertices in order of non-increasing distance from s
        while (! stack.isEmpty()) {
            V w = stack.pop();
            for (V v : predecessors.get(w)) {
                dependency.put(v, dependency.get(v) + (sigma.get(v) / sigma.get(w)) * (1 + dependency.get(w)));                
            }
            if (! w.equals(s)) {
                this.scores.put(w, this.scores.get(w) + dependency.get(w));
            }
        }
    }    
    
    private interface MyQueue<T, D> {
        void insert(T t, D d);
        void update(T t, D d);
        T remove();
        boolean isEmpty();
    }
    
    private class WeightedQueue implements MyQueue<V, Double> {

        FibonacciHeap<V> delegate = new FibonacciHeap<>();
        Map<V, FibonacciHeapNode<V>> seen = new HashMap<>();
        
        @Override
        public void insert(V t, Double d)
        {
            FibonacciHeapNode<V> node = new FibonacciHeapNode<>(t);
            delegate.insert(node, d);
            seen.put(t, node);
        }

        @Override
        public void update(V t, Double d)
        {
            if (! seen.containsKey(t)) {
                throw new IllegalArgumentException("Element " + t + " does not exist in queue");
            }
            delegate.decreaseKey(seen.get(t), d);
        }

        @Override
        public V remove()
        {
            return delegate.removeMin().getData();
        }

        @Override
        public boolean isEmpty()
        {
            return delegate.isEmpty();
        }
        
    }
    
    private class UnweightedQueue implements MyQueue<V, Double> {

        Queue<V> delegate = new LinkedBlockingQueue<>();
        
        @Override
        public void insert(V t, Double d)
        {
            delegate.add(t);
        }

        @Override
        public void update(V t, Double d)
        {
            //do nothing
        }

        @Override
        public V remove()
        {
            return delegate.remove();
        }

        @Override
        public boolean isEmpty()
        {
            return delegate.isEmpty();
        }
        
    }

    private void initScores() {
        this.scores = new HashMap<>();
        this.graph.vertexSet().forEach(v -> this.scores.put(v, 0.0));
    }
}
