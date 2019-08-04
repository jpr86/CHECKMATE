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
 *  Terrain model which implements a "Bald Earth".  This always returns an elevation
 *  of 0, and performs line-of-sight using the radar horizon.  This terrain model works for
 *  any Earth model.
 *
 * @author   Jeff Ridder
 */
public class BaldEarthTerrain implements ITerrainModel
{
    /**
     *  Creates a new instance of BaldEarthTerrain
     */
    public BaldEarthTerrain()
    {
    }

    /**
     *  Returns the terrain elevation at the specified point.
     *
     * @param  x x-coordinate in n.mi. (ENU) or radians (LLA)
     * @param  y y-coordinate in n.mi. (ENU) or radians (LLA)
     * @return terrain elevation in meters
     */
    @Override
    public double elevation(double x, double y)
    {
        return 0;
    }

    /**
     * Returns the index of the terrain cell covering the specified point.
     * This is useful for platforms to determine when they need to recalculate or invalidate LOS to other platforms.
     * @param  x x-coordinate in n.mi. (ENU) or radians (LLA)
     * @param  y y-coordinate in n.mi. (ENU) or radians (LLA)
     * @return terrain cell index (or -1 if not applicable or outside terrain bounds).
     */
    @Override
    public int terrainCell(double x, double y)
    {
        return -1;
    }

    /**
     *  Check for unobstructed Line Of Sight between two points on earth.
     *
     * @param  location1    Location to check line of sight from.
     * @param  location2    Location to check line of sight to.
     * @param  k            Earth Factor.
     * @return true if Line Of Sight is unobstructed, false otherwise
     */
    @Override
    public boolean hasLOS(Double3D location1, Double3D location2,
        IEarthModel.EarthFactor k)
    {
        double distance = CMWorld.getEarthModel().trueDistance(location1,
            location2);

        // horizonMultiplier accounts for k factor and conversion
        // of the altitudes from nmi to feet and the result
        // from miles to nmi. It also brings the 2 of d=sqrt(2a)
        // out from the square root.
        double horizonMultiplier = 82.89750;


        //value before converting altitude from nmi to feet --> 1.063;

        if (k == IEarthModel.EarthFactor.EM_EARTH)
        {
            horizonMultiplier = 95.72179262;
        }

        boolean los = false;
        //value before converting altitude from nmi to feet --> 1.228;
        if (distance < horizonMultiplier * (Math.sqrt(location1.getZ()) +
            Math.sqrt(location2.getZ())))
        {
            //LOS blocked by horizon
            los = true;
        }

        return los;
    }
}
