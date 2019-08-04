/*
 * 
 * Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and 
 * Multi-Agent Training Environment (CHECKMATE)
 *
 * Copyright 2006 Jeff Ridder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ridderware.checkmate;

import java.util.Set;

/**
 * Interface declaring methods for objects that receive radar tracks.
 *
 * @author Jeff Ridder
 */
public interface IRadarTrackReceiver
{
    /**
     * Method to be called by subordinate objects to report active radar tracks to their superior
     * or parent platform.
     * @param radar Radar object that is the source of the tracks.
     * @param tracks active tracks being reported.
     */
    public void reportActiveTracks(Radar radar, Set<Platform> tracks);

    /**
     * Method to be called by subordinate objects to report dropped radar tracks to their superior
     * or parent platform.
     * @param radar Radar object that dropped the tracks.
     * @param dropped_tracks dropped tracks being reported.
     */
    public void reportDroppedTracks(Radar radar, Set<Platform> dropped_tracks);
}

   