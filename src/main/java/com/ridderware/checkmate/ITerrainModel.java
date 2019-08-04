/*
 * 
 * Coadaptive Heterogeneous simulation Engine for Combat Kill-webs and 
 * Multi-Agent Training Environment (CHECKMATE)
 *
 * Copyright 2007 Jeff Ridder
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

import com.ridderware.fuse.Double3D;

/**
 *  Interface for classes that provide terrain services.  These services must include, at a minimum, 
 *  terrain elevation at a specified point and line-of-site between two points.  If a service is applicable
 *  only to a particular Earth model, then it is responsible for performing the necessary checks to restrict
 *  application.
 *
 *  @author Jeff Ridder
 */
public interface ITerrainModel
{
    /**
     *  Returns the terrain elevation at the specified point.
     *
     * @param  x x-coordinate in n.mi. (ENU) or radians (LLA)
     * @param  y y-coordinate in n.mi. (ENU) or radians (LLA)
     * @return terrain elevation in meters
     */
    public double elevation(double x, double y);

    /**
     * Returns the index of the terrain cell covering the specified point.
     * This is useful for platforms to determine when they need to recalculate or invalidate LOS to other platforms.
     * @param  x x-coordinate in n.mi. (ENU) or radians (LLA)
     * @param  y y-coordinate in n.mi. (ENU) or radians (LLA)
     * @return terrain cell index (or -1 if not applicable or outside terrain bounds).
     */
    public int terrainCell(double x, double y);

    /**
     *  Check for unobstructed Line Of Sight between two points on earth.
     *
     * @param  location1    Location to check line of sight from.
     * @param  location2    Location to check line of sight to. 
     * @param  k            Earth Factor.  
     * @return true if Line Of Sight is unobstructed, false otherwise
     */
    public boolean hasLOS(Double3D location1, Double3D location2,
        IEarthModel.EarthFactor k);
}
