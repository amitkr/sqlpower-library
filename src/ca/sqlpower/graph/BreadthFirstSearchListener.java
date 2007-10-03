/*
 * Copyright (c) 2007, SQL Power Group Inc.
 *
 * This file is part of Power*MatchMaker.
 *
 * Power*MatchMaker is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Power*MatchMaker is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */


package ca.sqlpower.graph;

/**
 * Listener interface for clients of the BreadthFirstSearch.  Allows
 * client code to take action during specific phases of the search.
 *
 * @param <V> The type of vertices in the graph being searched
 */
public interface BreadthFirstSearchListener<V> {

    /**
     * Called when a node is first discovered by the search.
     * 
     * @param node The node that was discovered
     */
    void nodeDiscovered(V node);
}